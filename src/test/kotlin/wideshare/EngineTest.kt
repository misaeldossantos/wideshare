package wideshare

import wideshare.core.ClientEngine
import wideshare.core.ClientInfo
import wideshare.core.GridPos
import wideshare.core.PairingRequest
import wideshare.core.ServerEngine
import wideshare.platform.ScreenSize
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineTest {
    private class Rig(secretlyPaired: Boolean, clientNames: List<String>, val positions: Map<String, GridPos>, val autoAccept: Boolean = true) {
        val capture = FakeCapture(ScreenSize(1920, 1080))
        val serverMe = identity("srv")
        val serverTrust = MemoryTrust()
        val infos = CopyOnWriteArrayList<List<ClientInfo>>()
        val prompts = CopyOnWriteArrayList<String>()
        val injectors = clientNames.associateWith { FakeInjector(if (it == "B") ScreenSize(1000, 500) else ScreenSize(1280, 720)) }
        val clientTrusts = clientNames.associateWith { MemoryTrust() }
        val clientIds = clientNames.associateWith { identity(it) }
        val server: ServerEngine
        val clients: List<ClientEngine>

        init {
            if (secretlyPaired) clientNames.forEach { preload(serverMe, serverTrust, clientIds.getValue(it), clientTrusts.getValue(it)) }
            val prompt = { req: PairingRequest? -> if (req != null) { prompts += req.code; if (autoAccept) req.accept() else req.reject() } }
            server = ServerEngine(serverMe, serverTrust, capture, { positions.getValue(it) }, {}, { infos += it }, prompt)
            clients = clientNames.map {
                ClientEngine(clientIds.getValue(it), clientTrusts.getValue(it), injectors.getValue(it), { true }, { "" }, {}, {}, {}, prompt)
            }
        }

        fun start() { server.start(); clients.forEach { it.start() } }
        fun stop() { clients.forEach { it.stop() }; server.stop() }
    }

    @Test
    fun cursorTravelsAcrossChainOfClients() {
        // Layout: [server] [A] [B]
        val rig = Rig(true, listOf("A", "B"), mapOf("A" to GridPos(1, 0), "B" to GridPos(2, 0)))
        val capture = rig.capture
        val injA = rig.injectors.getValue("A"); val injB = rig.injectors.getValue("B")
        rig.start()
        try {
            waitFor("two clients connected") { rig.infos.lastOrNull()?.map { it.name }?.toSet() == setOf("A", "B") }
            assertTrue(rig.prompts.isEmpty(), "already paired: no overlay")

            // Server -> A: leaves through the right, enters A through the left.
            capture.listener.onLocalMove(1919, 540)
            waitFor("cursor entered A") { injA.events.contains("move 0,359") }
            assertTrue(capture.remote)

            capture.listener.onKey(30, true)
            waitFor("key on A") { injA.events.contains("key 30 true") }

            // A -> B: pushes past A's right edge.
            capture.listener.onRemoteMove(1500, 0)
            waitFor("cursor entered B") { injB.events.any { it.startsWith("move 0,") } }
            waitFor("key stuck on A must be released") { injA.events.contains("key 30 false") }
            assertTrue(capture.remote, "server keeps forwarding input")

            capture.listener.onKey(31, true)
            waitFor("key on B") { injB.events.contains("key 31 true") }
            assertTrue(injA.events.none { it == "key 31 true" })

            // B -> A -> server: goes back through the left twice.
            capture.listener.onRemoteMove(-300, 0)
            waitFor("cursor went back to A") { injA.events.count { it.startsWith("move 1279,") } >= 2 }
            capture.listener.onRemoteMove(-2000, 0)
            waitFor("control returned to the server") { !capture.remote }
            assertEquals(1916, capture.resume!!.first)
        } finally {
            rig.stop()
        }
    }

    @Test
    fun edgeWithoutNeighborKeepsControl() {
        val rig = Rig(true, listOf("A"), mapOf("A" to GridPos(0, 1)))
        val capture = rig.capture; val inj = rig.injectors.getValue("A")
        rig.start()
        try {
            waitFor("client connected") { rig.infos.lastOrNull()?.isNotEmpty() == true }
            capture.listener.onLocalMove(1919, 100) // no neighbor on the right
            Thread.sleep(200)
            assertTrue(!capture.remote)
            capture.listener.onLocalMove(500, 1079) // client below: enters from the top
            waitFor("cursor entered") { inj.events.any { it.startsWith("move ") } }
            capture.listener.onRemoteMove(5000, 0) // client's right edge, no neighbor
            Thread.sleep(300)
            assertTrue(capture.remote, "no neighbor, the cursor stays on the client")
            capture.listener.onRemoteMove(0, -5000) // goes up: back to the server
            waitFor("went back to the server") { !capture.remote }
        } finally {
            rig.stop()
        }
    }

    @Test
    fun firstConnectionAsksBothSidesToConfirmSameCodeAndThenRemembers() {
        val rig = Rig(false, listOf("A"), mapOf("A" to GridPos(1, 0)))
        rig.start()
        try {
            // Without prior pairing the client does NOT connect by itself (it requires a user click).
            Thread.sleep(2500)
            assertTrue(rig.infos.isEmpty() && rig.prompts.isEmpty())

            rig.clients.single().connectTo(rig.serverMe.id)
            waitFor("client connected after confirming") { rig.infos.lastOrNull()?.any { it.name == "A" } == true }
            assertEquals(2, rig.prompts.size, "one overlay on each computer")
            assertEquals(1, rig.prompts.toSet().size, "with the same code")
            assertTrue(rig.serverTrust.peers.containsKey(rig.clientIds.getValue("A").id))
            assertTrue(rig.clientTrusts.getValue("A").peers.containsKey(rig.serverMe.id))
        } finally {
            rig.stop()
        }
    }

    @Test
    fun rejectedPairingDoesNotConnect() {
        val rig = Rig(false, listOf("A"), mapOf("A" to GridPos(1, 0)), autoAccept = false)
        rig.start()
        try {
            rig.clients.single().connectTo(rig.serverMe.id)
            waitFor("requests shown") { rig.prompts.size >= 1 }
            Thread.sleep(500)
            assertTrue(rig.infos.isEmpty())
            assertTrue(rig.serverTrust.peers.isEmpty())
        } finally {
            rig.stop()
        }
    }
}
