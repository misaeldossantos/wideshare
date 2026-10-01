package wideshare.platform.linux

import wideshare.platform.DragProbe
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.NativeLongByReference

/**
 * On X11, whoever drags something (XDND protocol) takes ownership of the "XdndSelection" selection. Together with the
 * left button pressed, this indicates a drag; [XdndKind] then confirms it carries files. Always use it from the same thread.
 */
class X11DragProbe : DragProbe {
    private val display: Pointer = openDisplay()
    private val root = x11.XDefaultRootWindow(display)
    private val dndSelection = x11.XInternAtom(display, "XdndSelection", 0)
    private val kind = XdndKind(display, root)
    private val rootRet = NativeLongByReference()
    private val childRet = NativeLongByReference()
    private val x = IntByReference()
    private val y = IntByReference()
    private val mask = IntByReference()

    override fun leftDown(): Boolean {
        x11.XQueryPointer(display, root, rootRet, childRet, x, y, IntByReference(), IntByReference(), mask)
        val pressed = mask.value and BUTTON1_MASK != 0
        if (!pressed) kind.reset()
        return pressed
    }

    override fun dragging() = kind.files(x11.XGetSelectionOwner(display, dndSelection).toLong(), leftDown())

    override fun close() {
        kind.close()
        x11.XCloseDisplay(display)
    }

    private companion object {
        const val BUTTON1_MASK = 0x100
    }
}
