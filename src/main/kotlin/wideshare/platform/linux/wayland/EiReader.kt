package wideshare.platform.linux.wayland

import wideshare.platform.CaptureListener
import com.sun.jna.Pointer
import kotlin.math.roundToInt

/**
 * Reads the input the compositor sends over the EIS socket ([fd]) and passes it to the [listener],
 * but only while [forwarding] is true (control is on another computer).
 */
internal class EiReader(
    private val lib: Ei,
    fd: Int,
    private val listener: CaptureListener,
    private val forwarding: () -> Boolean,
    private val onDisconnect: () -> Unit,
) {
    private val ctx = lib.ei_new_receiver(null) ?: error("libei")
    private var restX = 0.0
    private var restY = 0.0
    private var scrollX = 0.0
    private var scrollY = 0.0

    init {
        lib.ei_configure_name(ctx, "WideShare")
        check(lib.ei_setup_backend_fd(ctx, fd) == 0) { "libei fd" }
    }

    /** Reads everything that is pending; call it often. */
    fun poll() {
        lib.ei_dispatch(ctx)
        while (true) {
            val event = lib.ei_get_event(ctx) ?: break
            try { handle(event) } finally { lib.ei_event_unref(event) }
        }
    }

    fun close() { lib.ei_unref(ctx) }

    private fun handle(e: Pointer) {
        val type = lib.ei_event_get_type(e)
        when (type) {
            Ei.SEAT_ADDED -> lib.ei_event_get_seat(e)?.let {
                lib.ei_seat_bind_capabilities(it, Ei.CAP_POINTER, Ei.CAP_BUTTON, Ei.CAP_SCROLL, Ei.CAP_KEYBOARD, 0)
            }
            Ei.DISCONNECT -> onDisconnect()
        }
        if (!forwarding()) return
        when (type) {
            Ei.POINTER_MOTION -> motion(lib.ei_event_pointer_get_dx(e), lib.ei_event_pointer_get_dy(e))
            Ei.BUTTON -> EvdevButtons.toNeutral(lib.ei_event_button_get_button(e))?.let {
                listener.onButton(it, lib.ei_event_button_get_is_press(e).toInt() != 0)
            }
            // Positive vertical movement is "down" in EIS, and "up" in the messages.
            Ei.SCROLL_DISCRETE -> listener.onWheel(lib.ei_event_scroll_get_discrete_dx(e), -lib.ei_event_scroll_get_discrete_dy(e))
            Ei.SCROLL_DELTA -> smoothScroll(lib.ei_event_scroll_get_dx(e), lib.ei_event_scroll_get_dy(e))
            Ei.KEY -> listener.onKey(lib.ei_event_keyboard_get_key(e), lib.ei_event_keyboard_get_key_is_press(e).toInt() != 0)
        }
    }

    /** EIS motion is fractional; the remainder is kept so slow movements are not lost. */
    private fun motion(dx: Double, dy: Double) {
        restX += dx
        restY += dy
        val ix = restX.roundToInt()
        val iy = restY.roundToInt()
        restX -= ix
        restY -= iy
        if (ix != 0 || iy != 0) listener.onRemoteMove(ix, iy)
    }

    /** Smooth scrolling (touchpad) comes in pixels; 15 px count as one notch of 120. */
    private fun smoothScroll(dx: Double, dy: Double) {
        scrollX += dx * UNITS_PER_PIXEL
        scrollY += dy * UNITS_PER_PIXEL
        val ix = scrollX.toInt()
        val iy = scrollY.toInt()
        scrollX -= ix
        scrollY -= iy
        if (ix != 0 || iy != 0) listener.onWheel(ix, -iy)
    }

    private companion object {
        const val UNITS_PER_PIXEL = 8.0
    }
}
