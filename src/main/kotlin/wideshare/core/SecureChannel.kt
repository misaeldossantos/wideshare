package wideshare.core

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted TCP channel (AES-256-GCM, one key per direction).
 *
 * Handshake: each side generates an ephemeral X25519 pair. If both are already paired, they prove they know the
 * peer's key (HMAC) and the session key mixes in the ECDH secret (forward secrecy). Otherwise, the session
 * key comes from the ECDH alone and both compute a 6-digit code from the transcript: if a
 * man in the middle were present, the codes shown would differ (numeric comparison,
 * as in Bluetooth).
 */
class SecureChannel internal constructor(
    private val socket: Socket,
    private val input: DataInputStream,
    private val output: DataOutputStream,
    private val sendKey: SecretKeySpec,
    private val recvKey: SecretKeySpec,
) {
    private var sendCounter = 0L
    private var recvCounter = 0L

    val remoteAddress: String get() = socket.inetAddress.hostAddress

    fun setTimeout(ms: Int) { socket.soTimeout = ms }

    @Synchronized
    fun send(plain: ByteArray) {
        val encrypted = crypt(Cipher.ENCRYPT_MODE, sendKey, sendCounter++, plain)
        output.writeInt(encrypted.size)
        output.write(encrypted)
        output.flush()
    }

    fun receive(): ByteArray {
        val size = input.readInt()
        if (size < 16 || size > MAX_FRAME) throw IOException(tr("err.badFrame", size))
        val encrypted = ByteArray(size)
        input.readFully(encrypted)
        return try {
            crypt(Cipher.DECRYPT_MODE, recvKey, recvCounter++, encrypted)
        } catch (e: java.security.GeneralSecurityException) {
            throw IOException(tr("err.decrypt"), e)
        }
    }

    fun close() = runCatching { socket.close() }

    private fun crypt(mode: Int, key: SecretKeySpec, counter: Long, data: ByteArray): ByteArray {
        val nonce = ByteArray(12)
        for (i in 0 until 8) nonce[11 - i] = (counter ushr (8 * i)).toByte()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, key, GCMParameterSpec(128, nonce))
        return cipher.doFinal(data)
    }

    companion object {
        const val IDLE_TIMEOUT_MS = 8_000
        private const val MAX_FRAME = 64 * 1024

        fun serverHandshake(socket: Socket, me: Identity, trust: TrustStore) = Handshaker.serverHandshake(socket, me, trust)
        fun clientHandshake(socket: Socket, me: Identity, trust: TrustStore) = Handshaker.clientHandshake(socket, me, trust)
    }
}
