package wideshare.platform.linux.wayland

import wideshare.platform.ScreenSize
import java.awt.GraphicsEnvironment

/** Size of the primary screen according to Java; Wayland has no call to ask the compositor. */
internal fun awtScreen(): ScreenSize = runCatching {
    val mode = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.displayMode
    ScreenSize(mode.width, mode.height)
}.getOrElse { ScreenSize(1920, 1080) }
