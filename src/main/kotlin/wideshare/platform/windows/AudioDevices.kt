package wideshare.platform.windows

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference

internal class AudioEndpoint(val id: String, val name: String)

private val CLSID_ENUMERATOR = GUID("{BCDE0395-E52F-467C-8E3D-C4579291692E}")
private val IID_ENUMERATOR = GUID("{A95664D2-9614-4F35-A746-DE8DB63617E6}")
private val CLSID_POLICY_CONFIG = GUID("{870AF99C-171D-4F9E-AF0D-E63DF40C2BC9}")
private val IID_POLICY_CONFIG = GUID("{F8679F50-850A-41CF-9C72-430F290290C8}")
private val FRIENDLY_NAME = GUID("{A45C254E-DF1C-4EFD-8020-67D146A850E0}")

private const val VT_LPWSTR = 31

/** Windows sound outputs and switching the default one. Call inside [withCom]. */
internal object AudioDevices {
    fun endpoints(): List<AudioEndpoint> {
        val enumerator = comCreate(CLSID_ENUMERATOR, IID_ENUMERATOR)
        val list = enumerator.get(3, "EnumAudioEndpoints", 0, 1) // eRender, DEVICE_STATE_ACTIVE
        val count = IntByReference()
        check(list.call(3, count), "GetCount")
        return (0 until count.value).map { i -> list.get(4, "Item", i).let { AudioEndpoint(idOf(it), nameOf(it)) } }
    }

    fun defaultId(): String = idOf(comCreate(CLSID_ENUMERATOR, IID_ENUMERATOR).get(4, "GetDefaultAudioEndpoint", 0, 0))

    /** Makes [id] the default output for all roles (console, multimedia and communications). */
    fun setDefault(id: String) {
        val policy = comCreate(CLSID_POLICY_CONFIG, IID_POLICY_CONFIG)
        for (role in 0..2) check(policy.call(13, WString(id), role), "SetDefaultEndpoint")
    }

    private fun idOf(device: ComObject): String {
        val out = PointerByReference()
        check(device.call(5, out), "GetId")
        return out.value.getWideString(0).also { Ole32Lib.INSTANCE.CoTaskMemFree(out.value) }
    }

    private fun nameOf(device: ComObject): String {
        val store = device.get(4, "OpenPropertyStore", 0)
        val key = Memory(24).also { m ->
            FRIENDLY_NAME.write()
            m.write(0, FRIENDLY_NAME.pointer.getByteArray(0, 16), 0, 16)
            m.setInt(16, 14)
        }
        val value = Memory(24).also { it.clear() }
        check(store.call(5, key, value), "GetValue")
        if (value.getShort(0).toInt() != VT_LPWSTR) return ""
        val text = value.getPointer(8)
        return text.getWideString(0).also { Ole32Lib.INSTANCE.CoTaskMemFree(text) }
    }
}

/** Runs [block] with COM initialized on this thread. */
internal fun <T> withCom(block: () -> T): T {
    val hr = Ole32Lib.INSTANCE.CoInitializeEx(null, 0)
    try {
        return block()
    } finally {
        if (hr >= 0) Ole32Lib.INSTANCE.CoUninitialize()
    }
}

private val IID_ENDPOINT_VOLUME = GUID("{5CDF2C82-841E-4546-9722-0CF74078229A}")

/** Volume/mute control of the default output. Call inside [withCom]. */
internal object DefaultOutput {
    private fun volume() = comCreate(CLSID_ENUMERATOR, IID_ENUMERATOR).get(4, "GetDefaultAudioEndpoint", 0, 0)
        .get(3, "Activate", IID_ENDPOINT_VOLUME, 23, null)

    fun isMuted(): Boolean {
        val out = IntByReference()
        check(volume().call(15, out), "GetMute")
        return out.value != 0
    }

    fun setMuted(mute: Boolean) = check(volume().call(14, if (mute) 1 else 0, null), "SetMute")
}
