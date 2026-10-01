package wideshare.platform.linux.wayland

import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.types.Variant

internal fun opts(vararg pairs: Pair<String, Any>): Opts = pairs.associate { (k, v) -> k to Variant(v) }

/** D-Bus `(dd)`: position of the cursor. */
class CursorPosition(
    @JvmField @field:Position(0) val x: Double,
    @JvmField @field:Position(1) val y: Double,
) : Struct()

/** D-Bus `(iiii)`: a pointer barrier, a horizontal or vertical segment from (x1, y1) to (x2, y2). */
class BarrierLine(
    @JvmField @field:Position(0) val x1: Int,
    @JvmField @field:Position(1) val y1: Int,
    @JvmField @field:Position(2) val x2: Int,
    @JvmField @field:Position(3) val y2: Int,
) : Struct()

/** Elements of an array or struct that dbus-java returned (it may be an `Object[]` or a list). */
internal fun items(value: Any?): List<Any?> = when (value) {
    is Array<*> -> value.toList()
    is Iterable<*> -> value.toList()
    else -> emptyList()
}

internal fun numbers(value: Any?): List<Double> = items(value).mapNotNull { (it as? Number)?.toDouble() }

/** Rows of an array of structs made of numbers, e.g. the zones `a(uuii)`. */
internal fun rows(value: Any?): List<List<Double>> = items(value).map { numbers(it) }.filter { it.isNotEmpty() }
