package wideshare.ui

import wideshare.core.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import wideshare.core.FileHooks
import wideshare.core.Settings
import wideshare.core.TransferLog
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlinx.coroutines.flow.MutableStateFlow

/** Folder where files sent by another computer arrive (kept in the settings). */
class FileOptions(private val settings: Settings) {
    val folder = MutableStateFlow(settings.receiveFolder)
    val transfers = TransferLog()
    val hooks = FileHooks(::dir, { summary, accept -> FileOfferPopup.show(summary, transfers, accept) }, transfers, dragTarget = DropOverlay())

    init { FileOfferPopup.watch(transfers) }

    fun dir() = File(folder.value)

    fun set(path: String) {
        folder.value = path
        settings.receiveFolder = path.ifBlank { Settings.defaultReceiveFolder() }
        settings.save()
    }

    /** Opens the system folder chooser. */
    fun choose() = SwingUtilities.invokeLater {
        val chooser = JFileChooser(dir().takeIf { it.isDirectory } ?: dir().parentFile).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = tr("folder.dialog")
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) set(chooser.selectedFile.path)
    }
}

/** Field for the destination folder of received files, with a button to choose it. */
@Composable
internal fun ReceiveFolder(c: AppController) {
    val folder by c.files.folder.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(tr("folder.label"))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(folder, c.files::set, Settings.defaultReceiveFolder(), Modifier.weight(1f), mono = true, fontSize = 13)
            SmallButton(tr("btn.choose"), c.files::choose)
        }
    }
}
