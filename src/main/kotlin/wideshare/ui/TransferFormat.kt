package wideshare.ui

import wideshare.core.I18n
import wideshare.core.Language
import wideshare.core.Transfer
import wideshare.core.TransferState
import wideshare.core.tr

/** "12.4 MB" (or "12,4 MB" in Portuguese and Spanish) from a byte count. */
internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
    return "%.1f %s".format(java.util.Locale.ROOT, value, units[unit]).let { if (I18n.language == Language.EN) it else it.replace('.', ',') }
}

/** "09:12" for the time left until [expiresAt]. */
internal fun formatCountdown(expiresAt: Long, now: Long): String {
    val seconds = ((expiresAt - now) / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

internal fun Transfer.fraction() = if (total <= 0) (if (state == TransferState.RECEIVING) 0f else 1f) else (done.toFloat() / total).coerceIn(0f, 1f)

/** Progress line: "12.4 MB of 80 MB · 43%". */
internal fun Transfer.progressText() = tr("transfers.progress", formatBytes(done), formatBytes(total), (fraction() * 100).toInt())
