package wideshare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wideshare.core.Language
import wideshare.core.ThemeMode
import wideshare.core.tr

/** Drop-down that picks one of [options]; [name] gives the text shown for each. */
@Composable
internal fun <T> SelectField(label: String, options: List<T>, selected: T, name: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label)
        Box {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Palette.panel)
                    .clickable { open = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name(selected), Modifier.weight(1f), fontSize = 14.sp)
                Icon(AppIcons.KeyboardArrowDown, contentDescription = null, tint = Palette.muted)
            }
            DropdownMenu(open, { open = false }, containerColor = Palette.panelHi) {
                for (o in options) DropdownMenuItem(
                    text = { Text(name(o), fontSize = 14.sp, color = if (o == selected) Palette.accent else Palette.text) },
                    onClick = { onSelect(o); open = false },
                )
            }
        }
    }
}

/** Picks the interface language by its full name. */
@Composable
internal fun LanguageSelect(c: AppController) {
    val language by c.language.collectAsState()
    SelectField(tr("lang.label"), Language.entries, language, { it.label }, c::setLanguage)
}

/** Picks between following the system theme, dark and light. */
@Composable
internal fun ThemeSelect(c: AppController) {
    val theme by c.theme.collectAsState()
    SelectField(tr("theme.label"), ThemeMode.entries, theme, { tr("theme." + it.name.lowercase()) }, c::setTheme)
}
