package wideshare

import wideshare.core.FileSender
import wideshare.core.SendListener
import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SendListenerTest {
    private class Recorder : SendListener {
        val progress = mutableListOf<Pair<Long, Long>>()
        val results = mutableListOf<Boolean>()
        override fun progress(sent: Long, total: Long) { progress += sent to total }
        override fun finished(ok: Boolean) { results += ok }
    }

    private fun file(size: Int) = File(Files.createTempDirectory("s").toFile(), "a.bin").also { it.writeBytes(Random(2).nextBytes(size)) }

    @Test
    fun reportsTheTotalAndFinishesOk() {
        val recorder = Recorder()
        assertTrue(FileSender { true }.sendAll(listOf(file(200_000)), paste = false, listener = recorder))
        assertEquals(200_000L to 200_000L, recorder.progress.last())
        assertTrue(recorder.progress.all { it.second == 200_000L && it.first <= it.second })
        assertEquals(listOf(true), recorder.results)
    }

    @Test
    fun reportsFailureWhenTheConnectionDrops() {
        val recorder = Recorder()
        var messages = 0
        assertTrue(!FileSender { ++messages < 3 }.sendAll(listOf(file(200_000)), paste = false, listener = recorder))
        assertEquals(listOf(false), recorder.results)
        assertTrue(recorder.progress.last().first < 200_000L)
    }
}
