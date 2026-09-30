package wideshare.core

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.spec.SecretKeySpec

/** X25519 handshake of the [SecureChannel]: authentication by an already paired key or a 6-digit code. */
internal object Handshaker {
        private const val HANDSHAKE_TIMEOUT_MS = 5_000
        private val MAGIC = "BRX3".toByteArray()
        private val random = SecureRandom()

        private fun nonce() = ByteArray(16).also(random::nextBytes)
        private fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("X25519").generateKeyPair()

        private fun agree(mine: KeyPair, peerPublic: ByteArray): ByteArray {
            val pub = KeyFactory.getInstance("X25519").generatePublic(X509EncodedKeySpec(peerPublic))
            return KeyAgreement.getInstance("X25519").run {
                init(mine.private)
                doPhase(pub, true)
                generateSecret()
            }
        }

        private fun DataOutputStream.writeBlob(b: ByteArray) { writeShort(b.size); write(b) }
        private fun DataInputStream.readBlob(): ByteArray {
            val n = readUnsignedShort()
            if (n > 128) throw IOException(tr("err.invalidKey"))
            return ByteArray(n).also(::readFully)
        }

        private fun open(socket: Socket): Pair<DataInputStream, DataOutputStream> {
            socket.tcpNoDelay = true
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            return DataInputStream(socket.getInputStream().buffered()) to DataOutputStream(socket.getOutputStream().buffered())
        }

        private fun channel(
            socket: Socket, i: DataInputStream, o: DataOutputStream, root: ByteArray,
            nc: ByteArray, ns: ByteArray, pubC: ByteArray, pubS: ByteArray, isServer: Boolean,
        ): SecureChannel {
            val c2s = SecretKeySpec(Auth.hmac(root, "c2s".toByteArray(), ns, nc, pubC, pubS), "AES")
            val s2c = SecretKeySpec(Auth.hmac(root, "s2c".toByteArray(), ns, nc, pubC, pubS), "AES")
            socket.soTimeout = SecureChannel.IDLE_TIMEOUT_MS
            return if (isServer) SecureChannel(socket, i, o, s2c, c2s) else SecureChannel(socket, i, o, c2s, s2c)
        }

        /** 6-digit code the two people compare. */
        private fun sas(z: ByteArray, nc: ByteArray, ns: ByteArray, pubC: ByteArray, pubS: ByteArray): String {
            val h = Auth.hmac(z, "sas".toByteArray(), nc, ns, pubC, pubS)
            val n = ((h[0].toLong() and 0xFF) shl 24) or ((h[1].toLong() and 0xFF) shl 16) or ((h[2].toLong() and 0xFF) shl 8) or (h[3].toLong() and 0xFF)
            return "%06d".format(n % 1_000_000)
        }

        private fun psk(z: ByteArray, nc: ByteArray, ns: ByteArray) = Auth.hmac(z, "psk".toByteArray(), nc, ns)

        fun serverHandshake(socket: Socket, me: Identity, trust: TrustStore): Handshake {
            val (i, o) = open(socket)
            val magic = ByteArray(4).also(i::readFully)
            if (!magic.contentEquals(MAGIC)) throw IOException(tr("err.incompatibleClient"))
            val peerId = Auth.hex(ByteArray(16).also(i::readFully))
            val peerName = i.readUTF()
            val nc = ByteArray(16).also(i::readFully)
            val pubC = i.readBlob()

            val keys = newKeyPair()
            val pubS = keys.public.encoded
            val ns = nonce()
            val known = trust.pskFor(peerId)
            o.write(MAGIC); o.write(Auth.unhex(me.id)); o.writeUTF(me.name); o.write(ns); o.writeBlob(pubS)
            o.writeBoolean(known != null); o.flush()
            val z = agree(keys, pubC)

            if (i.readUnsignedByte() == 1) {
                val proof = ByteArray(32).also(i::readFully)
                if (known == null || !MessageDigest.isEqual(proof, Auth.hmac(known, "C".toByteArray(), ns, nc))) {
                    o.writeByte(0); o.flush()
                    throw AuthException(tr("err.invalidPairing", peerName))
                }
                o.writeByte(1); o.write(Auth.hmac(known, "S".toByteArray(), nc, ns)); o.flush()
                val ch = channel(socket, i, o, Auth.hmac(known, z), nc, ns, pubC, pubS, isServer = true)
                return Handshake(ch, peerId, peerName, null, null)
            }
            o.writeByte(2); o.flush()
            val ch = channel(socket, i, o, z, nc, ns, pubC, pubS, isServer = true)
            return Handshake(ch, peerId, peerName, sas(z, nc, ns, pubC, pubS), psk(z, nc, ns))
        }

        fun clientHandshake(socket: Socket, me: Identity, trust: TrustStore): Handshake {
            val (i, o) = open(socket)
            val keys = newKeyPair()
            val pubC = keys.public.encoded
            val nc = nonce()
            o.write(MAGIC); o.write(Auth.unhex(me.id)); o.writeUTF(me.name); o.write(nc); o.writeBlob(pubC); o.flush()

            val magic = ByteArray(4).also(i::readFully)
            if (!magic.contentEquals(MAGIC)) throw IOException(tr("err.incompatibleServer"))
            val peerId = Auth.hex(ByteArray(16).also(i::readFully))
            val peerName = i.readUTF()
            val ns = ByteArray(16).also(i::readFully)
            val pubS = i.readBlob()
            val serverKnowsMe = i.readBoolean()
            val z = agree(keys, pubS)

            val known = trust.pskFor(peerId)
            if (serverKnowsMe && known != null) {
                o.writeByte(1); o.write(Auth.hmac(known, "C".toByteArray(), ns, nc)); o.flush()
            } else {
                o.writeByte(0); o.flush()
            }
            when (i.readUnsignedByte()) {
                1 -> {
                    val proof = ByteArray(32).also(i::readFully)
                    if (known == null || !MessageDigest.isEqual(proof, Auth.hmac(known, "S".toByteArray(), nc, ns))) {
                        throw AuthException(tr("err.serverMismatch"))
                    }
                    val ch = channel(socket, i, o, Auth.hmac(known, z), nc, ns, pubC, pubS, isServer = false)
                    return Handshake(ch, peerId, peerName, null, null)
                }
                2 -> {
                    val ch = channel(socket, i, o, z, nc, ns, pubC, pubS, isServer = false)
                    return Handshake(ch, peerId, peerName, sas(z, nc, ns, pubC, pubS), psk(z, nc, ns))
                }
                else -> throw AuthException(tr("err.invalidPairing", peerName))
            }
        }
}
