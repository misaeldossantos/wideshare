package wideshare.core

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

enum class TransferState { RECEIVING, READY, SAVED, EXPIRED, FAILED }

/**
 * A batch of files received from another computer. [TransferState.READY] = is on the clipboard,
 * in a temporary folder that goes away at [expiresAt]; [TransferState.SAVED] = was written to [folder] (dragged onto the window).
 */
data class Transfer(
    val id: Long, val peer: String, val total: Long,
    val names: String = "", val done: Long = 0, val state: TransferState = TransferState.RECEIVING,
    val expiresAt: Long = 0, val folder: String = "",
)

/** Recent transfers, from newest to oldest, observable by the screen. */
class TransferLog {
    val items = MutableStateFlow<List<Transfer>>(emptyList())
    private val seq = AtomicLong()

    fun begin(peer: String, total: Long): Long {
        val id = seq.incrementAndGet()
        items.update { (listOf(Transfer(id, peer, total)) + it).take(30) }
        return id
    }

    fun update(id: Long, change: (Transfer) -> Transfer) = items.update { list -> list.map { if (it.id == id) change(it) else it } }
}
