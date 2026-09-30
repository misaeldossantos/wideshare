package wideshare.ui

import wideshare.core.GridPos
import wideshare.core.Settings
import kotlinx.coroutines.flow.MutableStateFlow

val GRID_X = -3..3
val GRID_Y = -2..2
private const val GRID_RADIUS_X = 3

/** Position of each client on the grid, persisted in [Settings.positions]. */
class LayoutStore(private val settings: Settings) {
    val layout = MutableStateFlow(settings.positions.toMap())

    /** Position of a client; new clients take the free cell closest to the server. */
    fun posOf(client: String): GridPos = synchronized(settings) {
        settings.positions.getOrPut(client) {
            val used = settings.positions.values.toSet() + GridPos.SERVER
            val free = candidates().first { it !in used }
            layout.value = settings.positions.toMap() + (client to free)
            free
        }
    }

    private fun candidates(): Sequence<GridPos> = (1..GRID_RADIUS_X).asSequence().flatMap { d ->
        sequenceOf(GridPos(d, 0), GridPos(-d, 0), GridPos(0, -d), GridPos(0, d)).filter { it.y in GRID_Y && it.x in GRID_X }
    } + (GRID_X.flatMap { x -> GRID_Y.map { y -> GridPos(x, y) } }).asSequence()

    /** Moves a client to [cell]; if another client is there, the two swap places. The server does not move. */
    fun move(client: String, cell: GridPos): Boolean {
        if (cell == GridPos.SERVER || cell.x !in GRID_X || cell.y !in GRID_Y) return false
        synchronized(settings) {
            val old = settings.positions[client]
            val other = settings.positions.entries.firstOrNull { it.value == cell && it.key != client }?.key
            if (other != null) {
                if (old == null) return false
                settings.positions[other] = old
            }
            settings.positions[client] = cell
            layout.value = settings.positions.toMap()
        }
        settings.save()
        return true
    }

    /** Frees a client's cell. */
    fun remove(client: String) {
        synchronized(settings) {
            settings.positions.remove(client)
            layout.value = settings.positions.toMap()
        }
        settings.save()
    }
}

data class PairedInfo(val id: String, val name: String)
