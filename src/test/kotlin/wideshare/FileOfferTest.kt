package wideshare

import wideshare.core.Codec
import wideshare.core.ClientSession
import wideshare.core.FileHooks
import wideshare.core.Message
import wideshare.core.tr
import wideshare.platform.ScreenSize
import java.io.File
import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FileOfferTest {
    private val outbox = LinkedBlockingQueue<Message>()

    private fun session(files: List<File>?, asked: MutableList<String>, accept: Boolean) = ClientSession(
        "srv", outbox, FakeInjector(ScreenSize(100, 100)), {},
        hooks = FileHooks(askUser = { summary, yes -> asked += summary; if (accept) yes() }), readFiles = { files },
    )

    @Test
    fun copiedFilesAreOnlyOfferedWhenTheCursorLeavesAndSentOnRequest() {
        val file = File(Files.createTempDirectory("copy").toFile(), "a.txt").also { it.writeText("hi") }
        val s = session(listOf(file), mutableListOf(), true)
        s.handle(Message.Enter(wideshare.core.Side.LEFT, 0.5f))
        s.handle(Message.Release)
        assertEquals(Message.FilesOffer("a.txt"), outbox.poll())
        assertTrue(outbox.isEmpty(), "no file before the request")

        s.handle(Message.FilesRequest)
        val sent = generateSequence { outbox.poll(2, java.util.concurrent.TimeUnit.SECONDS) }.takeWhile { it != Message.FilesDone }.toList()
        assertIs<Message.FilesStart>(sent.first())
        assertEquals(2L, (sent.first() as Message.FilesStart).total)
        assertTrue(sent.any { it is Message.FileBegin && it.path == "a.txt" })
    }

    @Test
    fun offerFromTheServerAsksTheUserAndRequestsOnlyIfAccepted() {
        val asked = mutableListOf<String>()
        session(null, asked, accept = false).handle(Message.FilesOffer("2 items: x, y"))
        assertEquals(listOf(tr("offer.copied", "srv", "2 items: x, y")), asked)
        assertTrue(outbox.isEmpty())

        session(null, asked, accept = true).handle(Message.FilesOffer("x"))
        assertEquals(Message.FilesRequest, outbox.poll())
    }

    @Test
    fun offerMessagesSurviveTheCodec() {
        assertEquals(Message.FilesOffer("ç"), Codec.decode(Codec.encode(Message.FilesOffer("ç"))))
        assertEquals(Message.FilesRequest, Codec.decode(Codec.encode(Message.FilesRequest)))
    }
}
