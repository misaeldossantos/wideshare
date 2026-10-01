package wideshare.platform.linux.wayland

import wideshare.core.Side
import wideshare.platform.ScreenSize
import org.freedesktop.dbus.types.UInt32

/** Area of the screens as the compositor describes it (the "zones" of the portal), and the barriers on its edges. */
internal class Zones(val x: Int, val y: Int, val width: Int, val height: Int, val set: UInt32) {
    val size get() = ScreenSize(width, height)

    /** One barrier per edge; the id says which one the cursor crossed (see [sideOf]). */
    fun barriers(): List<Opts> = listOf(
        barrier(Side.LEFT, BarrierLine(x, y, x, y + height - 1)),
        barrier(Side.RIGHT, BarrierLine(x + width, y, x + width, y + height - 1)),
        barrier(Side.TOP, BarrierLine(x, y, x + width - 1, y)),
        barrier(Side.BOTTOM, BarrierLine(x, y + height, x + width - 1, y + height)),
    )

    private fun barrier(side: Side, line: BarrierLine) = opts("barrier_id" to UInt32(side.ordinal + 1L), "position" to line)

    companion object {
        fun sideOf(barrierId: Any?): Side? = (barrierId as? Number)?.toInt()?.let { Side.entries.getOrNull(it - 1) }

        /** Reads the answer of GetZones (`a(uuii)`: width, height, x, y); with several screens, takes the area that contains them all. */
        fun parse(results: Opts, fallback: ScreenSize) = parse(results["zones"]?.value, results["zone_set"]?.value, fallback)

        fun parse(zonesValue: Any?, setValue: Any?, fallback: ScreenSize): Zones {
            val set = setValue as? UInt32 ?: UInt32(0)
            val zones = rows(zonesValue).filter { it.size >= 4 }
            if (zones.isEmpty()) return Zones(0, 0, fallback.width, fallback.height, set)
            val left = zones.minOf { it[2] }.toInt()
            val top = zones.minOf { it[3] }.toInt()
            val right = zones.maxOf { it[2] + it[0] }.toInt()
            val bottom = zones.maxOf { it[3] + it[1] }.toInt()
            return Zones(left, top, right - left, bottom - top, set)
        }
    }
}
