package wideshare.core

import java.io.File
import java.util.Properties

enum class Mode { SERVER, CLIENT }

/** Which color theme the interface uses; SYSTEM follows the operating system. */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** Configuration persisted in ~/.wideshare/settings.properties. */
class Settings(
    var mode: Mode = Mode.SERVER,
    var name: String = defaultName(),
    var deviceId: String = Auth.newId(),
    var autoConnect: Boolean = true,
    var lastServer: String = "",
    var theme: ThemeMode = ThemeMode.SYSTEM,
    var language: Language = Language.detect(),
    var shareClipboard: Boolean = false,
    var sendAudio: Boolean = false,
    var receiveClipboard: Boolean = false,
    var receiveFolder: String = defaultReceiveFolder(),
    val positions: MutableMap<String, GridPos> = mutableMapOf(),
    val paired: MutableMap<String, PairedPeer> = mutableMapOf(),
) {
    fun save() {
        val p = Properties()
        p["mode"] = mode.name
        p["name"] = name
        p["deviceId"] = deviceId
        p["autoConnect"] = autoConnect.toString()
        p["lastServer"] = lastServer
        p["theme"] = theme.name
        p["language"] = language.code
        p["shareClipboard"] = shareClipboard.toString()
        p["sendAudio"] = sendAudio.toString()
        p["receiveClipboard"] = receiveClipboard.toString()
        p["receiveFolder"] = receiveFolder
        paired.forEach { (id, peer) -> p["paired.$id"] = "${Auth.hex(peer.psk)}|${peer.name}" }
        positions.forEach { (client, pos) -> p["pos.$client"] = "${pos.x},${pos.y}" }
        runCatching {
            file.parentFile.mkdirs()
            file.outputStream().use { p.store(it, "WideShare") }
        }
    }

    companion object {
        val file = File(System.getProperty("user.home"), ".wideshare/settings.properties")

        /** Folder where files sent by another computer arrive. */
        fun defaultReceiveFolder(): String = File(System.getProperty("user.home"), "Downloads/WideShare").path

        fun defaultName(): String =
            runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull()?.takeIf { it.isNotBlank() } ?: "computer"

        fun load(): Settings {
            val s = Settings()
            if (!file.exists()) return s
            val p = Properties()
            runCatching { file.inputStream().use(p::load) }
            p.getProperty("mode")?.let { m -> Mode.entries.firstOrNull { it.name == m }?.let { s.mode = it } }
            p.getProperty("name")?.takeIf { it.isNotBlank() }?.let { s.name = it }
            p.getProperty("deviceId")?.takeIf { it.length == 32 }?.let { s.deviceId = it }
            s.autoConnect = p.getProperty("autoConnect", "true").toBoolean()
            s.lastServer = p.getProperty("lastServer", "")
            s.theme = ThemeMode.entries.firstOrNull { it.name == p.getProperty("theme") } ?: s.theme
            Language.fromCode(p.getProperty("language"))?.let { s.language = it }
            s.shareClipboard = p.getProperty("shareClipboard", "false").toBoolean()
            s.sendAudio = p.getProperty("sendAudio", "false").toBoolean()
            s.receiveClipboard = p.getProperty("receiveClipboard", s.shareClipboard.toString()).toBoolean()
            p.getProperty("receiveFolder")?.takeIf { it.isNotBlank() }?.let { s.receiveFolder = it }
            p.stringPropertyNames().filter { it.startsWith("pos.") }.forEach { k ->
                val (x, y) = p.getProperty(k).split(",").mapNotNull { it.trim().toIntOrNull() }.takeIf { it.size == 2 } ?: return@forEach
                s.positions[k.removePrefix("pos.")] = GridPos(x, y)
            }
            p.stringPropertyNames().filter { it.startsWith("paired.") }.forEach { k ->
                val (psk, name) = p.getProperty(k).split("|", limit = 2).takeIf { it.size == 2 } ?: return@forEach
                s.paired[k.removePrefix("paired.")] = PairedPeer(name, Auth.unhex(psk))
            }
            return s
        }
    }
}

/** Cell in the layout grid. The server is always at (0, 0); x grows to the right and y downwards. */
data class GridPos(val x: Int, val y: Int) {
    fun step(side: Side) = when (side) {
        Side.LEFT -> copy(x = x - 1)
        Side.RIGHT -> copy(x = x + 1)
        Side.TOP -> copy(y = y - 1)
        Side.BOTTOM -> copy(y = y + 1)
    }

    companion object {
        val SERVER = GridPos(0, 0)
    }
}

class PairedPeer(val name: String, val psk: ByteArray)
