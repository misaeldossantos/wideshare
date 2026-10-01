package wideshare.platform.linux.wayland

import wideshare.platform.InputCapture
import wideshare.platform.InputInjector

/** Creates the Wayland capture and injector when the desktop offers what they need; null means "use X11". */
object WaylandSupport {
    /** Session running on Wayland (as opposed to X11 or XWayland alone). */
    val isWayland: Boolean =
        System.getenv("XDG_SESSION_TYPE").equals("wayland", ignoreCase = true) || !System.getenv("WAYLAND_DISPLAY").isNullOrEmpty()

    fun capture(): InputCapture? {
        val portal = Portal.shared?.takeIf { it.supports("InputCapture") } ?: return null
        val ei = Ei.lib ?: return null
        return WaylandCapture(portal, ei)
    }

    fun injector(): InputInjector? = Portal.shared?.takeIf { it.supports("RemoteDesktop") }?.let { WaylandInjector(it) }
}
