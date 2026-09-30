package wideshare.platform

data class ScreenSize(val width: Int, val height: Int)

/** Events coming from the capture. Called on the capture thread: do not block. */
interface CaptureListener {
    /** Local mode: absolute position of the cursor. */
    fun onLocalMove(x: Int, y: Int)

    /** Remote mode: mouse displacement (the local cursor stays still). */
    fun onRemoteMove(dx: Int, dy: Int)
    fun onButton(button: Int, down: Boolean)
    fun onWheel(dx: Int, dy: Int)
    fun onKey(code: Int, down: Boolean)
}

/**
 * Global input capture. In local mode the events go on to the system as usual and only the
 * position is reported; in remote mode everything is swallowed and reported.
 */
interface InputCapture {
    fun screen(): ScreenSize
    fun start(listener: CaptureListener)
    fun stop()

    /** Turns on remote mode, or goes back to local by placing the cursor at ([x], [y]). */
    fun setRemote(on: Boolean, x: Int = 0, y: Int = 0)

    /** Cancels a file drag in progress here (Esc), before control passes to the other computer. */
    fun cancelDrag() {}
}

/** Injects synthetic input into the local system. Keys use evdev codes; buttons use [wideshare.core.Buttons]. */
interface InputInjector {
    fun screen(): ScreenSize
    fun moveTo(x: Int, y: Int)
    fun button(button: Int, down: Boolean)
    fun wheel(dx: Int, dy: Int)
    fun key(code: Int, down: Boolean)
    fun close() {}
}

object Platform {
    val isWindows = System.getProperty("os.name").lowercase().contains("win")

    fun createCapture(): InputCapture =
        if (isWindows) wideshare.platform.windows.WindowsCapture() else wideshare.platform.linux.X11Capture()

    fun createInjector(): InputInjector =
        if (isWindows) wideshare.platform.windows.WindowsInjector() else wideshare.platform.linux.X11Injector()

    /** File drag probe, or null if this system does not support it. */
    fun createDragProbe(): DragProbe? = runCatching {
        if (isWindows) wideshare.platform.windows.WindowsDragProbe() else wideshare.platform.linux.X11DragProbe()
    }.getOrNull()

    /** Files selected in the foreground window, to know what is being dragged (empty if it cannot be known). */
    fun dragSelection(): List<java.io.File> =
        if (isWindows) wideshare.platform.windows.ExplorerSelection.query() else emptyList()

    fun createVirtualSpeaker(server: String): AudioCapture =
        if (isWindows) wideshare.platform.windows.WindowsSpeaker() else wideshare.platform.linux.PulseSpeaker(server)

    /** Key that forcibly gives control back to the server (Scroll Lock, evdev 70). */
    const val PANIC_KEY = 70
}
