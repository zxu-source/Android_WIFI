package edu.wifi.light

import edu.wifi.light.core.model.*
import edu.wifi.light.data.remote.HttpDeviceRepository
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class HttpRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: HttpDeviceRepository
    @Before fun setup() {
        server = MockWebServer(); server.start()
        repo = HttpDeviceRepository("localhost", server.port, HttpDeviceRepository.clientBuilder().callTimeout(400, TimeUnit.MILLISECONDS).build())
    }
    @After fun cleanup() { repo.close(); server.shutdown() }
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Test fun getContract() = runBlocking {
        server.enqueue(json(fixture("device.json"))); server.enqueue(json(fixture("state.json")))
        assertEquals("esp8266-demo-01", repo.describe().deviceId)
        assertEquals(12L, repo.readState().revision)
        assertEquals("/api/v1/device", server.takeRequest().path)
        assertEquals("/api/v1/state", server.takeRequest().path)
    }
    @Test fun explicitWriteContract() = runBlocking {
        server.enqueue(json(fixture("receipt.json")))
        val result = repo.setProperty(PropertyKey("led.main", "on"), PropertyValue.BooleanValue(true), "boot-8f21", "b294c92a-02e2-4b4f-b44e-254908bdcbf3")
        assertEquals(PropertyValue.BooleanValue(true), result.value)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/resources/led.main/properties/on", request.path)
        assertTrue(request.body.readUtf8().contains("\"value\":true"))
    }
    @Test fun writeTimeoutIsUnknownAndNotRetried() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        try { repo.setProperty(PropertyKey("led.main", "on"), PropertyValue.BooleanValue(true), "boot-8f21", "request"); fail("Expected timeout") }
        catch (e: DeviceException) { assertTrue(e.outcomeUnknown); assertEquals("TIMEOUT", e.code) }
        assertEquals(1, server.requestCount)
    }
    @Test fun malformedWriteReceiptIsUnknown() = runBlocking {
        server.enqueue(json("{}"))
        try { repo.setProperty(PropertyKey("led.main", "on"), PropertyValue.BooleanValue(true), "boot-8f21", "request"); fail() }
        catch (e: DeviceException) { assertTrue(e.outcomeUnknown) }
    }
    @Test fun redirectsAreNotFollowed() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "http://example.com"))
        try { repo.describe(); fail() } catch (e: DeviceException) { assertEquals("HTTP_ERROR", e.code) }
        assertEquals(1, server.requestCount)
    }
    @Test fun oversizedAndInvalidResponsesAreRejected() = runBlocking {
        server.enqueue(json(" ".repeat(16385)))
        try { repo.readState(); fail() } catch (e: DeviceException) { assertEquals("INVALID_RESPONSE", e.code) }
        server.enqueue(json("not-json"))
        try { repo.readState(); fail() } catch (e: DeviceException) { assertEquals("INVALID_RESPONSE", e.code) }
    }
    @Test fun serverErrorsPreserveCode() = runBlocking {
        server.enqueue(json(fixture("error.json")).setResponseCode(422))
        try { repo.setProperty(PropertyKey("led.main", "on"), PropertyValue.BooleanValue(true), "boot-8f21", "request"); fail() }
        catch (e: DeviceException) { assertEquals("INVALID_VALUE", e.code); assertFalse(e.outcomeUnknown) }
    }
}
