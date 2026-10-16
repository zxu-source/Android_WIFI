# 安卓首版实施与验证记录

日期：2026-10-09。默认拓扑按用户要求调整为手机热点 + ESP8266 STA，兼容普通 WLAN/开发板 SoftAP。

2026-10-16 已续跑本机与 Android 17 模拟器验证，最新结果见[续跑验证记录](2026-10-16-resume-validation.md)。下列初版检查与缺口保留为 10 月 9 日的历史记录，不代表全部仍未完成。

## 已实现

- 协议模型、JSON DTO 与领域映射、版本/参数/地址/响应上限检查，共享 8 份 fixtures。
- Android 内模拟 LED、调光和传感器，以及未知类型兼容、延迟、断线、响应丢失、驱动失败和重启场景。
- Compose 动态外设控件、数值滑块/精确输入、确认值与等待值、连接配置、诊断日志。
- HTTP 三类端点、可取消请求、固定超时、禁止重定向/代理/自动写重试、明确目标状态。
- 前台轮询、后台暂停、网络恢复重新装配客户端、bootId/revision 与会话检查。
- 手机热点的系统路由、普通 WLAN 的 WiFi 绑定、局域网服务发现和系统设置/权限入口。
- PC 独立 HTTP 模拟服务；可选 zeroconf mDNS 广播（本机未安装/未实测广播）。

## 实际检查结果

| 检查 | 结果与证据 |
| --- | --- |
| debug APK 构建 | 通过，`app/build/outputs/apk/debug/app-debug.apk` |
| Android test APK 构建 | 通过，`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` |
| JVM 测试 | 21 项通过，0 失败、0 错误、0 跳过；其中 1 项是原模板算术测试 |
| 协议/校验 | 7 项通过：类型、版本、未知字段/类型、缺失值、安全地址等 |
| HTTP 契约 | 7 项通过：读写路径、超时不重发、无效回报、重定向、体积、错误码 |
| ViewModel | 5 项通过：确认状态、丢失回报恢复、取消释放锁、重启、迟到快照 |
| 跨实现闭环 | 1 项通过：实际 Kotlin HTTP 客户端 → 独立 Python HTTP 服务，读取→开启→重复开启→关闭→读取 |
| PC 服务自身检查 | 4 项通过，含幂等、非法值无副作用、只读保护与重启保护 |
| lint | 0 errors、27 warnings；包含已有依赖版本提醒、模板资源、API23忽略API24属性、实验 HTTP 明文策略和 KTX 建议；未为了清空提醒升级全部依赖或隐藏警告 |
| APK 签名核验 | 通过，debug APK v1/v2 签名有效 |
| 文档/补丁格式 | 本地文档链接与 JSON 检查、git diff --check |

测试报告位于 `app/build/reports/tests/testDebugUnitTest/`；lint 报告位于 `app/build/reports/lint-results-debug.html`。这些是生成物，不提交为源码。

跨实现测试起初揭示模拟 HTTP 服务默认响应未明确关闭连接；现改为 HTTP/1.1 + 明确 Connection: close，重新执行整个检查组合通过。没有启用自动写重试来掩盖故障。临时 loopback 测试服务已停止。

## 未完成的设备验证

2026-10-09 当次未检测到已连接设备，也没有现成 AVD/系统镜像。因此当时：

- 3 项新增 Compose UI 测试仅已编译成测试 APK，**未在 Android 设备上执行**。
- 未实际安装/启动 APK，未取得运行截图；界面布局、大字体、深色模式和旋转尚需设备验证。
- 未验证真实 Android6–17 的权限授予/拒绝/撤回、NSD、WiFi 路由、手机热点组播和厂商差异。
- Android16 人工启用的局域网保护 opt-in 不作为当前已支持的实测路径，详见[权限说明](../design/network-and-permissions.md)。
- ESP8266 固件尚未实现，不宣称已控制真实 LED、继电器或任何外设。

## 复验与下一步

1. 按[安装说明](../android/usage.md)把 APK 安装到手机，先体验模拟模式。
2. 连接 USB 调试后运行 `gradlew.bat :app:connectedDebugAndroidTest` 执行设备测试；记录具体设备/OS。
3. 用 PC 模拟服务验证手机真实 HTTP 和 NSD，分别覆盖手机热点与普通 WLAN。
4. 确认 ESP8266 板型/引脚后实现固件，默认 STA 加入手机热点并广播 mDNS，完成实物实验。

P0–P4 的首版代码和本机检查已落地；P5 仍保留真实设备验收缺口。详细总计划见[实施计划](../plans/implementation-plan.md)。
