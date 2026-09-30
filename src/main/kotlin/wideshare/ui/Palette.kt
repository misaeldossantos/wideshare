package wideshare.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

object Palette {
    /** Read during composition: changing the value redraws the interface with the other palette. */
    var dark by mutableStateOf(true)

    private fun pick(dark: Long, light: Long) = Color(if (this.dark) dark else light)

    val bg get() = pick(0xFF1C2129, 0xFFF5F7FA)
    val sidebar get() = pick(0xFF2A313B, 0xFFDDE3EB)
    val panel get() = pick(0xFF333B47, 0xFFFFFFFF)
    val panelHi get() = pick(0xFF3F4856, 0xFFCBD3DE)
    val line get() = pick(0xFF3A4350, 0xFFC4CCD8)
    val text get() = pick(0xFFE8ECF2, 0xFF151A21)
    val muted get() = pick(0xFF8892A0, 0xFF5F6B7A)
    val accent get() = pick(0xFF4C8DF6, 0xFF1F5FD6)
    val accentSoft get() = pick(0x264C8DF6, 0x261F5FD6)
    val ok get() = pick(0xFF5FC28A, 0xFF23794A)
    val bad get() = pick(0xFFE5645A, 0xFFC0392F)
    val onAccent get() = pick(0xFF08111F, 0xFFFFFFFF)
    val danger get() = pick(0xFFD64545, 0xFFC9372F)
    val onDanger get() = Color(0xFFFFFFFF)
    val scrim get() = pick(0xCC05070A, 0x991C1B18)

    val accentFill: Brush get() = SolidColor(accent)
}

val Mono = FontFamily.Monospace
