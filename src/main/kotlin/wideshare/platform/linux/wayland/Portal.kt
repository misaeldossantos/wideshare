package wideshare.platform.linux.wayland

import wideshare.core.tr
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBus
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.DBusSigHandler
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.messages.DBusSignal
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Connection to xdg-desktop-portal on the session bus; turns its Request/Response calls into blocking ones. */
internal class Portal private constructor(private val conn: DBusConnection) {
    private class Reply(val code: Int, val results: Opts)

    private val pending = ConcurrentHashMap<String, CompletableFuture<Reply>>()
    private val sender = conn.uniqueName.removePrefix(":").replace('.', '_')
    private val counter = AtomicInteger()

    // addSigHandler needs the unique name of the sender, not the well-known "org.freedesktop.portal.Desktop".
    private val busOwner: String = conn.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus::class.java).GetNameOwner(BUS)

    init {
        conn.addSigHandler(PortalRequest.Response::class.java, busOwner, DBusSigHandler<PortalRequest.Response> { sig ->
            pending[sig.path]?.complete(Reply(sig.response.toInt(), sig.results))
        })
    }

    fun <T : DBusInterface> api(type: Class<T>): T = conn.getRemoteObject(BUS, PATH, type)

    fun <T : DBusSignal> on(type: Class<T>, handler: DBusSigHandler<T>): AutoCloseable = conn.addSigHandler(type, busOwner, handler)

    /** True if the portal exposes [iface] ("RemoteDesktop" or "InputCapture"). */
    fun supports(iface: String): Boolean = runCatching {
        conn.getRemoteObject(BUS, PATH, Properties::class.java).Get<Any>("org.freedesktop.portal.$iface", "version") != null
    }.getOrDefault(false)

    /**
     * Runs a portal call that answers through a Request. [call] receives the `handle_token` that must go in
     * its options; returns the results, or fails if the user refused.
     */
    fun request(timeoutSeconds: Long = 120, call: (token: String) -> Unit): Opts {
        val token = "wideshare${counter.incrementAndGet()}"
        val path = "$PATH/request/$sender/$token"
        val reply = CompletableFuture<Reply>()
        pending[path] = reply
        try {
            call(token)
            val r = reply.get(timeoutSeconds, TimeUnit.SECONDS)
            if (r.code != 0) throw IOException(tr("err.portalDenied"))
            return r.results
        } finally {
            pending.remove(path)
        }
    }

    /** Session handle that the portal returns in the answer to CreateSession. */
    fun sessionOf(results: Opts) = DBusPath(results.getValue("session_handle").value.toString())

    fun close(session: DBusPath) {
        runCatching { conn.getRemoteObject(BUS, session.path, PortalSession::class.java).Close() }
    }

    companion object {
        private const val BUS = "org.freedesktop.portal.Desktop"
        private const val PATH = "/org/freedesktop/portal/desktop"

        /** Shared connection, or null if there is no session bus (not a graphical Linux session). */
        val shared: Portal? by lazy { runCatching { Portal(DBusConnectionBuilder.forSessionBus().build()) }.getOrNull() }
    }
}
