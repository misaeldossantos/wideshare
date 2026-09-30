package wideshare.core

import wideshare.platform.CaptureListener
import wideshare.platform.InputCapture
import wideshare.platform.Platform
import java.io.File
import java.net.BindException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Server: captures the local input and forwards it to the computer that has the cursor. The computers
 * occupy cells of a grid ([posOf]; the server sits at [GridPos.SERVER]) and the cursor moves
 * between neighboring cells, whether clients or the server itself.
 */
class ServerEngine(
    private val me: Identity,
    private val trust: TrustStore,
    private val capture: InputCapture,
    private val posOf: (String) -> GridPos,
    private val log: (String) -> Unit,
    private val onClients: (List<ClientInfo>) -> Unit,
    private val onPairing: (PairingRequest?) -> Unit = {},
    private val shareClipboard: () -> Boolean = { false },
    private val receiveClipboard: () -> Boolean = { false },
    files: FileHooks = FileHooks(),
) : CaptureListener {
    private val sessions = ConcurrentHashMap<String, ServerSession>()
    private val lock = Any()

    /** Client that has the cursor; null = the server. */
    @Volatile private var active: ServerSession? = null
    @Volatile private var running = false
    private var serverSocket: ServerSocket? = null
    private var beacon: Beacon? = null
    private val clipboard = ServerClipboard(files, shareClipboard, receiveClipboard, { active }, log, capture::cancelDrag)
    private val owner = object : ServerSession.Owner {
        override fun onLeave(session: ServerSession, leave: Message.Leave) { synchronized(lock) { if (active === session) cross(session, leave.side, leave.fraction) } }
        override val clipboard get() = this@ServerEngine.clipboard
        override fun onClosed(session: ServerSession) {
            if (sessions.remove(session.name, session)) log(tr("log.clientGone", session.name))
            synchronized(lock) { backToServer(session) }
            publish()
        }
    }

    fun start() {
        val ss = try { ServerSocket(Discovery.TCP_PORT) } catch (_: BindException) { ServerSocket(0) }
        serverSocket = ss
        running = true
        beacon = Beacon(me.name, me.id, ss.localPort).also { it.start() }
        capture.start(this)
        thread(isDaemon = true, name = "accept") {
            while (running) {
                val socket = try { ss.accept() } catch (_: Exception) { break }
                thread(isDaemon = true, name = "session") { handle(socket) }
            }
        }
        log(tr("log.serverAnnounced", me.name, ss.localPort))
    }

    fun stop() {
        running = false
        synchronized(lock) { active?.let { backToServer(it) } }
        beacon?.stop()
        runCatching { serverSocket?.close() }
        sessions.values.forEach { it.close() }
        capture.stop()
        publish()
    }

    /** Sends [files] (dragged) to the client [peer], or to all connected ones if null. */
    fun sendFiles(files: List<File>, peer: String? = null, listener: SendListener? = null) = sessions.values.filter { peer == null || it.name == peer }.forEach { it.sendFiles(files, false, listener) }
    /** Sends the list of clients to the UI again (for example, after one of them changes position). */
    fun refresh() = publish()

    private fun publish() = onClients(sessions.values.map { it.info() }.sortedBy { it.name })
    private fun ServerSession.info() = ClientInfo(name, width, height, posOf(name), active === this)

    private fun handle(socket: Socket) = serveClient(socket, me, trust, onPairing, log, owner) { session -> sessions.put(session.name, session)?.close(); publish() }

    /** Neighbor of [from] in the [side] direction, if there is a client in that cell. */
    private fun neighbor(from: GridPos, side: Side) = sessions.values.firstOrNull { posOf(it.name) == from.step(side) }

    /**
     * The cursor left [from] (null = server) through the [side] edge, at position [fraction] along it.
     * If there is a neighbor in that direction, hands control to it; otherwise nothing happens. Call with [lock].
     */
    private fun cross(from: ServerSession?, side: Side, fraction: Float) {
        val fromPos = from?.let { posOf(it.name) } ?: GridPos.SERVER
        val target = fromPos.step(side)
        if (target == GridPos.SERVER) {
            from ?: return
            from.send(Message.Release)
            active = null
            val (w, h) = capture.screen()
            val (x, y) = Edges.entryPoint(side.opposite, fraction, w, h)
            capture.setRemote(false, x, y)
        } else {
            val next = neighbor(fromPos, side) ?: return
            from?.send(Message.Release)
            active = next
            if (from == null && shareClipboard()) clipboard.push(next)
            next.send(Message.Enter(side.opposite, fraction))
            if (from == null) { clipboard.dragStart(next); capture.setRemote(true) }
        }
        publish()
    }

    /** Gives control back to the server without going through an edge (disconnection, Scroll Lock, stop). */
    private fun backToServer(session: ServerSession) {
        if (active !== session) return
        active = null
        val (w, h) = capture.screen()
        capture.setRemote(false, w / 2, h / 2)
        publish()
    }

    // --- CaptureListener ---

    override fun onLocalMove(x: Int, y: Int) {
        synchronized(lock) {
            if (active != null) return
            val (w, h) = capture.screen()
            val side = Edges.touching(x, y, w, h) ?: return
            cross(null, side, Edges.fraction(side, x, y, w, h))
        }
    }

    override fun onRemoteMove(dx: Int, dy: Int) { active?.send(Message.MouseMove(dx, dy)) }
    override fun onButton(button: Int, down: Boolean) { active?.send(Message.MouseButton(button, down)) }
    override fun onWheel(dx: Int, dy: Int) { active?.send(Message.Wheel(dx, dy)) }

    override fun onKey(code: Int, down: Boolean) {
        if (code == Platform.PANIC_KEY) {
            if (down) synchronized(lock) {
                active?.let {
                    it.send(Message.Release)
                    backToServer(it)
                    log(tr("log.controlBack"))
                }
            }
            return
        }
        active?.send(Message.Key(code, down))
    }
}
