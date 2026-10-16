package edu.wifi.light

import edu.wifi.light.core.model.*
import edu.wifi.light.core.protocol.ProtocolCodec
import edu.wifi.light.domain.Validation
import edu.wifi.light.feature.control.SnapshotRules
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

fun fixture(name: String): String = requireNotNull(object {}.javaClass.getResource("/$name")).readText()

class ProtocolTest {
    @Test fun sharedFixturesParseAndMatchTypes() {
        val device = ProtocolCodec.description(fixture("device.json"))
        val state = ProtocolCodec.snapshot(fixture("state.json"))
        SnapshotRules.validate(device, state)
        assertEquals(3, device.resources.size)
        assertEquals(PropertyValue.BooleanValue(false), state.readings[PropertyKey("led.main", "on")]?.value)
        assertEquals(13L, ProtocolCodec.receipt(fixture("receipt.json")).revision)
        assertEquals("INVALID_VALUE", ProtocolCodec.error(fixture("error.json"), 422).code)
    }
    @Test fun unavailableIsNotZero() {
        val reading = ProtocolCodec.snapshot(fixture("unavailable-state.json")).readings.values.single()
        assertEquals(PropertyValue.Unavailable, reading.value)
        assertEquals("unavailable", reading.quality)
    }
    @Test fun unknownTypeAndFieldSafelyDegrade() {
        val property = ProtocolCodec.description(fixture("unknown-device.json")).resources.single().properties.single()
        assertFalse(property.supported)
        assertFalse(property.controllable)
    }
    @Test fun incompatibleVersionFails() {
        assertEquals("VERSION_MISMATCH", assertThrows(DeviceException::class.java) {
            ProtocolCodec.description(fixture("device.json").replace("\"protocolVersion\": 1", "\"protocolVersion\": 2"))
        }.code)
    }
    @Test fun invalidMetadataAndDuplicateResourcesFail() {
        assertThrows(DeviceException::class.java) { ProtocolCodec.snapshot(fixture("state.json").replace("\"revision\": 12", "\"revision\": -1")) }
        assertThrows(DeviceException::class.java) { ProtocolCodec.description(fixture("device.json").replace("pwm.light", "led.main")) }
    }
    @Test fun wrongWireTypeIsNotConvertedToBoolean() {
        val device = ProtocolCodec.description(fixture("device.json"))
        val state = ProtocolCodec.snapshot(fixture("state.json").replace("\"on\":false", "\"on\":\"false\""))
        assertThrows(DeviceException::class.java) { SnapshotRules.validate(device, state) }
    }
    @Test fun validationRejectsUnsafeTargets() {
        val property = ProtocolCodec.description(fixture("device.json")).resources[1].properties.single()
        for (value in listOf(-1.0, 101.0, 0.5, Double.NaN)) assertThrows(DeviceException::class.java) { Validation.value(property, PropertyValue.NumberValue(value)) }
        Validation.value(property, PropertyValue.NumberValue(50.0))
        assertThrows(DeviceException::class.java) { Validation.id("../gpio") }
        assertThrows(DeviceException::class.java) { Validation.localAddresses("http://localhost", 80) }
        assertFalse(Validation.isLocal(InetAddress.getByName("8.8.8.8")))
        assertFalse(Validation.isLocal(InetAddress.getByName("0.0.0.0")))
        assertTrue(Validation.isLocal(InetAddress.getByName("192.168.43.10")))
        assertTrue(Validation.isLocal(InetAddress.getByName("::1")))
    }
}
