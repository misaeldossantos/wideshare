package wideshare.ui

import wideshare.core.ClientState
import wideshare.core.Mode
import wideshare.platform.Platform
import java.awt.MouseInfo
import kotlin.concurrent.thread

/** Connects the [DragWatcher] to the app: while it runs, dragging files shows the zone to send them to the other computer. */
internal class DragShare(private val c: AppController) {
    @Volatile private var running = false
    private val ui = SwingDragUi { files, peer, listener -> c.sendFiles(files, peer, listener) }

    fun start() {
        running = true
        thread(isDaemon = true, name = "drag-watch") {
            val probe = Platform.createDragProbe() ?: return@thread
            val watcher = DragWatcher(
                probe, ui, ::peers, { runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull() },
                c.files.hooks.stash, Platform::dragSelection, { work -> thread(isDaemon = true, name = "drag-select") { work() } },
                zoneFallback = !Platform.isWindows,
            )
            try {
                while (running) {
                    if (c.running.value) runCatching { watcher.tick() }
                    Thread.sleep(40)
                }
            } finally {
                ui.hide()
                probe.close()
            }
        }
    }

    fun stop() { running = false }

    /** Who files can be sent to right now: the connected clients (server) or the server (client). */
    private fun peers(): List<String> =
        if (c.mode.value == Mode.SERVER) c.clients.value.map { it.name }
        else (c.clientState.value as? ClientState.Connected)?.let { listOf(it.server) }.orEmpty()
}
