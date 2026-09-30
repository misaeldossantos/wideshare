package wideshare.core

import java.io.File
import java.io.IOException
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class ClientInfo(val name: String, val width: Int, val height: Int, val pos: GridPos, val active: Boolean)

/** Connection of an already authenticated client, as seen by the server. */
internal class ServerSession(
    val channel: SecureChannel, val name: String, val width: Int, val height: Int,
    private val owner: Owner,
) {
    interface Owner {
        fun onLeave(session: ServerSession, leave: Message.Leave)
        val clipboard: ServerClipboard
        fun onClosed(session: ServerSession)
    }

    private val queue = LinkedBlockingQueue<Message>()
    @Volatile private var closed = false
    private val player = AudioPlayer()
    val fileSync = FileSync()
    @Volatile private var offered: List<File> = emptyList()
    private val sender = FileSender(::sendBulk)
    private val receiver = FileReceiver(owner.clipboard.hooks, name, owner.clipboard::allowPaste) { files, paste -> owner.clipboard.onFiles(this, files, paste) }

    fun send(m: Message) { if (!closed) queue.offer(m) }

    /** Tells the client about the files copied on the server; they only follow once it accepts. */
    fun offerFiles(files: List<File>) {
        offered = files
        send(Message.FilesOffer(summarize(files)))
    }

    /** Sends [files] in the background (file copy or drag). */
    fun sendFiles(files: List<File>, paste: Boolean, listener: SendListener? = null) { thread(isDaemon = true, name = "files-$name") { sender.sendAll(files, paste, listener) } }

    /** Like [send], but waits for the queue to drain a little: a large file must not fill up memory. */
    private fun sendBulk(m: Message): Boolean {
        while (!closed && queue.size > 64) Thread.sleep(2)
        return !closed && queue.offer(m)
    }

    fun close() {
        closed = true
        channel.close()
    }

    fun run() {
        thread(isDaemon = true, name = "writer-$name") { writeLoop() }
        try {
            while (!closed) {
                when (val m = Codec.decode(channel.receive())) {
                    is Message.Leave -> owner.onLeave(this, m)
                    is Message.Clipboard -> owner.clipboard.onText(this, m.text)
                    is Message.Audio -> player.play(m.rate, m.pcm)
                    is Message.FilesOffer -> owner.clipboard.onOffer(this, m.summary)
                    Message.FilesRequest -> if (offered.isNotEmpty()) sendFiles(offered, true)
                    is Message.DragStart -> owner.clipboard.onClientDrag(this, m.summary)
                    Message.DragDrop -> owner.clipboard.onDrop(this)
                    else -> receiver.handle(m)
                }
            }
        } catch (_: Exception) {
        } finally {
            close()
            player.close()
            receiver.abort()
            owner.onClosed(this)
        }
    }

    private fun writeLoop() {
        try {
            while (!closed) {
                var m = queue.poll(2, TimeUnit.SECONDS) ?: Message.Ping
                while (m is Message.MouseMove) {
                    val next = queue.peek() as? Message.MouseMove ?: break
                    queue.poll()
                    m = Message.MouseMove(m.dx + next.dx, m.dy + next.dy)
                }
                channel.send(Codec.encode(m))
            }
        } catch (_: Exception) {
            close()
        }
    }
}

/** Handshake, pairing (if it is the first time) and Hello of a client that just connected. */
internal fun acceptClient(
    socket: Socket, me: Identity, trust: TrustStore, onPairing: (PairingRequest?) -> Unit,
    log: (String) -> Unit, owner: ServerSession.Owner,
): ServerSession {
    val hs = SecureChannel.serverHandshake(socket, me, trust)
    if (hs.sas != null) {
        log(tr("log.pairing", hs.peerName))
        runPairing(hs, trust, onPairing)
        log(tr("log.paired", hs.peerName))
    }
    val hello = Codec.decode(hs.channel.receive()) as? Message.Hello ?: throw IOException("Hello esperado")
    return ServerSession(hs.channel, hello.name, hello.width, hello.height, owner)
}

/** Serves an incoming connection until it ends; [onReady] runs once the client has introduced itself. */
internal fun serveClient(
    socket: Socket, me: Identity, trust: TrustStore, onPairing: (PairingRequest?) -> Unit,
    log: (String) -> Unit, owner: ServerSession.Owner, onReady: (ServerSession) -> Unit,
) {
    val address = socket.inetAddress.hostAddress
    try {
        val session = acceptClient(socket, me, trust, onPairing, log, owner)
        log(tr("log.clientConnected", session.name, address))
        onReady(session)
        session.run()
    } catch (e: IOException) {
        if (e is AuthException || e is PairingRejectedException) log(tr("log.refused", address, e.message))
        runCatching { socket.close() }
    } catch (e: Exception) {
        runCatching { socket.close() }
    }
}
