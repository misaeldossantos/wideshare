package wideshare

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import wideshare.core.ClientInfo
import wideshare.core.GridPos
import wideshare.core.Language
import wideshare.core.Mode
import wideshare.core.PairingRequest
import wideshare.core.ServerInfo
import wideshare.core.ThemeMode
import wideshare.core.Transfer
import wideshare.core.TransferState
import wideshare.ui.App
import wideshare.ui.AppController
import wideshare.ui.ClientSection
import wideshare.ui.ServerSection
import java.io.File
import javax.imageio.ImageIO
import java.nio.file.Files
import kotlin.test.Test

/** Renders the README screenshots into docs/screenshots. Run with `./gradlew test --tests '*Screenshots*' -Pscreenshots`. */
@OptIn(ExperimentalComposeUiApi::class)
class ScreenshotsTest {
    private val out = File("docs/screenshots").also { it.mkdirs() }

    private fun render(c: AppController, name: String) {
        val scale = 2
        val scene = ImageComposeScene(1140 * scale, 760 * scale, Density(scale.toFloat())) { App(c) }
        try {
            repeat(6) { scene.render(it * 50_000_000L) }
            val data = scene.render(400_000_000L).encodeToData(EncodedImageFormat.PNG)!!
            File(out, "$name.png").writeBytes(data.bytes)
        } finally { scene.close() }
    }

    /** The computer grid without the side menu, for the README. */
    private fun cropLayout() {
        val full = ImageIO.read(File(out, "positions-light.png"))
        ImageIO.write(full.getSubimage(628, 0, full.width - 628, 1100), "png", File(out, "layout.png"))
    }

    private fun serverScenes(c: AppController, suffix: String) {
        c.mode.value = Mode.SERVER
        c.running.value = true
        c.clients.value = listOf(
            ClientInfo("Laptop", 1920, 1080, GridPos(1, 0), true),
            ClientInfo("Studio", 2560, 1440, GridPos(-1, 0), false),
        )
        for ((section, name) in listOf(
            ServerSection.POSITIONS to "positions", ServerSection.PAIRED to "paired", ServerSection.SETTINGS to "settings",
            ServerSection.TRANSFERS to "transfers", ServerSection.ACTIVITY to "activity",
        )) { c.section.value = section; render(c, "$name-$suffix") }
        c.section.value = ServerSection.POSITIONS
        c.pairing.value = PairingRequest("Laptop", "482913")
        render(c, "pairing-$suffix")
        c.pairing.value = null
    }

    private fun clientScenes(c: AppController, suffix: String) {
        c.mode.value = Mode.CLIENT
        c.running.value = true
        c.servers.value = emptyList()
        render(c, "searching-$suffix")
        c.servers.value = listOf(ServerInfo("Desk PC", "a".repeat(32), "192.168.0.10", 24800, 0), ServerInfo("Office PC", "b".repeat(32), "192.168.0.22", 24800, 0))
        c.clientSection.value = ClientSection.SERVERS
        render(c, "servers-$suffix")
    }

    @Test
    fun renderScreenshots() {
        if (System.getProperty("screenshots").isNullOrEmpty()) return
        System.setProperty("user.home", Files.createTempDirectory("wideshare-shots").toString())
        val c = AppController()
        c.setLanguage(Language.EN)
        c.setName("Desk PC")
        c.files.set("C:/Users/me/Downloads/WideShare")
        listOf("Laptop" to GridPos(1, 0), "Studio" to GridPos(-1, 0), "Media PC" to GridPos(0, 1)).forEach { (n, p) -> c.setPosition(n, p) }
        listOf("Laptop", "Studio", "Media PC").forEachIndexed { i, n -> c.trust.trust("id$i", n, ByteArray(32)) }
        c.files.transfers.items.value = listOf(
            Transfer(-1, "Laptop", 80_000_000, "project.zip", 34_400_000, TransferState.RECEIVING),
            Transfer(-2, "Studio", 2_400_000, "notes.txt, photo.png", 2_400_000, TransferState.READY, System.currentTimeMillis() + 420_000, "C:/Temp/WideShare"),
            Transfer(-3, "Laptop", 12_500_000, "report.pdf", 12_500_000, TransferState.SAVED, folder = "C:/Users/me/Downloads/WideShare"),
        )
        listOf("09:12  Server \"Desk PC\" announced on the network (port 24800)", "09:12  Client \"Laptop\" connected (192.168.0.31)", "09:14  Received from \"Laptop\": report.pdf")
            .forEach { c.log(it) }
        for ((theme, suffix) in listOf(ThemeMode.DARK to "dark", ThemeMode.LIGHT to "light")) {
            c.setTheme(theme)
            serverScenes(c, suffix)
            if (suffix == "light") cropLayout()
            clientScenes(c, suffix)
        }
    }
}
