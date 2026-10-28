package app.linkphone.core.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

data class DiscoveredPc(
    val host: String,
    val port: Int,
    val fingerprint: String?,
)

class PcDiscovery(context: Context) {
    private val appContext = context.applicationContext

    suspend fun find(pcId: String, timeoutMs: Long = 6_000): DiscoveredPc? =
        withContext(Dispatchers.IO) {
            val nsd = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
                ?: return@withContext null
            val found = CompletableDeferred<DiscoveredPc?>()
            val resolving = AtomicBoolean(false)

            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit
                override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
                override fun onDiscoveryStopped(serviceType: String) {
                    if (!found.isCompleted) found.complete(null)
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    if (!found.isCompleted) found.complete(null)
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (serviceInfo.serviceType?.startsWith(SERVICE_TYPE_PREFIX) != true) return
                    if (!resolving.compareAndSet(false, true)) return
                    runCatching {
                        nsd.resolveService(
                            serviceInfo,
                            object : NsdManager.ResolveListener {
                                override fun onResolveFailed(
                                    serviceInfo: NsdServiceInfo,
                                    errorCode: Int,
                                ) {
                                    resolving.set(false)
                                }

                                override fun onServiceResolved(resolved: NsdServiceInfo) {
                                    resolving.set(false)
                                    val advertisedId = resolved.attributes
                                        ?.get("id")
                                        ?.toString(Charsets.UTF_8)
                                    if (advertisedId != null && advertisedId != pcId) return
                                    val host = resolveHost(resolved) ?: return
                                    if (resolved.port !in 1..65535) return
                                    val fingerprint = resolved.attributes
                                        ?.get("fp")
                                        ?.toString(Charsets.UTF_8)
                                    if (!found.isCompleted) {
                                        found.complete(
                                            DiscoveredPc(host, resolved.port, fingerprint),
                                        )
                                    }
                                }
                            },
                        )
                    }.onFailure { resolving.set(false) }
                }
            }

            try {
                nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (_: Exception) {
                return@withContext null
            }

            try {
                withTimeoutOrNull(timeoutMs) { found.await() }
            } finally {
                runCatching { nsd.stopServiceDiscovery(listener) }
            }
        }

    private fun resolveHost(info: NsdServiceInfo): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            info.hostAddresses.firstOrNull()?.hostAddress
        } else {
            info.host?.hostAddress
        }

    private companion object {
        const val SERVICE_TYPE = "_linkphone._tcp."
        const val SERVICE_TYPE_PREFIX = "_linkphone._tcp"
    }
}
