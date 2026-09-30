package wideshare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) = Text(
    text, modifier, color = Palette.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium,
)

/** Pill-shaped toggle with a highlight on the selected item. */
@Composable
fun PillToggle(
    options: List<String>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier, icons: List<ImageVector> = emptyList(),
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Palette.panel)
            .padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(5.dp))
                    .then(if (on) Modifier.background(Palette.accentFill) else Modifier)
                    .clickable(enabled = enabled && !on, interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                val fg = if (on) Palette.onAccent else if (enabled) Palette.text else Palette.muted
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    icons.getOrNull(i)?.let { Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp)) }
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg, style = CenteredLine)
                }
            }
        }
    }
}

@Composable
fun Field(
    value: String, onChange: (String) -> Unit, placeholder: String,
    modifier: Modifier = Modifier, enabled: Boolean = true, mono: Boolean = false, fontSize: Int = 14,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier.fillMaxWidth().clip(shape).background(Palette.panel)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, color = Palette.muted, fontSize = fontSize.sp, fontFamily = if (mono) Mono else null)
        BasicTextField(
            value, onChange, Modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
            textStyle = TextStyle(
                color = if (enabled) Palette.text else Palette.muted, fontSize = fontSize.sp,
                fontFamily = if (mono) Mono else Inter, letterSpacing = if (mono) 1.5.sp else 0.sp,
            ),
            cursorBrush = SolidColor(Palette.accent),
        )
    }
}
