package wideshare.platform.linux.wayland

import java.io.File

/** Token that lets the portal remember the user's approval, so the dialog does not appear on every start. */
internal object RestoreToken {
    private val file = File(System.getProperty("user.home"), ".wideshare/wayland-remote-desktop.token")

    fun read(): String? = runCatching { file.readText().trim().takeIf { it.isNotEmpty() } }.getOrNull()

    fun write(token: String) {
        runCatching {
            file.parentFile.mkdirs()
            file.writeText(token)
        }
    }
}
