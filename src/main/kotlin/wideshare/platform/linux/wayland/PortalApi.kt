package wideshare.platform.linux.wayland

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.FileDescriptor
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.MethodNoReply
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

/** D-Bus `a{sv}`: options and results of the portal calls. */
typealias Opts = Map<String, Variant<*>>

/** Answer of every portal call that is not instantaneous; it arrives as a signal on the request path. */
@DBusInterfaceName("org.freedesktop.portal.Request")
@JvmSuppressWildcards
interface PortalRequest : DBusInterface {
    class Response(path: String, val response: UInt32, val results: Opts) : DBusSignal(path, response, results)
}

@DBusInterfaceName("org.freedesktop.portal.Session")
interface PortalSession : DBusInterface {
    fun Close()
}

/** Injects input on Wayland: the user approves once in a dialog from the desktop. */
@DBusInterfaceName("org.freedesktop.portal.RemoteDesktop")
@JvmSuppressWildcards
interface RemoteDesktopApi : DBusInterface {
    fun CreateSession(options: Opts): DBusPath
    fun SelectDevices(session: DBusPath, options: Opts): DBusPath
    fun Start(session: DBusPath, parentWindow: String, options: Opts): DBusPath

    @MethodNoReply fun NotifyPointerMotion(session: DBusPath, options: Opts, dx: Double, dy: Double)
    @MethodNoReply fun NotifyPointerButton(session: DBusPath, options: Opts, button: Int, state: UInt32)
    @MethodNoReply fun NotifyPointerAxisDiscrete(session: DBusPath, options: Opts, axis: UInt32, steps: Int)
    @MethodNoReply fun NotifyKeyboardKeycode(session: DBusPath, options: Opts, keycode: Int, state: UInt32)
}

/** Captures input on Wayland: the compositor hands the events over through an EIS socket (libei). */
@DBusInterfaceName("org.freedesktop.portal.InputCapture")
@JvmSuppressWildcards
interface InputCaptureApi : DBusInterface {
    fun CreateSession(parentWindow: String, options: Opts): DBusPath
    fun GetZones(session: DBusPath, options: Opts): DBusPath
    fun SetPointerBarriers(session: DBusPath, options: Opts, barriers: List<Opts>, zoneSet: UInt32): DBusPath
    fun Enable(session: DBusPath, options: Opts)
    fun Release(session: DBusPath, options: Opts)
    fun ConnectToEIS(session: DBusPath, options: Opts): FileDescriptor

    /** The pointer crossed a barrier: from now on the compositor sends the input through EIS. */
    class Activated(path: String, val session: DBusPath, val options: Opts) : DBusSignal(path, session, options)

    /** The compositor took the capture back (another application, the user, a screen lock). */
    class Deactivated(path: String, val session: DBusPath, val options: Opts) : DBusSignal(path, session, options)
}
