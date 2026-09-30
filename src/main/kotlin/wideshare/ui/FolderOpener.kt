package wideshare.ui

import wideshare.platform.Platform
import java.awt.Desktop
import java.io.File
import kotlin.concurrent.thread

/** Opens a folder in the system file explorer. */
internal object FolderOpener {
    fun open(path: String) {
        thread(isDaemon = true, name = "open-folder") {
            val opened = runCatching {
                check(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN))
                Desktop.getDesktop().open(File(path))
            }
            if (opened.isFailure) runCatching { ProcessBuilder(if (Platform.isWindows) "explorer.exe" else "xdg-open", path).start() }
        }
    }
}
