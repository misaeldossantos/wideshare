package wideshare.platform.windows

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.ptr.IntByReference

internal const val WM_INPUT = 0x00FF
private const val RID_INPUT = 0x10000003
private const val RIDEV_INPUTSINK = 0x100
private const val MOUSE_MOVE_ABSOLUTE = 1
private const val HID_GENERIC_DESKTOP = 1
private const val HID_MOUSE = 2

@Structure.FieldOrder("usUsagePage", "usUsage", "dwFlags", "hwndTarget")
class RawInputDevice : Structure() {
    @JvmField var usUsagePage: Short = 0
    @JvmField var usUsage: Short = 0
    @JvmField var dwFlags: Int = 0
    @JvmField var hwndTarget: Pointer? = null
}

internal interface RawUser32 : Library {
    fun RegisterRawInputDevices(devices: Array<RawInputDevice>, count: Int, size: Int): Boolean
    fun GetRawInputData(hRawInput: Pointer, command: Int, data: Pointer?, size: IntByReference, headerSize: Int): Int
}

/**
 * Raw mouse displacement (WM_INPUT). Unlike the hook's position, it is not limited by the screen
 * edges, so the cursor can stay still at the edge while the other computer is controlled.
 * [register] and [handle] must run on the thread that pumps the messages.
 */
internal class RawMouse(private val onDelta: (Int, Int) -> Unit) {
    private val api = Native.load("user32", RawUser32::class.java)
    private val header = 8 + 2 * Native.POINTER_SIZE
    private val buffer = Memory(64)

    fun register(): Boolean {
        val window = user32.CreateWindowEx(0, "STATIC", null, 0, 0, 0, 0, 0, HWND(Pointer.createConstant(-3L)), null, null, null)
            ?: return false
        @Suppress("UNCHECKED_CAST")
        val devices = RawInputDevice().toArray(1) as Array<RawInputDevice>
        devices[0].usUsagePage = HID_GENERIC_DESKTOP.toShort()
        devices[0].usUsage = HID_MOUSE.toShort()
        devices[0].dwFlags = RIDEV_INPUTSINK
        devices[0].hwndTarget = window.pointer
        devices[0].write()
        return api.RegisterRawInputDevices(devices, 1, devices[0].size())
    }

    fun handle(lParam: Long) {
        val size = IntByReference(buffer.size().toInt())
        if (api.GetRawInputData(Pointer(lParam), RID_INPUT, buffer, size, header) <= 0) return
        if (buffer.getInt(0) != 0 || buffer.getShort(header.toLong()).toInt() and MOUSE_MOVE_ABSOLUTE != 0) return
        val dx = buffer.getInt(header + 12L)
        val dy = buffer.getInt(header + 16L)
        if (dx != 0 || dy != 0) onDelta(dx, dy)
    }
}
