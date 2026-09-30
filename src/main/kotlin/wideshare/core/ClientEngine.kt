package wideshare.core

import wideshare.platform.InputInjector
import wideshare.platform.AudioCapture
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class ClientEngine(
    private val me: Identity,
    private val trust: TrustStore,
    private val injector: InputInjector,
    private val autoConnect: () -> Boolean,
    private val preferredServerId: () -> String,
    private val log: (String) -> Unit,
    private val onState: (ClientState) -> Unit,
    private val onServers: (List<ServerInfo>) -> Unit,
    private val onPairing: (PairingRequest?) -> Unit = {},
    private val sendAudio: () -> Boolean = { false },
    private val files: FileHooks = FileHooks(),
) {
    private val listener = ServerListener { onServers(it) }
    @Volatile private var running = false
    @Volatile private var requested: String? = null
    @Volatile private var current: SecureChannel? = null
    private var outbox: LinkedBlockingQueue<Message>? = null
    private var serverName = ""
    private var speaker: AudioCapture? = null
    @Volatile private var session: ClientSession? = null

    fun start() {
        running = true
        listener.start()
        thread(isDaemon = true, name = "client-loop") { loop() }
    }

    fun stop() {
        running = false
        listener.stop()
        current?.close()
    }

    /** Turns sound sending on or off according to the toggle and the current connection (call when the toggle changes). */
    @Synchronized
    fun refreshAudio() {
        val box = outbox
        if (box != null && sendAudio()) {
            if (speaker == null) speaker = startSpeaker(serverName, box, log)
        } else {
            speaker?.stop()
            speaker = null
        }
    }

    /** Sends [files] (dragged onto the window) to the connected server. */
    fun sendFiles(files: List<File>, listener: SendListener? = null) { session?.sendFiles(files, false, listener) }

    /** Connects to the server with that id, pairing if it is not known yet (ends the current connection). */
    fun connectTo(serverId: String) {
        requested = serverId
        current?.close()
    }

    private fun pick() = pickServer(listener.servers, requested, autoConnect(), trust, preferredServerId())

    private fun loop() {
        while (running) {
            val target = pick()
            if (target == null) {
                onState(ClientState.Searching)
                sleep(500)
                continue
            }
            onState(ClientState.Connecting(target.name))
            try {
                connect(target)
            } catch (e: java.io.IOException) {
                if (!running) break
                when (e) {
                    is AuthException, is PairingRejectedException -> {
                        log("\"${target.name}\": ${e.message}")
                        onState(ClientState.Failed(e.message ?: tr("log.authFailed")))
                        requested = null
                        sleep(4000)
                    }
                    else -> {
                        log(tr("log.connectionLostWith", target.name, e.message ?: e.javaClass.simpleName))
                        onState(ClientState.Failed(e.message ?: tr("log.connectionLost")))
                        sleep(2000)
                    }
                }
            } catch (e: Exception) {
                if (running) {
                    log(tr("log.error", e.message ?: e.javaClass.simpleName))
                    sleep(2000)
                }
            }
        }
    }

    private fun sleep(ms: Long) = try { Thread.sleep(ms) } catch (_: InterruptedException) {}

    private fun connect(target: ServerInfo) {
        val socket = Socket()
        socket.connect(InetSocketAddress(target.address, target.port), 3000)
        val hs = SecureChannel.clientHandshake(socket, me, trust)
        val channel = hs.channel
        current = channel
        if (hs.sas != null) {
            onState(ClientState.Pairing(target.name))
            log(tr("log.pairing", target.name))
            runPairing(hs, trust, onPairing)
            log(tr("log.paired", target.name))
        }
        val screen = injector.screen()
        channel.send(Codec.encode(Message.Hello(me.name, screen.width, screen.height)))
        log(tr("log.connectedTo", target.name, target.address))
        onState(ClientState.Connected(target.name, false))

        val outbox = LinkedBlockingQueue<Message>()
        val session = ClientSession(target.name, outbox, injector, onState, hooks = files, connected = { running && current === channel }, onFiles = { log(tr("log.received", target.name, it.joinToString { f -> f.name })) })
        this.session = session
        this.outbox = outbox
        serverName = target.name
        refreshAudio()
        val writer = thread(isDaemon = true, name = "client-writer") {
            try {
                while (true) channel.send(Codec.encode(outbox.poll(2, TimeUnit.SECONDS) ?: Message.Ping))
            } catch (_: Exception) {
                channel.close()
            }
        }
        try {
            while (running) session.handle(Codec.decode(channel.receive()))
        } finally {
            session.releaseAll()
            session.close()
            this.session = null
            this.outbox = null
            refreshAudio()
            channel.close()
            writer.interrupt()
            current = null
        }
    }
}
