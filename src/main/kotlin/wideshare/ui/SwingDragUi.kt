package wideshare.ui

import wideshare.core.SendListener
import java.io.File
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * What appears on this computer during a file drag made here: the icon that follows the cursor and,
 * when it is not possible to know which files are being dragged (so they cannot follow to the other
 * computer), a drop zone per destination, in the bottom-right corner. After the drop the zone itself
 * shows the send progress and the result. [send] receives the dropped files, the destination and the progress listener.
 */
internal class SwingDragUi(private val send: (List<File>, String, SendListener) -> Unit) : DragUi {
    private var zone: JDialog? = null
    private var busy = 0
    private var hidden = true
    private val icon = DragIcon()

    override fun showIcon() = icon.show()

    override fun showZone(peers: List<String>) = SwingUtilities.invokeLater {
        zone?.dispose()
        busy = 0
        hidden = false
        zone = zoneDialog(peers).also { it.isVisible = true }
    }

    override fun follow(x: Int, y: Int) = icon.moveTo(x, y)

    override fun hide() {
        icon.hide()
        SwingUtilities.invokeLater {
            hidden = true
            // A moment of slack: the drop is still being delivered to the zone when the button is released.
            if (busy == 0) closeLater(400)
        }
    }

    /** Closes the current zone after [ms], if in the meantime it has not been used again. */
    private fun closeLater(ms: Int) {
        val leaving = zone
        Timer(ms) { if (zone === leaving && busy == 0 && hidden) { leaving?.dispose(); zone = null } }.apply { isRepeats = false; start() }
    }

    private fun zoneDialog(peers: List<String>) = JDialog().apply {
        isUndecorated = true
        isAlwaysOnTop = true
        focusableWindowState = false
        contentPane = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = Palette.panel.awt()
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
            peers.forEachIndexed { i, peer ->
                if (i > 0) add(Box.createVerticalStrut(10))
                add(ZonePanel(peer, { files, listener -> send(files, peer, listener) }, { SwingUtilities.invokeLater { busy++ } }, {
                    SwingUtilities.invokeLater { busy--; closeLater(CLOSE_DELAY_MS) }
                }))
            }
        }
        pack()
        roundCorners(22f)
        placeBottomRight()
    }

    private companion object {
        const val CLOSE_DELAY_MS = 6000
    }
}
