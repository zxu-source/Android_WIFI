package edu.wifi.light

import edu.wifi.light.core.model.*
import edu.wifi.light.core.protocol.ProtocolCodec
import edu.wifi.light.domain.Validation
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CapabilityListTest {
    private fun changed(transform: (JsonObject) -> JsonObject): String = transform(Json.parseToJsonElement(fixture("capability-list.json")).jsonObject).toString()

    @Test fun sameStandardTypeAllowsDifferentNamesAndIds() {
        val device = ProtocolCodec.description(fixture("capability-list.json"))
        val lights = device.resources.filter { it.kind == "binary-output" }
        assertEquals(listOf("客厅顶灯", "书桌台灯"), lights.map { it.name })
        assertEquals(2, lights.map { it.id }.distinct().size)
        assertTrue(lights.all { it.kindLabel == "二态开关" && it.properties.single().controllable })
        assertEquals(1L, device.capabilitiesRevision)
    }
    @Test fun fanDeclaresItsOwnRangePrecisionAndLabel() {
        val fan = ProtocolCodec.description(fixture("capability-list.json")).resources.first { it.id == "fan.desk" }
        assertEquals("范围调节", fan.kindLabel)
        val speed = fan.properties.single()
        assertEquals("风速", speed.name)
        Validation.value(speed, PropertyValue.NumberValue(40.5))
        assertThrows(DeviceException::class.java) { Validation.value(speed, PropertyValue.NumberValue(40.25)) }
        assertThrows(DeviceException::class.java) { Validation.value(speed, PropertyValue.NumberValue(101.0)) }
    }
    @Test fun legacyDevicesRemainCompatibleWithoutCapabilityVersion() {
        assertNull(ProtocolCodec.description(fixture("device.json")).capabilitiesRevision)
        assertNull(ProtocolCodec.snapshot(fixture("state.json")).capabilitiesRevision)
    }
    @Test fun stateCarriesTheSameCapabilityVersion() {
        assertEquals(ProtocolCodec.description(fixture("capability-list.json")).capabilitiesRevision,
            ProtocolCodec.snapshot(fixture("capability-state.json")).capabilitiesRevision)
    }
    @Test fun invalidNamesAreRejectedBeforeControlsAppear() {
        for (name in listOf("", "   ", "x".repeat(65), "控制\n器")) {
            assertThrows(DeviceException::class.java) { ProtocolCodec.description(changed { JsonObject(it + ("name" to JsonPrimitive(name))) }) }
        }
    }
    @Test fun emptyCapabilityListIsValid() {
        val device = ProtocolCodec.description(changed { JsonObject(it + ("resources" to JsonArray(emptyList()))) })
        assertTrue(device.resources.isEmpty())
    }
    @Test fun negativeCapabilityVersionsAreRejected() {
        assertThrows(DeviceException::class.java) { ProtocolCodec.description(changed { JsonObject(it + ("capabilitiesRevision" to JsonPrimitive(-1))) }) }
        assertThrows(DeviceException::class.java) { ProtocolCodec.snapshot(fixture("capability-state.json").replace("\"capabilitiesRevision\": 1", "\"capabilitiesRevision\": -1")) }
    }
    @Test fun namesDoNotAffectWriteAddressingAndUnknownKindsRemainGeneric() {
        val device = ProtocolCodec.description(fixture("capability-list.json").replace("客厅顶灯", "新名字").replace("binary-output", "custom-output"))
        assertEquals("led.main", device.resources.first().id)
        assertEquals("新名字", device.resources.first().name)
        assertEquals("自定义外设", device.resources.first().kindLabel)
        assertTrue(device.resources.first().properties.single().controllable)
    }
    @Test fun confirmedValuesMustRespectDeclaredRanges() {
        val device = ProtocolCodec.description(fixture("capability-list.json"))
        val state = ProtocolCodec.snapshot(fixture("capability-state.json"))
        val key = PropertyKey("fan.desk", "speed")
        val invalid = state.copy(readings = state.readings + (key to Reading(PropertyValue.NumberValue(100.5), "good")))
        assertThrows(DeviceException::class.java) { edu.wifi.light.feature.control.SnapshotRules.validate(device, invalid) }
    }
}
