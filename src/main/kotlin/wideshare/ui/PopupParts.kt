package wideshare.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JButton
import javax.swing.JComponent

internal fun Color.awt() = java.awt.Color(toArgb(), true)

private fun Graphics.smooth() = (this as Graphics2D).also { it.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON) }

/** Flat button with rounded corners and no border. */
internal class RoundedButton(text: String, private val fill: Color, private val fg: Color, onClick: () -> Unit) : JButton(text) {
    init {
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isOpaque = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        font = font.deriveFont(java.awt.Font.BOLD, 12.5f)
        foreground = fg.awt()
        addActionListener { onClick() }
    }

    override fun getPreferredSize(): Dimension = super.getPreferredSize().let { Dimension(it.width + 28, 36) }

    override fun paintComponent(g: Graphics) {
        val g2 = g.smooth()
        g2.color = fill.awt()
        g2.fillRoundRect(0, 0, width, height, 12, 12)
        super.paintComponent(g)
    }
}

/** Thin, rounded progress bar. */
internal class ProgressTrack(private val track: Color, private val fill: Color) : JComponent() {
    var fraction = 0f
        set(value) { field = value.coerceIn(0f, 1f); repaint() }

    init { preferredSize = Dimension(320, 8) }

    override fun paintComponent(g: Graphics) {
        val g2 = g.smooth()
        g2.color = track.awt()
        g2.fillRoundRect(0, 0, width, height, height, height)
        g2.color = fill.awt()
        g2.fillRoundRect(0, 0, (width * fraction).toInt().coerceAtLeast(if (fraction > 0f) height else 0), height, height, height)
    }
}

/** Draws a file icon (a sheet with a folded corner). */
internal fun Graphics2D.fileGlyph(x: Int, y: Int, w: Int, h: Int, body: java.awt.Color) {
    val cut = w / 3
    color = body
    fillPolygon(intArrayOf(x, x + w - cut, x + w, x + w, x), intArrayOf(y, y, y + cut, y + h, y + h), 5)
    color = java.awt.Color(0, 0, 0, 70)
    fillPolygon(intArrayOf(x + w - cut, x + w - cut, x + w), intArrayOf(y, y + cut, y + cut), 3)
    color = java.awt.Color(255, 255, 255, 170)
    for (i in 0..2) fillRoundRect(x + 7, y + cut + 6 + i * 7, w - 14, 3, 3, 3)
}

/** Rounds the window's corners, following its size. */
internal fun java.awt.Window.roundCorners(radius: Float) {
    fun apply() { shape = java.awt.geom.RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), radius, radius) }
    addComponentListener(object : java.awt.event.ComponentAdapter() {
        override fun componentResized(e: java.awt.event.ComponentEvent) = apply()
    })
    apply()
}

/** Puts the window in the bottom-right corner of the screen, the same place as the file notices. */
internal fun java.awt.Window.placeBottomRight(margin: Int = 20) {
    val area = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    location = java.awt.Point(area.x + area.width - width - margin, area.y + area.height - height - margin)
}
