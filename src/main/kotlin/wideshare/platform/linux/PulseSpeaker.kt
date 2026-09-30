package wideshare.platform.linux

import wideshare.platform.AudioCapture
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Virtual PulseAudio/PipeWire output (null-sink) that becomes the default; the sound is read from its monitor with `parec`. */
internal class PulseSpeaker(server: String) : AudioCapture {
    private val sink = "wideshare_" + server.filter { it.isLetterOrDigit() }.take(24)
    private val label = "WideShare-" + server.filter { it.isLetterOrDigit() || it == '-' }
    private var module: String? = null
    private var previous: String? = null
    private var process: Process? = null

    override fun start(onChunk: (rate: Int, pcm: ByteArray) -> Unit) {
        pactl("list", "short", "modules").lines().filter { "sink_name=$sink" in it }.forEach { pactl("unload-module", it.substringBefore('\t')) }
        previous = runCatching { pactl("get-default-sink") }.getOrNull()
        module = pactl("load-module", "module-null-sink", "sink_name=$sink", "sink_properties=device.description=$label")
        runCatching { pactl("list", "short", "sink-inputs").lines().forEach { pactl("move-sink-input", it.substringBefore('\t'), sink) } }
        pactl("set-default-sink", sink)
        val p = ProcessBuilder("parec", "--format=s16le", "--rate=$RATE", "--channels=2", "--latency-msec=20", "--device=$sink.monitor")
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        process = p
        thread(isDaemon = true, name = "audio-capture") {
            val buffer = ByteArray(RATE * 4 / 50)
            try {
                while (true) {
                    val n = p.inputStream.readNBytes(buffer, 0, buffer.size)
                    if (n <= 0) break
                    onChunk(RATE, buffer.copyOf(n - n % 4))
                }
            } catch (_: IOException) {
            }
        }
    }

    override fun stop() {
        process?.destroy()
        previous?.takeIf { it.isNotBlank() }?.let { runCatching { pactl("set-default-sink", it) } }
        module?.let { runCatching { pactl("unload-module", it) } }
        module = null
        process = null
    }

    private fun pactl(vararg args: String): String {
        val p = ProcessBuilder("pactl", *args).redirectErrorStream(true).start()
        val out = p.inputStream.readAllBytes().decodeToString().trim()
        if (!p.waitFor(5, TimeUnit.SECONDS) || p.exitValue() != 0) throw IOException("pactl ${args.first()}: $out")
        return out
    }

    private companion object {
        const val RATE = 48000
    }
}
