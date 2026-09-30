package wideshare.ui

import wideshare.core.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wideshare.core.GridPos

internal val TileW = 100.dp
internal val TileH = 64.dp

@Composable
internal fun LayoutEditor(c: AppController) {
    val running by c.running.collectAsState()
    val clients by c.clients.collectAsState()
    val layout by c.layout.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }
    val online = clients.associateBy { it.name }

    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(end = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr("layout.title"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                when {
                    selected != null -> tr("layout.hint.selected")
                    !running -> tr("layout.hint.stopped")
                    clients.isEmpty() -> tr("layout.hint.empty")
                    else -> tr("layout.hint.default")
                },
                color = Palette.muted, fontSize = 13.sp,
            )
        }
        }

        Column(
            Modifier.clip(RoundedCornerShape(8.dp)).background(Palette.panel).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (y in GRID_Y) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (x in GRID_X) {
                    val cell = GridPos(x, y)
                    val occupant = layout.entries.firstOrNull { it.value == cell }?.key
                    when {
                        cell == GridPos.SERVER -> Tile(tr("layout.thisPc"), tr("layout.server"), TileKind.SERVER, false) {}
                        occupant != null -> {
                            val info = online[occupant]
                            Tile(
                                occupant,
                                when { info == null -> tr("layout.offline"); info.active -> tr("layout.controlling"); else -> "${info.width}×${info.height}" },
                                when { info == null -> TileKind.OFFLINE; info.active -> TileKind.ACTIVE; else -> TileKind.ONLINE },
                                selected == occupant,
                            ) { selected = if (selected == occupant) null else occupant }
                        }
                        else -> EmptyCell(highlight = selected != null) {
                            selected?.let { c.setPosition(it, cell); selected = null }
                        }
                    }
                }
            }
        }

        val sel = selected
        if (sel != null && sel !in online) SmallButton(tr("layout.remove", sel), { c.forget(sel); selected = null }, color = Palette.bad)
        if (running) Text(tr("layout.tip"), color = Palette.muted, fontSize = 12.sp)
    }
    VerticalScrollbar(
        rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        style = LocalScrollbarStyle.current.copy(unhoverColor = Palette.panelHi, hoverColor = Palette.muted),
    )
    }
}

private enum class TileKind { SERVER, ONLINE, ACTIVE, OFFLINE }

/** A computer on the grid, drawn as a small monitor. */
@Composable
private fun Tile(title: String, subtitle: String, kind: TileKind, selected: Boolean, onClick: () -> Unit) {
    val (accent, fill) = when (kind) {
        TileKind.SERVER -> Palette.accent to Palette.accentSoft
        TileKind.ACTIVE -> Palette.ok to Palette.ok.copy(alpha = 0.14f)
        TileKind.ONLINE -> Palette.text to Palette.panelHi
        TileKind.OFFLINE -> Palette.muted to Palette.panel
    }
    val shape = RoundedCornerShape(6.dp)
    Column(
        Modifier.size(TileW, TileH).clip(shape).background(fill)
            .then(if (selected) Modifier.border(2.dp, Palette.text, shape) else Modifier)
            .clickable(enabled = kind != TileKind.SERVER, onClick = onClick).padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusDot(accent, 6.dp)
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(subtitle, fontSize = 10.sp, color = if (kind == TileKind.ACTIVE) Palette.ok else Palette.muted, maxLines = 1)
    }
}
