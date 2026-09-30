package wideshare.ui

import wideshare.core.PairedPeer
import wideshare.core.Settings
import wideshare.core.TrustStore
import kotlinx.coroutines.flow.MutableStateFlow

/** Paired computers stored in [Settings.paired]; [paired] feeds the interface's list. */
class SettingsTrust(private val settings: Settings) : TrustStore {
    val paired = MutableStateFlow(list())

    private fun list() = settings.paired.map { (id, p) -> PairedInfo(id, p.name) }.sortedBy { it.name }

    override fun pskFor(peerId: String) = synchronized(settings) { settings.paired[peerId]?.psk }

    override fun trust(peerId: String, name: String, psk: ByteArray) {
        synchronized(settings) { settings.paired[peerId] = PairedPeer(name, psk); settings.save() }
        paired.value = list()
    }

    override fun forget(peerId: String) {
        synchronized(settings) { settings.paired.remove(peerId); settings.save() }
        paired.value = list()
    }
}
