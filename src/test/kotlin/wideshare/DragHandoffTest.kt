package wideshare

import wideshare.core.Buttons
import wideshare.core.ClientSession
import wideshare.core.DragStash
import wideshare.core.DragTarget
import wideshare.core.FileHooks
import wideshare.core.Message
import wideshare.core.Side
import wideshare.platform.ScreenSize
import java.io.File
import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DragHandoffTest {
    private class Target(val zone: (Int, Int) -> Boolean = { x, y -> x >= 80 && y >= 80 }) : DragTarget {
        val events = mutableListOf<String>()
        override fun show(summary: String, onDrop: (() -> Unit)?) { events += "show $summary" }
        override fun hover(x: Int, y: Int) = zone(x, y).also { events += "hover $x,$y $it" }
        override fun hide() { events += "hide" }
    }

    private val outbox = LinkedBlockingQueue<Message>()
    private val injector = FakeInjector(ScreenSize(100, 100))
    private val target = Target()
    private val hooks = FileHooks(dragTarget = target, tempRoot = Files.createTempDirectory("t").toFile())
    private val session = ClientSession("srv", outbox, injector, {}, readClipboard = { null }, hooks = hooks, readFiles = { null })

    private fun enter() = session.handle(Message.Enter(Side.LEFT, 0.0f))

    @Test
    fun droppingOnTheZoneAsksTheServerForTheFiles() {
        enter()
        session.handle(Message.DragStart("2 items: a, b"))
        assertEquals("show srv: 2 items: a, b", target.events.first())
        session.handle(Message.MouseMove(90, 90))
        session.handle(Message.MouseButton(Buttons.LEFT, false))
        assertEquals(Message.DragDrop, outbox.poll())
        assertEquals("hide", target.events.last())
        assertTrue(injector.events.none { it.startsWith("button") }, "the drop does not go to the system")
    }

    @Test
    fun releasingOutsideTheZoneOrLosingControlDropsNothing() {
        enter()
        session.handle(Message.DragStart("a"))
        session.handle(Message.MouseMove(10, 10))
        session.handle(Message.MouseButton(Buttons.LEFT, false))
        assertTrue(outbox.isEmpty())

        session.handle(Message.DragStart("a"))
        session.handle(Message.Release)
        assertEquals("hide", target.events.last())
        assertTrue(outbox.isEmpty())
    }

    @Test
    fun aDragLeavingTheClientIsAnnouncedOnceAndCancelledWhenControlReturns() {
        val file = File(Files.createTempDirectory("c").toFile(), "a.txt").also { it.writeText("x") }
        hooks.stash.begin()
        hooks.stash.files = listOf(file)
        enter()
        session.handle(Message.MouseMove(-200, 0))
        session.handle(Message.MouseMove(-200, 0))
        val sent = generateSequence { outbox.poll() }.toList()
        assertEquals(1, sent.count { it is Message.DragStart }, "notifies only once")
        assertEquals(Message.DragStart("a.txt"), sent.first { it is Message.DragStart })

        session.handle(Message.Release)
        assertTrue(injector.events.containsAll(listOf("key 1 true", "key 1 false")), "Esc cancela o arrasto local")

        session.handle(Message.DragDrop)
        val batch = generateSequence { outbox.poll(2, java.util.concurrent.TimeUnit.SECONDS) }.takeWhile { it != Message.FilesDone }.toList()
        assertTrue(batch.any { it is Message.FileBegin && it.path == "a.txt" })
        assertEquals(emptyList(), hooks.stash.files)
    }

    @Test
    fun stashOnlyOffersWhileDraggingAndExpiresWithoutAnnouncement() {
        val stash = DragStash()
        stash.files = listOf(File("a"))
        assertTrue(!stash.ready)
        stash.begin()
        stash.files = listOf(File("a"))
        assertTrue(stash.ready)
        stash.end()
        assertEquals(emptyList(), stash.files)
    }
}
