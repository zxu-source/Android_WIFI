package edu.wifi.light.domain

import edu.wifi.light.core.model.*
import java.net.InetAddress
import kotlin.math.abs
import kotlin.math.round

object Validation {
    private val idPattern = Regex("[A-Za-z0-9._-]{1,48}")
    fun id(id: String) {
        if (!idPattern.matches(id)) throw DeviceException("INVALID_REQUEST", "外设或属性标识不合法")
    }

    fun value(property: PropertyDescription, value: PropertyValue) {
        if (!property.controllable) throw DeviceException("READ_ONLY", "该属性不可写或暂不支持")
        when (property.type) {
            "boolean" -> if (value !is PropertyValue.BooleanValue) invalid()
            "integer", "number" -> {
                val number = (value as? PropertyValue.NumberValue)?.value ?: invalid()
                val min = property.min ?: invalid()
                val max = property.max ?: invalid()
                val step = property.step ?: invalid()
                if (!number.isFinite() || number < min || number > max || step <= 0) invalid()
                if (property.type == "integer" && number != round(number)) invalid()
                val steps = (number - min) / step
                if (abs(steps - round(steps)) > 1e-6) invalid()
            }
            else -> invalid()
        }
    }

    private fun invalid(): Nothing = throw DeviceException("INVALID_VALUE", "目标值的类型、范围或步长不正确")

    /** Resolve once; HTTP pins these addresses to prevent a second DNS lookup changing the target. */
    fun localAddresses(host: String, port: Int): List<InetAddress> {
        if (port !in 1..65535) throw DeviceException("INVALID_ADDRESS", "端口应为 1–65535")
        if (host.isBlank() || host.length > 253 || host.any { it.isWhitespace() || it in "/@?#\\" }) {
            throw DeviceException("INVALID_ADDRESS", "请输入局域网 IP 或主机名，不要包含 URL 或路径")
        }
        val addresses = try { InetAddress.getAllByName(host).toList() } catch (_: Exception) {
            throw DeviceException("INVALID_ADDRESS", "无法解析设备地址，请核对 IP 和 WiFi 连接")
        }
        if (addresses.isEmpty() || addresses.any { !isLocal(it) }) {
            throw DeviceException("INVALID_ADDRESS", "实验模式仅支持私有局域网、链路本地或回环地址")
        }
        return addresses
    }

    fun isLocal(address: InetAddress): Boolean {
        val bytes = address.address
        val uniqueLocalV6 = bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc
        return !address.isAnyLocalAddress && !address.isMulticastAddress &&
            (address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress || uniqueLocalV6)
    }
}
