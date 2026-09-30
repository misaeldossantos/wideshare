package wideshare.platform.linux

import wideshare.core.Buttons
import wideshare.platform.CaptureListener
import wideshare.platform.InputCapture
import wideshare.platform.ScreenSize
import com.sun.jna.Memory
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.NativeLongByReference

/**
 * Capture on X11. Local mode: queries the pointer position (XQueryPointer). Remote mode: grabs
 * the pointer and the keyboard (XGrab*), re-centers the cursor on every movement and reports the deltas.
 */
class X11Capture : InputCapture {
    @Volatile private var running = false
    @Volatile private var wantRemote = false
    @Volatile private var resumeX = 0
    @Volatile private var resumeY = 0
    private var thread: Thread? = null
    private var probe: Pointer? = null

    override fun screen(): ScreenSize {
        val d = probe ?: openDisplay().also { probe = it }
        val s = x11.XDefaultScreen(d)
        return ScreenSize(x11.XDisplayWidth(d, s), x11.XDisplayHeight(d, s))
    }

    override fun start(listener: CaptureListener) {
        screen()
        running = true
        thread = Thread({ loop(listener) }, "x11-capture").apply { isDaemon = true; start() }
    }

    override fun stop() {
        running = false
        thread?.join(1000)
    }

    override fun setRemote(on: Boolean, x: Int, y: Int) {
        resumeX = x
        resumeY = y
        wantRemote = on
    }

    private fun loop(listener: CaptureListener) {
        val d = openDisplay()
        val root = x11.XDefaultRootWindow(d)
        val screen = x11.XDefaultScreen(d)
        val w = x11.XDisplayWidth(d, screen)
        val h = x11.XDisplayHeight(d, screen)
        val cx = w / 2
        val cy = h / 2
        x11.XkbSetDetectableAutoRepeat(d, 1, null)

        val blank = Memory(16).also { it.clear() }
        val pixmap = x11.XCreateBitmapFromData(d, root, ByteArray(1), 1, 1)
        val cursor = x11.XCreatePixmapCursor(d, pixmap, pixmap, blank, blank, 0, 0)

        val event = Memory(192)
        val rootRet = NativeLongByReference()
        val childRet = NativeLongByReference()
        val rx = IntByReference(); val ry = IntByReference()
        val wx = IntByReference(); val wy = IntByReference(); val mask = IntByReference()
        var remote = false

        try {
            while (running) {
                if (wantRemote != remote) {
                    remote = wantRemote
                    if (remote) {
                        x11.XWarpPointer(d, NONE, root, 0, 0, 0, 0, cx, cy)
                        val mouseMask = (1 shl 2) or (1 shl 3) or (1 shl 6) // press | release | motion
                        x11.XGrabPointer(d, root, 0, mouseMask, 1, 1, NONE, cursor, NativeLong(CURRENT_TIME))
                        x11.XGrabKeyboard(d, root, 0, 1, 1, NativeLong(CURRENT_TIME))
                    } else {
                        x11.XUngrabPointer(d, NativeLong(CURRENT_TIME))
                        x11.XUngrabKeyboard(d, NativeLong(CURRENT_TIME))
                        x11.XWarpPointer(d, NONE, root, 0, 0, 0, 0, resumeX, resumeY)
                    }
                    x11.XFlush(d)
                }
                if (!remote) {
                    x11.XQueryPointer(d, root, rootRet, childRet, rx, ry, wx, wy, mask)
                    listener.onLocalMove(rx.value, ry.value)
                    Thread.sleep(4)
                    continue
                }
                if (x11.XPending(d) == 0) { Thread.sleep(1); continue }
                x11.XNextEvent(d, event)
                when (event.getInt(0)) {
                    MOTION_NOTIFY -> {
                        val dx = event.getInt(72) - cx
                        val dy = event.getInt(76) - cy
                        if (dx != 0 || dy != 0) {
                            listener.onRemoteMove(dx, dy)
                            x11.XWarpPointer(d, NONE, root, 0, 0, 0, 0, cx, cy)
                            x11.XFlush(d)
                        }
                    }
                    BUTTON_PRESS, BUTTON_RELEASE -> {
                        val down = event.getInt(0) == BUTTON_PRESS
                        when (event.getInt(84)) {
                            1 -> listener.onButton(Buttons.LEFT, down)
                            2 -> listener.onButton(Buttons.MIDDLE, down)
                            3 -> listener.onButton(Buttons.RIGHT, down)
                            4 -> if (down) listener.onWheel(0, 120)
                            5 -> if (down) listener.onWheel(0, -120)
                            6 -> if (down) listener.onWheel(-120, 0)
                            7 -> if (down) listener.onWheel(120, 0)
                            8 -> listener.onButton(Buttons.BACK, down)
                            9 -> listener.onButton(Buttons.FORWARD, down)
                            else -> {}
                        }
                    }
                    KEY_PRESS, KEY_RELEASE -> {
                        val code = event.getInt(84) - 8
                        if (code > 0) listener.onKey(code, event.getInt(0) == KEY_PRESS)
                    }
                }
            }
        } catch (_: InterruptedException) {
        } finally {
            if (remote) {
                x11.XUngrabPointer(d, NativeLong(CURRENT_TIME))
                x11.XUngrabKeyboard(d, NativeLong(CURRENT_TIME))
                x11.XFlush(d)
            }
            x11.XCloseDisplay(d)
        }
    }

    private companion object {
        const val KEY_PRESS = 2
        const val KEY_RELEASE = 3
        const val BUTTON_PRESS = 4
        const val BUTTON_RELEASE = 5
        const val MOTION_NOTIFY = 6
    }
}
