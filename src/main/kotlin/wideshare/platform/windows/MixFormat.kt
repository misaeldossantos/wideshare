package wideshare.platform.windows

import com.sun.jna.Pointer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Mix format of the device (WAVEFORMATEX): integer or float PCM, N channels. */
internal class MixFormat(val rate: Int, private val channels: Int, private val bits: Int, private val float: Boolean) {
    private val frameBytes = channels * bits / 8

    /** Converts [frames] frames read from [data] to 16-bit stereo little-endian PCM. */
    fun toStereo16(data: Pointer, frames: Int): ByteArray {
        val src = ByteBuffer.wrap(data.getByteArray(0, frames * frameBytes)).order(ByteOrder.LITTLE_ENDIAN)
        val out = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in 0 until frames) {
            val left = sample(src, f * frameBytes)
            val right = if (channels > 1) sample(src, f * frameBytes + bits / 8) else left
            out.putShort(left).putShort(right)
        }
        return out.array()
    }

    private fun sample(b: ByteBuffer, at: Int): Short = when {
        float -> (b.getFloat(at).coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        bits == 16 -> b.getShort(at)
        bits == 24 -> ((b.get(at + 2).toInt() shl 8) or (b.get(at + 1).toInt() and 0xFF)).toShort()
        else -> (b.getInt(at) shr 16).toShort()
    }

    companion object {
        private const val FORMAT_FLOAT = 3
        private const val FORMAT_EXTENSIBLE = 0xFFFE

        fun read(p: Pointer): MixFormat {
            var tag = p.getShort(0).toInt() and 0xFFFF
            if (tag == FORMAT_EXTENSIBLE) tag = p.getShort(24).toInt() and 0xFFFF // first bytes of the SubFormat
            return MixFormat(p.getInt(4), p.getShort(2).toInt(), p.getShort(14).toInt(), tag == FORMAT_FLOAT)
        }
    }
}
