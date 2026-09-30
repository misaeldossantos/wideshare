package wideshare

import wideshare.core.Codec
import wideshare.core.Message
import wideshare.core.Side
import wideshare.platform.windows.WinKeyMap
import kotlin.test.Test
import kotlin.test.assertEquals

class CodecTest {
    @Test
    fun codecRoundTrip() {
        val all = listOf(
            Message.Hello("pc-1", 1920, 1080), Message.Enter(Side.LEFT, 0.25f), Message.Leave(Side.TOP, 0.9f),
            Message.MouseMove(-5, 7), Message.MouseButton(2, true), Message.Wheel(0, -120),
            Message.Key(30, false), Message.Release, Message.Ping, Message.PairDecision(true),
        )
        all.forEach { assertEquals(it, Codec.decode(Codec.encode(it))) }
    }

    @Test
    fun winKeyMapRoundTrip() {
        for (code in listOf(1, 30, 57, 89, 96, 97, 100, 103, 105, 106, 108, 111, 125)) {
            val (scan, ext) = WinKeyMap.fromEvdev(code)!!
            assertEquals(code, WinKeyMap.toEvdev(scan, ext))
        }
    }
}
