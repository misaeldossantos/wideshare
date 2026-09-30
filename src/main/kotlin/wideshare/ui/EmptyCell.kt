package wideshare.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
internal fun EmptyCell(highlight: Boolean, onClick: () -> Unit) {
    val color = if (highlight) Palette.accent else Palette.line
    Canvas(Modifier.size(TileW, TileH).clip(RoundedCornerShape(6.dp)).clickable(enabled = highlight, onClick = onClick)) {
        drawRoundRect(
            color, cornerRadius = CornerRadius(6.dp.toPx()),
            style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
        )
        if (highlight) {
            val center = Offset(size.width / 2, size.height / 2)
            drawLine(color, center - Offset(8.dp.toPx(), 0f), center + Offset(8.dp.toPx(), 0f), 1.5.dp.toPx())
            drawLine(color, center - Offset(0f, 8.dp.toPx()), center + Offset(0f, 8.dp.toPx()), 1.5.dp.toPx())
        }
    }
}
