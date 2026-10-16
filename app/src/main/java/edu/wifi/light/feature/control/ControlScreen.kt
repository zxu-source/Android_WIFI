package edu.wifi.light.feature.control

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import edu.wifi.light.core.model.*
import edu.wifi.light.data.discovery.DiscoveredDevice
import edu.wifi.light.data.discovery.DiscoveryState
import edu.wifi.light.data.mock.MockFault
import edu.wifi.light.data.remote.NetworkRoute
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.round

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlScreen(
    state: ControlState, discovery: DiscoveryState,
    onConfig: (ConnectionConfig) -> Unit, onConnect: () -> Unit, onDisconnect: () -> Unit,
    onRefresh: () -> Unit, onWrite: (PropertyKey, PropertyValue) -> Unit,
    onDiscover: () -> Unit, onStopDiscovery: () -> Unit, onSelect: (DiscoveredDevice) -> Unit,
    onSettings: (String) -> Unit, onFault: (MockFault) -> Unit, onReboot: () -> Unit,
) {
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    val busy = state.pending.isNotEmpty() || state.refreshing
    Scaffold { insets ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(insets), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("WiFi 外设遥控", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("手机发出命令 · 下位机驱动外设", style = MaterialTheme.typography.bodyMedium)
            }
            item {
                Section("连接设备") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !state.config.mock, onClick = { onConfig(state.config.copy(mock = false)) }, label = { Text("真实设备") })
                        FilterChip(selected = state.config.mock, onClick = { onConfig(state.config.copy(mock = true)) }, label = { Text("模拟模式") }, modifier = Modifier.testTag("mock-mode"))
                    }
                    if (state.config.mock) {
                        Text("模拟模式：界面展示的外设和数据不来自真实开发板", color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text("默认：手机开启热点，让 ESP8266 加入。也可使用普通无线局域网。", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(value = state.config.host, onValueChange = { onConfig(state.config.copy(host = it)) }, label = { Text("设备 IP / 主机名") }, placeholder = { Text("从发现列表或板端日志获取") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = state.config.port, onValueChange = { onConfig(state.config.copy(port = it)) }, label = { Text("端口") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        Choice("网络路由", state.config.route.label, NetworkRoute.entries.map { it.label }) { label -> onConfig(state.config.copy(route = NetworkRoute.entries.first { it.label == label })) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onSettings("hotspot") }) { Text("热点/无线设置") }
                            TextButton(onClick = { onSettings("wifi") }) { Text("WiFi 设置") }
                            TextButton(onClick = { onSettings("permissions") }) { Text("应用权限") }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onConnect, enabled = state.status != ConnectionStatus.CONNECTING, modifier = Modifier.testTag("connect")) { Text(if (state.device == null) "连接" else "重新连接") }
                        OutlinedButton(onClick = onDisconnect, enabled = state.status != ConnectionStatus.DISCONNECTED) { Text("断开") }
                        if (state.device != null) OutlinedButton(onClick = onRefresh, enabled = !busy) { Text("读取状态") }
                    }
                    Text("${state.status.label}${state.lastSuccess?.let { " · 最近确认 ${time(it)}" }.orEmpty()}", modifier = Modifier.testTag("connection-status"))
                    if (state.status == ConnectionStatus.CONNECTING || state.refreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            if (!state.config.mock) item {
                Section("发现局域网设备") {
                    Text(discovery.message, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = if (discovery.searching) onStopDiscovery else onDiscover) { Text(if (discovery.searching) "停止发现" else "发现设备") }
                    discovery.devices.forEach { device ->
                        OutlinedButton(onClick = { onSelect(device) }, modifier = Modifier.fillMaxWidth()) {
                            Text("${device.name}\n${device.host}:${device.port}")
                        }
                    }
                    Text("发现使用 mDNS，不扫描 WiFi 热点。部分手机热点隔离组播，找不到时可手动输入 ESP8266 的 IP。", style = MaterialTheme.typography.bodySmall)
                }
            }
            state.device?.let { device ->
                item { Section(device.name) {
                    Text("${device.deviceId} · 固件 ${device.firmwareVersion} · 协议 v1", style = MaterialTheme.typography.bodySmall)
                    Text("外设回报为逻辑输出状态；没有反馈传感器时不代表触点/负载实测。", style = MaterialTheme.typography.bodySmall)
                } }
                items(device.resources, key = { it.id }) { resource ->
                    Section(resource.name) {
                        Text(resource.id, style = MaterialTheme.typography.labelSmall)
                        resource.properties.forEach { property ->
                            val key = PropertyKey(resource.id, property.id)
                            PropertyControl(property, state.snapshot?.readings?.get(key), state.pending[key], state.propertyErrors[key],
                                enabled = state.status == ConnectionStatus.CONNECTED && !busy,
                                tag = "${resource.id}.${property.id}", onWrite = { onWrite(key, it) })
                        }
                    }
                }
            }
            if (state.config.mock && state.device != null) item {
                Section("模拟故障测试") {
                    Choice("场景", state.fault.label, MockFault.entries.map { it.label }) { label -> onFault(MockFault.entries.first { it.label == label }) }
                    OutlinedButton(onClick = onReboot, enabled = !busy) { Text("模拟下位机重启") }
                }
            }
            item {
                TextButton(onClick = { showDiagnostics = !showDiagnostics }) { Text(if (showDiagnostics) "收起诊断记录" else "查看诊断记录（${state.events.size}）") }
                if (showDiagnostics) state.events.forEach { event -> Text("${time(event.time)} ${event.message}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp)) }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    } }
}

@Composable
private fun Choice(label: String, selected: String, choices: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick = { expanded = true }) { Text("$label：$selected") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice -> DropdownMenuItem(text = { Text(choice) }, onClick = { expanded = false; onSelect(choice) }) }
        }
    }
}

@Composable
private fun PropertyControl(property: PropertyDescription, reading: Reading?, pending: PropertyValue?, error: String?, enabled: Boolean, tag: String, onWrite: (PropertyValue) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(property.name, style = MaterialTheme.typography.labelLarge)
        val good = reading?.quality == "good" && reading.value != PropertyValue.Unavailable
        Text("确认状态：${formatValue(reading?.value)} ${property.unit}${if (reading != null && reading.quality != "good") "（${reading.quality}）" else ""}")
        when {
            !property.supported -> Text("不支持的属性类型：${property.type}（不可操作）")
            !property.controllable -> Text("只读", style = MaterialTheme.typography.labelSmall)
            property.type == "boolean" -> {
                val confirmed = (reading?.value as? PropertyValue.BooleanValue)?.value ?: false
                Switch(checked = (pending as? PropertyValue.BooleanValue)?.value ?: confirmed,
                    onCheckedChange = { onWrite(PropertyValue.BooleanValue(it)) }, enabled = enabled && good,
                    modifier = Modifier.testTag(tag).semantics { contentDescription = "${property.name}控制" })
            }
            property.type in setOf("integer", "number") -> {
                val min = property.min!!; val max = property.max!!; val step = property.step!!
                val actual = (reading?.value as? PropertyValue.NumberValue)?.value ?: min
                var target by remember(tag, actual) { mutableDoubleStateOf(actual.coerceIn(min, max)) }
                var numericInput by remember(tag, actual) { mutableStateOf(actual.toString()) }
                Text("目标：${formatValue(PropertyValue.NumberValue(target))} ${property.unit}")
                if (min.toFloat().isFinite() && max.toFloat().isFinite() && max.toFloat() > min.toFloat()) Slider(value = target.toFloat(), onValueChange = { raw -> target = (min + round((raw.toDouble() - min) / step) * step).coerceIn(min, max) },
                    onValueChangeFinished = { onWrite(PropertyValue.NumberValue(target)) }, valueRange = min.toFloat()..max.toFloat(),
                    enabled = enabled && good && max > min, modifier = Modifier.testTag(tag).semantics { contentDescription = "${property.name}目标值" })
                OutlinedTextField(value = numericInput, onValueChange = { numericInput = it }, label = { Text("精确目标值 (${property.min}–${property.max})") },
                    singleLine = true, enabled = enabled && good, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                val number = numericInput.toDoubleOrNull()
                val validNumber = number != null && number.isFinite() && number in min..max &&
                    (property.type != "integer" || number == round(number)) && kotlin.math.abs((number - min) / step - round((number - min) / step)) <= 1e-6
                OutlinedButton(onClick = { onWrite(PropertyValue.NumberValue(number!!)) }, enabled = enabled && good && validNumber) { Text("设置数值") }
            }
        }
        pending?.let { Text("等待设备确认：${formatValue(it)}", color = MaterialTheme.colorScheme.primary) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun time(value: Long) = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(value))
private fun formatValue(value: PropertyValue?) = when (value) {
    is PropertyValue.BooleanValue -> if (value.value) "开启" else "关闭"
    is PropertyValue.NumberValue -> if (value.value == round(value.value) && kotlin.math.abs(value.value) <= 9_007_199_254_740_991.0) value.value.toLong().toString() else String.format(Locale.getDefault(), "%.6g", value.value)
    is PropertyValue.TextValue -> value.value
    else -> "不可用"
}
