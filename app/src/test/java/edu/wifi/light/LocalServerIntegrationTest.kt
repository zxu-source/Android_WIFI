package edu.wifi.light

import edu.wifi.light.core.model.*
import edu.wifi.light.data.remote.HttpDeviceRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Opt-in integration with the independent PC simulator, never targets real hardware. */
class LocalServerIntegrationTest {
    @Test fun kotlinClientControlsPythonSimulator() = runBlocking {
        val port = System.getenv("WIFI_SIMULATOR_TEST_PORT")?.toIntOrNull()
        assumeTrue("Start the PC simulator and set WIFI_SIMULATOR_TEST_PORT to run integration", port != null)
        val repo = HttpDeviceRepository("127.0.0.1", port!!, HttpDeviceRepository.clientBuilder().build())
        try {
            val device = repo.describe()
            val key = PropertyKey("led.main", "on")
            val first = repo.setProperty(key, PropertyValue.BooleanValue(true), device.bootId, UUID.randomUUID().toString())
            val repeated = repo.setProperty(key, PropertyValue.BooleanValue(true), device.bootId, UUID.randomUUID().toString())
            assertEquals(first.revision, repeated.revision)
            assertEquals(PropertyValue.BooleanValue(true), repo.readState().readings[key]?.value)
            repo.setProperty(key, PropertyValue.BooleanValue(false), device.bootId, UUID.randomUUID().toString())
            assertEquals(PropertyValue.BooleanValue(false), repo.readState().readings[key]?.value)
        } finally { repo.close() }
    }
}
