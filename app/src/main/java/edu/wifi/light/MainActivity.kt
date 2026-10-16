package edu.wifi.light

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.wifi.light.core.model.DeviceException
import edu.wifi.light.data.discovery.DeviceDiscovery
import edu.wifi.light.data.mock.MockDeviceRepository
import edu.wifi.light.data.preferences.ConnectionPreferences
import edu.wifi.light.data.remote.NetworkRouter
import edu.wifi.light.feature.control.ControlViewModel
import edu.wifi.light.feature.control.ControlScreen
import edu.wifi.light.ui.theme.WIFILightTheme

class MainActivity : ComponentActivity() {
    companion object { private const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK" }
    private fun allowed() = Build.VERSION.SDK_INT < 37 || ContextCompat.checkSelfPermission(this, LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = ConnectionPreferences(applicationContext)
        val router = NetworkRouter(applicationContext)
        val appContext = applicationContext
        setContent {
            WIFILightTheme {
                val model: ControlViewModel = viewModel(factory = viewModelFactory { initializer {
                    ControlViewModel(preferences.read(), createRepository = { config ->
                        if (config.mock) MockDeviceRepository() else {
                            if (Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(appContext, LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) throw SecurityException("Local network permission denied")
                            val port = config.port.toIntOrNull() ?: throw DeviceException("INVALID_ADDRESS", "端口必须为整数")
                            router.create(config.host.trim(), port, config.route)
                        }
                    }, saveConfig = preferences::save)
                } })
                val discovery = remember { DeviceDiscovery(applicationContext) }
                val state by model.state.collectAsStateWithLifecycle()
                val discoveryState by discovery.state.collectAsStateWithLifecycle()
                var pendingAction by rememberSaveable { mutableStateOf("") }
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    val action = pendingAction; pendingAction = ""
                    if (granted) { if (action == "discover") discovery.start() else model.connect() }
                    else model.permissionRequired()
                }
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                DisposableEffect(lifecycle, model, discovery) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_START -> {
                                if (!model.state.value.config.mock && !allowed() && model.state.value.device != null) model.permissionRequired()
                                model.setForeground(true)
                            }
                            Lifecycle.Event.ON_STOP -> { model.setForeground(false); discovery.stop() }
                            else -> Unit
                        }
                    }
                    lifecycle.addObserver(observer)
                    model.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                    onDispose { lifecycle.removeObserver(observer); discovery.stop(); model.setForeground(false) }
                }
                fun runWithPermission(action: String) {
                    if ((state.config.mock && action == "connect") || allowed()) {
                        if (action == "discover") discovery.start() else model.connect()
                    } else { pendingAction = action; permission.launch(LOCAL_NETWORK) }
                }
                ControlScreen(state, discoveryState,
                    onConfig = { discovery.stop(); model.configure(it) },
                    onConnect = { runWithPermission("connect") }, onDisconnect = model::disconnect,
                    onRefresh = model::refresh, onWrite = model::write,
                    onDiscover = { runWithPermission("discover") }, onStopDiscovery = discovery::stop,
                    onSelect = { device -> model.configure(state.config.copy(mock = false, host = device.host, port = device.port.toString())); discovery.stop() },
                    onSettings = { action ->
                        val intent = when (action) {
                            "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
                            "permissions" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                            else -> Intent(Settings.ACTION_WIRELESS_SETTINGS)
                        }
                        runCatching { startActivity(intent) }.onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                    }, onFault = model::setFault, onReboot = model::rebootMock,
                    onRefreshCapabilities = model::reloadCapabilities, onAddMockResource = model::addMockResource,
                )
            }
        }
    }
}
