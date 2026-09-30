package wideshare.core

import java.io.File

/**
 * Files the user is dragging on this computer (read at the start of the drag). If the cursor moves
 * to the other computer during the drag and the user drops there, this is where they come from.
 */
class DragStash {
    /** A file drag is in progress here. */
    @Volatile var active = false

    /** The other computer has already been told about this drag (so the files are kept until the drop). */
    @Volatile var announced = false
    @Volatile private var stored: List<File> = emptyList()
    @Volatile private var storedAt = 0L

    var files: List<File>
        get() = if (System.currentTimeMillis() - storedAt < KEEP_MS) stored else emptyList()
        set(value) { stored = value; storedAt = System.currentTimeMillis() }

    /** Can be offered to the other computer: dragging right now and with the list at hand. */
    val ready: Boolean get() = active && files.isNotEmpty()

    fun begin() {
        active = true
        announced = false
        stored = emptyList()
    }

    /** The drag ended here; if the other side was not told, the list is discarded. */
    fun end() {
        active = false
        if (!announced) stored = emptyList()
    }

    fun clear() {
        stored = emptyList()
        announced = false
    }

    private companion object {
        const val KEEP_MS = 30_000L
    }
}
