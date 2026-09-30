package wideshare.ui

import wideshare.core.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wideshare.core.Transfer
import wideshare.core.TransferState
import kotlinx.coroutines.delay

/** Transfers screen: progress of the files that other computers sent to this one. */
@Composable
internal fun TransfersPage(c: AppController) {
    val items by c.files.transfers.items.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeader(tr("nav.transfers"), tr("transfers.subtitle"))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Palette.accentSoft).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.width(3.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(Palette.accent))
            Text(
                tr("transfers.note"),
                fontSize = 12.5.sp, color = Palette.text,
            )
        }
        if (items.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(tr("transfers.empty"), color = Palette.muted, fontSize = 13.sp)
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { TransferCard(it, now) }
        }
    }
}

@Composable
private fun TransferCard(t: Transfer, now: Long) {
    val (label, color) = when (t.state) {
        TransferState.RECEIVING -> tr("transfers.receiving") to Palette.accent
        TransferState.READY -> tr("transfers.ready") to Palette.ok
        TransferState.SAVED -> tr("transfers.saved") to Palette.ok
        TransferState.EXPIRED -> tr("transfers.expired") to Palette.muted
        TransferState.FAILED -> tr("transfers.failed") to Palette.bad
    }
    val detail = when (t.state) {
        TransferState.RECEIVING -> t.progressText()
        TransferState.READY -> tr("transfers.readyDetail", formatCountdown(t.expiresAt, now))
        TransferState.SAVED -> tr("transfers.savedDetail", formatBytes(t.total), t.folder)
        TransferState.EXPIRED -> tr("transfers.expiredDetail")
        TransferState.FAILED -> tr("transfers.failedDetail")
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Palette.panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.names.ifBlank { tr("transfers.preparing") }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tr("transfers.from", t.peer), fontSize = 12.sp, color = Palette.muted)
            }
            Pill(label, color)
            if (t.folder.isNotBlank() && (t.state == TransferState.SAVED || t.state == TransferState.READY)) {
                SmallButton(tr("btn.openFolder"), { FolderOpener.open(t.folder) }, Modifier.padding(start = 12.dp))
            }
        }
        ProgressBar(t.fraction(), if (t.state == TransferState.FAILED) Palette.bad else color)
        Text(detail, fontSize = 12.sp, color = Palette.muted)
    }
}

@Composable
private fun ProgressBar(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.panelHi)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(3.dp)).background(color))
    }
}
