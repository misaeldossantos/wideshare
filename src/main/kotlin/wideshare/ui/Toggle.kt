package wideshare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).clickable { onChange(!on) }.padding(6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(width = 38.dp, height = 22.dp).clip(CircleShape).background(if (on) Palette.accent else Palette.panelHi)
                .padding(3.dp),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        ) { Box(Modifier.size(16.dp).clip(CircleShape).background(if (on) Palette.onAccent else Palette.muted)) }
        Text(label, fontSize = 13.sp, color = Palette.muted)
    }
}
