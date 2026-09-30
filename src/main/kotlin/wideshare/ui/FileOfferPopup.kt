package wideshare.ui

import wideshare.core.tr
import wideshare.core.Transfer
import wideshare.core.TransferLog
import wideshare.core.TransferState
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Notice in the corner of the screen about files arriving from another computer. If they were copied there, it first asks
 * whether they should come to this clipboard. Then (or straight away, if they were dragged) it shows the progress and,
 * when done, offers to open the folder. It does not steal keyboard focus.
 */
internal object FileOfferPopup {
    private var current: OfferDialog? = null

    fun show(summary: String, log: TransferLog, onAccept: () -> Unit) = SwingUtilities.invokeLater {
        current?.dispose()
        current = OfferDialog(summary, log, onAccept, log.items.value.maxOfOrNull { it.id } ?: 0L).also { it.open() }
    }

    /** Shows the progress of every new transfer that has no notice open (the dragged ones: nobody was asked anything). */
    fun watch(log: TransferLog) {
        var seen = log.items.value.maxOfOrNull { it.id } ?: 0L
        CoroutineScope(Dispatchers.Default).launch {
            log.items.collect { list ->
                val fresh = list.filter { it.id > seen }.minByOrNull { it.id } ?: return@collect
                seen = fresh.id
                SwingUtilities.invokeLater {
                    if (current == null) current = OfferDialog(null, log, {}, fresh.id - 1).also { it.open() }
                }
            }
        }
    }

    /** [summary] null = just follows a transfer starting from [baseId] (no question). */
    private class OfferDialog(private val summary: String?, private val log: TransferLog, private val onAccept: () -> Unit, private val baseId: Long) : JDialog() {
        private val title = label(15f, Font.BOLD, Palette.text)
        private val body = label(13f, Font.PLAIN, Palette.muted)
        private val bar = ProgressTrack(Palette.panelHi, Palette.accent)
        private val status = label(12f, Font.PLAIN, Palette.muted)
        private val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply { isOpaque = false; alignmentX = 0f }
        private val poll = Timer(150) { refresh() }
        private val expire = Timer(OFFER_TIMEOUT_MS) { dispose() }.apply { isRepeats = false }

        init {
            isUndecorated = true
            isAlwaysOnTop = true
            focusableWindowState = false
            contentPane = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                background = Palette.panel.awt()
                border = BorderFactory.createEmptyBorder(20, 22, 18, 22)
                listOf(title, Box.createVerticalStrut(8), body, Box.createVerticalStrut(14), bar, Box.createVerticalStrut(8), status, Box.createVerticalStrut(16), buttons)
                    .forEach { add(it) }
            }
            roundCorners(22f)
            addWindowListener(object : WindowAdapter() {
                override fun windowClosed(e: WindowEvent) { poll.stop(); expire.stop(); if (current === this@OfferDialog) current = null }
            })
            if (summary != null) showOffer(summary) else startProgress()
        }

        fun open() {
            isVisible = true
            if (summary != null) expire.start()
        }

        private fun showOffer(summary: String) {
            title.text = tr("offer.title")
            setText(body, summary)
            bar.isVisible = false
            status.text = " "
            buttons.removeAll()
            buttons.add(RoundedButton(tr("offer.later"), Palette.panelHi, Palette.text) { dispose() })
            buttons.add(RoundedButton(tr("offer.send"), Palette.accent, Palette.onAccent) {
                expire.stop()
                startProgress()
                onAccept()
            })
            place()
        }

        private fun startProgress() {
            buttons.removeAll()
            title.text = tr("recv.title")
            setText(body, " ")
            bar.isVisible = true
            bar.fraction = 0f
            status.text = tr("recv.waiting")
            place()
            poll.start()
        }

        private fun refresh() {
            val t = log.items.value.filter { it.id > baseId }.minByOrNull { it.id } ?: return
            bar.fraction = t.fraction()
            when (t.state) {
                TransferState.RECEIVING -> {
                    setText(body, t.names.ifBlank { " " })
                    status.text = t.progressText()
                }
                TransferState.READY -> finished(t, tr("recv.readyTitle"), tr("recv.readyBody"))
                TransferState.SAVED -> finished(t, tr("recv.savedTitle"), tr("recv.savedBody", t.names, t.folder))
                else -> finished(t, tr("recv.failTitle"), tr("recv.failBody"))
            }
        }

        private fun finished(t: Transfer, heading: String, message: String) {
            poll.stop()
            title.text = heading
            setText(body, message)
            status.text = " "
            buttons.removeAll()
            buttons.add(RoundedButton(tr("btn.close"), Palette.panelHi, Palette.text) { dispose() })
            if (t.folder.isNotBlank()) buttons.add(RoundedButton(tr("btn.openFolder"), Palette.accent, Palette.onAccent) { FolderOpener.open(t.folder); dispose() })
            place()
            Timer(CLOSE_DELAY_MS) { dispose() }.apply { isRepeats = false; start() }
        }

        private fun setText(label: JLabel, text: String) {
            label.text = "<html><div style='width:320px'>${text.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>")}</div></html>"
        }

        private fun place() { pack(); placeBottomRight() }

        private fun label(size: Float, style: Int, color: androidx.compose.ui.graphics.Color) = JLabel().apply {
            font = font.deriveFont(style, size)
            foreground = color.awt()
            alignmentX = 0f
        }
    }

    private const val OFFER_TIMEOUT_MS = 30_000
    private const val CLOSE_DELAY_MS = 20_000
}
