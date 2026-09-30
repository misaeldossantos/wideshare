package wideshare.ui

import wideshare.core.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ClientContent(c: AppController) {
    val section by c.clientSection.collectAsState()
    when (section) {
        ClientSection.SERVERS -> ClientView(c)
        ClientSection.PAIRED -> PairedPage(c)
        ClientSection.SETTINGS -> ClientSettingsPage(c)
        ClientSection.TRANSFERS -> TransfersPage(c)
        ClientSection.ACTIVITY -> ActivityLog(c, Modifier.fillMaxSize())
    }
}

@Composable
private fun ClientSettingsPage(c: AppController) {
    val running by c.running.collectAsState()
    val name by c.name.collectAsState()
    val auto by c.autoConnect.collectAsState()
    val sendAudio by c.sendAudio.collectAsState()
    ScrollPage {
        PageHeader(tr("settings.title"), tr("settings.client.subtitle"))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(tr("settings.name"))
            Field(name, c::setName, tr("settings.name.hint"), enabled = !running)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Toggle(tr("settings.auto"), auto, c::setAutoConnect)
            Toggle(tr("settings.sendAudio"), sendAudio, c::setSendAudio)
        }
        ThemeSelect(c)
        LanguageSelect(c)
        ReceiveFolder(c)
    }
}
