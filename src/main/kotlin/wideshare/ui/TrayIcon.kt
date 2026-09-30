package wideshare.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.imageio.ImageIO

/** System tray icon: a left click opens the app (Compose's Tray only reacts to a double click). */
@Composable
internal fun AppTrayIcon(tooltip: String, openLabel: String, exitLabel: String, onOpen: () -> Unit, onExit: () -> Unit) {
    val open by rememberUpdatedState(onOpen)
    val exit by rememberUpdatedState(onExit)
    DisposableEffect(tooltip, openLabel, exitLabel) {
        val image = Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")?.use(ImageIO::read)
        if (image == null || !SystemTray.isSupported()) return@DisposableEffect onDispose {}
        val menu = PopupMenu().apply {
            add(MenuItem(openLabel).apply { addActionListener { open() } })
            add(MenuItem(exitLabel).apply { addActionListener { exit() } })
        }
        val icon = TrayIcon(image, tooltip, menu).apply {
            isImageAutoSize = true
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) { if (e.button == MouseEvent.BUTTON1) open() }
            })
        }
        runCatching { SystemTray.getSystemTray().add(icon) }
        onDispose { SystemTray.getSystemTray().remove(icon) }
    }
}
