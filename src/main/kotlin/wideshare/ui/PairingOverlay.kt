package wideshare.ui

import wideshare.core.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wideshare.core.PairingRequest

/** Bluetooth style: both computers show the same code; each person confirms that it matches. */
@Composable
internal fun PairingOverlay(req: PairingRequest) {
    var answered by remember(req) { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().background(Palette.scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(8.dp)
        Column(
            Modifier.width(440.dp).clip(shape).background(Palette.panel).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(tr("pairing.title", req.peerName), fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                tr("pairing.check"),
                color = Palette.muted, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 19.sp,
            )
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Palette.bg)
                    .padding(vertical = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    req.code.chunked(3).joinToString("  "), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 44.sp,
                    letterSpacing = 4.sp, color = Palette.accent,
                )
            }
            if (answered) {
                Text(tr("pairing.waiting"), color = Palette.muted, fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { PrimaryButton(tr("btn.reject"), { req.reject() }, danger = true) }
                if (!answered) Box(Modifier.weight(1f)) { PrimaryButton(tr("btn.confirm"), { answered = true; req.accept() }) }
            }
        }
    }
}
