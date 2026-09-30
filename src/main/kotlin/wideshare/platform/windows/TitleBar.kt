package wideshare.platform.windows

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window

private interface Dwmapi : StdCallLibrary {
    fun DwmSetWindowAttribute(hwnd: HWND, attribute: Int, value: IntArray, size: Int): Int
}

private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
private const val SWP_FLAGS = 0x0002 or 0x0001 or 0x0004 or 0x0010 or 0x0020 // NOMOVE | NOSIZE | NOZORDER | NOACTIVATE | FRAMECHANGED

/** Makes the native title bar of [window] dark or light. Returns false if the window has no native peer yet. */
fun setTitleBarDark(window: Window, dark: Boolean): Boolean {
    if (!window.isDisplayable) return false
    return runCatching {
        val hwnd = HWND(Native.getComponentPointer(window) ?: return false)
        val dwm = Native.load("dwmapi", Dwmapi::class.java)
        dwm.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, intArrayOf(if (dark) 1 else 0), 4)
        User32.INSTANCE.SetWindowPos(hwnd, null, 0, 0, 0, 0, SWP_FLAGS)
        true
    }.getOrDefault(false)
}
