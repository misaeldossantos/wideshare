package wideshare.core

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/** Plays the audio received from the server (16-bit stereo PCM). Packets that do not fit in the buffer are dropped so latency does not build up. */
internal class AudioPlayer {
    private var line: SourceDataLine? = null
    private var rate = 0

    fun play(rate: Int, pcm: ByteArray) {
        if (rate != this.rate) open(rate)
        val l = line ?: return
        if (l.available() >= pcm.size) l.write(pcm, 0, pcm.size)
    }

    private fun open(rate: Int) {
        close()
        this.rate = rate
        val format = AudioFormat(rate.toFloat(), 16, 2, true, false)
        line = runCatching {
            AudioSystem.getSourceDataLine(format).also { it.open(format, rate * 4 * 15 / 100); it.start() }
        }.getOrNull()
    }

    fun close() {
        line?.let { runCatching { it.stop(); it.flush(); it.close() } }
        line = null
        rate = 0
    }
}
