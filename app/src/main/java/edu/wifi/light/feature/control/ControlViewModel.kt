package edu.wifi.light.feature.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.wifi.light.core.model.*
import edu.wifi.light.data.mock.MockDeviceRepository
import edu.wifi.light.data.mock.MockFault
import edu.wifi.light.data.remote.NetworkRoute
import edu.wifi.light.domain.DeviceRepository
import edu.wifi.light.domain.Validation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import java.util.UUID

data class ConnectionConfig(val mock: Boolean = false, val host: String = "", val port: String = "80", val route: NetworkRoute = NetworkRoute.SYSTEM)
enum class ConnectionStatus(val label: String) { DISCONNECTED("未连接"), CONNECTING("连接中"), CONNECTED("已连接"), RECOVERING("等待恢复"), PERMISSION_REQUIRED("需要局域网权限") }
data class DiagnosticEvent(val time: Long, val message: String)
data class ControlState(
    val config: ConnectionConfig = ConnectionConfig(),
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val device: DeviceDescription? = null,
    val snapshot: DeviceSnapshot? = null,
    val pending: Map<PropertyKey, PropertyValue> = emptyMap(),
    val propertyErrors: Map<PropertyKey, String> = emptyMap(),
    val error: String? = null,
    val lastSuccess: Long? = null,
    val capabilitiesUpdatedAt: Long? = null,
    val refreshing: Boolean = false,
    val fault: MockFault = MockFault.NONE,
    val events: List<DiagnosticEvent> = emptyList(),
)

class ControlViewModel(
    initial: ConnectionConfig,
    private val createRepository: suspend (ConnectionConfig) -> DeviceRepository,
    private val saveConfig: (ConnectionConfig) -> Unit = {},
) : ViewModel() {
    private val mutable = MutableStateFlow(ControlState(config = initial))
    val state = mutable.asStateFlow()
    private var repository: DeviceRepository? = null
    private var session = 0L
    private var sessionJob: Job? = null
    private var scope: CoroutineScope? = null
    private var pollJob: Job? = null
    private val operations = Mutex()
    private var foreground = false

    fun configure(config: ConnectionConfig) {
        if (config == mutable.value.config) return
        disconnect()
        mutable.update { it.copy(config = config) }
        saveConfig(config)
    }
    fun permissionRequired() {
        disconnect()
        mutable.update { it.copy(status = ConnectionStatus.PERMISSION_REQUIRED, error = "连接和发现设备需要局域网权限，请授权后重试") }
    }
    fun connect() {
        disconnect()
        val id = session
        val config = mutable.value.config
        val job = SupervisorJob(viewModelScope.coroutineContext[Job])
        sessionJob = job
        scope = CoroutineScope(viewModelScope.coroutineContext + job)
        mutable.update { it.copy(status = ConnectionStatus.CONNECTING, error = null) }
        scope!!.launch {
            try {
                val repo = createRepository(config)
                if (id != session || !isActive) { repo.close(); return@launch }
                repository = repo
                operations.lock()
                try { synchronize(repo, reload = true) } finally { operations.unlock() }
                event("连接成功：${mutable.value.device?.name}")
                startPolling()
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if (id == session) {
                    report(e); repository?.close(); repository = null
                    mutable.update { it.copy(status = ConnectionStatus.DISCONNECTED) }
                }
            }
        }
    }

    fun disconnect() {
        session++; pollJob?.cancel(); pollJob = null; sessionJob?.cancel(); sessionJob = null
        repository?.close(); repository = null; scope = null
        mutable.update { it.copy(status = ConnectionStatus.DISCONNECTED, device = null, snapshot = null, pending = emptyMap(), propertyErrors = emptyMap(), refreshing = false, error = null, lastSuccess = null, capabilitiesUpdatedAt = null, fault = MockFault.NONE) }
    }

    fun setForeground(active: Boolean) {
        foreground = active
        if (active) { if (repository != null) { refresh(); startPolling() } }
        else { pollJob?.cancel(); pollJob = null }
    }
    private fun startPolling() {
        if (!foreground || repository == null || pollJob?.isActive == true) return
        pollJob = scope?.launch { while (isActive) { delay(2000); refresh() } }
    }
    fun refresh() {
        refreshInternal(reloadCapabilities = false)
    }
    fun reloadCapabilities() {
        refreshInternal(reloadCapabilities = true)
    }
    private fun refreshInternal(reloadCapabilities: Boolean) {
        val repo = repository ?: return
        if (!operations.tryLock()) return
        val id = session
        scope?.launch(start = CoroutineStart.UNDISPATCHED) {
            mutable.update { it.copy(refreshing = true) }
            try {
                var active = repo
                if (mutable.value.status == ConnectionStatus.RECOVERING && !mutable.value.config.mock) {
                    val replacement = createRepository(mutable.value.config)
                    if (id != session || !isActive) { replacement.close(); throw CancellationException() }
                    repo.close(); repository = replacement; active = replacement
                }
                synchronize(active, reload = reloadCapabilities || mutable.value.status != ConnectionStatus.CONNECTED)
            }
            catch (e: CancellationException) { throw e } catch (e: Exception) { if (id == session) report(e) }
            finally { if (id == session) mutable.update { it.copy(refreshing = false) }; operations.unlock() }
        }
    }

    private suspend fun synchronize(repo: DeviceRepository, reload: Boolean = false) {
        val id = session
        var loadedDescription = reload || mutable.value.device == null
        var description = if (reload) repo.describe() else mutable.value.device ?: repo.describe()
        var snapshot = repo.readState()
        if (snapshot.deviceId != description.deviceId) throw DeviceException("INVALID_RESPONSE", "设备身份与描述不一致")
        if (snapshot.bootId != description.bootId || snapshot.capabilitiesRevision != description.capabilitiesRevision || description.resources.any { r -> r.properties.any { p -> p.supported && PropertyKey(r.id, p.id) !in snapshot.readings } }) {
            description = repo.describe(); snapshot = repo.readState()
            loadedDescription = true
        }
        if (snapshot.deviceId != description.deviceId || snapshot.bootId != description.bootId) throw DeviceException("DEVICE_RESTARTED", "设备正在重启，请稍后刷新")
        if (snapshot.capabilitiesRevision != description.capabilitiesRevision) throw DeviceException("CAPABILITIES_CHANGED", "功能清单与状态版本不一致，暂不可控制，请重新获取")
        currentCoroutineContext().ensureActive()
        if (id != session) throw CancellationException()
        SnapshotRules.validate(description, snapshot)
        val old = mutable.value.snapshot
        if (old?.bootId == snapshot.bootId && old.capabilitiesRevision != null &&
            (snapshot.capabilitiesRevision == null || snapshot.capabilitiesRevision < old.capabilitiesRevision)) {
            throw DeviceException("CAPABILITIES_CHANGED", "同一次启动的功能清单版本不可回退，请检查设备固件后重新连接")
        }
        if (old?.bootId == snapshot.bootId && old.revision > snapshot.revision) return
        val keys = description.resources.flatMap { r -> r.properties.map { PropertyKey(r.id, it.id) } }.toSet()
        mutable.update { it.copy(device = description, snapshot = snapshot, status = ConnectionStatus.CONNECTED, lastSuccess = System.currentTimeMillis(),
            capabilitiesUpdatedAt = if (loadedDescription) System.currentTimeMillis() else it.capabilitiesUpdatedAt,
            propertyErrors = it.propertyErrors.filterKeys { key -> key in keys }, error = null) }
        if (loadedDescription) event("已获取功能清单：${description.resources.size} 项外设，清单版本 ${description.capabilitiesRevision ?: "旧版未提供"}")
    }

    fun write(key: PropertyKey, value: PropertyValue) {
        val current = mutable.value
        if (current.status != ConnectionStatus.CONNECTED || !foreground) return
        val repo = repository ?: return
        val description = current.device ?: return
        val property = description.resources.find { it.id == key.resourceId }?.properties?.find { it.id == key.propertyId } ?: return
        try { Validation.value(property, value) } catch (e: Exception) { report(e); return }
        if (!operations.tryLock()) return
        val requestId = UUID.randomUUID().toString()
        val id = session
        mutable.update { it.copy(pending = it.pending + (key to value), propertyErrors = it.propertyErrors - key, error = null) }
        scope?.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                event("发送 $key，requestId=$requestId")
                val receipt = repo.setProperty(key, value, description.bootId, requestId)
                currentCoroutineContext().ensureActive()
                if (id != session) throw CancellationException()
                if (receipt.deviceId != description.deviceId || receipt.bootId != description.bootId || receipt.key != key || receipt.requestId != requestId || receipt.value != value || receipt.revision < (mutable.value.snapshot?.revision ?: 0)) {
                    throw DeviceException("INVALID_RESPONSE", "控制回报与请求不一致，结果未知", true)
                }
                mutable.update { it.copy(snapshot = it.snapshot?.copy(revision = receipt.revision, readings = it.snapshot.readings + (key to Reading(receipt.value, "good"))), lastSuccess = System.currentTimeMillis()) }
                event("控制确认 $key，requestId=$requestId")
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                val message = readableError(e)
                mutable.update { it.copy(propertyErrors = it.propertyErrors + (key to message)) }
                report(e)
                val reloadRequired = e is DeviceException && e.code in setOf("DEVICE_RESTARTED", "CAPABILITIES_CHANGED", "RESOURCE_NOT_FOUND", "PROPERTY_NOT_FOUND")
                if (e is DeviceException && (e.outcomeUnknown || reloadRequired)) {
                    try {
                        synchronize(repo, reload = reloadRequired)
                        event("已重新读取状态；未自动重发命令")
                        mutable.update { it.copy(propertyErrors = it.propertyErrors + (key to "$message；已重新读取设备状态")) }
                    } catch (readError: CancellationException) { throw readError } catch (readError: Exception) { report(readError) }
                }
            } finally { if (id == session) mutable.update { it.copy(pending = it.pending - key) }; operations.unlock() }
        }
    }

    fun setFault(fault: MockFault) { (repository as? MockDeviceRepository)?.fault = fault; mutable.update { it.copy(fault = fault) } }
    fun rebootMock() { (repository as? MockDeviceRepository)?.reboot(); refresh() }
    fun addMockResource() { (repository as? MockDeviceRepository)?.addDemoResource(); refresh() }
    private fun readableError(e: Exception) = when (e) {
        is SecurityException -> "系统阻止局域网访问，请在应用权限中允许附近设备/局域网权限"
        is DeviceException -> "${e.code}：${e.message}"
        else -> "操作失败，请检查设备与网络后重试"
    }
    private fun report(e: Exception) {
        val message = readableError(e)
        mutable.update { it.copy(error = message, status = if (repository != null) ConnectionStatus.RECOVERING else ConnectionStatus.DISCONNECTED) }
        event(message)
    }
    private fun event(message: String) { mutable.update { it.copy(events = (listOf(DiagnosticEvent(System.currentTimeMillis(), message)) + it.events).take(100)) } }
    override fun onCleared() { disconnect(); super.onCleared() }
}
