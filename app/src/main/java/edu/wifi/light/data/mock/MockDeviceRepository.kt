package edu.wifi.light.data.mock

import edu.wifi.light.core.model.*
import edu.wifi.light.domain.DeviceRepository
import edu.wifi.light.domain.Validation
import kotlinx.coroutines.delay

enum class MockFault(val label: String) {
    NONE("正常"), SLOW("延迟响应"), OFFLINE("模拟断线"), APPLIED_TIMEOUT("已执行但响应丢失"), DRIVER("驱动失败")
}

class MockDeviceRepository : DeviceRepository {
    var fault = MockFault.NONE
    private var closed = false
    private var generation = 1
    private var revision = 0L
    private val values = mutableMapOf(
        PropertyKey("led.main", "on") to Reading(PropertyValue.BooleanValue(false), "good"),
        PropertyKey("pwm.light", "level") to Reading(PropertyValue.NumberValue(35.0), "good"),
        PropertyKey("sensor.room", "temperature") to Reading(PropertyValue.NumberValue(24.5), "good"),
    )
    private val description get() = DeviceDescription("mock-device", "mock-boot-$generation", "模拟外设控制器", "mock-1.0", listOf(
        ResourceDescription("led.main", "主 LED", "binary-output", listOf(PropertyDescription("on", "开关", "boolean", true, true))),
        ResourceDescription("pwm.light", "调光灯", "range-output", listOf(PropertyDescription("level", "亮度", "integer", true, true, 0.0, 100.0, 1.0, "%"))),
        ResourceDescription("sensor.room", "室温", "sensor", listOf(PropertyDescription("temperature", "温度", "number", true, false, unit = "°C"))),
        ResourceDescription("future.device", "扩展兼容示例", "custom", listOf(PropertyDescription("color", "新类型", "color", true, true))),
    ))

    private suspend fun waitForDevice() {
        delay(if (fault == MockFault.SLOW) 1600 else 150)
        if (closed || fault == MockFault.OFFLINE) throw DeviceException("NETWORK", "模拟设备已断线，请恢复正常模式后重连")
    }
    override suspend fun describe(): DeviceDescription { waitForDevice(); return description }
    override suspend fun readState(): DeviceSnapshot {
        waitForDevice()
        return DeviceSnapshot(description.deviceId, description.bootId, revision, values.toMap())
    }
    override suspend fun setProperty(key: PropertyKey, value: PropertyValue, expectedBootId: String, requestId: String): WriteReceipt {
        waitForDevice()
        if (expectedBootId != description.bootId) throw DeviceException("DEVICE_RESTARTED", "模拟设备已重启，请刷新")
        val resource = description.resources.find { it.id == key.resourceId } ?: throw DeviceException("RESOURCE_NOT_FOUND", "外设不存在")
        val property = resource.properties.find { it.id == key.propertyId } ?: throw DeviceException("PROPERTY_NOT_FOUND", "属性不存在")
        Validation.value(property, value)
        if (fault == MockFault.DRIVER) throw DeviceException("DRIVER_ERROR", "模拟驱动执行失败")
        if (values[key]?.value != value) { values[key] = Reading(value, "good"); revision++ }
        if (fault == MockFault.APPLIED_TIMEOUT) {
            fault = MockFault.NONE
            throw DeviceException("TIMEOUT", "命令可能已执行，但响应丢失。正在重新读取状态。", true)
        }
        return WriteReceipt(requestId, description.deviceId, description.bootId, key, value, revision)
    }
    fun reboot() {
        generation++; revision = 0
        values[PropertyKey("led.main", "on")] = Reading(PropertyValue.BooleanValue(false), "good")
        values[PropertyKey("pwm.light", "level")] = Reading(PropertyValue.NumberValue(0.0), "good")
    }
    override fun close() { closed = true }
}
