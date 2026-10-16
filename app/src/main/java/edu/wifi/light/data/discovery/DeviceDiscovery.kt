package edu.wifi.light.data.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import edu.wifi.light.domain.Validation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiscoveredDevice(val name: String, val host: String, val port: Int)
data class DiscoveryState(val searching: Boolean = false, val devices: List<DiscoveredDevice> = emptyList(), val message: String = "下位机需广播 _wifi-control._tcp 服务")

/** NSD is available on every supported OS. It discovers services, not WiFi access points. */
@Suppress("DEPRECATION")
class DeviceDiscovery(context: Context) {
    private val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val handler = Handler(Looper.getMainLooper())
    private val mutable = MutableStateFlow(DiscoveryState())
    val state = mutable.asStateFlow()
    private var listener: NsdManager.DiscoveryListener? = null
    private var generation = 0
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private val visibleNames = mutableSetOf<String>()
    private val timeout = Runnable { stop() }

    fun start() {
        if (listener != null) return
        val current = ++generation
        visibleNames.clear(); queue.clear()
        mutable.value = DiscoveryState(true, message = "发现中（20 秒）；未找到时仍可手动输入地址")
        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onServiceFound(service: NsdServiceInfo) { handler.post {
                if (generation == current && listener != null && visibleNames.add(service.serviceName)) {
                    if (queue.size < 32) queue.addLast(service)
                    resolveNext()
                }
            } }
            override fun onServiceLost(service: NsdServiceInfo) { handler.post {
                if (generation == current) {
                    visibleNames.remove(service.serviceName)
                    mutable.value = mutable.value.copy(devices = mutable.value.devices.filterNot { it.name == service.serviceName })
                }
            } }
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) { handler.post {
                if (generation == current) { stop(); mutable.value = mutable.value.copy(message = "发现失败（$code），请检查局域网权限，或手动输入地址") }
            } }
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
        }
        listener = discovery
        try {
            manager.discoverServices("_wifi-control._tcp.", NsdManager.PROTOCOL_DNS_SD, discovery)
            handler.postDelayed(timeout, 20_000)
        } catch (_: Exception) {
            stop(); mutable.value = mutable.value.copy(message = "无法启动发现，请检查权限与系统网络，或手动连接")
        }
    }

    private fun resolveNext() {
        if (resolving || queue.isEmpty() || listener == null) return
        val service = queue.removeFirst()
        val current = generation
        resolving = true
        val resolver = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, code: Int) { handler.post { resolving = false; resolveNext() } }
            override fun onServiceResolved(info: NsdServiceInfo) { handler.post {
                resolving = false
                val address = info.host
                if (current == generation && listener != null && info.serviceName in visibleNames && address != null && Validation.isLocal(address) && info.port in 1..65535) {
                    val device = DiscoveredDevice(info.serviceName, address.hostAddress.orEmpty(), info.port)
                    mutable.value = mutable.value.copy(devices = (mutable.value.devices.filterNot { it.name == device.name } + device).take(32))
                }
                resolveNext()
            } }
        }
        try { manager.resolveService(service, resolver) } catch (_: Exception) { resolving = false; resolveNext() }
    }

    fun stop() {
        handler.removeCallbacks(timeout)
        listener?.let { runCatching { manager.stopServiceDiscovery(it) } }
        listener = null; generation++; queue.clear()
        mutable.value = mutable.value.copy(searching = false, message = "发现已停止；请选择已发现设备，或手动输入地址")
    }
}
