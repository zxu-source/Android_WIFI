package edu.wifi.light

import edu.wifi.light.core.model.*
import edu.wifi.light.core.protocol.ProtocolCodec
import edu.wifi.light.domain.DeviceRepository
import edu.wifi.light.feature.control.*
import edu.wifi.light.data.mock.MockDeviceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CapabilityRefreshTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    private class Repository : DeviceRepository {
        var device = ProtocolCodec.description(fixture("capability-list.json"))
        var state = ProtocolCodec.snapshot(fixture("capability-state.json"))
        val calls = mutableListOf<String>()
        override suspend fun describe(): DeviceDescription { calls += "device"; return device }
        override suspend fun readState(): DeviceSnapshot { calls += "state"; return state }
        override suspend fun setProperty(key: PropertyKey, value: PropertyValue, expectedBootId: String, requestId: String): WriteReceipt = error("Unexpected control request")
        override fun close() = Unit
    }

    @Test fun connectAutomaticallyLoadsCapabilitiesBeforeStateAndNeverWrites() = runTest(dispatcher) {
        val repository = Repository()
        val model = ControlViewModel(ConnectionConfig(mock = true), { repository })
        model.connect(); runCurrent()
        assertEquals(listOf("device", "state"), repository.calls)
        assertEquals(5, model.state.value.device!!.resources.size)
        assertEquals(ConnectionStatus.CONNECTED, model.state.value.status)
        assertNotNull(model.state.value.capabilitiesUpdatedAt)
        model.disconnect()
    }
    @Test fun changedCapabilityVersionAutomaticallyReloadsAndRemovesOldResources() = runTest(dispatcher) {
        val repository = Repository()
        val model = ControlViewModel(ConnectionConfig(mock = true), { repository })
        model.connect(); runCurrent(); repository.calls.clear()
        repository.device = repository.device.copy(capabilitiesRevision = 2, resources = repository.device.resources.drop(1))
        repository.state = repository.state.copy(capabilitiesRevision = 2, revision = 1, readings = repository.state.readings.filterKeys { it.resourceId != "led.main" })
        model.refresh(); runCurrent()
        assertEquals(listOf("state", "device", "state"), repository.calls)
        assertEquals(2L, model.state.value.device!!.capabilitiesRevision)
        assertFalse(model.state.value.device!!.resources.any { it.id == "led.main" })
        assertEquals(ConnectionStatus.CONNECTED, model.state.value.status)
        model.disconnect()
    }
    @Test fun reconnectUpdatesLegacyNamesWithoutRebootOrVersionField() = runTest(dispatcher) {
        val repository = Repository().apply { device = device.copy(capabilitiesRevision = null); state = state.copy(capabilitiesRevision = null) }
        val model = ControlViewModel(ConnectionConfig(mock = true), { repository })
        model.connect(); runCurrent(); repository.calls.clear()
        repository.device = repository.device.copy(resources = repository.device.resources.map { it.copy(name = "重新命名-${it.name}") })
        model.refresh(); runCurrent()
        assertEquals(listOf("state"), repository.calls)
        assertFalse(model.state.value.device!!.resources.any { it.name.startsWith("重新命名-") })
        repository.calls.clear()
        model.disconnect(); model.connect(); runCurrent()
        assertEquals(listOf("device", "state"), repository.calls)
        assertTrue(model.state.value.device!!.resources.all { it.name.startsWith("重新命名-") })
        model.disconnect()
    }
    @Test fun mismatchedCapabilitySnapshotDisablesWritesAndKeepsConfirmedPanel() = runTest(dispatcher) {
        val repository = Repository()
        val model = ControlViewModel(ConnectionConfig(mock = true), { repository })
        model.connect(); runCurrent()
        val original = model.state.value.device
        repository.state = repository.state.copy(capabilitiesRevision = 2, revision = 1)
        model.refresh(); runCurrent()
        assertEquals(ConnectionStatus.RECOVERING, model.state.value.status)
        assertEquals(original, model.state.value.device)
        assertTrue(model.state.value.error!!.contains("CAPABILITIES_CHANGED"))
        model.disconnect()
    }
    @Test fun emptyListConnectsWithoutInventingAnyControls() = runTest(dispatcher) {
        val repository = Repository().apply { device = device.copy(resources = emptyList()); state = state.copy(readings = emptyMap()) }
        val model = ControlViewModel(ConnectionConfig(mock = true), { repository })
        model.connect(); runCurrent()
        assertEquals(ConnectionStatus.CONNECTED, model.state.value.status)
        assertTrue(model.state.value.device!!.resources.isEmpty())
        model.disconnect()
    }
    @Test fun newMockPeripheralAppearsWithoutAnAppSpecificButton() = runTest(dispatcher) {
        val mock = MockDeviceRepository()
        val model = ControlViewModel(ConnectionConfig(mock = true), { mock })
        model.connect(); runCurrent(); advanceTimeBy(400); runCurrent()
        val count = model.state.value.device!!.resources.size
        model.addMockResource(); advanceTimeBy(500); runCurrent()
        assertEquals(count + 1, model.state.value.device!!.resources.size)
        assertEquals("床头灯", model.state.value.device!!.resources.last().name)
        model.disconnect()
    }
    @Test fun capabilityVersionsCannotRollBackDuringSameBoot() = runTest(dispatcher) {
        val repository = Repository().apply { device = device.copy(capabilitiesRevision = 2); state = state.copy(capabilitiesRevision = 2) }
        val model = ControlViewModel(ConnectionConfig(mock = true), { repository })
        model.connect(); runCurrent()
        repository.device = repository.device.copy(capabilitiesRevision = 1)
        repository.state = repository.state.copy(capabilitiesRevision = 1, revision = 1)
        model.refresh(); runCurrent()
        assertEquals(ConnectionStatus.RECOVERING, model.state.value.status)
        assertEquals(2L, model.state.value.device!!.capabilitiesRevision)
        model.disconnect()
    }
    @Test fun rebootResetsNewlyRegisteredMockOutputsToo() = runTest(dispatcher) {
        val mock = MockDeviceRepository()
        mock.addDemoResource()
        val device = mock.describe()
        mock.setProperty(PropertyKey("switch.bedside", "power"), PropertyValue.BooleanValue(true), device.bootId, "first")
        mock.setProperty(PropertyKey("fan.desk", "speed"), PropertyValue.NumberValue(50.0), device.bootId, "second")
        mock.reboot()
        val state = mock.readState()
        assertEquals(PropertyValue.BooleanValue(false), state.readings[PropertyKey("switch.bedside", "power")]!!.value)
        assertEquals(PropertyValue.NumberValue(0.0), state.readings[PropertyKey("fan.desk", "speed")]!!.value)
    }
}
