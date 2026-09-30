package wideshare.ui

import wideshare.core.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Switches for what the server shares with the clients. */
@Composable
internal fun ShareOptions(c: AppController) {
    val toServer by c.receiveClipboard.collectAsState()
    val toClients by c.shareClipboard.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("share.title"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Toggle(tr("share.toServer"), toServer, c::setReceiveClipboard)
        Toggle(tr("share.toClients"), toClients, c::setShareClipboard)
    }
}
