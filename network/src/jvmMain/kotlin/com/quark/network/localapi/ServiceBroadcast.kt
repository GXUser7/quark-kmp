package com.quark.network.localapi

import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

/**
 * Announces the local api on the network as `quark` of type `_quarkaudio._tcp`,
 * with the attributes the Dart build prepared for it (`version`, `path`).
 * Only used when the api listens beyond the loopback address — announcing a
 * port nobody else can reach would only mislead clients.
 */
class ServiceBroadcast(private val port: Int) {
    private var dns: JmDNS? = null

    fun start() {
        if (dns != null) return
        dns = JmDNS.create(InetAddress.getLocalHost()).also { jmdns ->
            jmdns.registerService(
                ServiceInfo.create(
                    "_quarkaudio._tcp.local.",
                    "quark",
                    port,
                    0,
                    0,
                    mapOf("version" to LocalApiServer.API_VERSION.toString(), "path" to "/api"),
                )
            )
        }
    }

    fun stop() {
        dns?.let { runCatching { it.unregisterAllServices(); it.close() } }
        dns = null
    }
}
