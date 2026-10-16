package edu.wifi.light

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import edu.wifi.light.core.model.*
import edu.wifi.light.data.discovery.DiscoveryState
import edu.wifi.light.feature.control.*
import edu.wifi.light.ui.theme.WIFILightTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ControlScreenTest {
    @get:Rule val compose = createComposeRule()
    private val key = PropertyKey("relay.second", "on")
    private fun display(type: String = "boolean", writable: Boolean = true, onWrite: (PropertyKey, PropertyValue) -> Unit = { _, _ -> }) {
        val property = PropertyDescription("on", "继电器", type, true, writable)
        val device = DeviceDescription("test", "boot", "测试设备", "test", listOf(ResourceDescription(key.resourceId, "第二路继电器", "binary-output", listOf(property))))
        val state = ControlState(config = ConnectionConfig(mock = true), status = ConnectionStatus.CONNECTED, device = device,
            snapshot = DeviceSnapshot("test", "boot", 0, mapOf(key to Reading(PropertyValue.BooleanValue(false), "good"))))
        compose.setContent { WIFILightTheme { ControlScreen(state, DiscoveryState(), {}, {}, {}, {}, onWrite, {}, {}, {}, {}, {}, {}) } }
    }
    @Test fun dynamicSwitchDispatchesExplicitTargetAndKeepsConfirmedValue() {
        var sent: Pair<PropertyKey, PropertyValue>? = null
        display(onWrite = { key, value -> sent = key to value })
        compose.onNodeWithTag("relay.second.on").performScrollTo().performClick()
        assertEquals(key to PropertyValue.BooleanValue(true), sent)
        compose.onNodeWithText("确认状态：关闭 ").assertExists()
    }
    @Test fun readOnlyHasNoSwitch() {
        display(writable = false)
        compose.onNodeWithTag("relay.second.on").assertDoesNotExist()
        compose.onNodeWithText("只读").performScrollTo().assertIsDisplayed()
    }
    @Test fun unknownTypeIsVisibleButNotControllable() {
        display(type = "future-type")
        compose.onNodeWithTag("relay.second.on").assertDoesNotExist()
        compose.onNodeWithText("不支持的属性类型：future-type（不可操作）").performScrollTo().assertIsDisplayed()
    }
}
