package edu.wifi.light.core.protocol

import edu.wifi.light.core.model.*
import edu.wifi.light.domain.Validation
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Serializable
internal data class DeviceDto(val protocolVersion: Int, val deviceId: String, val bootId: String, val name: String, val firmwareVersion: String, val resources: List<ResourceDto>)
@Serializable
internal data class ResourceDto(val id: String, val name: String, val kind: String, val properties: List<PropertyDto>)
@Serializable
internal data class PropertyDto(val id: String, val name: String, val type: String, val readable: Boolean, val writable: Boolean, val min: Double? = null, val max: Double? = null, val step: Double? = null, val unit: String = "")
@Serializable
internal data class StateDto(val protocolVersion: Int, val deviceId: String, val bootId: String, val revision: Long, val resources: List<StateResourceDto>)
@Serializable
internal data class StateResourceDto(val id: String, val values: Map<String, JsonElement>, val quality: Map<String, String>)
@Serializable
internal data class ReceiptDto(val protocolVersion: Int, val requestId: String, val deviceId: String, val bootId: String, val resourceId: String, val propertyId: String, val value: JsonElement, val revision: Long)
@Serializable
internal data class WriteDto(val requestId: String, val expectedBootId: String, val value: JsonElement)
@Serializable
internal data class ErrorDto(val protocolVersion: Int, val requestId: String? = null, val error: ErrorDetail)
@Serializable
internal data class ErrorDetail(val code: String, val message: String)

object ProtocolCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private fun version(value: Int) { if (value != 1) throw DeviceException("VERSION_MISMATCH", "设备协议版本 $value 暂不支持") }
    private fun metadata(deviceId: String, bootId: String, revision: Long = 0) {
        require(deviceId.isNotBlank() && bootId.isNotBlank() && revision >= 0) { "设备身份或状态版本缺失" }
    }
    private inline fun <T> parse(block: () -> T): T = try { block() } catch (e: DeviceException) {
        throw e
    } catch (_: Exception) { throw DeviceException("INVALID_RESPONSE", "设备响应格式或字段不合法") }

    fun description(text: String): DeviceDescription = parse {
        val dto = json.decodeFromString<DeviceDto>(text)
        version(dto.protocolVersion); metadata(dto.deviceId, dto.bootId)
        require(dto.resources.size <= 8 && dto.resources.map { it.id }.distinct().size == dto.resources.size)
        val resources = dto.resources.map { r ->
            Validation.id(r.id)
            require(r.properties.size in 1..4 && r.properties.map { it.id }.distinct().size == r.properties.size)
            ResourceDescription(r.id, r.name, r.kind, r.properties.map { p ->
                Validation.id(p.id)
                require(p.readable && (p.type != "string" || !p.writable))
                if (p.writable && p.type in setOf("integer", "number")) {
                    require(p.min?.isFinite() == true && p.max?.isFinite() == true && p.step?.isFinite() == true)
                    require(p.min!! <= p.max!! && p.step!! > 0)
                    if (p.type == "integer") require(listOf(p.min, p.max, p.step).all { it == kotlin.math.round(it!!) && kotlin.math.abs(it) <= 9_007_199_254_740_991.0 })
                }
                PropertyDescription(p.id, p.name, p.type, p.readable, p.writable, p.min, p.max, p.step, p.unit)
            })
        }
        DeviceDescription(dto.deviceId, dto.bootId, dto.name, dto.firmwareVersion, resources)
    }

    fun snapshot(text: String): DeviceSnapshot = parse {
        val dto = json.decodeFromString<StateDto>(text)
        version(dto.protocolVersion); metadata(dto.deviceId, dto.bootId, dto.revision)
        require(dto.resources.size <= 8 && dto.resources.map { it.id }.distinct().size == dto.resources.size)
        val readings = mutableMapOf<PropertyKey, Reading>()
        dto.resources.forEach { resource ->
            Validation.id(resource.id); require(resource.values.size <= 4)
            resource.values.forEach { (id, raw) ->
                Validation.id(id)
                val quality = resource.quality[id] ?: error("quality missing")
                require(quality in setOf("good", "stale", "unavailable"))
                val value = decodeValue(raw)
                require((value != PropertyValue.Unavailable) || quality == "unavailable")
                readings[PropertyKey(resource.id, id)] = Reading(value, quality)
            }
        }
        DeviceSnapshot(dto.deviceId, dto.bootId, dto.revision, readings)
    }

    fun receipt(text: String): WriteReceipt = parse {
        val dto = json.decodeFromString<ReceiptDto>(text)
        version(dto.protocolVersion); metadata(dto.deviceId, dto.bootId, dto.revision)
        Validation.id(dto.resourceId); Validation.id(dto.propertyId)
        WriteReceipt(dto.requestId, dto.deviceId, dto.bootId, PropertyKey(dto.resourceId, dto.propertyId), decodeValue(dto.value), dto.revision)
    }

    fun write(requestId: String, bootId: String, value: PropertyValue): String = json.encodeToString(WriteDto(requestId, bootId, encodeValue(value)))
    fun error(text: String, status: Int): DeviceException = try {
        val dto = json.decodeFromString<ErrorDto>(text)
        version(dto.protocolVersion)
        DeviceException(dto.error.code, dto.error.message)
    } catch (_: Exception) { DeviceException("HTTP_ERROR", "设备返回 HTTP $status") }

    private fun decodeValue(raw: JsonElement): PropertyValue {
        if (raw == JsonNull) return PropertyValue.Unavailable
        val primitive = raw as? JsonPrimitive ?: error("value must be primitive")
        if (primitive.isString) { require(primitive.content.length <= 128); return PropertyValue.TextValue(primitive.content) }
        primitive.booleanOrNull?.let { return PropertyValue.BooleanValue(it) }
        val number = primitive.doubleOrNull ?: error("invalid value")
        require(number.isFinite())
        return PropertyValue.NumberValue(number)
    }

    private fun encodeValue(value: PropertyValue): JsonElement = when (value) {
        is PropertyValue.BooleanValue -> JsonPrimitive(value.value)
        is PropertyValue.NumberValue -> { require(value.value.isFinite()); JsonPrimitive(value.value) }
        is PropertyValue.TextValue -> JsonPrimitive(value.value)
        PropertyValue.Unavailable -> JsonNull
    }
}
