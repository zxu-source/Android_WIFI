package edu.wifi.light

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class MainActivitySmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun mockControlSurvivesActivityRecreation() {
        compose.onNodeWithTag("mock-mode").performClick()
        compose.onNodeWithTag("connect").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("模拟外设控制器").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("led.main.on").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("确认状态：开启 ").fetchSemanticsNodes().isNotEmpty()
        }
        saveScreenshot("mock-control.png")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("led.main.on").performScrollTo().assertIsOn()
        compose.onNodeWithText("确认状态：开启 ").assertExists()
        saveScreenshot("mock-after-recreation.png")
        compose.onNodeWithTag("control-list").performScrollToNode(hasTestTag("connect"))
        compose.onNodeWithText("真实设备").performClick()
    }

    @Test fun capabilityChangeAddsANamedLightWithoutChangingTheApp() {
        compose.onNodeWithTag("mock-mode").performClick()
        compose.onNodeWithTag("connect").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("capability-summary").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("control-list").performScrollToNode(hasTestTag("mock-add-resource"))
        compose.onNodeWithTag("mock-add-resource").performClick()
        compose.onNodeWithTag("control-list").performScrollToNode(hasTestTag("capability-summary"))
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("capability-summary").fetchSemanticsNodes().any {
                it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.any { text -> text.text.contains("7 项外设") }
            }
        }
        compose.onNodeWithTag("control-list").performScrollToNode(hasTestTag("switch.bedside.power"))
        compose.onNodeWithTag("switch.bedside.power").assertIsOff().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("确认状态：开启 ").fetchSemanticsNodes().isNotEmpty() }
        saveScreenshot("capability-new-light.png")
        compose.onNodeWithTag("control-list").performScrollToNode(hasTestTag("connect"))
        compose.onNodeWithText("真实设备").performClick()
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "evidence")
        check(folder.isDirectory || folder.mkdirs())
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
