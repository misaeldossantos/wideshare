package wideshare

import wideshare.core.Buttons
import wideshare.core.Side
import wideshare.platform.ScreenSize
import wideshare.platform.linux.wayland.EvdevButtons
import wideshare.platform.linux.wayland.WheelAccumulator
import wideshare.platform.linux.wayland.Zones
import wideshare.platform.linux.wayland.numbers
import wideshare.platform.linux.wayland.rows
import org.freedesktop.dbus.types.UInt32
import wideshare.platform.linux.wayland.BarrierLine
import wideshare.platform.linux.wayland.CursorPosition
import wideshare.platform.linux.wayland.InputCaptureApi
import wideshare.platform.linux.wayland.PortalRequest
import wideshare.platform.linux.wayland.RemoteDesktopApi
import org.freedesktop.dbus.Marshalling
import org.freedesktop.dbus.types.Variant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WaylandTest {
    @Test
    fun wheelDeliversWholeNotchesAndKeepsTheRemainder() {
        val w = WheelAccumulator()
        assertEquals(0, w.add(60))
        assertEquals(1, w.add(60))
        assertEquals(-2, w.add(-250))
        assertEquals(-1, w.add(-110)) // -10 left over + -110
        assertEquals(0, w.add(0))
    }

    @Test
    fun buttonsRoundTripThroughEvdev() {
        for (b in listOf(Buttons.LEFT, Buttons.RIGHT, Buttons.MIDDLE, Buttons.BACK, Buttons.FORWARD)) {
            assertEquals(b, EvdevButtons.toNeutral(EvdevButtons.fromNeutral(b)!!))
        }
        assertNull(EvdevButtons.fromNeutral(99))
        assertEquals(Buttons.BACK, EvdevButtons.toNeutral(0x116))
        assertEquals(Buttons.FORWARD, EvdevButtons.toNeutral(0x115))
    }

    @Test
    fun zonesAreMergedIntoOneAreaAndKeepTheirOffsets() {
        // width, height, x, y: two 1920x1080 monitors side by side, the second one lower.
        val zones = listOf(arrayOf<Any>(UInt32(1920), UInt32(1080), 0, 0), arrayOf<Any>(UInt32(1920), UInt32(1080), 1920, 100))
        val z = Zones.parse(zones, UInt32(7), ScreenSize(800, 600))
        assertEquals(ScreenSize(3840, 1180), z.size)
        assertEquals(7L, z.set.toLong())
    }

    @Test
    fun withoutZonesTheFallbackSizeIsUsed() {
        assertEquals(ScreenSize(800, 600), Zones.parse(emptyMap(), ScreenSize(800, 600)).size)
    }

    @Test
    fun barrierIdsMapBackToSides() {
        for (side in Side.entries) assertEquals(side, Zones.sideOf(UInt32(side.ordinal + 1L)))
        assertNull(Zones.sideOf(UInt32(0)))
        assertNull(Zones.sideOf(null))
    }

    @Test
    fun structsAreReadWhetherTheyComeAsArraysOrLists() {
        assertEquals(listOf(1.5, 2.0), numbers(arrayOf<Any>(1.5, 2)))
        assertEquals(listOf(1.5, 2.0), numbers(listOf(1.5, 2)))
        assertEquals(listOf(listOf(1.0, 2.0), listOf(3.0)), rows(listOf(arrayOf<Any>(1, 2), listOf(3))))
    }
}

class PortalSignatureTest {
    private fun sig(type: Class<*>, method: String): String =
        type.methods.first { it.name == method }.let { m ->
            Marshalling.getDBusType(m.genericParameterTypes)
        }

    @Test
    fun remoteDesktopCallsMatchThePortalSpecification() {
        val api = RemoteDesktopApi::class.java
        assertEquals("a{sv}", sig(api, "CreateSession"))
        assertEquals("oa{sv}", sig(api, "SelectDevices"))
        assertEquals("osa{sv}", sig(api, "Start"))
        assertEquals("oa{sv}dd", sig(api, "NotifyPointerMotion"))
        assertEquals("oa{sv}iu", sig(api, "NotifyPointerButton"))
        assertEquals("oa{sv}ui", sig(api, "NotifyPointerAxisDiscrete"))
        assertEquals("oa{sv}iu", sig(api, "NotifyKeyboardKeycode"))
    }

    @Test
    fun inputCaptureCallsMatchThePortalSpecification() {
        val api = InputCaptureApi::class.java
        assertEquals("sa{sv}", sig(api, "CreateSession"))
        assertEquals("oa{sv}aa{sv}u", sig(api, "SetPointerBarriers"))
        assertEquals("oa{sv}", sig(api, "Release"))
    }

    @Test
    fun structsAreSentWithTheExpectedSignature() {
        assertEquals("(iiii)", Variant(BarrierLine(0, 0, 0, 10)).sig)
        assertEquals("(dd)", Variant(CursorPosition(1.0, 2.0)).sig)
    }

    @Test
    fun signalsCarryTheirArguments() {
        assertEquals("ua{sv}", Marshalling.getDBusType(PortalRequest.Response::class.java.constructors.first().genericParameterTypes.drop(1).toTypedArray()))
        assertEquals("oa{sv}", Marshalling.getDBusType(InputCaptureApi.Activated::class.java.constructors.first().genericParameterTypes.drop(1).toTypedArray()))
    }
}
