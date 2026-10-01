package wideshare.platform.linux

import com.sun.jna.Memory
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.NativeLongByReference
import com.sun.jna.ptr.PointerByReference

/**
 * Tells whether the drag in progress (XDND) carries files: asks the drag source for the list of formats it offers
 * ("TARGETS") and looks for "text/uri-list". Plain text and other content do not offer it. Always use it from the same thread.
 */
internal class XdndKind(private val display: Pointer, root: NativeLong) {
    private val window = x11.XCreateSimpleWindow(display, root, 0, 0, 1, 1, 0, NONE, NONE)
    private val selection = x11.XInternAtom(display, "XdndSelection", 0)
    private val targets = x11.XInternAtom(display, "TARGETS", 0)
    private val property = x11.XInternAtom(display, "WIDESHARE_DND_TARGETS", 0)
    private val uriList = x11.XInternAtom(display, "text/uri-list", 0).toLong()
    private val event = Memory(EVENT_SIZE)
    private var lastOwner = 0L
    private var asked = 0L
    private var verdict: Boolean? = null

    /**
     * [owner]: who owns the XDND selection (0 = nobody). Sources often keep it after the drop, so the answer is only
     * kept while the button stays [pressed] and the owner is the same: each new press asks again. Until the source
     * answers, it is not treated as files.
     */
    fun files(owner: Long, pressed: Boolean): Boolean {
        if (owner != lastOwner || !pressed) { asked = 0; verdict = null; lastOwner = owner }
        if (owner == 0L || !pressed) return false
        verdict?.let { return it }
        val now = System.currentTimeMillis()
        if (asked == 0L) {
            x11.XConvertSelection(display, selection, targets, property, window, NativeLong(CURRENT_TIME))
            x11.XFlush(display)
            asked = now
        }
        if (x11.XCheckTypedWindowEvent(display, window, SELECTION_NOTIFY, event) != 0) {
            verdict = event.getLong(PROPERTY_OFFSET) != 0L && offersFiles()
        } else if (now - asked > TIMEOUT_MS) {
            verdict = true // A source that does not answer: behave as before, treating it as files.
        }
        return verdict ?: false
    }

    /** The button was released: the next drag must be asked about again. */
    fun reset() { asked = 0; verdict = null }

    fun close() { x11.XDestroyWindow(display, window) }

    private fun offersFiles(): Boolean {
        val type = NativeLongByReference()
        val format = IntByReference()
        val count = NativeLongByReference()
        val rest = NativeLongByReference()
        val data = PointerByReference()
        val status = x11.XGetWindowProperty(
            display, window, property, NativeLong(0), NativeLong(MAX_ATOMS), 1, NativeLong(XA_ATOM), type, format, count, rest, data,
        )
        val pointer = data.value ?: return false
        try {
            return status == 0 && format.value == 32 && (0 until count.value.toInt()).any { pointer.getLong(it * 8L) == uriList }
        } finally {
            x11.XFree(pointer)
        }
    }

    private companion object {
        const val EVENT_SIZE = 192L // sizeof(XEvent) on 64 bits
        const val SELECTION_NOTIFY = 31
        const val PROPERTY_OFFSET = 56L // XSelectionEvent.property
        const val XA_ATOM = 4L
        const val MAX_ATOMS = 1024L
        const val TIMEOUT_MS = 500L
    }
}
