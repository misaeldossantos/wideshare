package wideshare.platform.linux.wayland

import wideshare.core.Side
import wideshare.core.tr
import wideshare.platform.CaptureListener
import wideshare.platform.InputCapture
import wideshare.platform.Platform
import wideshare.platform.ScreenSize
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.interfaces.DBusSigHandler
import org.freedesktop.dbus.types.UInt32

/**
 * Capture on Wayland through the InputCapture portal. The compositor cannot be asked where the cursor is,
 * so barriers are placed on the screen edges: when the cursor crosses one, the compositor stops
 * delivering the input to the local applications and sends it over EIS. The edge is reported as a local
 * move, so the engine decides as on X11; if there is no neighbor there, the cursor is given back.
 */
class WaylandCapture internal constructor(private val portal: Portal, private val ei: Ei) : InputCapture {
    private val api = portal.api(InputCaptureApi::class.java)
    @Volatile private var running = false
    @Volatile private var remote = false
    @Volatile private var session: DBusPath? = null
    @Volatile private var activation: UInt32? = null
    @Volatile private var zones = Zones.parse(emptyMap(), awtScreen())
    private var worker: Thread? = null
    private val handlers = mutableListOf<AutoCloseable>()

    override fun screen(): ScreenSize = zones.size

    override fun start(listener: CaptureListener) {
        running = true
        worker = Thread({ run(listener) }, "wayland-capture").apply { isDaemon = true; start() }
    }

    override fun stop() {
        running = false
        worker?.join(1000)
    }

    override fun setRemote(on: Boolean, x: Int, y: Int) {
        remote = on
        if (!on) release(x, y)
    }

    /** Gives the cursor back to the local applications at ([x], [y]), if the compositor is holding it. */
    private fun release(x: Int, y: Int) {
        val path = session ?: return
        val id = activation ?: return
        activation = null
        val point = CursorPosition((x + zones.x).toDouble(), (y + zones.y).toDouble())
        runCatching { api.Release(path, opts("activation_id" to id, "cursor_position" to point)) }
    }

    private fun run(listener: CaptureListener) {
        var reader: EiReader? = null
        try {
            val path = portal.sessionOf(portal.request {
                api.CreateSession("", opts("handle_token" to it, "session_handle_token" to "wideshareinputcapture", "capabilities" to UInt32(KEYBOARD_AND_POINTER)))
            })
            session = path
            zones = Zones.parse(portal.request { api.GetZones(path, opts("handle_token" to it)) }, awtScreen())
            val barriers = portal.request { api.SetPointerBarriers(path, opts("handle_token" to it), zones.barriers(), zones.set) }
            if (items(barriers["failed_barriers"]?.value).isNotEmpty()) System.err.println(tr("err.waylandPortal", "barriers"))
            handlers += portal.on(InputCaptureApi.Activated::class.java, DBusSigHandler { if (it.session.path == path.path) activated(listener, it.options) })
            handlers += portal.on(InputCaptureApi.Deactivated::class.java, DBusSigHandler { if (it.session.path == path.path) lost(listener) })
            val fd = api.ConnectToEIS(path, opts()).intFileDescriptor
            reader = EiReader(ei, fd, listener, { remote }, { lost(listener) })
            api.Enable(path, opts())
            while (running) {
                reader.poll()
                Thread.sleep(2)
            }
        } catch (e: Exception) {
            System.err.println(tr("err.waylandPortal", e.message ?: e.javaClass.simpleName))
        } finally {
            handlers.forEach { runCatching { it.close() } }
            reader?.close()
            session?.let(portal::close)
        }
    }

    private fun activated(listener: CaptureListener, options: Opts) {
        activation = options["activation_id"]?.value as? UInt32
        val (w, h) = zones.size
        val at = numbers(options["cursor_position"]?.value)
        var x = ((at.getOrNull(0) ?: (w / 2.0)) - zones.x).toInt().coerceIn(0, w - 1)
        var y = ((at.getOrNull(1) ?: (h / 2.0)) - zones.y).toInt().coerceIn(0, h - 1)
        when (Zones.sideOf(options["barrier_id"]?.value)) {
            Side.LEFT -> x = 0
            Side.RIGHT -> x = w - 1
            Side.TOP -> y = 0
            Side.BOTTOM -> y = h - 1
            null -> {}
        }
        listener.onLocalMove(x, y)
        // The engine turns the remote mode on during the call above when there is a neighbor; otherwise the cursor goes back inside.
        if (!remote) release(x.coerceIn(MARGIN, w - 1 - MARGIN), y.coerceIn(MARGIN, h - 1 - MARGIN))
    }

    /** The compositor ended the capture (or EIS dropped): reports Scroll Lock so the engine brings control back. */
    private fun lost(listener: CaptureListener) {
        activation = null
        if (remote) listener.onKey(Platform.PANIC_KEY, true)
    }

    private companion object {
        const val KEYBOARD_AND_POINTER = 3L
        const val MARGIN = 3
    }
}
