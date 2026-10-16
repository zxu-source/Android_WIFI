package edu.wifi.light

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import edu.wifi.light.data.discovery.DeviceDiscovery
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class DeviceDiscoveryTest {
    @Test fun discoversLocalAdvertisedControlService() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= 37) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, "android.permission.ACCESS_LOCAL_NETWORK")
        }
        val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val registered = CompletableDeferred<String>()
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) { registered.complete(info.serviceName) }
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) { registered.completeExceptionally(IllegalStateException("NSD registration failed: $code")) }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) = Unit
        }
        val service = NsdServiceInfo().apply {
            serviceName = "WiFi-Control-Test-${UUID.randomUUID().toString().take(8)}"
            serviceType = "_wifi-control._tcp."
            port = 18081
        }
        val discovery = DeviceDiscovery(context)
        manager.registerService(service, NsdManager.PROTOCOL_DNS_SD, listener)
        try {
            val name = withTimeout(15_000) { registered.await() }
            instrumentation.runOnMainSync { discovery.start() }
            val result = withTimeout(18_000) { discovery.state.first { state -> state.devices.any { it.name == name } } }
            assertEquals(18081, result.devices.first { it.name == name }.port)
        } finally {
            instrumentation.runOnMainSync { discovery.stop() }
            runCatching { manager.unregisterService(listener) }
        }
    }
}
