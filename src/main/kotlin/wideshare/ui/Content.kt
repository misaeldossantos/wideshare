package wideshare.ui

import wideshare.core.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wideshare.core.Mode

@Composable
internal fun Content(c: AppController, modifier: Modifier) {
    val mode by c.mode.collectAsState()
    Box(modifier.padding(28.dp)) { if (mode == Mode.SERVER) ServerContent(c) else ClientContent(c) }
}

@Composable
internal fun ActivityLog(c: AppController, modifier: Modifier) {
    val logs by c.logs.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) { if (logs.isNotEmpty()) listState.scrollToItem(logs.lastIndex) }
    Column(
        modifier.clip(RoundedCornerShape(8.dp)).background(Palette.panel).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PageHeader(tr("nav.activity"), tr("activity.subtitle"))
        SelectionContainer {
            LazyColumn(state = listState) {
                if (logs.isEmpty()) item { Text(tr("activity.empty"), color = Palette.muted, fontSize = 12.sp) }
                items(logs) { Text(it, fontFamily = Mono, fontSize = 11.5.sp, color = Palette.muted) }
            }
        }
    }
}
