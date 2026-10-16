package edu.wifi.light.domain

import edu.wifi.light.core.model.*

interface DeviceRepository {
    suspend fun describe(): DeviceDescription
    suspend fun readState(): DeviceSnapshot
    suspend fun setProperty(key: PropertyKey, value: PropertyValue, expectedBootId: String, requestId: String): WriteReceipt
    fun close()
}
