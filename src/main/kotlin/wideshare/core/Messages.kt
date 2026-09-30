package wideshare.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

enum class Side {
    LEFT, RIGHT, TOP, BOTTOM;

    val opposite: Side
        get() = when (this) {
            LEFT -> RIGHT
            RIGHT -> LEFT
            TOP -> BOTTOM
            BOTTOM -> TOP
        }
}

/** Neutral buttons: 0 left, 1 right, 2 middle, 3 back, 4 forward. */
object Buttons {
    const val LEFT = 0
    const val RIGHT = 1
    const val MIDDLE = 2
    const val BACK = 3
    const val FORWARD = 4
}

sealed interface Message {
    /** Client -> server, right after authentication. */
    data class Hello(val name: String, val width: Int, val height: Int) : Message

    /** Server -> client: the cursor enters through the client's [side] edge, at position [fraction] (0..1) along it. */
    data class Enter(val side: Side, val fraction: Float) : Message

    /** Client -> server: the cursor tried to leave through the [side] edge; [fraction] is the position along it. */
    data class Leave(val side: Side, val fraction: Float) : Message

    data class MouseMove(val dx: Int, val dy: Int) : Message
    data class MouseButton(val button: Int, val down: Boolean) : Message

    /** Scroll in units of 120 per wheel "click"; dx > 0 = right, dy > 0 = up. */
    data class Wheel(val dx: Int, val dy: Int) : Message

    /** Physical key, identified by its Linux evdev code. */
    data class Key(val code: Int, val down: Boolean) : Message

    /** Server -> client: control moved to another computer; release everything. */
    data object Release : Message
    data object Ping : Message

    /** During pairing: the user on this side confirmed (or refused) that the code matches. */
    data class PairDecision(val accepted: Boolean) : Message

    /** Server -> client: text copied on the server, to paste on the client. */
    data class Clipboard(val text: String) : Message

    /** Server -> client: sound playing on the server, 16-bit stereo little-endian PCM at [rate] Hz. */
    class Audio(val rate: Int, val pcm: ByteArray) : Message

    /** Starts a batch of files; [paste] = came from copy/paste (also goes to the clipboard), otherwise from a drag; [total] = bytes in the batch, for the progress. */
    data class FilesStart(val paste: Boolean, val total: Long) : Message

    /** Opens an entry of the batch: relative [path], using "/"; [size] = -1 for a folder. */
    data class FileBegin(val path: String, val size: Long) : Message

    /** A chunk of the file opened by [FileBegin]. */
    class FileData(val bytes: ByteArray) : Message
    data object FileEnd : Message
    data object FilesDone : Message

    /** Announces that there are files copied here ([summary] = names); they only follow if the other side answers with [FilesRequest]. */
    data class FilesOffer(val summary: String) : Message
    data object FilesRequest : Message

    /** A file drag is heading from the source computer to this one: show the drop zone ([summary] = what is being dragged). */
    data class DragStart(val summary: String) : Message

    /** The user dropped onto the zone: send the files that were being dragged. */
    data object DragDrop : Message
}

/** Limit of the copied text that is sent to the client, in bytes. */
const val MAX_CLIPBOARD = 1 shl 20

/** Limit of an audio packet, in bytes (fits in one channel frame). */
const val MAX_AUDIO = 16 * 1024

/** Limit of a file chunk, in bytes (fits in one channel frame). */
const val MAX_FILE_CHUNK = 32 * 1024

object Codec {
    fun encode(m: Message): ByteArray {
        val bytes = ByteArrayOutputStream()
        val o = DataOutputStream(bytes)
        when (m) {
            is Message.Hello -> { o.writeByte(1); o.writeUTF(m.name); o.writeInt(m.width); o.writeInt(m.height) }
            is Message.Enter -> { o.writeByte(2); o.writeByte(m.side.ordinal); o.writeFloat(m.fraction) }
            is Message.Leave -> { o.writeByte(3); o.writeByte(m.side.ordinal); o.writeFloat(m.fraction) }
            is Message.MouseMove -> { o.writeByte(4); o.writeInt(m.dx); o.writeInt(m.dy) }
            is Message.MouseButton -> { o.writeByte(5); o.writeByte(m.button); o.writeBoolean(m.down) }
            is Message.Wheel -> { o.writeByte(6); o.writeInt(m.dx); o.writeInt(m.dy) }
            is Message.Key -> { o.writeByte(7); o.writeShort(m.code); o.writeBoolean(m.down) }
            Message.Release -> o.writeByte(8)
            Message.Ping -> o.writeByte(9)
            is Message.PairDecision -> { o.writeByte(10); o.writeBoolean(m.accepted) }
            is Message.Clipboard -> { o.writeByte(11); m.text.toByteArray().let { o.writeInt(it.size); o.write(it) } }
            is Message.Audio -> { o.writeByte(12); o.writeInt(m.rate); o.writeInt(m.pcm.size); o.write(m.pcm) }
            is Message.FilesStart -> { o.writeByte(13); o.writeBoolean(m.paste); o.writeLong(m.total) }
            is Message.FileBegin -> { o.writeByte(14); o.writeUTF(m.path); o.writeLong(m.size) }
            is Message.FileData -> { o.writeByte(15); o.writeInt(m.bytes.size); o.write(m.bytes) }
            Message.FileEnd -> o.writeByte(16)
            Message.FilesDone -> o.writeByte(17)
            is Message.FilesOffer -> { o.writeByte(18); o.writeUTF(m.summary) }
            Message.FilesRequest -> o.writeByte(19)
            is Message.DragStart -> { o.writeByte(20); o.writeUTF(m.summary) }
            Message.DragDrop -> o.writeByte(21)
        }
        return bytes.toByteArray()
    }

    fun decode(data: ByteArray): Message {
        val i = DataInputStream(ByteArrayInputStream(data))
        return when (val type = i.readUnsignedByte()) {
            1 -> Message.Hello(i.readUTF(), i.readInt(), i.readInt())
            2 -> Message.Enter(Side.entries[i.readUnsignedByte()], i.readFloat())
            3 -> Message.Leave(Side.entries[i.readUnsignedByte()], i.readFloat())
            4 -> Message.MouseMove(i.readInt(), i.readInt())
            5 -> Message.MouseButton(i.readUnsignedByte(), i.readBoolean())
            6 -> Message.Wheel(i.readInt(), i.readInt())
            7 -> Message.Key(i.readUnsignedShort(), i.readBoolean())
            8 -> Message.Release
            9 -> Message.Ping
            10 -> Message.PairDecision(i.readBoolean())
            11 -> Message.Clipboard(String(i.readNBytes(i.readInt().also { if (it !in 0..MAX_CLIPBOARD) throw java.io.IOException("Texto grande demais") })))
            12 -> Message.Audio(i.readInt(), i.readNBytes(i.readInt().also { if (it !in 0..MAX_AUDIO) throw java.io.IOException(tr("err.audioTooBig")) }))
            13 -> Message.FilesStart(i.readBoolean(), i.readLong())
            14 -> Message.FileBegin(i.readUTF(), i.readLong())
            15 -> Message.FileData(i.readNBytes(i.readInt().also { if (it !in 0..MAX_FILE_CHUNK) throw java.io.IOException(tr("err.chunkTooBig")) }))
            16 -> Message.FileEnd
            17 -> Message.FilesDone
            18 -> Message.FilesOffer(i.readUTF())
            19 -> Message.FilesRequest
            20 -> Message.DragStart(i.readUTF())
            21 -> Message.DragDrop
            else -> throw java.io.IOException("Mensagem desconhecida: $type")
        }
    }
}
