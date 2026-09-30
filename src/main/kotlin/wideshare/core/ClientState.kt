package wideshare.core

sealed interface ClientState {
    data object Searching : ClientState
    data class Connecting(val server: String) : ClientState
    data class Pairing(val server: String) : ClientState
    data class Connected(val server: String, val hasControl: Boolean) : ClientState
    data class Failed(val reason: String) : ClientState
}
