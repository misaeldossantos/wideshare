package wideshare

import wideshare.core.ClientEngine
import wideshare.core.ClientInfo
import wideshare.core.Codec
import wideshare.core.FileHooks
import wideshare.core.FileReceiver
import wideshare.core.FileSender
import wideshare.core.GridPos
import wideshare.core.Message
import wideshare.core.ServerEngine
import wideshare.core.TransferState
import wideshare.platform.ScreenSize
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileTransferTest {
    private fun sample(): File {
        val root = Files.createTempDirectory("send").toFile()
        val dir = File(root, "folder").also { it.mkdirs() }
        File(dir, "big.bin").writeBytes(Random(1).nextBytes(100_000))
        File(dir, "sub").mkdirs()
        File(dir, "sub/note.txt").writeText("hola")
        File(dir, "empty").mkdirs()
        File(root, "loose.txt").writeText("hi")
        return root
    }

    private fun assertSameTree(a: File, b: File) {
        assertEquals(a.isDirectory, b.isDirectory, b.path)
        if (a.isDirectory) {
            assertEquals(a.list()!!.sorted(), b.list()!!.sorted(), b.path)
            a.listFiles()!!.forEach { assertSameTree(it, File(b, it.name)) }
        } else assertContentEquals(a.readBytes(), b.readBytes(), b.path)
    }

    private fun hooks(dest: File, ttlMs: Long = 600_000) =
        FileHooks(folder = { dest }, tempRoot = Files.createTempDirectory("tmp").toFile(), ttlMs = ttlMs)

    @Test
    fun droppedFoldersAndFilesSurviveTheCodecIntoTheReceiveFolder() {
        val src = sample()
        val dest = Files.createTempDirectory("recv").toFile()
        val hooks = hooks(dest)
        val receiver = FileReceiver(hooks, "pc", { true }) { _, _ -> }
        val sender = FileSender { receiver.handle(Codec.decode(Codec.encode(it))); true }
        assertTrue(sender.sendAll(listOf(File(src, "folder"), File(src, "loose.txt")), paste = false))
        assertSameTree(File(src, "folder"), File(dest, "folder"))
        assertSameTree(File(src, "loose.txt"), File(dest, "loose.txt"))
        assertEquals(TransferState.SAVED, hooks.transfers.items.value.single().state)

        sender.sendAll(listOf(File(src, "loose.txt")), paste = false)
        assertEquals("hi", File(dest, "loose (1).txt").readText())
    }

    @Test
    fun pastedFilesGoToATempFolderWithProgressAndAreDeletedAfterTheTtl() {
        val src = sample()
        val dest = Files.createTempDirectory("recv").toFile()
        val hooks = hooks(dest, ttlMs = 400)
        val got = CopyOnWriteArrayList<List<File>>()
        val receiver = FileReceiver(hooks, "pc", { true }) { files, paste -> if (paste) got += files }
        FileSender { receiver.handle(it); true }.sendAll(listOf(File(src, "folder")), paste = true)

        val copy = got.single().single()
        assertTrue(copy.path.startsWith(dest.path).not(), "does not go to the received folder")
        assertSameTree(File(src, "folder"), copy)
        val done = hooks.transfers.items.value.single()
        assertEquals(TransferState.READY, done.state)
        assertEquals(100_004L, done.total)
        assertEquals(done.total, done.done)
        assertTrue(done.expiresAt > System.currentTimeMillis())

        waitFor("temporary folder deleted") { !copy.exists() }
        waitFor("transfer marked as deleted") { hooks.transfers.items.value.single().state == TransferState.EXPIRED }
    }

    @Test
    fun receiverRejectsPathsOutsideItsFolderAndHonorsPasteSwitch() {
        val dest = Files.createTempDirectory("recv").toFile()
        var allowed = false
        val got = CopyOnWriteArrayList<List<File>>()
        val receiver = FileReceiver(hooks(dest), "pc", { allowed }) { files, _ -> got += files }
        fun batch(paste: Boolean, path: String) {
            listOf(Message.FilesStart(paste, 1), Message.FileBegin(path, 1), Message.FileData(byteArrayOf(1)), Message.FileEnd, Message.FilesDone)
                .forEach { receiver.handle(it) }
        }
        batch(paste = false, path = "../fora.txt")
        assertTrue(!File(dest.parentFile, "fora.txt").exists() && got.isEmpty())
        batch(paste = true, path = "colado.txt")
        assertTrue(got.isEmpty(), "paste off: nothing is written")
        allowed = true
        batch(paste = true, path = "colado.txt")
        assertTrue(got.single().single().exists())
    }

    @Test
    fun droppedFilesReachTheOtherSideInBothDirections() {
        val capture = FakeCapture(ScreenSize(1920, 1080))
        val serverMe = identity("srv"); val clientMe = identity("cli")
        val serverTrust = MemoryTrust(); val clientTrust = MemoryTrust()
        preload(serverMe, serverTrust, clientMe, clientTrust)
        val infos = CopyOnWriteArrayList<List<ClientInfo>>()
        val serverDir = Files.createTempDirectory("srv").toFile()
        val clientDir = Files.createTempDirectory("cli").toFile()
        val server = ServerEngine(serverMe, serverTrust, capture, { GridPos(1, 0) }, {}, { infos += it }, files = FileHooks(folder = { serverDir }))
        val client = ClientEngine(clientMe, clientTrust, FakeInjector(ScreenSize(1280, 720)), { true }, { "" }, {}, {}, {}, files = FileHooks(folder = { clientDir }))
        server.start(); client.start()
        try {
            waitFor("client connected") { infos.lastOrNull()?.isNotEmpty() == true }
            val src = sample()
            server.sendFiles(listOf(File(src, "folder")))
            waitFor("folder reached the client") { File(clientDir, "folder/sub/note.txt").exists() && File(clientDir, "folder/big.bin").length() == 100_000L }
            assertSameTree(File(src, "folder"), File(clientDir, "folder"))

            client.sendFiles(listOf(File(src, "loose.txt")))
            waitFor("file reached the server") { File(serverDir, "loose.txt").exists() }
            assertEquals("hi", File(serverDir, "loose.txt").readText())
        } finally { client.stop(); server.stop() }
    }
}
