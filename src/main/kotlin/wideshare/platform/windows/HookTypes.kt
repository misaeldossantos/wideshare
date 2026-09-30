package wideshare.platform.windows

import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser

internal interface LowLevelMouseProc : WinUser.HOOKPROC {
    fun callback(nCode: Int, wParam: WPARAM, info: WinUser.MSLLHOOKSTRUCT): LRESULT
}
