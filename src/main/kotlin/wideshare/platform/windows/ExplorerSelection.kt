package wideshare.platform.windows

import com.sun.jna.Pointer
import java.io.File
import java.util.concurrent.TimeUnit

/** Items selected in the foreground Explorer window (this is what gets dragged when the button is pressed). */
object ExplorerSelection {
    fun query(): List<File> = runCatching {
        val window = user32.GetForegroundWindow() ?: return emptyList()
        val hwnd = Pointer.nativeValue(window.pointer)
        val script = "[Console]::OutputEncoding=[Text.Encoding]::UTF8; \$h=$hwnd; " +
            "(New-Object -ComObject Shell.Application).Windows() | Where-Object { \$_.HWND -eq \$h } | " +
            "ForEach-Object { \$_.Document.SelectedItems() | ForEach-Object { \$_.Path } }"
        val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        if (!process.waitFor(TIMEOUT_S, TimeUnit.SECONDS)) process.destroyForcibly()
        output.lines().map { it.trim() }.filter { it.isNotEmpty() }.map(::File).filter { it.exists() }
    }.getOrDefault(emptyList())

    private const val TIMEOUT_S = 6L
}
