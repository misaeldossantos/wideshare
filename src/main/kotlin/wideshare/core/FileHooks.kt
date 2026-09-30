package wideshare.core

import java.io.File
import java.util.Timer
import java.util.TimerTask
import java.util.UUID

/**
 * What receiving files asks of the app: the folder for dragged files, the question to the user and the log
 * of transfers. What arrives by copy/paste goes to a temporary folder in [tempRoot], deleted
 * [ttlMs] later (the clipboard's source folder, as the file explorer expects).
 */
class FileHooks(
    val folder: () -> File = { File(Settings.defaultReceiveFolder()) },
    val askUser: (summary: String, accept: () -> Unit) -> Unit = { _, accept -> accept() },
    val transfers: TransferLog = TransferLog(),
    private val tempRoot: File = File(System.getProperty("java.io.tmpdir"), "wideshare-received"),
    val ttlMs: Long = 10 * 60_000L,
    val dragTarget: DragTarget = NoDragTarget,
) {
    /** Files the user is dragging on this computer. */
    val stash = DragStash()
    private val timer = Timer("temp-cleanup", true)

    init {
        tempRoot.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > ttlMs }?.forEach { it.deleteRecursively() }
    }

    internal fun newTempDir(): File = File(tempRoot, UUID.randomUUID().toString().take(8)).also { it.mkdirs() }

    /** Deletes [dir] after [ttlMs] and then calls [onDeleted]. */
    internal fun deleteLater(dir: File, onDeleted: () -> Unit) {
        timer.schedule(object : TimerTask() {
            override fun run() {
                dir.deleteRecursively()
                onDeleted()
            }
        }, ttlMs)
    }
}
