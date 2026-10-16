package edu.wifi.light

import edu.wifi.light.core.model.*
import edu.wifi.light.data.mock.*
import edu.wifi.light.feature.control.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ControlViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    private val key = PropertyKey("led.main", "on")

    @Test fun onlyConfirmedWritesChangeCurrentState() = runTest(dispatcher) {
        val mock = MockDeviceRepository()
        val vm = ControlViewModel(ConnectionConfig(mock = true), { mock })
        vm.connect(); runCurrent(); advanceTimeBy(400); runCurrent()
        vm.setForeground(true); advanceTimeBy(200); runCurrent()
        vm.write(key, PropertyValue.BooleanValue(true))
        assertEquals(PropertyValue.BooleanValue(false), vm.state.value.snapshot?.readings?.get(key)?.value)
        assertEquals(PropertyValue.BooleanValue(true), vm.state.value.pending[key])
        advanceTimeBy(200); runCurrent()
        assertEquals(PropertyValue.BooleanValue(true), vm.state.value.snapshot?.readings?.get(key)?.value)
        assertTrue(vm.state.value.pending.isEmpty()); vm.disconnect()
    }
    @Test fun appliedTimeoutRecoversByReadingWithoutReversingAction() = runTest(dispatcher) {
        val mock = MockDeviceRepository()
        val vm = ControlViewModel(ConnectionConfig(mock = true), { mock })
        vm.connect(); runCurrent(); advanceTimeBy(400); runCurrent()
        vm.setForeground(true); advanceTimeBy(200); runCurrent()
        vm.setFault(MockFault.APPLIED_TIMEOUT); vm.write(key, PropertyValue.BooleanValue(true))
        advanceTimeBy(400); runCurrent()
        assertEquals(PropertyValue.BooleanValue(true), vm.state.value.snapshot?.readings?.get(key)?.value)
        assertTrue(vm.state.value.propertyErrors[key]!!.contains("重新读取"))
        assertEquals(ConnectionStatus.CONNECTED, vm.state.value.status); vm.disconnect()
    }
    @Test fun disconnectDuringReadDoesNotLeakMutexIntoNextSession() = runTest(dispatcher) {
        val vm = ControlViewModel(ConnectionConfig(mock = true), { MockDeviceRepository() })
        vm.connect(); runCurrent(); advanceTimeBy(400); runCurrent()
        vm.refresh(); vm.disconnect(); runCurrent()
        vm.connect(); runCurrent(); advanceTimeBy(400); runCurrent()
        assertEquals(ConnectionStatus.CONNECTED, vm.state.value.status); vm.disconnect()
    }
    @Test fun deviceRebootReloadsBootAndDefaultOutput() = runTest(dispatcher) {
        val mock = MockDeviceRepository()
        val vm = ControlViewModel(ConnectionConfig(mock = true), { mock })
        vm.connect(); runCurrent(); advanceTimeBy(400); runCurrent()
        val oldBoot = vm.state.value.device?.bootId
        vm.rebootMock(); advanceTimeBy(500); runCurrent()
        assertNotEquals(oldBoot, vm.state.value.device?.bootId)
        assertEquals(PropertyValue.BooleanValue(false), vm.state.value.snapshot?.readings?.get(key)?.value); vm.disconnect()
    }
    @Test fun staleSnapshotCannotOverwriteConfirmedWrite() = runTest(dispatcher) {
        val mock = MockDeviceRepository()
        val old = mock.readState()
        var reads = 0
        val repository = object : edu.wifi.light.domain.DeviceRepository by mock {
            override suspend fun readState(): DeviceSnapshot = if (++reads >= 3) old else mock.readState()
        }
        val vm = ControlViewModel(ConnectionConfig(mock = true), { repository })
        vm.connect(); runCurrent(); advanceTimeBy(400); runCurrent()
        vm.setForeground(true); advanceTimeBy(200); runCurrent()
        vm.write(key, PropertyValue.BooleanValue(true)); advanceTimeBy(200); runCurrent()
        vm.refresh(); runCurrent()
        assertEquals(PropertyValue.BooleanValue(true), vm.state.value.snapshot?.readings?.get(key)?.value)
        assertEquals(1L, vm.state.value.snapshot?.revision); vm.disconnect()
    }
}
