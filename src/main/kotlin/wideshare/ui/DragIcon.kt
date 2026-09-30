package wideshare.ui

import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import javax.swing.JPanel
import javax.swing.JWindow
import javax.swing.SwingUtilities

/** File icon that follows the cursor during a drag. It sits slightly away from the cursor, so it does not cover the target. */
internal class DragIcon {
    private var window: JWindow? = null

    fun show() = SwingUtilities.invokeLater {
        val w = window ?: build().also { window = it }
        w.isVisible = true
    }

    /** [x], [y]: cursor position in AWT coordinates. */
    fun moveTo(x: Int, y: Int) = SwingUtilities.invokeLater { window?.location = Point(x + 18, y + 18) }

    fun hide() = SwingUtilities.invokeLater { window?.isVisible = false }

    private fun build() = JWindow().apply {
        focusableWindowState = false
        isAlwaysOnTop = true
        runCatching { background = java.awt.Color(0, 0, 0, 0) }
        contentPane = object : JPanel() {
            init { isOpaque = false; preferredSize = Dimension(38, 46) }

            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.fileGlyph(2, 2, 34, 42, Palette.accent.awt())
            }
        }
        pack()
    }
}
