package wideshare.core

import wideshare.platform.InputInjector
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/** State of a client's connection: applies to the local computer what the server sends. */
internal class ClientSession(
    private val server: String,
    private val outbox: LinkedBlockingQueue<Message>,
    private val injector: InputInjector,
    private val onState: (ClientState) -> Unit,
    private val setClipboard: (String) -> Unit = Clipboard::write,
    private val readClipboard: () -> String? = Clipboard::read,
    private val connected: () -> Boolean = { true },
    private val hooks: FileHooks = FileHooks(),
    private val onFiles: (List<File>) -> Unit = {},
    private val readFiles: () -> List<File>? = FileClipboard::read,
) {
    private var active = false
    private var x = 0
    private var y = 0
    private var lastLeave = 0L
    private val heldKeys = HashSet<Int>()
    private val heldButtons = HashSet<Int>()
    private val fileSync = FileSync()
    @Volatile private var offered: List<File> = emptyList()
    private val sender = FileSender(::sendBulk)
    private val drag = ClientDrag(hooks, outbox, injector) { sendFiles(it, false) }
    private val receiver = FileReceiver(hooks, server, { true }) { files, paste ->
        if (paste) { FileClipboard.write(files); fileSync.mark(files) }
        onFiles(files)
    }

    fun handle(m: Message) {
        when (m) {
            is Message.Enter -> {
                val (w, h) = injector.screen()
                x = when (m.side) {
                    Side.LEFT -> 0
                    Side.RIGHT -> w - 1
                    else -> (m.fraction * (w - 1)).toInt()
                }
                y = when (m.side) {
                    Side.TOP -> 0
                    Side.BOTTOM -> h - 1
                    else -> (m.fraction * (h - 1)).toInt()
                }
                active = true
                lastLeave = 0
                injector.moveTo(x, y)
                onState(ClientState.Connected(server, true))
            }
            is Message.MouseMove -> if (active) move(m.dx, m.dy)
            is Message.MouseButton -> if (active && !(m.button == Buttons.LEFT && !m.down && drag.buttonUp(x, y))) {
                if (m.down) heldButtons += m.button else heldButtons -= m.button
                injector.button(m.button, m.down)
            }
            is Message.Wheel -> if (active) injector.wheel(m.dx, m.dy)
            is Message.Key -> if (active) {
                if (m.down) heldKeys += m.code else heldKeys -= m.code
                injector.key(m.code, m.down)
            }
            is Message.Clipboard -> setClipboard(m.text)
            Message.Release -> release()
            is Message.DragStart -> drag.start("$server: ${m.summary}")
            Message.DragDrop -> drag.dropped()
            is Message.FilesOffer -> hooks.askUser(tr("offer.copied", server, m.summary)) { outbox.offer(Message.FilesRequest) }
            Message.FilesRequest -> if (offered.isNotEmpty()) sendFiles(offered, true)
            else -> receiver.handle(m)
        }
    }

    private fun move(dx: Int, dy: Int) {
        val (w, h) = injector.screen()
        val nx = x + dx
        val ny = y + dy
        val side = when {
            nx < 0 -> Side.LEFT
            nx > w - 1 -> Side.RIGHT
            ny < 0 -> Side.TOP
            ny > h - 1 -> Side.BOTTOM
            else -> null
        }
        x = nx.coerceIn(0, w - 1)
        y = ny.coerceIn(0, h - 1)
        injector.moveTo(x, y)
        drag.moved(x, y)
        // Only the server knows whether there is a neighbor there; it answers with Release if the cursor left.
        val now = System.nanoTime()
        if (side != null && now - lastLeave > 50_000_000) {
            lastLeave = now
            val fraction = when (side) {
                Side.LEFT, Side.RIGHT -> y.toFloat() / (h - 1)
                else -> x.toFloat() / (w - 1)
            }.coerceIn(0f, 1f)
            drag.leaving()
            outbox.offer(Message.Leave(side, fraction))
        }
    }

    private fun release() {
        if (active) pushClipboard()
        drag.controlLost()
        active = false
        releaseAll()
        onState(ClientState.Connected(server, false))
    }

    /** Sends [files] to the server in the background (file copy or drag). */
    fun sendFiles(files: List<File>, paste: Boolean, listener: SendListener? = null) { thread(isDaemon = true, name = "files") { sender.sendAll(files, paste, listener) } }

    /** The connection ended: discards a half-received batch. */
    fun close() = receiver.abort()

    private fun sendBulk(m: Message): Boolean {
        while (connected() && outbox.size > 64) Thread.sleep(2)
        return connected() && outbox.offer(m)
    }

    /** The cursor left this computer: sends what was copied here (text, or the notice of changed files). */
    private fun pushClipboard() {
        val files = readFiles()
        when {
            files == null -> readClipboard()?.let { outbox.offer(Message.Clipboard(it)) }
            fileSync.changed(files) -> {
                offered = files
                outbox.offer(Message.FilesOffer(summarize(files)))
            }
        }
    }

    fun releaseAll() {
        heldKeys.forEach { injector.key(it, false) }
        heldButtons.forEach { injector.button(it, false) }
        heldKeys.clear()
        heldButtons.clear()
    }
}
