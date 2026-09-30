package wideshare.platform.windows

import wideshare.platform.AudioCapture

/**
 * Windows sound output sent to the server. With VB-Cable (vb-audio.com/Cable) installed, "CABLE Input"
 * becomes the default output and what plays on it is captured. Without it, it captures the default output and leaves it
 * muted while sending lasts (loopback capture is not affected by mute).
 */
internal class WindowsSpeaker : AudioCapture {
    private var loopback: WasapiLoopback? = null
    private var previous: String? = null
    private var wasMuted: Boolean? = null

    override fun start(onChunk: (rate: Int, pcm: ByteArray) -> Unit) {
        val (cable, current) = withCom {
            AudioDevices.endpoints().firstOrNull { it.name.contains("CABLE Input", ignoreCase = true) }?.id to AudioDevices.defaultId()
        }
        loopback = WasapiLoopback(cable).also { it.start(onChunk) }
        when {
            cable == null -> withCom { wasMuted = DefaultOutput.isMuted(); DefaultOutput.setMuted(true) }
            current != cable -> { previous = current; withCom { AudioDevices.setDefault(cable) } }
        }
    }

    override fun stop() {
        loopback?.stop()
        loopback = null
        previous?.let { id -> runCatching { withCom { AudioDevices.setDefault(id) } } }
        wasMuted?.let { muted -> runCatching { withCom { DefaultOutput.setMuted(muted) } } }
        previous = null
        wasMuted = null
    }
}
