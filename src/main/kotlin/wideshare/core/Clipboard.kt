package wideshare.core

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/** System clipboard, text only. */
internal object Clipboard {
    /** Text copied right now, or null if it is empty, is not text or exceeds [MAX_CLIPBOARD]. */
    fun read(): String? = runCatching {
        val board = Toolkit.getDefaultToolkit().systemClipboard
        if (!board.isDataFlavorAvailable(DataFlavor.stringFlavor)) return null
        (board.getData(DataFlavor.stringFlavor) as? String)?.takeIf { it.isNotEmpty() && it.length <= MAX_CLIPBOARD / 4 }
    }.getOrNull()

    fun write(text: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
    }
}
