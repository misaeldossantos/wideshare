package wideshare.ui

import wideshare.core.tr
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.datatransfer.DataFlavor
import java.io.File

/** Area that receives files and folders dragged from the explorer and sends them to the other computer. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun FileDrop(c: AppController, modifier: Modifier, content: @Composable () -> Unit) {
    var hovering by remember { mutableStateOf(false) }
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { hovering = true }
            override fun onExited(event: DragAndDropEvent) { hovering = false }
            override fun onEnded(event: DragAndDropEvent) { hovering = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                hovering = false
                val dropped = runCatching { event.awtTransferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*> }
                    .getOrNull().orEmpty().filterIsInstance<File>().filter { it.exists() }
                if (dropped.isNotEmpty()) c.sendFiles(dropped)
                return dropped.isNotEmpty()
            }
        }
    }
    Box(modifier.dragAndDropTarget({ it.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) }, target)) {
        content()
        if (hovering) Box(Modifier.fillMaxSize().background(Palette.scrim), contentAlignment = Alignment.Center) {
            Text(
                tr("filedrop.hint"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.text,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Palette.panel).padding(horizontal = 28.dp, vertical = 20.dp),
            )
        }
    }
}
