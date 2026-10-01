package wideshare

import wideshare.core.DragStash
import wideshare.platform.DragProbe
import wideshare.ui.DragUi
import wideshare.ui.DragWatcher
import java.awt.Point
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DragWatcherTest {
    private class Probe : DragProbe {
        var down = false
        var dragging = false
        override fun leftDown() = down
        override fun dragging() = dragging
    }

    private class Ui : DragUi {
        val events = mutableListOf<String>()
        override fun showIcon() { events += "icon" }
        override fun showZone(peers: List<String>) { events += "zone $peers" }
        override fun follow(x: Int, y: Int) { events += "follow $x,$y" }
        override fun hide() { events += "hide" }
    }

    private val probe = Probe()
    private val ui = Ui()
    private val stash = DragStash()
    private var at = Point(100, 100)
    private var peers = listOf("notebook")
    private var selection = listOf(File("a.txt"))
    private val watcher = DragWatcher(probe, ui, { peers }, { at }, stash, { selection }, { it() })

    private fun startDrag() {
        probe.down = true
        watcher.tick()
        at = Point(140, 100)
        probe.dragging = true
        watcher.tick()
    }

    @Test
    fun aRealDragShowsTheIconKeepsTheFilesAndShowsNoLocalZone() {
        probe.down = true
        watcher.tick()
        at = Point(140, 100)
        watcher.tick()
        assertEquals(emptyList(), ui.events, "button pressed and moved, but the system does not confirm a drag")

        probe.dragging = true
        watcher.tick()
        at = Point(200, 120)
        watcher.tick()
        assertEquals(listOf("icon", "follow 140,100", "follow 200,120"), ui.events)
        assertTrue(stash.ready)

        probe.down = false
        watcher.tick()
        assertEquals("hide", ui.events.last())
        assertFalse(stash.ready)
        assertEquals(emptyList(), stash.files, "dropped right here: nothing is kept")
    }

    @Test
    fun withoutAFileListItFallsBackToTheLocalZone() {
        selection = emptyList()
        startDrag()
        assertEquals(listOf("icon", "zone [notebook]", "follow 140,100"), ui.events)
    }

    @Test
    fun withoutAFileListAndNoFallbackTheDragIsIgnored() {
        selection = emptyList()
        val quiet = DragWatcher(probe, ui, { peers }, { at }, stash, { selection }, { it() }, zoneFallback = false)
        probe.down = true
        quiet.tick()
        at = Point(140, 100)
        probe.dragging = true
        quiet.tick()
        assertEquals(listOf("follow 140,100"), ui.events, "no icon and no zone for something that may be text")
    }

    @Test
    fun aDragTakenToTheOtherComputerKeepsItsFilesUntilTheDrop() {
        startDrag()
        stash.announced = true
        probe.dragging = false
        watcher.tick()
        assertEquals("hide", ui.events.last())
        val before = ui.events.size
        at = Point(500, 500)
        watcher.tick()
        assertEquals(before, ui.events.size, "after cancelling, the rest of the drag belongs to the other side")

        probe.down = false
        watcher.tick()
        assertEquals(listOf(File("a.txt")), stash.files)
    }

    @Test
    fun ignoresTinyMovesAndDragsWithNobodyToSendTo() {
        probe.down = true
        probe.dragging = true
        watcher.tick()
        at = Point(103, 102)
        watcher.tick()
        at = Point(300, 300)
        peers = emptyList()
        watcher.tick()
        assertEquals(emptyList(), ui.events)
    }
}
