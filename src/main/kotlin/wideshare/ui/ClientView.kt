package wideshare.ui

import wideshare.core.tr
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wideshare.core.ClientState
import wideshare.core.ServerInfo

@Composable
internal fun ClientView(c: AppController) {
    val running by c.running.collectAsState()
    val servers by c.servers.collectAsState()
    val state by c.clientState.collectAsState()
    val paired by c.paired.collectAsState()
    val pairedIds = paired.map { it.id }.toSet()
    val connected = state as? ClientState.Connected

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeader(tr("servers.title"))

        when {
            !running -> Empty(tr("servers.ready.title"), tr("servers.ready.body"))
            servers.isEmpty() -> Column(
                Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Radar(Modifier.size(200.dp), Palette.accent)
                Spacer(Modifier.height(16.dp))
                Text(tr("servers.searching"), fontWeight = FontWeight.SemiBold)
                Text(tr("servers.firewall"), color = Palette.muted, fontSize = 12.sp)
            }
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(servers, key = { it.id }) { s ->
                    ServerRow(
                        s, s.id in pairedIds, connected?.server == s.name,
                        connected?.hasControl == true && connected.server == s.name,
                    ) { c.connectTo(s) }
                }
            }
        }
    }
}

@Composable
private fun ServerRow(info: ServerInfo, paired: Boolean, connected: Boolean, hasControl: Boolean, onConnect: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (connected) Palette.ok.copy(alpha = 0.14f) else Palette.panel).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(info.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(if (paired) tr("servers.paired", info.address) else info.address, color = Palette.muted, fontSize = 12.sp, fontFamily = Mono)
        }
        when {
            hasControl -> Pill(tr("servers.controlling"), Palette.ok)
            connected -> Pill(tr("servers.connected"), Palette.ok)
            else -> Box(Modifier.width(120.dp)) { PrimaryButton(if (paired) tr("btn.connect") else tr("btn.pair"), onConnect) }
        }
    }
}

@Composable
private fun Empty(title: String, subtitle: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = Palette.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

/** Concentric waves that expand, indicating a search in progress. */
@Composable
private fun Radar(modifier: Modifier, color: Color) {
    val t by rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart))
    Canvas(modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val max = size.minDimension / 2
        for (i in 0..2) {
            val p = (t + i / 3f) % 1f
            drawCircle(color.copy(alpha = (1f - p) * 0.55f), radius = max * p, center = center, style = Stroke(2.dp.toPx()))
        }
        drawCircle(Palette.accentFill, radius = 9.dp.toPx(), center = center)
    }
}
