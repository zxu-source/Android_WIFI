package edu.wifi.light.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import edu.wifi.light.core.model.DeviceException
import edu.wifi.light.domain.Validation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class NetworkRoute(val label: String) { AUTO("自动（优先 WiFi）"), SYSTEM("系统路由（手机热点）"), WIFI("指定 WiFi 网络") }

class NetworkRouter(context: Context) {
    private val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    @Suppress("DEPRECATION")
    suspend fun create(host: String, port: Int, route: NetworkRoute): HttpDeviceRepository {
        val (network, client) = withContext(Dispatchers.IO) {
        val network = if (route == NetworkRoute.SYSTEM) null else manager.allNetworks.firstOrNull {
            manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        if (route == NetworkRoute.WIFI && network == null) throw DeviceException("NETWORK", "没有已连接的 WiFi 网络；手机作为热点时请选择系统路由")
        if (port !in 1..65535 || host.isBlank() || host.length > 253 || host.any { it.isWhitespace() || it in "/@?#\\" }) {
            throw DeviceException("INVALID_ADDRESS", "请输入主机/IP 和 1–65535 的端口，不要包含 URL")
        }
        val addresses = if (network != null) {
            try { network.getAllByName(host).toList() } catch (_: Exception) { throw DeviceException("INVALID_ADDRESS", "无法在 WiFi 网络解析设备地址") }
        } else Validation.localAddresses(host, port)
        if (addresses.isEmpty() || addresses.any { !Validation.isLocal(it) }) throw DeviceException("INVALID_ADDRESS", "只允许连接局域网设备地址")
        val builder = HttpDeviceRepository.clientBuilder().dns(object : okhttp3.Dns {
            override fun lookup(hostname: String) = if (hostname == host) addresses else throw java.net.UnknownHostException("Unexpected host")
        })
        network?.let { builder.socketFactory(it.socketFactory) }
        network to builder.build()
        }
        // Register only after returning to the caller's context, so cancelled DNS work cannot leak callbacks.
        if (network == null) return HttpDeviceRepository(host, port, client)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(lost: Network) {
                if (lost == network) { client.dispatcher.cancelAll(); client.connectionPool.evictAll() }
            }
        }
        manager.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), callback)
        return HttpDeviceRepository(host, port, client).also { it.onClose = { runCatching { manager.unregisterNetworkCallback(callback) } } }
    }
}
