package wideshare.core

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

const val PAIR_TIMEOUT_MS = 60_000L

/** Pairing request shown to the user: they check the [code] and call [accept] or [reject]. */
class PairingRequest(val peerName: String, val code: String) {
    private val decision = CompletableFuture<Boolean>()

    fun accept() { decision.complete(true) }
    fun reject() { decision.complete(false) }

    internal fun await(ms: Long): Boolean = try {
        decision.get(ms, TimeUnit.MILLISECONDS)
    } catch (_: Exception) {
        false
    }
}

/**
 * Runs the pairing confirmation over the already encrypted channel: each side sends its
 * user's decision and only stores the key if both accepted. Blocks until the decision (max. [PAIR_TIMEOUT_MS]).
 */
fun runPairing(hs: Handshake, trust: TrustStore, onPairing: (PairingRequest?) -> Unit) {
    val req = PairingRequest(hs.peerName, hs.sas ?: error(tr("err.noPairingCode")))
    val remote = CompletableFuture<Boolean>()
    hs.channel.setTimeout((PAIR_TIMEOUT_MS + 5_000).toInt())
    onPairing(req)
    try {
        // Reads the other side's decision right away: if they decline, the overlay here closes immediately.
        thread(isDaemon = true, name = "pair-read") {
            val accepted = runCatching { (Codec.decode(hs.channel.receive()) as? Message.PairDecision)?.accepted ?: false }.getOrDefault(false)
            remote.complete(accepted)
            if (!accepted) req.reject()
        }
        val local = req.await(PAIR_TIMEOUT_MS)
        runCatching { hs.channel.send(Codec.encode(Message.PairDecision(local))) }
        val theirs = runCatching { remote.get(PAIR_TIMEOUT_MS, TimeUnit.MILLISECONDS) }.getOrDefault(false)
        if (!local) throw PairingRejectedException("Pareamento recusado")
        if (!theirs) throw PairingRejectedException(tr("err.notConfirmed", hs.peerName))
        trust.trust(hs.peerId, hs.peerName, hs.pendingPsk!!)
        hs.channel.setTimeout(SecureChannel.IDLE_TIMEOUT_MS)
    } finally {
        onPairing(null)
    }
}
