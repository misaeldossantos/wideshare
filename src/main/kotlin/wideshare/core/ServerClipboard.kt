package wideshare.core

import java.io.File

/** What the server does with copied text and files: passes them between the computers according to the enabled options. */
internal class ServerClipboard(
    val hooks: FileHooks,
    private val toClients: () -> Boolean,
    private val toServer: () -> Boolean,
    private val active: () -> ServerSession?,
    private val log: (String) -> Unit,
    private val cancelDrag: () -> Unit,
) {
    fun allowPaste() = toServer()

    /** Text copied on a client: goes to the server and, if another client has the cursor, to that one. */
    fun onText(sender: ServerSession, text: String) {
        if (toServer()) Clipboard.write(text)
        val next = active()
        if (toClients() && next != null && next !== sender) next.send(Message.Clipboard(text))
    }

    /** A client copied files: asks the server's user whether they want to receive them on the clipboard. */
    fun onOffer(sender: ServerSession, summary: String) {
        if (toServer()) hooks.askUser(tr("offer.copied", "\"${sender.name}\"", summary)) { sender.send(Message.FilesRequest) }
    }

    /** Batch received from [sender]; if it came from copy/paste, it also goes to the clipboard. */
    fun onFiles(sender: ServerSession, files: List<File>, paste: Boolean) {
        log(tr("log.receivedIn", sender.name, files.joinToString { it.name }, files.first().parent))
        if (!paste) return
        FileClipboard.write(files)
        sender.fileSync.mark(files)
    }

    /** The cursor goes from the server to [next] in the middle of a file drag: cancels the local drag and [next] shows the drop zone. */
    fun dragStart(next: ServerSession) {
        val stash = hooks.stash
        if (!stash.ready) return
        stash.announced = true
        cancelDrag()
        next.send(Message.DragStart(summarize(stash.files)))
    }

    /** A client is dragging files and the cursor is coming back: shows the zone here; if the user drops onto it, asks for the files. */
    fun onClientDrag(sender: ServerSession, summary: String) {
        hooks.dragTarget.show("\"${sender.name}\": $summary") { sender.send(Message.DragDrop) }
    }

    /** The user dropped onto the zone of [target]: sends what was being dragged here. */
    fun onDrop(target: ServerSession) {
        val files = hooks.stash.files
        if (files.isEmpty()) return
        hooks.stash.clear()
        target.sendFiles(files, false)
    }

    /** The cursor entered [next]: sends what is copied on the server (text, or the notice of changed files). */
    fun push(next: ServerSession) {
        val files = FileClipboard.read()
        when {
            files == null -> Clipboard.read()?.let { next.send(Message.Clipboard(it)) }
            next.fileSync.changed(files) -> next.offerFiles(files)
        }
    }
}
