package wideshare

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import wideshare.platform.Platform
import wideshare.platform.windows.setTitleBarDark
import wideshare.ui.App
import wideshare.ui.AppController
import java.awt.Dimension

/** Minimum window size (in logical pixels): below this the side menu and the content do not fit. */
private const val MIN_WIDTH = 900
private const val MIN_HEIGHT = 620

fun main() {
    fixJavaHomeForNativeImage()
    application {
        val controller = AppController()
        Window(
            onCloseRequest = {
                controller.stop()
                exitApplication()
            },
            title = "WideShare",
            icon = painterResource("icon.png"),
            state = rememberWindowState(width = 1140.dp, height = 760.dp),
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(MIN_WIDTH, MIN_HEIGHT) }
            App(controller) { dark ->
                if (Platform.isWindows) repeat(20) { if (setTitleBarDark(window, dark)) return@App; Thread.sleep(50) }
            }
        }
    }
}
