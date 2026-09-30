package wideshare.ui

import wideshare.core.tr
import wideshare.core.SendListener
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.io.File
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Local drop zone, used when it is not possible to know which files are being dragged (Linux). Dropping
 * onto it sends the files to the computer [peer]; the zone itself shows the send progress and the result.
 * [onStart] and [onEnd] notify the window that contains it.
 */
internal class ZonePanel(
    private val peer: String,
    private val onFiles: (List<File>, SendListener) -> Unit,
    private val onStart: () -> Unit,
    private val onEnd: () -> Unit,
) : JPanel() {
    private enum class Phase { IDLE, HOVER, SENDING, DONE, FAILED }

    @Volatile private var phase = Phase.IDLE
    @Volatile private var sent = 0L
    @Volatile private var total = 0L
    private var count = 0

    init {
        isOpaque = false
        preferredSize = Dimension(380, 104)
        DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE, object : DropTargetAdapter() {
            override fun dragEnter(e: DropTargetDragEvent) { if (phase == Phase.IDLE) update(Phase.HOVER); answer(e) }
            override fun dragOver(e: DropTargetDragEvent) = answer(e)
            override fun dragExit(e: DropTargetEvent) { if (phase == Phase.HOVER) update(Phase.IDLE) }
            override fun drop(e: DropTargetDropEvent) = receive(e)
        }, true)
    }

    private fun answer(e: DropTargetDragEvent) {
        if (phase < Phase.SENDING && e.isDataFlavorSupported(DataFlavor.javaFileListFlavor) && e.sourceActions and DnDConstants.ACTION_COPY != 0) e.acceptDrag(DnDConstants.ACTION_COPY)
        else e.rejectDrag()
    }

    /** Accepts copy only: accepting "move" would make the explorer delete the originals. */
    private fun receive(e: DropTargetDropEvent) {
        if (e.sourceActions and DnDConstants.ACTION_COPY == 0) { e.rejectDrop(); return }
        e.acceptDrop(DnDConstants.ACTION_COPY)
        val files = runCatching { e.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*> }.getOrNull().orEmpty().filterIsInstance<File>()
        e.dropComplete(files.isNotEmpty())
        if (files.isEmpty()) return update(Phase.IDLE)
        count = files.size
        sent = 0
        total = 0
        update(Phase.SENDING)
        onStart()
        onFiles(files, object : SendListener {
            override fun progress(sent: Long, total: Long) { this@ZonePanel.sent = sent; this@ZonePanel.total = total; repaintLater() }
            override fun finished(ok: Boolean) { phase = if (ok) Phase.DONE else Phase.FAILED; repaintLater(); onEnd() }
        })
    }

    private fun update(next: Phase) { phase = next; repaintLater() }
    private fun repaintLater() = SwingUtilities.invokeLater { repaint() }

    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        val p = phase
        val active = p != Phase.IDLE
        g2.color = (when (p) { Phase.IDLE -> Palette.accentSoft; Phase.FAILED -> Palette.danger; else -> Palette.accent }).awt()
        g2.fillRoundRect(0, 0, width, height, 18, 18)
        val strong = (if (active) Palette.onAccent else Palette.text).awt()
        val soft = (if (active) Palette.onAccent else Palette.muted).awt()
        g2.fileGlyph(24, height / 2 - 22, 34, 44, (if (active) Palette.onAccent else Palette.accent).awt())
        val (title, sub) = when (p) {
            Phase.IDLE -> tr("dropzone.idle") to tr("dropzone.idleHint", peer)
            Phase.HOVER -> tr("dropzone.hover") to tr("dropzone.idleHint", peer)
            Phase.SENDING -> tr("dropzone.sending", peer) to tr("dropzone.sendingHint", formatBytes(sent), formatBytes(total), percent())
            Phase.DONE -> tr("dropzone.done") to tr("dropzone.doneHint", if (count == 1) tr("items.one") else tr("items.many", count), peer)
            Phase.FAILED -> tr("dropzone.failed") to tr("dropzone.failedHint")
        }
        val lift = if (p == Phase.SENDING || p == Phase.DONE) 10 else 0
        g2.font = font.deriveFont(Font.BOLD, 16f)
        g2.color = strong
        g2.drawString(title, 78, height / 2 - 4 - lift)
        g2.font = font.deriveFont(Font.PLAIN, 12.5f)
        g2.color = soft
        g2.drawString(sub, 78, height / 2 + 16 - lift)
        if (p == Phase.SENDING || p == Phase.DONE) drawBar(g2, if (p == Phase.DONE) 1f else percent() / 100f)
    }

    private fun percent() = if (total <= 0) 0 else (sent * 100 / total).toInt().coerceIn(0, 100)

    private fun drawBar(g2: Graphics2D, fraction: Float) {
        val x = 78
        val w = width - x - 24
        g2.color = java.awt.Color(255, 255, 255, 70)
        g2.fillRoundRect(x, height - 26, w, 8, 8, 8)
        g2.color = Palette.onAccent.awt()
        g2.fillRoundRect(x, height - 26, (w * fraction).toInt().coerceAtLeast(if (fraction > 0f) 8 else 0), 8, 8, 8)
    }
}
