package wideshare.core

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Sends files and folders to a peer, in chunks. [send] delivers a message and returns false when the
 * connection dropped; in that case the sending stops. Only one batch goes out at a time, so batches do not get mixed up.
 */
internal class FileSender(private val send: (Message) -> Boolean) {
    private var sent = 0L
    private var total = 0L
    private var lastReport = 0L
    private var listener: SendListener? = null

    fun sendAll(files: List<File>, paste: Boolean, listener: SendListener? = null): Boolean = synchronized(this) {
        this.listener = listener
        total = totalSize(files)
        sent = 0
        val ok = send(Message.FilesStart(paste, total)) && files.all { sendTree(it.toPath()) } && send(Message.FilesDone)
        listener?.progress(if (ok) total else sent, total)
        listener?.finished(ok)
        ok
    }

    private fun totalSize(files: List<File>): Long {
        var sum = 0L
        for (root in files) Files.walkFileTree(root.toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isRegularFile) sum += attrs.size()
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException) = FileVisitResult.CONTINUE
        })
        return sum
    }

    private fun sendTree(root: Path): Boolean {
        val base = root.parent ?: return true
        var alive = true
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                alive = send(Message.FileBegin(relative(base, dir), -1))
                return if (alive) FileVisitResult.CONTINUE else FileVisitResult.TERMINATE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isRegularFile) alive = sendFile(file, relative(base, file), attrs.size())
                return if (alive) FileVisitResult.CONTINUE else FileVisitResult.TERMINATE
            }

            override fun visitFileFailed(file: Path, exc: IOException) = FileVisitResult.CONTINUE
        })
        return alive
    }

    private fun sendFile(file: Path, name: String, size: Long): Boolean {
        if (!send(Message.FileBegin(name, size))) return false
        try {
            Files.newInputStream(file).use { input ->
                var left = size
                val buffer = ByteArray(MAX_FILE_CHUNK)
                while (left > 0) {
                    val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (n <= 0) break
                    if (!send(Message.FileData(buffer.copyOf(n)))) return false
                    left -= n
                    sent += n
                    report()
                }
            }
        } catch (_: IOException) {
            // The file disappeared or is locked: what was already read stays as a partial file.
        }
        return send(Message.FileEnd)
    }

    private fun report() {
        val now = System.currentTimeMillis()
        if (now - lastReport < 100) return
        lastReport = now
        listener?.progress(sent, total)
    }

    private fun relative(base: Path, path: Path) = base.relativize(path).joinToString("/") { it.toString() }
}
