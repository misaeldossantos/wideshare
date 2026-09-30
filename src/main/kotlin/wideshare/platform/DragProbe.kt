package wideshare.platform

/** Detects, system-wide, a file drag in progress (for example, from the file explorer). */
interface DragProbe {
    /** The left mouse button is pressed. */
    fun leftDown(): Boolean

    /** Some program is dragging items (not just holding the button down). */
    fun dragging(): Boolean
    fun close() {}
}
