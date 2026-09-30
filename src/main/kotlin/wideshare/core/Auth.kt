package wideshare.core

import java.io.IOException
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class AuthException(message: String) : IOException(message)
class PairingRejectedException(message: String) : IOException(message)

/** Stable identity of this computer: [id] is 16 random bytes in hexadecimal. */
class Identity(val id: String, val name: String)

/** Computers that are already paired and the shared key (PSK) of each one. */
interface TrustStore {
    fun pskFor(peerId: String): ByteArray?
    fun trust(peerId: String, name: String, psk: ByteArray)
    fun forget(peerId: String)
}

/**
 * Result of the handshake. If [sas] is not null, the two sides do not know each other yet: the channel is
 * already encrypted, but it is only trustworthy once the people confirm that the [sas] code is the same
 * on both screens; then [pendingPsk] must be stored as the peer's key.
 */
class Handshake(val channel: SecureChannel, val peerId: String, val peerName: String, val sas: String?, val pendingPsk: ByteArray?)

object Auth {
    fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        parts.forEach(mac::update)
        return mac.doFinal()
    }

    fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    fun unhex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    fun newId() = hex(ByteArray(16).also(SecureRandom()::nextBytes))
}
