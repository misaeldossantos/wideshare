package wideshare.ui

import wideshare.core.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wideshare.core.Mode

/** Side menu item: each one opens a screen. */
interface NavItem {
    val label: String
    val icon: ImageVector
}

/** Server screens. */
enum class ServerSection(private val key: String, override val icon: ImageVector) : NavItem {
    PAIRED("nav.paired", AppIcons.Link),
    POSITIONS("nav.positions", AppIcons.GridView),
    SETTINGS("nav.settings", AppIcons.Settings),
    TRANSFERS("nav.transfers", AppIcons.SwapVert),
    ACTIVITY("nav.activity", AppIcons.Receipt);

    override val label get() = tr(key)
}

/** Client screens. */
enum class ClientSection(private val key: String, override val icon: ImageVector) : NavItem {
    SERVERS("nav.servers", AppIcons.Dns),
    PAIRED("nav.paired", AppIcons.Link),
    SETTINGS("nav.settings", AppIcons.Settings),
    TRANSFERS("nav.transfers", AppIcons.SwapVert),
    ACTIVITY("nav.activity", AppIcons.Receipt);

    override val label get() = tr(key)
}

@Composable
internal fun SideNav(c: AppController, mode: Mode, modifier: Modifier) {
    val server by c.section.collectAsState()
    val client by c.clientSection.collectAsState()
    if (mode == Mode.SERVER) NavMenu(ServerSection.entries, server, modifier) { c.section.value = it }
    else NavMenu(ClientSection.entries, client, modifier) { c.clientSection.value = it }
}

@Composable
private fun <T : NavItem> NavMenu(items: List<T>, current: T, modifier: Modifier, onSelect: (T) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (s in items) {
            val selected = s == current
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                    .background(if (selected) Palette.accentSoft else Palette.sidebar)
                    .clickable { onSelect(s) }.padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(s.icon, contentDescription = null, tint = if (selected) Palette.accent else Palette.muted, modifier = Modifier.size(18.dp))
                Text(
                    s.label, fontSize = 13.sp, style = CenteredLine,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) Palette.text else Palette.muted,
                )
            }
        }
    }
}
