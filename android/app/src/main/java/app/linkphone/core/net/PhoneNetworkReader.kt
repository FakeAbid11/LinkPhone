package app.linkphone.core.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address

/**
 * The IPv4 links this phone currently holds, for the pairing failure message.
 *
 * `allNetworks` rather than `registerDefaultNetworkCallback` on purpose: with a
 * full-tunnel VPN the default network *is* the tunnel, which would hide the Wi-Fi
 * subnet and report the opposite of what we need to know. Enumerating every
 * network keeps both, so the message can say "you are on 192.168.1.23/24 while a
 * VPN is active".
 *
 * `java.net.NetworkInterface` was the other candidate; it needs no permission but
 * Android filters its list for non-system apps and can omit VPN interfaces.
 */
class PhoneNetworkReader(context: Context) {
    private val appContext = context.applicationContext

    /** Never throws: a failed read is "no addresses", which classifies as UNKNOWN. */
    @Suppress("DEPRECATION")
    fun read(): PhoneNetwork = runCatching {
        val manager = appContext.getSystemService(ConnectivityManager::class.java)
            ?: return@runCatching PhoneNetwork()
        val lan = ArrayList<LocalAddress>(2)
        val tunnel = ArrayList<LocalAddress>(2)
        var vpn = false

        for (network in manager.allNetworks) {
            val isVpn = manager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            if (isVpn) vpn = true
            val link = manager.getLinkProperties(network) ?: continue
            for (linkAddress in link.linkAddresses) {
                val address = linkAddress.address
                if (address !is Inet4Address) continue
                if (address.isLoopbackAddress || address.isLinkLocalAddress) continue
                if (address.isAnyLocalAddress) continue
                val host = address.hostAddress ?: continue
                // Link-local never carries the PC; the desktop skips it too.
                if (host.startsWith("169.254.")) continue
                val entry = LocalAddress(host, linkAddress.prefixLength)
                val target = if (isVpn) tunnel else lan
                if (target.none { it.address == entry.address }) target += entry
            }
        }

        // LAN first, so `addresses.first()` is the most likely match and the copy
        // reads in the order a person would say it.
        PhoneNetwork(addresses = lan + tunnel, vpnActive = vpn)
    }.getOrElse { PhoneNetwork() }
}
