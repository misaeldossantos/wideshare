package wideshare.ui

import wideshare.core.tr
import wideshare.core.ClientEngine
import wideshare.core.ClientInfo
import wideshare.core.ClientState
import wideshare.core.Mode
import wideshare.core.Identity
import wideshare.core.PairingRequest
import wideshare.core.ServerEngine
import wideshare.core.ServerInfo
import wideshare.core.SendListener
import wideshare.core.Settings
import wideshare.core.ThemeMode
import wideshare.core.GridPos
import wideshare.core.I18n
import wideshare.core.Language
import wideshare.platform.InputInjector
import wideshare.platform.Platform
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** Connects the UI to the server/client engines and holds the observable state. */
class AppController {
    internal val settings = Settings.load().also { it.save() }

    val mode = MutableStateFlow(settings.mode)
    val name = MutableStateFlow(settings.name)
    val autoConnect = MutableStateFlow(settings.autoConnect)
    val theme = MutableStateFlow(settings.theme)
    val language = MutableStateFlow(settings.language.also { I18n.language = it })
    val shareClipboard = MutableStateFlow(settings.shareClipboard)
    val sendAudio = MutableStateFlow(settings.sendAudio)
    val receiveClipboard = MutableStateFlow(settings.receiveClipboard)

    val section = MutableStateFlow(ServerSection.POSITIONS)
    val clientSection = MutableStateFlow(ClientSection.SERVERS)

    val running = MutableStateFlow(false)
    val clients = MutableStateFlow<List<ClientInfo>>(emptyList())
    val servers = MutableStateFlow<List<ServerInfo>>(emptyList())
    val clientState = MutableStateFlow<ClientState>(ClientState.Searching)
    private val buffer = LogBuffer()
    val logs = buffer.lines
    val files = FileOptions(settings)
    private val store = LayoutStore(settings)
    val layout = store.layout

    /** Pairing request waiting for the user's confirmation (shown as an overlay). */
    val pairing = MutableStateFlow<PairingRequest?>(null)
    internal val trust = SettingsTrust(settings)
    val paired = trust.paired

    internal fun identity() = Identity(settings.deviceId, name.value)

    private var server: ServerEngine? = null
    private var client: ClientEngine? = null
    internal var injector: InputInjector? = null

    fun setMode(m: Mode) { mode.value = m; persist() }
    fun setName(v: String) { name.value = v; persist() }
    fun setAutoConnect(v: Boolean) { autoConnect.value = v; persist() }
    fun setTheme(v: ThemeMode) { theme.value = v; persist() }
    fun setLanguage(v: Language) { I18n.language = v; language.value = v; persist() }
    fun setShareClipboard(v: Boolean) { shareClipboard.value = v; persist() }
    fun setReceiveClipboard(v: Boolean) { receiveClipboard.value = v; persist() }
    fun setSendAudio(v: Boolean) { sendAudio.value = v; persist(); kotlin.concurrent.thread(isDaemon = true) { client?.refreshAudio() } }

    /** Only one pairing at a time: simultaneous requests are declined. */
    internal fun onPairing(request: PairingRequest?) {
        if (request != null && pairing.value != null) { request.reject(); return }
        pairing.value = request
    }

    fun forgetPeer(id: String) = trust.forget(id)

    private fun persist() {
        settings.mode = mode.value
        settings.name = name.value
        settings.autoConnect = autoConnect.value
        settings.theme = theme.value
        settings.language = language.value
        settings.shareClipboard = shareClipboard.value
        settings.sendAudio = sendAudio.value
        settings.receiveClipboard = receiveClipboard.value
        settings.save()
    }

    internal fun log(line: String) = buffer.add(line)

    fun setPosition(client: String, cell: GridPos) { if (store.move(client, cell)) server?.refresh() }

    /** Forgets a disconnected client, freeing its cell. */
    fun forget(client: String) {
        if (clients.value.none { it.name == client }) store.remove(client)
    }

    fun start() {
        if (running.value) return
        if (name.value.isBlank()) { log(tr("log.nameRequired")); return }
        try {
            when (mode.value) {
                Mode.SERVER -> {
                    val s = ServerEngine(identity(), trust, Platform.createCapture(), store::posOf, ::log, { clients.value = it }, ::onPairing, { shareClipboard.value }, { receiveClipboard.value }, files.hooks)
                    s.start()
                    server = s
                }
                Mode.CLIENT -> {
                    client = launchClient()
                    log(tr("log.searchingServers"))
                }
            }
            running.value = true
        } catch (e: Throwable) {
            log(tr("log.startFailed", e.message ?: e.javaClass.simpleName))
            stop()
        }
    }

    fun stop() {
        runCatching { server?.stop() }
        runCatching { client?.stop() }
        runCatching { injector?.close() }
        server = null; client = null; injector = null
        clients.value = emptyList()
        servers.value = emptyList()
        clientState.value = ClientState.Searching
        pairing.value?.reject()
        if (running.value) log(tr("log.stopped"))
        running.value = false
    }

    fun sendFiles(dropped: List<File>, peer: String? = null, listener: SendListener? = null) { server?.sendFiles(dropped, peer, listener); client?.sendFiles(dropped, listener) }

    fun connectTo(server: ServerInfo) { client?.connectTo(server.id) }
}

