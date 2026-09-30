package wideshare.platform.windows


/** Table between Windows scancodes (set 1) and Linux evdev codes. */
object WinKeyMap {
    private val extendedToEvdev = mapOf(
        0x1C to 96, 0x1D to 97, 0x35 to 98, 0x37 to 99, 0x38 to 100, 0x45 to 69,
        0x47 to 102, 0x48 to 103, 0x49 to 104, 0x4B to 105, 0x4D to 106, 0x4F to 107,
        0x50 to 108, 0x51 to 109, 0x52 to 110, 0x53 to 111, 0x5B to 125, 0x5C to 126, 0x5D to 127,
    )
    private val evdevToExtended = extendedToEvdev.entries.associate { (k, v) -> v to k }

    /** 0 = key with no equivalent (ignored). */
    fun toEvdev(scan: Int, extended: Boolean): Int = when {
        extended -> extendedToEvdev[scan] ?: 0
        scan == 0x73 -> 89 // ABNT2: "/ ?" key
        scan == 0x7E -> 121 // ABNT2: numeric keypad dot
        scan in 1..0x58 -> scan
        else -> 0
    }

    /** Returns (scancode, extended) or null. */
    fun fromEvdev(code: Int): Pair<Int, Boolean>? = when {
        evdevToExtended.containsKey(code) -> evdevToExtended.getValue(code) to true
        code == 89 -> 0x73 to false
        code == 121 -> 0x7E to false
        code in 1..0x58 -> code to false
        else -> null
    }
}
