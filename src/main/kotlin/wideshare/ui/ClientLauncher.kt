package wideshare.ui

import wideshare.core.ClientEngine
import wideshare.core.ClientState
import wideshare.platform.Platform

/** Creates and starts the client engine, wiring it to the controller's observable state. */
internal fun AppController.launchClient(): ClientEngine {
    val inj = Platform.createInjector()
    injector = inj
    val engine = ClientEngine(
        identity(), trust, inj,
        autoConnect = { autoConnect.value },
        sendAudio = { sendAudio.value },
        files = files.hooks,
        preferredServerId = { servers.value.firstOrNull { it.name == settings.lastServer }?.id ?: "" },
        log = this::log,
        onState = { st ->
            clientState.value = st
            if (st is ClientState.Connected && settings.lastServer != st.server) {
                settings.lastServer = st.server
                settings.save()
            }
        },
        onServers = { servers.value = it },
        onPairing = this::onPairing,
    )
    engine.start()
    return engine
}
