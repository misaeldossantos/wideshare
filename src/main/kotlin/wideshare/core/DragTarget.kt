package wideshare.core

/**
 * The drop zone this computer shows when the other one is dragging files to it.
 * It sits in the corner of the screen, in the same place as the copy and paste notice.
 */
interface DragTarget {
    /**
     * Shows the zone. With [onDrop] the mouse is this computer's own: the zone follows the real cursor and calls
     * [onDrop] if the button is released over it. Without [onDrop] the cursor is injected by the server: use [hover].
     */
    fun show(summary: String, onDrop: (() -> Unit)? = null)

    /** Cursor injected at ([x], [y]), in screen pixels; returns whether it is over the zone. */
    fun hover(x: Int, y: Int): Boolean
    fun hide()
}

object NoDragTarget : DragTarget {
    override fun show(summary: String, onDrop: (() -> Unit)?) {}
    override fun hover(x: Int, y: Int) = false
    override fun hide() {}
}
