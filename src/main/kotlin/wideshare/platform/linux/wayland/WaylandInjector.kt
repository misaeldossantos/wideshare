package wideshare.platform.linux.wayland

import wideshare.core.tr
import wideshare.platform.InputInjector
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * Injects input on Wayland through the RemoteDesktop portal. The portal only moves the pointer by
 * displacement, so the position is tracked here and, when it is not known, the pointer is first pushed
 * into the top-left corner to start from a known point.
 */
class WaylandInjector internal constructor(private val portal: Portal) : InputInjector {
    private val api = portal.api(RemoteDesktopApi::class.java)
    @Volatile private var session: DBusPath? = null
    private var cx = UNKNOWN
    private var cy = UNKNOWN
    private val vertical = WheelAccumulator()
    private val horizontal = WheelAccumulator()
    private val none = emptyMap<String, Variant<*>>()

    init {
        // Start waits for the user to approve in a desktop dialog, so it must not block whoever creates the injector.
        thread(isDaemon = true, name = "wayland-injector") {
            runCatching { open() }.onFailure { System.err.println(tr("err.waylandPortal", it.message ?: it.javaClass.simpleName)) }
        }
    }

    private fun open() {
        val path = portal.sessionOf(portal.request { api.CreateSession(opts("handle_token" to it, "session_handle_token" to "wideshareinput")) })
        val select = mutableMapOf<String, Variant<*>>(
            "types" to Variant(UInt32(KEYBOARD or POINTER)),
            "persist_mode" to Variant(UInt32(PERSIST_UNTIL_REVOKED)),
        )
        RestoreToken.read()?.let { select["restore_token"] = Variant(it) }
        portal.request { select["handle_token"] = Variant(it); api.SelectDevices(path, select) }
        val started = portal.request { api.Start(path, "", opts("handle_token" to it)) }
        started["restore_token"]?.let { RestoreToken.write(it.value.toString()) }
        session = path
    }

    override fun screen() = awtScreen()

    override fun moveTo(x: Int, y: Int) {
        val s = session ?: return
        if (cx == UNKNOWN || abs(x - cx) > FAR || abs(y - cy) > FAR) {
            val (w, h) = screen()
            api.NotifyPointerMotion(s, none, -w.toDouble(), -h.toDouble())
            cx = 0
            cy = 0
        }
        if (x != cx || y != cy) api.NotifyPointerMotion(s, none, (x - cx).toDouble(), (y - cy).toDouble())
        cx = x
        cy = y
    }

    override fun button(button: Int, down: Boolean) {
        val s = session ?: return
        val code = EvdevButtons.fromNeutral(button) ?: return
        api.NotifyPointerButton(s, none, code, UInt32(if (down) 1 else 0))
    }

    override fun wheel(dx: Int, dy: Int) {
        val s = session ?: return
        // Positive dy is "up" here, while the portal counts steps downward.
        val v = vertical.add(dy)
        val h = horizontal.add(dx)
        if (v != 0) api.NotifyPointerAxisDiscrete(s, none, UInt32(0), -v)
        if (h != 0) api.NotifyPointerAxisDiscrete(s, none, UInt32(1), h)
    }

    /** Keys use evdev codes, the same ones the portal expects. */
    override fun key(code: Int, down: Boolean) {
        val s = session ?: return
        api.NotifyKeyboardKeycode(s, none, code, UInt32(if (down) 1 else 0))
    }

    override fun close() {
        session?.let { portal.close(it) }
        session = null
    }

    private companion object {
        const val UNKNOWN = Int.MIN_VALUE
        /** A jump larger than this is a new placement (cursor entered), not a movement, and re-syncs the position. */
        const val FAR = 300
        const val KEYBOARD = 1L
        const val POINTER = 2L
        const val PERSIST_UNTIL_REVOKED = 2L
    }
}
