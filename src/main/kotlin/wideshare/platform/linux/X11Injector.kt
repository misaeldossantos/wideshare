package wideshare.platform.linux

import wideshare.platform.InputInjector
import wideshare.platform.ScreenSize

class X11Injector : InputInjector {
    private val display = openDisplay()
    private val lock = Any()
    private var wheelX = 0
    private var wheelY = 0

    override fun screen() = synchronized(lock) {
        val s = x11.XDefaultScreen(display)
        ScreenSize(x11.XDisplayWidth(display, s), x11.XDisplayHeight(display, s))
    }

    override fun moveTo(x: Int, y: Int) = synchronized(lock) {
        xtst.XTestFakeMotionEvent(display, -1, x, y, NONE)
        x11.XFlush(display)
        Unit
    }

    override fun button(button: Int, down: Boolean) = synchronized(lock) {
        xButton.getOrNull(button)?.let { xtst.XTestFakeButtonEvent(display, it, if (down) 1 else 0, NONE) }
        x11.XFlush(display)
        Unit
    }

    override fun wheel(dx: Int, dy: Int) = synchronized(lock) {
        wheelX += dx
        wheelY += dy
        while (wheelY >= 120) { click(4); wheelY -= 120 }
        while (wheelY <= -120) { click(5); wheelY += 120 }
        while (wheelX >= 120) { click(7); wheelX -= 120 }
        while (wheelX <= -120) { click(6); wheelX += 120 }
        x11.XFlush(display)
        Unit
    }

    private fun click(xButton: Int) {
        xtst.XTestFakeButtonEvent(display, xButton, 1, NONE)
        xtst.XTestFakeButtonEvent(display, xButton, 0, NONE)
    }

    override fun key(code: Int, down: Boolean) = synchronized(lock) {
        xtst.XTestFakeKeyEvent(display, code + 8, if (down) 1 else 0, NONE)
        x11.XFlush(display)
        Unit
    }

    override fun close() { synchronized(lock) { x11.XCloseDisplay(display) } }
}
