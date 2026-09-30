package wideshare

import wideshare.core.Codec
import wideshare.core.Message
import wideshare.platform.Platform
import wideshare.platform.windows.AudioDevices
import wideshare.platform.windows.DefaultOutput
import wideshare.platform.windows.WasapiLoopback
import wideshare.platform.windows.withCom
import java.util.concurrent.CopyOnWriteArrayList
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class AudioTest {
    @Test
    fun audioCodecRoundTrip() {
        val pcm = ByteArray(1920) { it.toByte() }
        val back = Codec.decode(Codec.encode(Message.Audio(48000, pcm))) as Message.Audio
        assertEquals(48000, back.rate)
        assertContentEquals(pcm, back.pcm)
    }

    /** Plays a tone and checks that the capture of what the computer plays receives non-zero samples. */
    @Test
    fun capturesWhatThisComputerPlays() {
        assumeTrue(Platform.isWindows)
        val chunks = CopyOnWriteArrayList<ByteArray>()
        val capture = wideshare.platform.windows.WasapiLoopback()
        capture.start { _, pcm -> chunks += pcm }
        val format = AudioFormat(44100f, 16, 2, true, false)
        val line = AudioSystem.getSourceDataLine(format).also { it.open(format); it.start() }
        val tone = ByteArray(44100 * 4) { i ->
            val s = (sin(2 * Math.PI * 440 * (i / 4) / 44100) * 12000).toInt()
            if (i % 2 == 0) s.toByte() else (s shr 8).toByte()
        }
        line.write(tone, 0, tone.size)
        Thread.sleep(300)
        capture.stop()
        line.stop()
        line.close()
        assertTrue(chunks.isNotEmpty(), "no packet received")
        assertTrue(chunks.any { c -> c.any { it != 0.toByte() } }, "only silence captured")
    }
}

class AudioDevicesTest {
    @Test
    fun listsRenderEndpointsAndTheDefaultIsOneOfThem() {
        assumeTrue(Platform.isWindows)
        withCom {
            val endpoints = AudioDevices.endpoints()
            println("outputs: " + endpoints.joinToString { it.name })
            assertTrue(endpoints.isNotEmpty() && endpoints.all { it.name.isNotBlank() })
            assertTrue(endpoints.any { it.id == AudioDevices.defaultId() })
            AudioDevices.setDefault(AudioDevices.defaultId()) // re-asserts the current default: validates the call without changing anything
        }
    }
}

class MutedLoopbackTest {
    /** Without VB-Cable, the sound is captured with the default output muted: the capture must keep receiving audio. */
    @Test
    fun loopbackStillHearsWhileOutputIsMuted() {
        assumeTrue(Platform.isWindows)
        val wasMuted = withCom { DefaultOutput.isMuted() }
        val chunks = CopyOnWriteArrayList<ByteArray>()
        val capture = WasapiLoopback()
        try {
            capture.start { _, pcm -> chunks += pcm }
            withCom { DefaultOutput.setMuted(true) }
            val format = AudioFormat(44100f, 16, 2, true, false)
            val line = AudioSystem.getSourceDataLine(format).also { it.open(format); it.start() }
            val tone = ByteArray(44100 * 4) { i ->
                val s = (sin(2 * Math.PI * 440 * (i / 4) / 44100) * 12000).toInt()
                if (i % 2 == 0) s.toByte() else (s shr 8).toByte()
            }
            line.write(tone, 0, tone.size)
            Thread.sleep(300)
            line.stop(); line.close()
        } finally {
            capture.stop()
            withCom { DefaultOutput.setMuted(wasMuted) }
        }
        assertTrue(chunks.any { c -> c.any { it != 0.toByte() } }, "only silence arrived with mute on")
    }
}
