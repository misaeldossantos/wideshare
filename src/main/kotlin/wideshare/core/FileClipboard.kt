package wideshare.core

import wideshare.platform.Platform
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.SystemFlavorMap
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.ByteArrayInputStream
import java.io.File

/** Files and folders on the system clipboard (what the file explorer copies). */
internal object FileClipboard {
    fun read(): List<File>? = runCatching {
        val board = Toolkit.getDefaultToolkit().systemClipboard
        if (!board.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) return null
        (board.getData(DataFlavor.javaFileListFlavor) as? List<*>)?.filterIsInstance<File>()?.filter { it.exists() }?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    fun write(files: List<File>) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(FileSelection(files), null) }
    }
}

/** Signals "copy" to Nautilus and the like, which only paste files coming in this format (AWT on X11 only exports a list of URIs). */
private val gnomeFlavor = DataFlavor("x-special/gnome-copied-files;class=java.io.InputStream", "gnome files").also {
    runCatching {
        val map = SystemFlavorMap.getDefaultFlavorMap() as SystemFlavorMap
        map.addUnencodedNativeForFlavor(it, "x-special/gnome-copied-files")
        map.addFlavorForUnencodedNative("x-special/gnome-copied-files", it)
    }
}

private class FileSelection(private val files: List<File>) : Transferable {
    private val flavors = if (Platform.isWindows) arrayOf(DataFlavor.javaFileListFlavor) else arrayOf(DataFlavor.javaFileListFlavor, gnomeFlavor)
    override fun getTransferDataFlavors() = flavors
    override fun isDataFlavorSupported(flavor: DataFlavor) = flavors.any { it.equals(flavor) }
    override fun getTransferData(flavor: DataFlavor): Any = when {
        flavor == DataFlavor.javaFileListFlavor -> files
        flavor == gnomeFlavor && !Platform.isWindows ->
            ByteArrayInputStream(("copy\n" + files.joinToString("\n") { it.toPath().toUri().toString() }).toByteArray())
        else -> throw UnsupportedFlavorException(flavor)
    }
}

/** Short summary of the items, to show to the user: "3 items: a.txt, folder, b.png". */
internal fun summarize(files: List<File>): String {
    val names = files.joinToString { it.name }
    val shown = if (names.length > 100) names.take(100) + "…" else names
    return if (files.size == 1) shown else tr("summary.items", files.size, shown)
}

/** Remembers the last list of files exchanged with a peer, so the same copy is not sent again each time the cursor crosses. */
internal class FileSync {
    @Volatile private var last = ""

    /** true (and records it) if [files] differs from what was already exchanged. */
    fun changed(files: List<File>): Boolean {
        val sig = signature(files)
        if (sig == last) return false
        last = sig
        return true
    }

    fun mark(files: List<File>) { last = signature(files) }

    private fun signature(files: List<File>) = files.joinToString("|") { "${it.absolutePath}:${it.length()}:${it.lastModified()}" }
}
