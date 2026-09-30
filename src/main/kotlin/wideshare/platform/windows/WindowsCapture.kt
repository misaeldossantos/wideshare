package wideshare.platform.windows

import wideshare.core.tr
import wideshare.core.Buttons
import wideshare.platform.CaptureListener
import wideshare.platform.InputCapture
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.platform.win32.WinUser.HHOOK

class WindowsCapture : InputCapture {
    @Volatile private var remote = false
    private var listener: CaptureListener? = null
    private var thread: Thread? = null
    @Volatile private var threadId = 0
    private var mouseHook: HHOOK? = null
    private var keyboardHook: HHOOK? = null
    private val rawMouse = RawMouse { dx, dy -> if (remote) listener?.onRemoteMove(dx, dy) }

    // Keys/buttons that Windows considers pressed but whose release we swallowed in remote mode.
    private val localKeys = HashSet<Int>()
    private val localButtons = HashSet<Int>()
    private val stuckKeys = HashSet<Pair<Int, Boolean>>()
    private val stuckButtons = HashSet<Int>()

    private val mouseProc = object : LowLevelMouseProc {
        override fun callback(nCode: Int, wParam: WPARAM, info: WinUser.MSLLHOOKSTRUCT): LRESULT {
            if (nCode >= 0 && (info.flags and LLMHF_INJECTED) == 0 && onMouse(wParam.toInt(), info)) return LRESULT(1)
            return user32.CallNextHookEx(mouseHook, nCode, wParam, LPARAM(Pointer.nativeValue(info.pointer)))
        }
    }

    private val keyboardProc = WinUser.LowLevelKeyboardProc { nCode, wParam, info ->
        if (nCode >= 0 && (info.flags and LLKHF_INJECTED) == 0 && onKey(wParam.toInt(), info)) LRESULT(1)
        else user32.CallNextHookEx(keyboardHook, nCode, wParam, LPARAM(Pointer.nativeValue(info.pointer)))
    }

    override fun screen() = primaryScreen()

    override fun start(listener: CaptureListener) {
        this.listener = listener
        val ready = java.util.concurrent.CountDownLatch(1)
        thread = Thread({
            val module = Kernel32.INSTANCE.GetModuleHandle(null)
            mouseHook = user32.SetWindowsHookEx(WH_MOUSE_LL, mouseProc, module, 0)
            keyboardHook = user32.SetWindowsHookEx(WH_KEYBOARD_LL, keyboardProc, module, 0)
            check(rawMouse.register()) { tr("err.rawMouse") }
            threadId = Kernel32.INSTANCE.GetCurrentThreadId()
            ready.countDown()
            val msg = WinUser.MSG()
            while (user32.GetMessage(msg, null, 0, 0) > 0) {
                if (msg.message == WM_INPUT) rawMouse.handle(msg.lParam.toLong())
                user32.TranslateMessage(msg)
                user32.DispatchMessage(msg)
            }
            mouseHook?.let(user32::UnhookWindowsHookEx)
            keyboardHook?.let(user32::UnhookWindowsHookEx)
        }, "win-hooks").apply { isDaemon = true; start() }
        ready.await()
        check(mouseHook != null && keyboardHook != null) { tr("err.hooks") }
    }

    override fun stop() {
        if (remote) setRemote(false, -1, -1)
        if (threadId != 0) user32.PostThreadMessage(threadId, WM_QUIT, WPARAM(0), LPARAM(0))
        thread?.join(1000)
    }

    /** Injected Esc: Windows cancels the Explorer drag in progress. */
    override fun cancelDrag() {
        sendKey(ESC_SCAN, false, true)
        sendKey(ESC_SCAN, false, false)
    }

    @Synchronized
    override fun setRemote(on: Boolean, x: Int, y: Int) {
        if (on) {
            remote = true
        } else {
            remote = false
            if (x >= 0) setCursor(x, y)
            stuckKeys.forEach { (scan, ext) -> sendKey(scan, ext, false) }
            stuckButtons.forEach { b -> buttonFlags(b)?.let { (_, up, data) -> sendMouse(up, data) } }
            stuckKeys.clear(); stuckButtons.clear()
        }
    }

    private companion object {
        const val ESC_SCAN = 0x01
    }

    private fun onMouse(msg: Int, info: WinUser.MSLLHOOKSTRUCT): Boolean {
        val l = listener ?: return false
        val data = info.mouseData
        val button = when (msg) {
            WM_LBUTTONDOWN, WM_LBUTTONUP -> Buttons.LEFT
            WM_RBUTTONDOWN, WM_RBUTTONUP -> Buttons.RIGHT
            WM_MBUTTONDOWN, WM_MBUTTONUP -> Buttons.MIDDLE
            WM_XBUTTONDOWN, WM_XBUTTONUP -> if ((data shr 16) and 0xFFFF == 1) Buttons.BACK else Buttons.FORWARD
            else -> -1
        }
        val down = msg == WM_LBUTTONDOWN || msg == WM_RBUTTONDOWN || msg == WM_MBUTTONDOWN || msg == WM_XBUTTONDOWN

        if (!remote) {
            when {
                msg == WM_MOUSEMOVE -> l.onLocalMove(info.pt.x, info.pt.y)
                button >= 0 -> synchronized(this) { if (down) localButtons += button else localButtons -= button }
            }
            return false
        }
        when {
            // The movement comes from the raw mouse (RawMouse); the cursor stays where it left.
            msg == WM_MOUSEMOVE -> {}
            button >= 0 -> {
                if (!down) synchronized(this) { if (localButtons.remove(button)) stuckButtons += button }
                l.onButton(button, down)
            }
            msg == WM_MOUSEWHEEL -> l.onWheel(0, (data shr 16).toShort().toInt())
            msg == WM_MOUSEHWHEEL -> l.onWheel((data shr 16).toShort().toInt(), 0)
        }
        return true
    }

    private fun onKey(msg: Int, info: WinUser.KBDLLHOOKSTRUCT): Boolean {
        val l = listener ?: return false
        val down = msg == WM_KEYDOWN || msg == WM_SYSKEYDOWN
        val extended = (info.flags and LLKHF_EXTENDED) != 0
        val scan = info.scanCode
        val code = WinKeyMap.toEvdev(scan, extended)
        val id = scan or (if (extended) 0x100 else 0)
        if (!remote) {
            synchronized(this) { if (down) localKeys += id else localKeys -= id }
            return false
        }
        if (!down) synchronized(this) { if (localKeys.remove(id)) stuckKeys += (scan to extended) }
        if (code != 0) l.onKey(code, down)
        return true
    }
}

