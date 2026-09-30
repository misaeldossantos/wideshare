package wideshare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Removes the font's extra space above/below the text so it lines up with the center of the icon. */
internal val CenteredLine = TextStyle(
    lineHeight = 18.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false, icon: ImageVector? = null) {
    val shape = RoundedCornerShape(6.dp)
    val fg = if (danger) Palette.onDanger else Palette.onAccent
    Box(
        modifier.fillMaxWidth().clip(shape)
            .then(if (danger) Modifier.background(Palette.danger) else Modifier.background(Palette.accentFill))
            .clickable(onClick = onClick).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp, style = CenteredLine)
        }
    }
}

@Composable
fun SmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Palette.accent) {
    Text(
        text, modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp),
        color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
    )
}
