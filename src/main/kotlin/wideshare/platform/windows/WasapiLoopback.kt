package wideshare.platform.windows

import wideshare.core.tr
import wideshare.platform.AudioCapture
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Captures what the [deviceId] output (the default one, if null) is playing, via WASAPI in loopback mode. */
internal class WasapiLoopback(private val deviceId: String? = null) : AudioCapture {
    @Volatile private var running = false

    override fun start(onChunk: (rate: Int, pcm: ByteArray) -> Unit) {
        running = true
        val ready = CompletableFuture<Unit>()
        thread(isDaemon = true, name = "audio-capture") {
            val ole = Ole32Lib.INSTANCE
            ole.CoInitializeEx(null, 0)
            var loop: Loopback? = null
            try {
                loop = Loopback(deviceId)
                ready.complete(Unit)
                loop.pump({ running }, onChunk)
            } catch (e: Throwable) {
                ready.completeExceptionally(e)
            } finally {
                runCatching { loop?.close() }
                ole.CoUninitialize()
            }
        }
        try {
            ready.get(3, TimeUnit.SECONDS)
        } catch (e: Exception) {
            running = false
            throw IOException((e as? ExecutionException)?.cause?.message ?: tr("err.wasapi"))
        }
    }

    override fun stop() { running = false }
}

private val CLSID_ENUMERATOR = GUID("{BCDE0395-E52F-467C-8E3D-C4579291692E}")
private val IID_ENUMERATOR = GUID("{A95664D2-9614-4F35-A746-DE8DB63617E6}")
private val IID_AUDIO_CLIENT = GUID("{1CB9AD4C-DBFA-4C32-B178-C2F568A703B2}")
private val IID_CAPTURE_CLIENT = GUID("{C8ADBD64-E71E-48A0-A4DE-185C395CD317}")

private const val CLSCTX_ALL = 23
private const val LOOPBACK = 0x00020000
private const val SILENT = 2
private const val MAX_CHUNK_FRAMES = 2048

/** Capture session over the default output; the format is Windows' mix format, converted in [pump]. */
private class Loopback(deviceId: String?) : AutoCloseable {
    private val client: ComObject
    private val capture: ComObject
    private val format: MixFormat

    init {
        val enumerator = comCreate(CLSID_ENUMERATOR, IID_ENUMERATOR)
        val device = if (deviceId == null) enumerator.get(4, "GetDefaultAudioEndpoint", 0, 0) else enumerator.get(5, "GetDevice", WString(deviceId))
        client = device.get(3, "Activate", IID_AUDIO_CLIENT, CLSCTX_ALL, null)
        val mix = PointerByReference()
        check(client.call(8, mix), "GetMixFormat")
        format = MixFormat.read(mix.value)
        val init = client.call(3, 0, LOOPBACK, 10_000_000L, 0L, mix.value, null)
        Ole32Lib.INSTANCE.CoTaskMemFree(mix.value)
        check(init, "Initialize")
        capture = client.get(14, "GetService", IID_CAPTURE_CLIENT)
        check(client.call(10), "Start")
    }

    fun pump(running: () -> Boolean, onChunk: (rate: Int, pcm: ByteArray) -> Unit) {
        val size = IntByReference()
        val data = PointerByReference()
        val frames = IntByReference()
        val flags = IntByReference()
        while (running()) {
            Thread.sleep(10)
            while (running()) {
                check(capture.call(5, size), "GetNextPacketSize")
                if (size.value == 0) break
                check(capture.call(3, data, frames, flags, null, null), "GetBuffer")
                val n = frames.value
                val pcm = if (flags.value and SILENT != 0) ByteArray(n * 4) else format.toStereo16(data.value, n)
                capture.call(4, n)
                for (from in pcm.indices step MAX_CHUNK_FRAMES * 4) {
                    onChunk(format.rate, pcm.copyOfRange(from, minOf(from + MAX_CHUNK_FRAMES * 4, pcm.size)))
                }
            }
        }
    }

    override fun close() {
        client.call(11)
        capture.release()
        client.release()
    }
}

internal fun comCreate(clsid: GUID, iid: GUID): ComObject {
    val out = PointerByReference()
    check(Ole32Lib.INSTANCE.CoCreateInstance(clsid, null, CLSCTX_ALL, iid, out), "CoCreateInstance")
    return ComObject(out.value)
}
