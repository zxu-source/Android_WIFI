package edu.wifi.light.feature.control

import edu.wifi.light.core.model.*

object SnapshotRules {
    fun validate(description: DeviceDescription, snapshot: DeviceSnapshot) {
        description.resources.forEach { r -> r.properties.filter { it.supported }.forEach property@{ p ->
            val reading = snapshot.readings[PropertyKey(r.id, p.id)] ?: return@property
            if (reading.quality == "unavailable" && reading.value == PropertyValue.Unavailable) return@property
            val valid = when (p.type) {
                "boolean" -> reading.value is PropertyValue.BooleanValue
                "number" -> reading.value is PropertyValue.NumberValue
                "integer" -> reading.value is PropertyValue.NumberValue && reading.value.value == kotlin.math.round(reading.value.value)
                "string" -> reading.value is PropertyValue.TextValue
                else -> true
            }
            if (!valid) throw DeviceException("INVALID_RESPONSE", "属性 ${r.id}.${p.id} 的回报类型与描述不一致")
        } }
    }
}
