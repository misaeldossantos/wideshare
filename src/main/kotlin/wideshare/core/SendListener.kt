package wideshare.core

/** Progress of a file send, to show to whoever is sending. */
interface SendListener {
    /** [sent] of [total] bytes already sent (called at most about ten times per second). */
    fun progress(sent: Long, total: Long)

    /** The send finished; [ok] = false if the connection dropped before the end. */
    fun finished(ok: Boolean)
}
