package wideshare.ui

import wideshare.core.tr
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wideshare.core.ClientState
import wideshare.core.Mode

@Composable
internal fun Sidebar(c: AppController, modifier: Modifier) {
    val mode by c.mode.collectAsState()
    val running by c.running.collectAsState()

    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Wordmark()

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(tr("mode.label"))
            PillToggle(listOf(tr("mode.server"), tr("mode.client")), mode.ordinal, icons = listOf(AppIcons.Dns, AppIcons.Computer), enabled = !running, onSelect = { c.setMode(Mode.entries[it]) })
            Text(
                if (mode == Mode.SERVER) tr("mode.server.hint") else tr("mode.client.hint"),
                color = Palette.muted, fontSize = 12.sp, lineHeight = 17.sp,
            )
        }

        SideNav(c, mode, Modifier.weight(1f))
        StatusFooter(c, mode, running)
    }
}

@Composable
private fun Wordmark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource("icon.png"), contentDescription = null, modifier = Modifier.size(36.dp))
        Text("WideShare", fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

@Composable
private fun StatusFooter(c: AppController, mode: Mode, running: Boolean) {
    val clients by c.clients.collectAsState()
    val state by c.clientState.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (running) {
            val (label, color) = when {
                mode == Mode.SERVER -> when (clients.size) {
                    0 -> tr("status.waiting") to Palette.accent
                    1 -> tr("status.one") to Palette.ok
                    else -> tr("status.many", clients.size) to Palette.ok
                }
                else -> when (val s = state) {
                    ClientState.Searching -> tr("status.searching") to Palette.accent
                    is ClientState.Connecting -> tr("status.connecting") to Palette.accent
                    is ClientState.Pairing -> tr("status.pairing", s.server) to Palette.accent
                    is ClientState.Connected -> tr("status.connected", s.server) to Palette.ok
                    is ClientState.Failed -> s.reason to Palette.bad
                }
            }
            Pill(label, color)
        }
        if (running) PrimaryButton(tr("btn.stop"), c::stop, danger = true, icon = AppIcons.Stop)
        else if (mode == Mode.SERVER) PrimaryButton(tr("btn.startServer"), c::start, icon = AppIcons.PlayArrow)
        else PrimaryButton(tr("btn.searchServer"), c::start, icon = AppIcons.Search)
    }
}
