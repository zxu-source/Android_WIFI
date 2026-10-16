package edu.wifi.light

import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import edu.wifi.light.core.model.PropertyKey
import edu.wifi.light.core.model.PropertyValue
import edu.wifi.light.data.remote.NetworkRoute
import edu.wifi.light.data.remote.NetworkRouter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Opt-in: emulator talks to the PC simulator through the emulator's host alias. */
class AndroidHttpIntegrationTest {
    @Test fun deviceTransportControlsIndependentSimulator() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val port = arguments.getString("simulatorPort")?.toIntOrNull()
        assumeTrue("Pass simulatorPort to enable emulator HTTP integration", port != null)
        val context = instrumentation.targetContext
        val localPermission = "android.permission.ACCESS_LOCAL_NETWORK"
        if (Build.VERSION.SDK_INT >= 37) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, localPermission)
            assertEquals(PackageManager.PERMISSION_GRANTED, ContextCompat.checkSelfPermission(context, localPermission))
        }
        val host = arguments.getString("simulatorHost") ?: "10.0.2.2"
        val repository = NetworkRouter(context).create(host, port!!, NetworkRoute.SYSTEM)
        val key = PropertyKey("led.main", "on")
        try {
            val device = repository.describe()
            val original = repository.readState().readings.getValue(key).value
            try {
                val target = PropertyValue.BooleanValue(!(original as PropertyValue.BooleanValue).value)
                repository.setProperty(key, target, device.bootId, UUID.randomUUID().toString())
                assertEquals(target, repository.readState().readings.getValue(key).value)
            } finally {
                repository.setProperty(key, original, device.bootId, UUID.randomUUID().toString())
            }
        } finally { repository.close() }
    }
}
