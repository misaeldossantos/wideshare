package wideshare.core

/**
 * Chooses which server the client connects to: the explicit request, or, if [autoConnect], one already paired
 * (the preferred one, or the only one). Without an explicit request it never connects to unknown servers: pairing requires human confirmation.
 */
internal fun pickServer(servers: List<ServerInfo>, requested: String?, autoConnect: Boolean, trust: TrustStore, preferredId: String): ServerInfo? {
    requested?.let { wanted -> return servers.firstOrNull { it.id == wanted } }
    if (!autoConnect) return null
    val paired = servers.filter { trust.pskFor(it.id) != null }
    return paired.firstOrNull { it.id == preferredId } ?: paired.singleOrNull()
}
