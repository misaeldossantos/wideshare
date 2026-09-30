package wideshare.ui

import wideshare.core.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ServerContent(c: AppController) {
    val section by c.section.collectAsState()
    when (section) {
        ServerSection.PAIRED -> PairedPage(c)
        ServerSection.POSITIONS -> LayoutEditor(c)
        ServerSection.SETTINGS -> SettingsPage(c)
        ServerSection.TRANSFERS -> TransfersPage(c)
        ServerSection.ACTIVITY -> ActivityLog(c, Modifier.fillMaxSize())
    }
}

@Composable
internal fun PageHeader(title: String, subtitle: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) Text(subtitle, color = Palette.muted, fontSize = 13.sp)
    }
}

@Composable
internal fun PairedPage(c: AppController) {
    val paired by c.paired.collectAsState()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeader(tr("nav.paired"), tr("paired.subtitle"))
        if (paired.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(tr("paired.empty"), color = Palette.muted, fontSize = 13.sp)
            }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(paired, key = { it.id }) { p ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Palette.panel).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(Palette.ok, 8.dp)
                    Text(p.name, Modifier.weight(1f).padding(start = 12.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    SmallButton(tr("btn.forget"), { c.forgetPeer(p.id) }, color = Palette.bad)
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(c: AppController) {
    val running by c.running.collectAsState()
    val name by c.name.collectAsState()
    val tray by c.closeToTray.collectAsState()
    ScrollPage {
        PageHeader(tr("settings.title"), tr("settings.server.subtitle"))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(tr("settings.name"))
            Field(name, c::setName, tr("settings.name.hint"), enabled = !running)
        }
        Toggle(tr("settings.tray"), tray, c::setCloseToTray)
        ThemeSelect(c)
        LanguageSelect(c)
        ReceiveFolder(c)
        ShareOptions(c)
    }
}
