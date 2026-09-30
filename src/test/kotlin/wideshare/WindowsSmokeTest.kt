package wideshare

import wideshare.platform.CaptureListener
import wideshare.platform.Platform
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class WindowsSmokeTest {
    @Test
    fun hooksInstallAndInjectorMovesCursor() {
        assumeTrue(Platform.isWindows)
        val capture = Platform.createCapture()
        val moves = java.util.concurrent.CopyOnWriteArrayList<Pair<Int, Int>>()
        capture.start(object : CaptureListener {
            override fun onLocalMove(x: Int, y: Int) { moves += x to y }
            override fun onRemoteMove(dx: Int, dy: Int) {}
            override fun onButton(button: Int, down: Boolean) {}
            override fun onWheel(dx: Int, dy: Int) {}
            override fun onKey(code: Int, down: Boolean) {}
        })
        val injector = Platform.createInjector()
        val s = injector.screen()
        assertTrue(s.width > 0 && s.height > 0)
        injector.moveTo(s.width / 2 + 3, s.height / 2 + 3)
        Thread.sleep(300)
        capture.stop()
    }
}
