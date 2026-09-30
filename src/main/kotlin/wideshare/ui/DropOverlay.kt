package wideshare.ui

import wideshare.core.tr
import wideshare.core.DragTarget
import wideshare.platform.DragProbe
import wideshare.platform.Platform
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * The drop zone this computer shows when the other one drags files to it: bottom-right corner,
 * the same place as the copy and paste notice. It follows the cursor (injected by the server or the real one) with the
 * file icon and is highlighted when the cursor is over it.
 */
internal class DropOverlay : DragTarget {
    private val icon = DragIcon()
    private var dialog: JDialog? = null
    private var panel: ZoneCard? = null
    private var poll: Timer? = null
    private var probe: DragProbe? = null

    /** The zone in screen pixels (the injected cursor uses real pixels, AWT uses scale-adjusted pixels). */
    @Volatile private var zonePx: Rectangle? = null
    @Volatile private var scale = 1.0

    override fun show(summary: String, onDrop: (() -> Unit)?) = SwingUtilities.invokeLater {
        close()
        val card = ZoneCard(summary)
        val d = JDialog().apply {
            isUndecorated = true
            isAlwaysOnTop = true
            focusableWindowState = false
            contentPane = card
            pack()
            roundCorners(22f)
            placeBottomRight()
        }
        scale = d.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0
        zonePx = d.bounds.let { Rectangle((it.x * scale).toInt(), (it.y * scale).toInt(), (it.width * scale).toInt(), (it.height * scale).toInt()) }
        dialog = d
        panel = card
        d.isVisible = true
        icon.show()
        if (onDrop != null) followRealMouse(d, onDrop)
    }

    override fun hover(x: Int, y: Int): Boolean {
        val inside = zonePx?.contains(x, y) == true
        SwingUtilities.invokeLater {
            panel?.highlight(inside)
            icon.moveTo((x / scale).toInt(), (y / scale).toInt())
        }
        return inside
    }

    override fun hide() = SwingUtilities.invokeLater { close() }

    /** Real mouse: follows the cursor and, when the left button is released, drops if it is over the zone. */
    private fun followRealMouse(d: JDialog, onDrop: () -> Unit) {
        val started = System.currentTimeMillis()
        poll = Timer(40) {
            val p = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
            val probe = probe ?: Platform.createDragProbe().also { probe = it }
            val inside = p != null && d.bounds.contains(p)
            if (p != null) { panel?.highlight(inside); icon.moveTo(p.x, p.y) }
            if (probe?.leftDown() != true || System.currentTimeMillis() - started > TIMEOUT_MS) {
                close()
                if (inside) onDrop()
            }
        }.also { it.start() }
    }

    private fun close() {
        poll?.stop()
        poll = null
        probe?.close()
        probe = null
        dialog?.dispose()
        dialog = null
        panel = null
        zonePx = null
        icon.hide()
    }

    private class ZoneCard(private val summary: String) : JPanel() {
        private var hover = false

        init {
            background = Palette.panel.awt()
            preferredSize = Dimension(380, 132)
        }

        fun highlight(on: Boolean) { if (on != hover) { hover = on; repaint() } }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.color = (if (hover) Palette.accent else Palette.accentSoft).awt()
            g2.fillRoundRect(12, 12, width - 24, height - 24, 18, 18)
            g2.fileGlyph(34, height / 2 - 22, 34, 44, (if (hover) Palette.onAccent else Palette.accent).awt())
            g2.font = font.deriveFont(Font.BOLD, 16f)
            g2.color = (if (hover) Palette.onAccent else Palette.text).awt()
            g2.drawString(if (hover) tr("drop.hover") else tr("drop.idle"), 88, height / 2 - 4)
            g2.font = font.deriveFont(Font.PLAIN, 12.5f)
            g2.color = (if (hover) Palette.onAccent else Palette.muted).awt()
            g2.drawString(fit(g2, summary, width - 88 - 26), 88, height / 2 + 16)
        }

        private fun fit(g2: Graphics2D, text: String, max: Int): String {
            if (g2.fontMetrics.stringWidth(text) <= max) return text
            var cut = text.length
            while (cut > 1 && g2.fontMetrics.stringWidth(text.take(cut) + "…") > max) cut--
            return text.take(cut) + "…"
        }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
