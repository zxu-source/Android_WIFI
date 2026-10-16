package edu.wifi.light.data.preferences

import android.content.Context
import edu.wifi.light.data.remote.NetworkRoute
import edu.wifi.light.feature.control.ConnectionConfig

class ConnectionPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("connection", Context.MODE_PRIVATE)
    fun read() = ConnectionConfig(
        mock = preferences.getBoolean("mock", false),
        host = preferences.getString("host", "").orEmpty(),
        port = preferences.getString("port", "80") ?: "80",
        route = runCatching { NetworkRoute.valueOf(preferences.getString("route", NetworkRoute.SYSTEM.name)!!) }.getOrDefault(NetworkRoute.SYSTEM),
    )
    fun save(config: ConnectionConfig) {
        preferences.edit().putBoolean("mock", config.mock).putString("host", config.host)
            .putString("port", config.port).putString("route", config.route.name).apply()
    }
}
