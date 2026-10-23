package edu.wifi.light

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import edu.wifi.light.core.model.*
import edu.wifi.light.data.discovery.DiscoveryState
import edu.wifi.light.feature.control.*
import edu.wifi.light.ui.theme.WIFILightTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RefreshLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val key = PropertyKey("fan.desk", "speed")
    private fun initial(): ControlState {
        val property = PropertyDescription(key.propertyId, "风速", "number", true, true, 0.0, 100.0, 0.5, "%")
        val device = DeviceDescription("test", "boot", "测试设备", "test", listOf(ResourceDescription(key.resourceId, "桌面风扇", "range-output", listOf(property))))
        return ControlState(config = ConnectionConfig(mock = true), status = ConnectionStatus.CONNECTED, device = device,
            snapshot = DeviceSnapshot("test", "boot", 0, mapOf(key to Reading(PropertyValue.NumberValue(35.0), "good"))), lastSuccess = 1_700_000_000_000)
    }
    private fun display(state: State<ControlState>) {
        compose.setContent { WIFILightTheme { ControlScreen(state.value, DiscoveryState(), {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}) } }
    }
    @Test fun manualReadProgressDoesNotChangeConnectionCardBounds() {
        val state = mutableStateOf(initial()); display(state)
        val card = compose.onNodeWithTag("connection-card")
        val before = card.fetchSemanticsNode().boundsInRoot
        val slot = compose.onNodeWithTag("read-progress-slot").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("read-progress").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(refreshing = true) }
        compose.onNodeWithTag("read-progress").assertExists()
        assertEquals(before, card.fetchSemanticsNode().boundsInRoot)
        assertEquals(slot, compose.onNodeWithTag("read-progress-slot").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { state.value = state.value.copy(refreshing = false) }
        compose.onNodeWithTag("read-progress").assertDoesNotExist()
        assertEquals(before, card.fetchSemanticsNode().boundsInRoot)
    }
    @Test fun waitingForControlConfirmationDoesNotResizePeripheralCard() {
        val state = mutableStateOf(initial()); display(state)
        compose.onNodeWithTag("control-list").performScrollToNode(hasTestTag("resource:${key.resourceId}"))
        val card = compose.onNodeWithTag("resource:${key.resourceId}")
        val before = card.fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { state.value = state.value.copy(pending = mapOf(key to PropertyValue.NumberValue(40.5))) }
        compose.onNodeWithTag("pending:${key.resourceId}.${key.propertyId}").assertTextContains("等待设备确认：", substring = true)
        assertEquals(before, card.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { state.value = state.value.copy(pending = emptyMap()) }
        assertEquals(before, card.fetchSemanticsNode().boundsInRoot)
    }
    @Test fun repeatedSilentUpdatesPreserveDraftControlAndScrollPosition() {
        val state = mutableStateOf(initial()); display(state)
        compose.onNodeWithTag("control-list").performScrollToNode(hasSetTextAction())
        val input = compose.onNode(hasSetTextAction())
        input.performTextReplacement("40.5")
        val before = input.fetchSemanticsNode().boundsInRoot
        repeat(5) { index ->
            compose.runOnIdle { state.value = state.value.copy(lastSuccess = 1_700_000_002_000 + index * 2000L,
                snapshot = state.value.snapshot!!.copy(revision = index.toLong() + 1)) }
            input.assertTextContains("40.5").assertIsEnabled()
            compose.onNodeWithTag("${key.resourceId}.${key.propertyId}").assertIsEnabled()
            compose.onNodeWithTag("read-progress").assertDoesNotExist()
            assertEquals(before, input.fetchSemanticsNode().boundsInRoot)
        }
    }
}
