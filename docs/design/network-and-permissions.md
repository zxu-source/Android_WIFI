# 网络与权限补充设计

日期：2026-10-09。依据用户开发阶段澄清：默认由 Android 手机提供热点，ESP8266 加入；同时支持普通 WLAN，并充分考虑设备发现和系统权限。

本文件更新原设计中的默认拓扑。HTTP/JSON 能力协议和外设驱动边界不变。

## 网络模式

| 场景 | 板端模式 | 手机操作 | App 路由 |
| --- | --- | --- | --- |
| 默认：手机提供热点 | STA 加入手机热点 | 系统设置中打开热点，记录 SSID/密码供板端配置 | 默认“系统路由” |
| 普通 WLAN | STA 加入路由器 | 手机与板端连接同一网络 | 自动或指定 WiFi |
| 开发板提供热点 | SoftAP | 手机连接开发板热点 | 自动或指定 WiFi |

手机热点下，板端 IP 由手机 DHCP 决定，不固定为 192.168.4.1。读取固件串口地址，或通过 mDNS 发现。没有互联网不影响局域网控制，但实际厂商热点可能限制访问或组播；App 不承诺可以绕过厂商隔离。

“系统路由”不将 socket 绑定到手机作为客户端的 WiFi；“自动”优先选择已连接 WiFi 并绑定该网络，没有 WiFi 时使用系统路由；“指定 WiFi”要求已有 WiFi 网络。连接池和网络回调归属单个 Repository，丢失 WiFi 时取消请求，恢复阶段重新创建 Repository 和读取身份/能力/状态。

## 兼容范围与权限

App minSdk 保持 23：目标覆盖 Android 6.0/API 23 及以上；不是支持早于 Android 6.0 的系统，也不等于所有版本和厂商已完成实测。

| 系统/目标条件 | 实现 |
| --- | --- |
| Android 6–16 正常模式 | Manifest INTERNET、ACCESS_NETWORK_STATE；不弹定位或扫描权限 |
| Android 17/API37 + target37 | 连接/发现前请求 ACCESS_LOCAL_NETWORK，拒绝后保留模拟模式及应用权限入口，返回前台检查撤回 |
| Android 16 局域网保护开发者 opt-in | 不是首版默认启用的兼容实验；如人工开启该限制，需额外按官方 Android16 测试流程配置 NEARBY_WIFI_DEVICES，当前 App 不宣称已覆盖该实验模式 |
| 手机热点启停/加入 WLAN | 使用系统设置入口，由用户操作；不静默修改热点、扫描或自动连接 |

App 使用系统 NSD 发现网络服务，不做 WiFi AP 扫描，不调用需要定位/附近 WiFi 权限的热点配置 API，因此不因系统版本高就申请所有权限。未来如增加 WiFi 扫描/应用内热点/自动连接，应按对应 API 引入权限和版本分支，不能复用局域网权限替代它们。

## 设备发现约定

- 服务类型：`_wifi-control._tcp.`，完整 mDNS 类型 `_wifi-control._tcp.local.`。
- 实例名建议 `WiFi-Control-<deviceId>`，SRV 端口为真实 HTTP 端口；TXT 推荐 `protocolVersion=1`，可选 deviceId。
- ESP8266 后续固件使用 mDNS responder 广播该服务；这是项目实验约定，不声称已向 IANA 注册公共服务类型。
- 发现运行 20 秒，结果与待解析队列上限 32；按序解析，后台停止，选择候选填入 IP/端口，用户点击连接后再通过 `/device` 验证。
- NSD 使用兼容 API23 的回调接口，高版本存在 API 废弃提示但仍可工作。未使用只对新系统开放的系统服务选择器；API37 使用明确的局域网授权路径。
- 无 mDNS 服务、组播限制、厂商兼容性问题时保留手动 IP，不通过扫描整个网段猜测设备，也不把 SSID 扫描结果当成外设列表。

## HTTP 与数据约束

实验构建允许 HTTP，API23 使用 usesCleartextTraffic，API24+ 使用 Network Security Configuration。连接前限制解析结果为私有/链路本地/回环地址，固定 DNS 结果，禁用代理、重定向及自动连接重试。允许开发模拟器通过 10.0.2.2 访问 PC。

客户端全局串行化网络操作（包含读和写），这比规划的“按资源串行”更保守，首版适合 8 项以内外设；若扩展到更高吞吐再拆为每资源队列与快照协调。数值使用 Double，integer 限于可精确表示的范围；对数值提供输入控件，Float 能表示的范围再提供滑块。

参考，核对日期 2026-10-09：

- [Android Local Network Permission](https://developer.android.com/privacy-and-security/local-network-permission)
- [Android NSD](https://developer.android.com/develop/connectivity/wifi/use-nsd)
- [Network Security Configuration](https://developer.android.com/privacy-and-security/security-config)
