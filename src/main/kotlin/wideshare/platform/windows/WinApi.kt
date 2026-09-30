package wideshare.platform.windows

import wideshare.core.Buttons
import wideshare.platform.ScreenSize
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTR

internal const val WH_KEYBOARD_LL = 13
internal const val WH_MOUSE_LL = 14

internal const val WM_MOUSEMOVE = 0x200
internal const val WM_LBUTTONDOWN = 0x201
internal const val WM_LBUTTONUP = 0x202
internal const val WM_RBUTTONDOWN = 0x204
internal const val WM_RBUTTONUP = 0x205
internal const val WM_MBUTTONDOWN = 0x207
internal const val WM_MBUTTONUP = 0x208
internal const val WM_MOUSEWHEEL = 0x20A
internal const val WM_XBUTTONDOWN = 0x20B
internal const val WM_XBUTTONUP = 0x20C
internal const val WM_MOUSEHWHEEL = 0x20E
internal const val WM_KEYDOWN = 0x100
internal const val WM_KEYUP = 0x101
internal const val WM_SYSKEYDOWN = 0x104
internal const val WM_SYSKEYUP = 0x105
internal const val WM_QUIT = 0x12

internal const val LLMHF_INJECTED = 0x01
internal const val LLKHF_EXTENDED = 0x01
internal const val LLKHF_INJECTED = 0x10

internal val user32: User32 get() = User32.INSTANCE

internal fun primaryScreen() = ScreenSize(
    user32.GetSystemMetrics(WinUser.SM_CXSCREEN),
    user32.GetSystemMetrics(WinUser.SM_CYSCREEN),
)

internal fun sendInput(configure: (WinUser.INPUT) -> Unit) {
    @Suppress("UNCHECKED_CAST")
    val inputs = WinUser.INPUT().toArray(1) as Array<WinUser.INPUT>
    configure(inputs[0])
    user32.SendInput(DWORD(1), inputs, inputs[0].size())
}

internal fun sendKey(scan: Int, extended: Boolean, down: Boolean) = sendInput { i ->
    i.type = DWORD(WinUser.INPUT.INPUT_KEYBOARD.toLong())
    i.input.setType("ki")
    i.input.ki.wVk = WinDef.WORD(0)
    i.input.ki.wScan = WinDef.WORD(scan.toLong())
    var flags = 0x0008 // KEYEVENTF_SCANCODE
    if (extended) flags = flags or 0x0001
    if (!down) flags = flags or 0x0002
    i.input.ki.dwFlags = DWORD(flags.toLong())
    i.input.ki.time = DWORD(0)
    i.input.ki.dwExtraInfo = ULONG_PTR(0)
}

internal fun sendMouse(flags: Int, data: Int = 0) = sendInput { i ->
    i.type = DWORD(WinUser.INPUT.INPUT_MOUSE.toLong())
    i.input.setType("mi")
    i.input.mi.dx = WinDef.LONG(0)
    i.input.mi.dy = WinDef.LONG(0)
    i.input.mi.mouseData = DWORD(data.toLong() and 0xFFFFFFFFL)
    i.input.mi.dwFlags = DWORD(flags.toLong())
    i.input.mi.time = DWORD(0)
    i.input.mi.dwExtraInfo = ULONG_PTR(0)
}

/** Neutral buttons -> (flag down, flag up, mouseData). */
internal fun buttonFlags(button: Int): Triple<Int, Int, Int>? = when (button) {
    Buttons.LEFT -> Triple(0x0002, 0x0004, 0)
    Buttons.RIGHT -> Triple(0x0008, 0x0010, 0)
    Buttons.MIDDLE -> Triple(0x0020, 0x0040, 0)
    Buttons.BACK -> Triple(0x0080, 0x0100, 1)
    Buttons.FORWARD -> Triple(0x0080, 0x0100, 2)
    else -> null
}

internal fun setCursor(x: Int, y: Int) {
    user32.SetCursorPos(x.toLong(), y.toLong())
}
