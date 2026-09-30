package wideshare.core

/** Geometry of the screen edges, used to decide where the cursor leaves and where it comes back. */
internal object Edges {
    private const val MARGIN = 3

    /** Edge the cursor at ([x], [y]) is touching on a [w]x[h] screen, or null if it is in the middle. */
    fun touching(x: Int, y: Int, w: Int, h: Int): Side? = when {
        x >= w - 1 -> Side.RIGHT
        x <= 0 -> Side.LEFT
        y >= h - 1 -> Side.BOTTOM
        y <= 0 -> Side.TOP
        else -> null
    }

    /** Position (0..1) of the cursor along the [side] edge. */
    fun fraction(side: Side, x: Int, y: Int, w: Int, h: Int): Float = when (side) {
        Side.LEFT, Side.RIGHT -> y.toFloat() / (h - 1)
        else -> x.toFloat() / (w - 1)
    }.coerceIn(0f, 1f)

    /** Point on the screen, 3 px inside the [edge], where the cursor enters. */
    fun entryPoint(edge: Side, fraction: Float, w: Int, h: Int): Pair<Int, Int> {
        val along = { size: Int -> (fraction * (size - 1)).toInt() }
        return when (edge) {
            Side.RIGHT -> (w - 1 - MARGIN) to along(h)
            Side.LEFT -> MARGIN to along(h)
            Side.BOTTOM -> along(w) to (h - 1 - MARGIN)
            Side.TOP -> along(w) to MARGIN
        }
    }
}
