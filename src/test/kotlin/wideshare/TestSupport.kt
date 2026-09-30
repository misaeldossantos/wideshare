package wideshare

import wideshare.core.Auth
import wideshare.core.Identity
import wideshare.core.TrustStore
import wideshare.platform.CaptureListener
import wideshare.platform.InputCapture
import wideshare.platform.InputInjector
import wideshare.platform.ScreenSize
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.fail

class FakeCapture(private val size: ScreenSize) : InputCapture {
    lateinit var listener: CaptureListener
    @Volatile var remote = false
    @Volatile var resume: Pair<Int, Int>? = null
    override fun screen() = size
    override fun start(listener: CaptureListener) { this.listener = listener }
    override fun stop() {}
    // resume before remote: whoever waits for "!remote" already finds the return position.
    override fun setRemote(on: Boolean, x: Int, y: Int) { if (!on) resume = x to y; remote = on }
}

class FakeInjector(private val size: ScreenSize) : InputInjector {
    val events = CopyOnWriteArrayList<String>()
    override fun screen() = size
    override fun moveTo(x: Int, y: Int) { events += "move $x,$y" }
    override fun button(button: Int, down: Boolean) { events += "button $button $down" }
    override fun wheel(dx: Int, dy: Int) { events += "wheel $dx,$dy" }
    override fun key(code: Int, down: Boolean) { events += "key $code $down" }
}

class MemoryTrust : TrustStore {
    val peers = ConcurrentHashMap<String, Pair<String, ByteArray>>()
    override fun pskFor(peerId: String) = peers[peerId]?.second
    override fun trust(peerId: String, name: String, psk: ByteArray) { peers[peerId] = name to psk }
    override fun forget(peerId: String) { peers.remove(peerId) }
}

fun identity(name: String) = Identity(Auth.newId(), name)

/** Makes [a] and [b] trust each other, as if they had paired before. */
fun preload(a: Identity, ta: MemoryTrust, b: Identity, tb: MemoryTrust) {
    val psk = ByteArray(32) { it.toByte() }
    ta.trust(b.id, b.name, psk)
    tb.trust(a.id, a.name, psk)
}

fun waitFor(what: String, timeoutMs: Long = 8000, cond: () -> Boolean) {
    val end = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < end) { if (cond()) return; Thread.sleep(20) }
    fail("Timed out waiting for: $what")
}
