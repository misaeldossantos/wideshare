package wideshare.platform.linux

import wideshare.core.tr
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.NativeLongByReference

interface Xlib : Library {
    fun XOpenDisplay(name: String?): Pointer?
    fun XCloseDisplay(display: Pointer): Int
    fun XDefaultRootWindow(display: Pointer): NativeLong
    fun XDefaultScreen(display: Pointer): Int
    fun XDisplayWidth(display: Pointer, screen: Int): Int
    fun XDisplayHeight(display: Pointer, screen: Int): Int
    fun XQueryPointer(
        display: Pointer, window: NativeLong, rootReturn: NativeLongByReference, childReturn: NativeLongByReference,
        rootX: IntByReference, rootY: IntByReference, winX: IntByReference, winY: IntByReference, mask: IntByReference,
    ): Int
    fun XPending(display: Pointer): Int
    fun XNextEvent(display: Pointer, event: Pointer): Int
    fun XGrabPointer(
        display: Pointer, window: NativeLong, ownerEvents: Int, eventMask: Int, pointerMode: Int, keyboardMode: Int,
        confineTo: NativeLong, cursor: NativeLong, time: NativeLong,
    ): Int
    fun XUngrabPointer(display: Pointer, time: NativeLong): Int
    fun XGrabKeyboard(display: Pointer, window: NativeLong, ownerEvents: Int, pointerMode: Int, keyboardMode: Int, time: NativeLong): Int
    fun XUngrabKeyboard(display: Pointer, time: NativeLong): Int
    fun XWarpPointer(display: Pointer, src: NativeLong, dest: NativeLong, sx: Int, sy: Int, sw: Int, sh: Int, dx: Int, dy: Int): Int
    fun XInternAtom(display: Pointer, name: String, onlyIfExists: Int): NativeLong
    fun XGetSelectionOwner(display: Pointer, selection: NativeLong): NativeLong
    fun XFlush(display: Pointer): Int
    fun XSync(display: Pointer, discard: Int): Int
    fun XkbSetDetectableAutoRepeat(display: Pointer, detectable: Int, supported: IntByReference?): Int
    fun XCreateBitmapFromData(display: Pointer, drawable: NativeLong, data: ByteArray, width: Int, height: Int): NativeLong
    fun XCreatePixmapCursor(display: Pointer, source: NativeLong, mask: NativeLong, fg: Pointer, bg: Pointer, x: Int, y: Int): NativeLong
}

interface XTest : Library {
    fun XTestFakeKeyEvent(display: Pointer, keycode: Int, isPress: Int, delay: NativeLong): Int
    fun XTestFakeButtonEvent(display: Pointer, button: Int, isPress: Int, delay: NativeLong): Int
    fun XTestFakeMotionEvent(display: Pointer, screen: Int, x: Int, y: Int, delay: NativeLong): Int
}

internal fun <T : Library> load(names: List<String>, type: Class<T>): T {
    var last: Throwable? = null
    for (n in names) {
        try { return Native.load(n, type) } catch (e: UnsatisfiedLinkError) { last = e }
    }
    throw IllegalStateException(tr("err.x11Missing", names.first()), last)
}

internal val x11: Xlib by lazy { load(listOf("X11", "libX11.so.6"), Xlib::class.java) }
internal val xtst: XTest by lazy { load(listOf("Xtst", "libXtst.so.6"), XTest::class.java) }

internal fun openDisplay(): Pointer =
    x11.XOpenDisplay(null) ?: error(tr("err.x11Display"))

internal val NONE = NativeLong(0)
internal const val CURRENT_TIME = 0L

// X11 buttons for the neutral buttons (left, right, middle, back, forward).
internal val xButton = intArrayOf(1, 3, 2, 8, 9)
