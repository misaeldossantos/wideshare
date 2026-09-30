package wideshare

import wideshare.platform.CaptureListener
import wideshare.platform.Platform
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertTrue

class LinuxSmokeTest {
    @Test
    fun x11InjectorMovesPointerAndCaptureSeesIt() {
        assumeTrue(!Platform.isWindows && System.getenv("DISPLAY") != null)
        val moves = CopyOnWriteArrayList<Pair<Int, Int>>()
        val capture = Platform.createCapture()
        capture.start(object : CaptureListener {
            override fun onLocalMove(x: Int, y: Int) { moves += x to y }
            override fun onRemoteMove(dx: Int, dy: Int) {}
            override fun onButton(button: Int, down: Boolean) {}
            override fun onWheel(dx: Int, dy: Int) {}
            override fun onKey(code: Int, down: Boolean) {}
        })
        val injector = Platform.createInjector()
        try {
            val s = injector.screen()
            assertTrue(s.width > 0 && s.height > 0, "screen $s")
            injector.moveTo(123, 234)
            try { waitFor("capture saw the pointer at 123,234") { moves.any { it == 123 to 234 } } } catch (e: AssertionError) { throw AssertionError("${e.message}; screen=$s; distinct=${moves.distinct().take(8)}") }
        } finally {
            capture.stop()
            injector.close()
        }
    }
}
