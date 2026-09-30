package wideshare.ui

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A page whose content scrolls vertically when it does not fit, with a scrollbar on the right. */
@Composable
internal fun ScrollPage(spacing: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(end = 14.dp), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
        VerticalScrollbar(
            rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            style = LocalScrollbarStyle.current.copy(unhoverColor = Palette.panelHi, hoverColor = Palette.muted),
        )
    }
}
