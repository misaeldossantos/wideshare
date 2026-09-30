package wideshare.platform.windows

import wideshare.platform.DragProbe

/** On Windows, a shell (Explorer) drag shows the drag image in a "SysDragImage" window. */
class WindowsDragProbe : DragProbe {
    override fun leftDown() = user32.GetAsyncKeyState(VK_LBUTTON).toInt() and 0x8000 != 0

    override fun dragging(): Boolean {
        val window = user32.FindWindow("SysDragImage", null) ?: return false
        return user32.IsWindowVisible(window)
    }

    private companion object {
        const val VK_LBUTTON = 0x01
    }
}
