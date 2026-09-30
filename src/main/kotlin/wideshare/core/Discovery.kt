package wideshare.core

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import kotlin.concurrent.thread

object Discovery {
    const val PORT = 24801
    const val TCP_PORT = 24800
    const val EXPIRE_MS = 5_000L
    private const val HEADER = "BRX3"

    fun encode(name: String, id: String, tcpPort: Int) = "$HEADER\n$tcpPort\n$id\n$name".toByteArray()

    fun decode(data: ByteArray, length: Int, from: String): ServerInfo? {
        val lines = String(data, 0, length).split("\n", limit = 4)
        if (lines.size < 4 || lines[0] != HEADER) return null
        val port = lines[1].toIntOrNull() ?: return null
        return ServerInfo(lines[3], lines[2], from, port, System.currentTimeMillis())
    }
}

data class ServerInfo(val name: String, val id: String, val address: String, val port: Int, val lastSeen: Long)

/** Announces the server by UDP broadcast every second on all network interfaces. */
class Beacon(private val name: String, private val id: String, private val tcpPort: Int) {
    @Volatile private var running = false
    private var socket: DatagramSocket? = null

    fun start() {
        running = true
        val s = DatagramSocket().apply { broadcast = true }
        socket = s
        thread(isDaemon = true, name = "beacon") {
            val payload = Discovery.encode(name, id, tcpPort)
            while (running) {
                targets().forEach { addr ->
                    runCatching { s.send(DatagramPacket(payload, payload.size, addr, Discovery.PORT)) }
                }
                try { Thread.sleep(1000) } catch (_: InterruptedException) { break }
            }
        }
    }

    fun stop() {
        running = false
        socket?.close()
    }

    private fun targets(): List<InetAddress> {
        val list = mutableListOf(InetAddress.getByName("255.255.255.255"), InetAddress.getLoopbackAddress())
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNullTo(list) { it.broadcast }
        }
        return list.distinct()
    }
}

/** Listens for the announcements and keeps the list of servers seen in the last few seconds. */
class ServerListener(private val onChange: (List<ServerInfo>) -> Unit) {
    @Volatile private var running = false
    private var socket: DatagramSocket? = null
    private val seen = LinkedHashMap<String, ServerInfo>()

    val servers: List<ServerInfo> get() = synchronized(seen) { seen.values.toList() }

    fun start() {
        running = true
        val s = DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(Discovery.PORT))
            soTimeout = 1000
        }
        socket = s
        thread(isDaemon = true, name = "discovery") {
            val buf = ByteArray(512)
            while (running) {
                var changed = false
                try {
                    val p = DatagramPacket(buf, buf.size)
                    s.receive(p)
                    val info = Discovery.decode(p.data, p.length, p.address.hostAddress)
                    if (info != null) synchronized(seen) {
                        // The same server arrives through several interfaces/addresses: merge them by name and port.
                        val key = info.id
                        val previous = seen[key]
                        val keepAddress = previous != null && java.net.InetAddress.getByName(info.address).isLoopbackAddress
                        seen[key] = if (keepAddress) info.copy(address = previous!!.address) else info
                        changed = previous == null || previous.address != seen.getValue(key).address
                    }
                } catch (_: java.net.SocketTimeoutException) {
                } catch (_: Exception) {
                    if (!running) break
                }
                val now = System.currentTimeMillis()
                synchronized(seen) { if (seen.values.removeIf { now - it.lastSeen > Discovery.EXPIRE_MS }) changed = true }
                if (changed) onChange(servers)
            }
        }
    }

    fun stop() {
        running = false
        socket?.close()
    }
}
