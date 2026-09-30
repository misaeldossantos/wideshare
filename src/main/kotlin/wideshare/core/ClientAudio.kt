package wideshare.core

import wideshare.platform.AudioCapture
import wideshare.platform.Platform
import java.util.concurrent.LinkedBlockingQueue

private const val MAX_QUEUED_AUDIO = 30

/**
 * Creates a virtual sound output on the client and sends everything played on it to the server; null if that was not possible.
 * Late packets are dropped instead of accumulating latency.
 */
internal fun startSpeaker(server: String, outbox: LinkedBlockingQueue<Message>, log: (String) -> Unit): AudioCapture? =
    try {
        Platform.createVirtualSpeaker(server).also { speaker ->
            speaker.start { rate, pcm -> if (outbox.size < MAX_QUEUED_AUDIO) outbox.offer(Message.Audio(rate, pcm)) }
            log(tr("log.audioSent", server))
        }
    } catch (e: Exception) {
        log(tr("log.audioFailed", e.message ?: e.javaClass.simpleName))
        null
    }
