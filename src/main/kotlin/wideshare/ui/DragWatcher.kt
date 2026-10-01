package wideshare.ui

import wideshare.core.DragStash
import wideshare.platform.DragProbe
import java.awt.Point
import java.io.File
import kotlin.math.abs

/** What appears on this computer during a drag made here: the icon next to the cursor and, if needed, a local drop zone. */
internal interface DragUi {
    fun showIcon()
    fun showZone(peers: List<String>)
    fun follow(x: Int, y: Int)
    fun hide()
}

/**
 * Follows the mouse: when the left button is pressed, the cursor has moved far enough and the system confirms
 * a drag, shows the icon and keeps the dragged files in [stash] ([select] reads them, in the background
 * via [spawn]). If it cannot tell which files they are, shows a local drop zone when [zoneFallback] is on; otherwise
 * the drag is ignored (it may be plain text). When the button is released, hides everything.
 * Call [tick] often.
 */
internal class DragWatcher(
    private val probe: DragProbe,
    private val ui: DragUi,
    private val peers: () -> List<String>,
    private val pointer: () -> Point?,
    private val stash: DragStash,
    private val select: () -> List<File>,
    private val spawn: (() -> Unit) -> Unit,
    private val zoneFallback: Boolean = true,
) {
    private var press: Point? = null
    @Volatile private var active = false
    private var over = false

    fun tick() {
        if (!probe.leftDown()) {
            press = null
            over = false
            if (active) { active = false; ui.hide() }
            stash.end()
            return
        }
        if (over) return
        val now = pointer() ?: return
        val start = press ?: now.also { press = it }
        if (!active) {
            if (abs(now.x - start.x) + abs(now.y - start.y) < MIN_MOVE || !probe.dragging()) return
            val targets = peers().ifEmpty { return }
            begin(targets)
        } else if (!probe.dragging()) {
            // The drag ended here (cancelled, or the cursor moved to the other computer): what follows belongs to the other side.
            active = false
            over = true
            ui.hide()
            return
        }
        ui.follow(now.x, now.y)
    }

    private fun begin(targets: List<String>) {
        active = true
        stash.begin()
        if (zoneFallback) ui.showIcon()
        spawn {
            val files = select()
            stash.files = files
            if (!active) return@spawn
            if (files.isNotEmpty()) { if (!zoneFallback) ui.showIcon() }
            else if (zoneFallback) ui.showZone(targets)
        }
    }

    private companion object {
        const val MIN_MOVE = 8
    }
}
