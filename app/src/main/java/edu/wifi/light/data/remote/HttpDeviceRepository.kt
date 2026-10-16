package edu.wifi.light.data.remote

import edu.wifi.light.core.model.*
import edu.wifi.light.core.protocol.ProtocolCodec
import edu.wifi.light.domain.DeviceRepository
import edu.wifi.light.domain.Validation
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HttpDeviceRepository(private val host: String, private val port: Int, private val client: OkHttpClient) : DeviceRepository {
    internal var onClose: (() -> Unit)? = null
    companion object {
        fun clientBuilder() = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS).writeTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
            .proxy(java.net.Proxy.NO_PROXY)
    }
    private fun url(path: String) = HttpUrl.Builder().scheme("http").host(host).port(port)
        .addPathSegments("api/v1/$path").build()

    private suspend fun request(request: Request, write: Boolean = false): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isActive) return
                val timeout = e is InterruptedIOException
                continuation.resumeWithException(DeviceException(if (timeout) "TIMEOUT" else "NETWORK",
                    if (write) "未收到控制确认，结果未知，请重新读取设备状态" else if (timeout) "设备响应超时，请核对网络和地址" else "无法连接设备，请核对热点、地址和端口", write).apply { initCause(e) })
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        val body = it.body ?: throw DeviceException("INVALID_RESPONSE", "设备响应为空", write)
                        if (body.contentLength() > 16384) throw DeviceException("INVALID_RESPONSE", "设备响应超过 16 KiB", write)
                        val source = body.source()
                        source.request(16385)
                        if (source.buffer.size > 16384) throw DeviceException("INVALID_RESPONSE", "设备响应超过 16 KiB", write)
                        val text = source.readUtf8()
                        if (!it.isSuccessful) {
                            val error = ProtocolCodec.error(text, it.code)
                            throw DeviceException(error.code, error.message, write && it.code >= 500)
                        }
                        if (it.header("Content-Type")?.substringBefore(';')?.trim() != "application/json") {
                            throw DeviceException("INVALID_RESPONSE", "设备未返回 JSON", write)
                        }
                        if (continuation.isActive) continuation.resume(text)
                    }
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(
                        if (e is DeviceException) e else DeviceException("INVALID_RESPONSE", "响应读取失败", write))
                }
            }
        })
    }

    private fun get(path: String) = Request.Builder().url(url(path)).header("Accept", "application/json").header("Cache-Control", "no-store").build()
    override suspend fun describe() = ProtocolCodec.description(request(get("device")))
    override suspend fun readState() = ProtocolCodec.snapshot(request(get("state")))
    override suspend fun setProperty(key: PropertyKey, value: PropertyValue, expectedBootId: String, requestId: String): WriteReceipt {
        Validation.id(key.resourceId); Validation.id(key.propertyId)
        val body = ProtocolCodec.write(requestId, expectedBootId, value)
        if (body.toByteArray().size > 1024) throw DeviceException("INVALID_REQUEST", "命令超过长度限制")
        val text = request(Request.Builder().url(url("resources/${key.resourceId}/properties/${key.propertyId}"))
            .header("Accept", "application/json").put(body.toRequestBody("application/json; charset=utf-8".toMediaType())).build(), true)
        val receipt = try { ProtocolCodec.receipt(text) } catch (_: DeviceException) {
            throw DeviceException("INVALID_RESPONSE", "控制响应无法校验，结果未知，请读取状态", true)
        }
        if (receipt.requestId != requestId || receipt.key != key || receipt.bootId != expectedBootId || receipt.value != value) {
            throw DeviceException("INVALID_RESPONSE", "控制响应与请求不一致，结果未知，请读取状态", true)
        }
        return receipt
    }
    override fun close() { client.dispatcher.cancelAll(); client.connectionPool.evictAll(); onClose?.invoke(); onClose = null }
}
