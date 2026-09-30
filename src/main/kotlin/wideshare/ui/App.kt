package wideshare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import wideshare.core.ThemeMode

@Composable
fun App(c: AppController, onDark: (Boolean) -> Unit = {}) {
    val theme by c.theme.collectAsState()
    val dark = when (theme) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    Palette.dark = dark
    LaunchedEffect(dark) { onDark(dark) }
    val scheme = if (dark) {
        darkColorScheme(primary = Palette.accent, background = Palette.bg, surface = Palette.panel, onSurface = Palette.text, onBackground = Palette.text)
    } else {
        lightColorScheme(primary = Palette.accent, background = Palette.bg, surface = Palette.panel, onSurface = Palette.text, onBackground = Palette.text)
    }
    val pairing by c.pairing.collectAsState()
    val language by c.language.collectAsState()
    DisposableEffect(c) {
        val share = DragShare(c).also { it.start() }
        onDispose { share.stop() }
    }
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = Palette.bg, contentColor = Palette.text) {
            ProvideTextStyle(TextStyle(fontFamily = Inter)) {
                FileDrop(c, Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxSize()) {
                        key(language) {
                            Sidebar(c, Modifier.width(310.dp).fillMaxHeight().background(Palette.sidebar))
                            Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.line))
                            Content(c, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                    pairing?.let { key(language) { PairingOverlay(it) } }
                }
            }
        }
    }
}
