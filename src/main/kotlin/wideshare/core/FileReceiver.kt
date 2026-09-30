package wideshare.core

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Writes the files sent by a [FileSender] and records the progress in [FileHooks.transfers].
 * Copy/paste goes to a temporary folder; dragging goes to the received folder (repeated names get " (1)").
 * [allowPaste] decides whether copy/paste batches are accepted; [onReceived] runs when a batch finishes.
 */
internal class FileReceiver(
    private val hooks: FileHooks,
    private val peer: String,
    private val allowPaste: () -> Boolean,
    private val onReceived: (files: List<File>, paste: Boolean) -> Unit,
) {
    private val names = LinkedHashMap<String, String>()
    private var dir = File(".")
    private var out: OutputStream? = null
    private var paste = false
    private var skip = true
    private var id = 0L
    private var total = 0L
    private var done = 0L
    private var lastReport = 0L

    /** Handles the file messages; returns false for any other. */
    fun handle(m: Message): Boolean {
        when (m) {
            is Message.FilesStart -> start(m.paste, m.total)
            is Message.FileBegin -> if (!skip) begin(m)
            is Message.FileData -> out?.let { write(it, m.bytes) }
            Message.FileEnd -> closeOut()
            Message.FilesDone -> done()
            else -> return false
        }
        return true
    }

    /** The connection dropped in the middle of a batch. */
    fun abort() {
        closeOut()
        if (id != 0L) hooks.transfers.update(id) { it.copy(state = TransferState.FAILED) }
        reset()
    }

    private fun reset() {
        names.clear()
        skip = true
        id = 0L
    }

    private fun start(paste: Boolean, total: Long) {
        abort()
        this.paste = paste
        skip = paste && !allowPaste()
        if (skip) return
        dir = if (paste) hooks.newTempDir() else hooks.folder().also { it.mkdirs() }
        this.total = total
        done = 0
        id = hooks.transfers.begin(peer, total)
    }

    private fun begin(m: Message.FileBegin) {
        closeOut()
        val parts = m.path.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." || it.any { c -> c == '\\' || c == ':' || c == '\u0000' } }) return
        val known = names.size
        val top = names.getOrPut(parts[0]) { unique(parts[0]) }
        if (names.size != known) hooks.transfers.update(id) { it.copy(names = names.values.joinToString()) }
        val target = File(dir, (listOf(top) + parts.drop(1)).joinToString("/"))
        if (m.size < 0) target.mkdirs()
        else {
            target.parentFile?.mkdirs()
            out = runCatching { FileOutputStream(target) }.getOrNull()
        }
    }

    private fun write(stream: OutputStream, bytes: ByteArray) {
        runCatching { stream.write(bytes) }.onFailure { closeOut() }
        done += bytes.size
        val now = System.currentTimeMillis()
        if (now - lastReport > 100) {
            lastReport = now
            hooks.transfers.update(id) { it.copy(done = done) }
        }
    }

    private fun done() {
        closeOut()
        val files = names.values.map { File(dir, it) }
        val wasPaste = paste
        val batch = dir
        val finished = id
        val received = !skip && files.isNotEmpty()
        id = 0L
        if (finished != 0L) finish(finished, received, wasPaste, batch)
        reset()
        if (received) onReceived(files, wasPaste)
    }

    private fun finish(finished: Long, received: Boolean, wasPaste: Boolean, batch: File) {
        val log = hooks.transfers
        when {
            !received -> log.update(finished) { it.copy(state = TransferState.FAILED) }
            wasPaste -> {
                log.update(finished) { it.copy(done = total, state = TransferState.READY, expiresAt = System.currentTimeMillis() + hooks.ttlMs, folder = batch.path) }
                hooks.deleteLater(batch) { log.update(finished) { it.copy(state = TransferState.EXPIRED) } }
            }
            else -> log.update(finished) { it.copy(done = total, state = TransferState.SAVED, folder = batch.path) }
        }
    }

    private fun unique(name: String): String {
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        var candidate = name
        var n = 1
        while (File(dir, candidate).exists()) candidate = "${name.substring(0, dot)} (${n++})${name.substring(dot)}"
        return candidate
    }

    private fun closeOut() {
        runCatching { out?.close() }
        out = null
    }
}
