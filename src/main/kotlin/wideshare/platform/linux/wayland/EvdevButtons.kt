package wideshare.platform.linux.wayland

import wideshare.core.Buttons

/** Conversion between the neutral buttons ([Buttons]) and the evdev codes (BTN_*) that Wayland uses. */
internal object EvdevButtons {
    fun fromNeutral(button: Int): Int? = when (button) {
        Buttons.LEFT -> 0x110
        Buttons.RIGHT -> 0x111
        Buttons.MIDDLE -> 0x112
        Buttons.BACK -> 0x113
        Buttons.FORWARD -> 0x114
        else -> null
    }

    fun toNeutral(code: Int): Int? = when (code) {
        0x110 -> Buttons.LEFT
        0x111 -> Buttons.RIGHT
        0x112 -> Buttons.MIDDLE
        0x113, 0x116 -> Buttons.BACK // BTN_SIDE, BTN_BACK
        0x114, 0x115 -> Buttons.FORWARD // BTN_EXTRA, BTN_FORWARD
        else -> null
    }
}
