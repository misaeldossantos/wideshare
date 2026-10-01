package wideshare.platform.linux.wayland

/** Gathers wheel movement (120 per notch, as on Windows) and delivers it in whole notches. */
internal class WheelAccumulator {
    private var rest = 0

    /** Adds [delta] and returns the whole notches reached, keeping the remainder for the next call. */
    fun add(delta: Int): Int {
        rest += delta
        val notches = rest / NOTCH
        rest -= notches * NOTCH
        return notches
    }

    private companion object {
        const val NOTCH = 120
    }
}
