package wideshare.platform.windows

import wideshare.platform.InputInjector

class WindowsInjector : InputInjector {
    override fun screen() = primaryScreen()

    override fun moveTo(x: Int, y: Int) {
        setCursor(x, y)
    }

    override fun button(button: Int, down: Boolean) {
        val (downFlag, upFlag, data) = buttonFlags(button) ?: return
        sendMouse(if (down) downFlag else upFlag, data)
    }

    override fun wheel(dx: Int, dy: Int) {
        if (dy != 0) sendMouse(0x0800, dy)
        if (dx != 0) sendMouse(0x1000, dx)
    }

    override fun key(code: Int, down: Boolean) {
        val (scan, extended) = WinKeyMap.fromEvdev(code) ?: return
        sendKey(scan, extended, down)
    }
}
