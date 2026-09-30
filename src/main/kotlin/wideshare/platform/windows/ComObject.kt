package wideshare.platform.windows

import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Guid
import com.sun.jna.ptr.PointerByReference
import java.io.IOException

internal interface Ole32Lib : Library {
    fun CoInitializeEx(reserved: Pointer?, coInit: Int): Int
    fun CoUninitialize()
    fun CoCreateInstance(clsid: Guid.GUID, outer: Pointer?, context: Int, iid: Guid.GUID, out: PointerByReference): Int
    fun CoTaskMemFree(p: Pointer?)

    companion object {
        val INSTANCE: Ole32Lib by lazy { Native.load("ole32", Ole32Lib::class.java) }
    }
}

internal fun check(hr: Int, what: String) {
    if (hr < 0) throw IOException("$what falhou (0x${Integer.toHexString(hr)})")
}

/** COM interface called through the vtable: [call] takes the method index (IUnknown occupies 0..2). */
internal class ComObject(private val ptr: Pointer) {
    fun call(index: Int, vararg args: Any?): Int {
        val method = ptr.getPointer(0).getPointer(index.toLong() * Native.POINTER_SIZE)
        return Function.getFunction(method, Function.ALT_CONVENTION).invokeInt(arrayOf<Any?>(ptr, *args))
    }

    /** Calls the method [index] whose last argument is an output pointer to another interface. */
    fun get(index: Int, what: String, vararg args: Any?): ComObject {
        val out = PointerByReference()
        check(call(index, *args, out), what)
        return ComObject(out.value)
    }

    fun release() { call(2) }
}
