package wideshare.core

import wideshare.platform.InputInjector
import java.io.File
import java.util.concurrent.LinkedBlockingQueue

/**
 * The client's part of dragging files between computers. As the target: shows the drop zone while the
 * server is dragging and, if the button is released over it, asks for the files. As the source: tells the server when the
 * cursor leaves through an edge in the middle of a drag and sends the files if the server asks for them.
 */
internal class ClientDrag(
    private val hooks: FileHooks,
    private val outbox: LinkedBlockingQueue<Message>,
    private val injector: InputInjector,
    private val send: (List<File>) -> Unit,
) {
    private var target = false

    fun start(summary: String) {
        target = true
        hooks.dragTarget.show(summary)
    }

    fun moved(x: Int, y: Int) { if (target) hooks.dragTarget.hover(x, y) }

    /** The left button was released at ([x], [y]); returns true if it was the drop of the drag (which then must not be passed on to the system). */
    fun buttonUp(x: Int, y: Int): Boolean {
        if (!target) return false
        val inside = hooks.dragTarget.hover(x, y)
        stop()
        if (inside) outbox.offer(Message.DragDrop)
        return true
    }

    /** The cursor left through an edge: if a drag from here was in progress, the server is notified (once per drag). */
    fun leaving() {
        val stash = hooks.stash
        if (!stash.ready || stash.announced) return
        stash.announced = true
        outbox.offer(Message.DragStart(summarize(stash.files)))
    }

    /** Control went back to the server: the zone disappears and the local drag (already announced) is cancelled with Esc, so it is not dropped on the edge. */
    fun controlLost() {
        stop()
        if (hooks.stash.announced && hooks.stash.active) {
            injector.key(ESC, true)
            injector.key(ESC, false)
        }
    }

    /** The server asked for the files that were being dragged here. */
    fun dropped() {
        val files = hooks.stash.files
        if (files.isEmpty()) return
        hooks.stash.clear()
        send(files)
    }

    private fun stop() {
        if (target) hooks.dragTarget.hide()
        target = false
    }

    private companion object {
        const val ESC = 1
    }
}
