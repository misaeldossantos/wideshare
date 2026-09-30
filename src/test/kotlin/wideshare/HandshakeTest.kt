package wideshare

import wideshare.core.AuthException
import wideshare.core.Codec
import wideshare.core.Handshake
import wideshare.core.Identity
import wideshare.core.Message
import wideshare.core.PairingRequest
import wideshare.core.SecureChannel
import wideshare.core.TrustStore
import wideshare.core.runPairing
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HandshakeTest {
    private class Pair2(val server: Result<Handshake>, val client: Result<Handshake>)

    private fun handshake(sMe: Identity, sTrust: TrustStore, cMe: Identity, cTrust: TrustStore): Pair2 {
        val ss = ServerSocket(0)
        var server: Result<Handshake>? = null
        val t = thread { server = runCatching { SecureChannel.serverHandshake(ss.accept(), sMe, sTrust) } }
        val client = runCatching { SecureChannel.clientHandshake(Socket("127.0.0.1", ss.localPort), cMe, cTrust) }
        t.join()
        ss.close()
        return Pair2(server!!, client)
    }

    @Test
    fun firstContactYieldsSameSixDigitCodeOnBothSides() {
        val s = identity("srv"); val c = identity("cli")
        val r = handshake(s, MemoryTrust(), c, MemoryTrust())
        val sh = r.server.getOrThrow(); val ch = r.client.getOrThrow()
        assertNotNull(sh.sas)
        assertEquals(6, sh.sas!!.length)
        assertEquals(sh.sas, ch.sas)
        assertContentEquals(sh.pendingPsk, ch.pendingPsk)
        assertEquals("cli", sh.peerName)
        assertEquals("srv", ch.peerName)
        // The channel is already encrypted and working before the confirmation.
        val payload = Codec.encode(Message.Ping)
        ch.channel.send(payload)
        assertContentEquals(payload, sh.channel.receive())
    }

    @Test
    fun differentSessionsProduceDifferentCodes() {
        val codes = (1..6).map { handshake(identity("s"), MemoryTrust(), identity("c"), MemoryTrust()).server.getOrThrow().sas }.toSet()
        assertTrue(codes.size > 1)
    }

    @Test
    fun pairedDevicesReconnectWithoutCode() {
        val s = identity("srv"); val c = identity("cli")
        val ts = MemoryTrust(); val tc = MemoryTrust()
        preload(s, ts, c, tc)
        val r = handshake(s, ts, c, tc)
        val sh = r.server.getOrThrow(); val ch = r.client.getOrThrow()
        assertNull(sh.sas); assertNull(ch.sas)
        val payload = Codec.encode(Message.MouseMove(3, 4))
        ch.channel.send(payload)
        assertContentEquals(payload, sh.channel.receive())
        sh.channel.send(payload)
        assertContentEquals(payload, ch.channel.receive())
    }

    @Test
    fun mismatchedKeysAreRejected() {
        val s = identity("srv"); val c = identity("cli")
        val ts = MemoryTrust(); val tc = MemoryTrust()
        ts.trust(c.id, "cli", ByteArray(32) { 1 })
        tc.trust(s.id, "srv", ByteArray(32) { 2 })
        val r = handshake(s, ts, c, tc)
        assertFailsWith<AuthException> { r.server.getOrThrow() }
        assertTrue(r.client.isFailure)
    }

    @Test
    fun pairingStoresKeyOnlyWhenBothAccept() {
        fun pair(serverAccepts: Boolean, clientAccepts: Boolean): Pair<MemoryTrust, MemoryTrust> {
            val s = identity("srv"); val c = identity("cli")
            val ts = MemoryTrust(); val tc = MemoryTrust()
            val r = handshake(s, ts, c, tc)
            val sh = r.server.getOrThrow(); val ch = r.client.getOrThrow()
            val shown = CopyOnWriteArrayList<String>()
            fun decide(accept: Boolean) = { req: PairingRequest? ->
                if (req != null) { shown += req.code; if (accept) req.accept() else req.reject() }
            }
            val t = thread { runCatching { runPairing(sh, ts, decide(serverAccepts)) } }
            runCatching { runPairing(ch, tc, decide(clientAccepts)) }
            t.join()
            if (serverAccepts && clientAccepts) assertEquals(1, shown.toSet().size, "same code on both screens")
            return ts to tc
        }
        val (ts, tc) = pair(true, true)
        assertEquals(1, ts.peers.size); assertEquals(1, tc.peers.size)
        assertContentEquals(ts.peers.values.first().second, tc.peers.values.first().second)

        for ((a, b) in listOf(true to false, false to true, false to false)) {
            val (s2, c2) = pair(a, b)
            assertTrue(s2.peers.isEmpty() && c2.peers.isEmpty(), "nothing is stored if someone declines ($a,$b)")
        }
    }
}
