package wideshare.platform.linux.wayland

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

/** The part of libei (the library of the EIS protocol) used to receive the captured input. */
internal interface Ei : Library {
    fun ei_new_receiver(userData: Pointer?): Pointer?
    fun ei_configure_name(ei: Pointer, name: String)
    fun ei_setup_backend_fd(ei: Pointer, fd: Int): Int
    fun ei_dispatch(ei: Pointer)
    fun ei_get_event(ei: Pointer): Pointer?
    fun ei_event_unref(event: Pointer): Pointer?
    fun ei_unref(ei: Pointer): Pointer?
    fun ei_event_get_type(event: Pointer): Int
    fun ei_event_get_seat(event: Pointer): Pointer?
    fun ei_seat_bind_capabilities(seat: Pointer, vararg capabilities: Any)
    fun ei_event_pointer_get_dx(event: Pointer): Double
    fun ei_event_pointer_get_dy(event: Pointer): Double
    fun ei_event_button_get_button(event: Pointer): Int
    fun ei_event_button_get_is_press(event: Pointer): Byte
    fun ei_event_scroll_get_dx(event: Pointer): Double
    fun ei_event_scroll_get_dy(event: Pointer): Double
    fun ei_event_scroll_get_discrete_dx(event: Pointer): Int
    fun ei_event_scroll_get_discrete_dy(event: Pointer): Int
    fun ei_event_keyboard_get_key(event: Pointer): Int
    fun ei_event_keyboard_get_key_is_press(event: Pointer): Byte

    companion object {
        // enum ei_event_type
        const val SEAT_ADDED = 3
        const val DISCONNECT = 2
        const val POINTER_MOTION = 300
        const val BUTTON = 400
        const val SCROLL_DELTA = 500
        const val SCROLL_DISCRETE = 503
        const val KEY = 600

        // enum ei_device_capability
        const val CAP_POINTER = 1
        const val CAP_KEYBOARD = 4
        const val CAP_SCROLL = 16
        const val CAP_BUTTON = 32

        /** The loaded library, or null if libei is not installed. */
        val lib: Ei? by lazy {
            listOf("ei", "libei.so.1").firstNotNullOfOrNull { runCatching { Native.load(it, Ei::class.java) }.getOrNull() }
        }
    }
}
