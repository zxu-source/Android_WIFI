package edu.wifi.light

import edu.wifi.light.core.model.*
import edu.wifi.light.core.protocol.ProtocolCodec
import edu.wifi.light.domain.DeviceRepository
import edu.wifi.light.feature.control.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RefreshSchedulingTest {
    private val dispatcher = StandardTestDispatcher()
    private val key = PropertyKey("led.main", "on")
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    private class Repository : DeviceRepository {
        var device = ProtocolCodec.description(fixture("device.json"))
        var snapshot = ProtocolCodec.snapshot(fixture("state.json"))
        var gate: CompletableDeferred<Unit>? = null
        var failRead = false
        var reads = 0
        val writes = mutableListOf<PropertyValue>()
        override suspend fun describe() = device
        override suspend fun readState(): DeviceSnapshot {
            reads++
            val wait = gate; gate = null; wait?.await()
            if (failRead) throw DeviceException("NETWORK", "模拟读取失败")
            return snapshot
        }
        override suspend fun setProperty(key: PropertyKey, value: PropertyValue, expectedBootId: String, requestId: String): WriteReceipt {
            writes += value
            snapshot = snapshot.copy(revision = snapshot.revision + 1, readings = snapshot.readings + (key to Reading(value, "good")))
            return WriteReceipt(requestId, device.deviceId, device.bootId, key, value, snapshot.revision)
        }
        override fun close() = Unit
    }
    private fun connect(repository: Repository): ControlViewModel {
        val model = ControlViewModel(ConnectionConfig(mock = true), { repository })
        model.setForeground(true); model.connect(); dispatcher.scheduler.runCurrent()
        assertEquals(ConnectionStatus.CONNECTED, model.state.value.status)
        return model
    }
    private fun TestScope.pausePoll(repository: Repository): CompletableDeferred<Unit> {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        advanceTimeBy(2000); runCurrent()
        return gate
    }
    private fun scenario(block: suspend TestScope.(Repository, ControlViewModel) -> Unit) = runTest(dispatcher) {
        val repository = Repository(); val model = connect(repository)
        try { block(repository, model) } finally { model.disconnect(); runCurrent() }
    }
    @Test fun periodicReadIsSilentAndRetainsConfirmedPanel() = scenario { repository, model ->
        val original = model.state.value.snapshot
        val gate = pausePoll(repository)
        assertEquals(2, repository.reads)
        assertFalse(model.state.value.refreshing)
        assertEquals(ConnectionStatus.CONNECTED, model.state.value.status)
        assertEquals(original, model.state.value.snapshot)
        gate.complete(Unit); runCurrent()
        assertFalse(model.state.value.refreshing)
        model.disconnect()
    }
    @Test fun foregroundResumeAlsoReadsSilently() = scenario { repository, model ->
        model.setForeground(false)
        val gate = CompletableDeferred<Unit>(); repository.gate = gate
        model.setForeground(true); runCurrent()
        assertEquals(2, repository.reads)
        assertFalse(model.state.value.refreshing)
        gate.complete(Unit); runCurrent(); model.disconnect()
    }
    @Test fun explicitReadStillShowsProgressUntilCompletion() = scenario { repository, model ->
        val gate = CompletableDeferred<Unit>(); repository.gate = gate
        model.refresh(); runCurrent()
        assertTrue(model.state.value.refreshing)
        gate.complete(Unit); runCurrent()
        assertFalse(model.state.value.refreshing)
        model.disconnect()
    }
    @Test fun controlDuringPollWaitsThenSendsExactlyOnce() = scenario { repository, model ->
        val gate = pausePoll(repository)
        model.write(key, PropertyValue.BooleanValue(true))
        assertEquals(PropertyValue.BooleanValue(true), model.state.value.pending[key])
        assertEquals(PropertyValue.BooleanValue(false), model.state.value.snapshot!!.readings[key]!!.value)
        assertTrue(repository.writes.isEmpty())
        model.write(key, PropertyValue.BooleanValue(false))
        model.refresh(); runCurrent()
        assertEquals(2, repository.reads)
        gate.complete(Unit); runCurrent()
        assertEquals(listOf(PropertyValue.BooleanValue(true)), repository.writes)
        assertEquals(PropertyValue.BooleanValue(true), model.state.value.snapshot!!.readings[key]!!.value)
        assertTrue(model.state.value.pending.isEmpty())
        model.disconnect()
    }
    @Test fun disconnectCancelsQueuedControlAndLeavesNextSessionUsable() = scenario { repository, model ->
        val gate = pausePoll(repository)
        model.write(key, PropertyValue.BooleanValue(true))
        model.disconnect(); gate.complete(Unit); runCurrent()
        assertTrue(repository.writes.isEmpty())
        assertTrue(model.state.value.pending.isEmpty())
        model.connect(); runCurrent()
        assertEquals(ConnectionStatus.CONNECTED, model.state.value.status)
        model.disconnect()
    }
    @Test fun backgroundingCancelsQueuedControlWithoutSending() = scenario { repository, model ->
        val gate = pausePoll(repository)
        model.write(key, PropertyValue.BooleanValue(true))
        model.setForeground(false); gate.complete(Unit); runCurrent()
        assertTrue(repository.writes.isEmpty())
        assertTrue(model.state.value.pending.isEmpty())
        model.disconnect()
    }
    @Test fun rebootDuringPollInvalidatesQueuedControl() = scenario { repository, model ->
        val gate = pausePoll(repository)
        model.write(key, PropertyValue.BooleanValue(true))
        repository.device = repository.device.copy(bootId = "new-boot")
        repository.snapshot = repository.snapshot.copy(bootId = "new-boot")
        gate.complete(Unit); runCurrent()
        assertTrue(repository.writes.isEmpty())
        assertTrue(model.state.value.propertyErrors[key]!!.contains("DEVICE_RESTARTED"))
        assertEquals("new-boot", model.state.value.device!!.bootId)
        model.disconnect()
    }
    @Test fun rapidForegroundResumeDoesNotReviveQueuedControl() = scenario { repository, model ->
        val gate = pausePoll(repository)
        model.write(key, PropertyValue.BooleanValue(true))
        model.setForeground(false); model.setForeground(true)
        gate.complete(Unit); runCurrent()
        assertTrue(repository.writes.isEmpty())
        assertTrue(model.state.value.pending.isEmpty())
        model.disconnect()
    }
    @Test fun capabilityChangeDuringPollInvalidatesQueuedControl() = scenario { repository, model ->
        val gate = pausePoll(repository)
        model.write(key, PropertyValue.BooleanValue(true))
        repository.device = repository.device.copy(capabilitiesRevision = 2)
        repository.snapshot = repository.snapshot.copy(capabilitiesRevision = 2, revision = repository.snapshot.revision + 1)
        gate.complete(Unit); runCurrent()
        assertTrue(repository.writes.isEmpty())
        assertTrue(model.state.value.propertyErrors[key]!!.contains("CAPABILITIES_CHANGED"))
        model.disconnect()
    }
    @Test fun failedPollDoesNotSendQueuedControl() = scenario { repository, model ->
        val gate = pausePoll(repository)
        model.write(key, PropertyValue.BooleanValue(true))
        repository.failRead = true; gate.complete(Unit); runCurrent()
        assertTrue(repository.writes.isEmpty())
        assertEquals(ConnectionStatus.RECOVERING, model.state.value.status)
        assertTrue(model.state.value.pending.isEmpty())
        model.disconnect()
    }
    @Test fun invalidRevisionRollbackWithNewCapabilitiesDoesNotSendQueuedControl() = scenario { repository, model ->
        val gate = pausePoll(repository)
        model.write(key, PropertyValue.BooleanValue(true))
        repository.device = repository.device.copy(capabilitiesRevision = 2)
        repository.snapshot = repository.snapshot.copy(capabilitiesRevision = 2, revision = 1)
        gate.complete(Unit); runCurrent()
        assertTrue(repository.writes.isEmpty())
        assertEquals(ConnectionStatus.RECOVERING, model.state.value.status)
        assertNull(model.state.value.device!!.capabilitiesRevision)
        model.disconnect()
    }
    @Test fun staleConfirmationDoesNotSendQueuedControl() = scenario { repository, model ->
        val gate = pausePoll(repository)
        model.write(key, PropertyValue.BooleanValue(true))
        repository.snapshot = repository.snapshot.copy(readings = repository.snapshot.readings + (key to Reading(PropertyValue.BooleanValue(false), "stale")))
        gate.complete(Unit); runCurrent()
        assertTrue(repository.writes.isEmpty())
        assertTrue(model.state.value.propertyErrors[key]!!.contains("STATE_UNAVAILABLE"))
        model.disconnect()
    }
}
