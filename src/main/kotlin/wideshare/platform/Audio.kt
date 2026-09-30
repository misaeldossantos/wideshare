package wideshare.platform

/** A virtual sound output: what programs play goes to it (instead of the speakers) and arrives here as 16-bit stereo little-endian PCM. */
interface AudioCapture {
    /** Creates the output, makes it the default and delivers packets to [onChunk] (rate in Hz, data) on its own thread; throws if it cannot. */
    fun start(onChunk: (rate: Int, pcm: ByteArray) -> Unit)

    /** Restores the previous default output and removes the virtual one. */
    fun stop()
}
