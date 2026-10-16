package edu.wifi.light.core.model

data class DeviceDescription(
    val deviceId: String,
    val bootId: String,
    val name: String,
    val firmwareVersion: String,
    val resources: List<ResourceDescription>,
)

data class ResourceDescription(val id: String, val name: String, val kind: String, val properties: List<PropertyDescription>)

data class PropertyDescription(
    val id: String,
    val name: String,
    val type: String,
    val readable: Boolean,
    val writable: Boolean,
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val unit: String = "",
) {
    val supported: Boolean get() = type in setOf("boolean", "integer", "number", "string")
    val controllable: Boolean get() = supported && writable && type != "string"
}

sealed interface PropertyValue {
    data class BooleanValue(val value: Boolean) : PropertyValue
    data class NumberValue(val value: Double) : PropertyValue
    data class TextValue(val value: String) : PropertyValue
    data object Unavailable : PropertyValue
}

data class PropertyKey(val resourceId: String, val propertyId: String)
data class Reading(val value: PropertyValue, val quality: String)
data class DeviceSnapshot(val deviceId: String, val bootId: String, val revision: Long, val readings: Map<PropertyKey, Reading>)
data class WriteReceipt(val requestId: String, val deviceId: String, val bootId: String, val key: PropertyKey, val value: PropertyValue, val revision: Long)

class DeviceException(val code: String, override val message: String, val outcomeUnknown: Boolean = false) : Exception(message)
