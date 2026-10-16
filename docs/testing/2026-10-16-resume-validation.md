# 2026-10-16 安卓开发续跑验证

本次接着 10 月 9 日未收尾的安卓首版工作，保留已有实现；补齐独立服务联调、设备端测试和运行截图。默认网络仍为手机提供热点、ESP8266 STA 加入，也支持普通 WLAN。

## 结果

| 检查 | 本次结果 |
| --- | --- |
| JVM 测试 | 21 项，0 失败、0 错误、0 跳过；包含独立 PC 服务联调 |
| Android 17/API37 模拟器测试 | 7 项，0 失败、0 错误、0 跳过 |
| PC 模拟服务测试 | 4 项通过 |
| debug APK | 构建通过；模拟器安装、启动、操作通过 |
| lint | 0 errors、25 warnings，未隐藏警告 |
| 文档 | 本地链接和 8 份 JSON fixtures 校验通过 |

Android 设备端 7 项包含：原模板上下文检查、动态开关、只读属性保护、未知类型降级、完整 Activity 重建后保持已确认状态、真实 Android HTTP 客户端控制 PC 模拟服务，以及 NSD 服务发现。

HTTP 测试使用模拟器的 `10.0.2.2` 访问 PC loopback 服务，并走实际 NetworkRouter/HTTP 客户端；在 API37 上授予并检查 ACCESS_LOCAL_NETWORK。发现测试在模拟器上注册临时 `_wifi-control._tcp` 服务，验证发现和解析出的端口，完成后注销。它不能替代真实热点跨设备组播测试。

## 修复的问题

1. **联调测试可能被缓存为跳过**：原构建没有将 `WIFI_SIMULATOR_TEST_PORT` 纳入测试输入。上次不启用服务的跳过结果，即使之后启用服务也可能被复用。现在端口作为任务输入并显式传给测试进程；本次确认该项实际执行通过。
2. **旧版界面测试依赖不兼容 Android17**：Espresso3.5.1 调用已不存在的 InputManager.getInstance，导致界面测试在启动时失败。仅将测试依赖更新为 Espresso3.7.0 与 AndroidX Test JUnit1.3.0，再完整重跑通过。版本参考[AndroidX Test 官方发布说明](https://developer.android.com/jetpack/androidx/releases/test)。应用功能依赖与最低 SDK 未因此升级。
3. **PowerShell 参数传递**：带点的 Gradle `-P` 参数需要整体引号，避免被拆成错误任务名。下面给出实际使用格式。

## 复验方法

先在一个终端从仓库根目录启动模拟服务：

```powershell
python tools/mock-device/server.py --port 18080
```

另一个终端使用可用 JDK 和已启动的模拟器：

```powershell
$env:WIFI_SIMULATOR_TEST_PORT = '18080'
$env:ANDROID_SERIAL = 'emulator-5554'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.simulatorPort=18080' --console=plain
python -m unittest discover -s tools/mock-device -p test_server.py
```

ANDROID_SERIAL 应替换为自己选择的设备。实体手机不能使用模拟器专属的 10.0.2.2；需显式设置 simulatorHost 为 PC 局域网地址，并让 PC 服务监听该接口。真实手机连接与权限弹窗需另行验证。

本次测试报告：`app/build/reports/tests/testDebugUnitTest/`、`app/build/reports/androidTests/connected/debug/`；lint：`app/build/reports/lint-results-debug.html`。这些生成报告不作为源码提交。

## APK 与截图

APK：`app/build/outputs/apk/debug/app-debug.apk`。它是 debug 签名实验版本。

- [模拟外设开启后的实际页面](evidence/2026-10-16/mock-control.png)
- [Activity 重建后保持状态的实际页面](evidence/2026-10-16/mock-after-recreation.png)

截图来自真实运行的 Android 17 模拟器，显示的是明确标识的模拟设备，不是 ESP8266 实物状态。界面文字和控件已目视检查，无明显裁切或重叠；下方其他外设通过滚动访问。

## 仍需实物验证

- 用户实体手机的安装、热点、普通 WLAN、蜂窝网络并用以及厂商差异。
- 更低版本 Android 的运行和权限行为；当前未宣称所有版本已实测。
- 权限拒绝、永久拒绝和撤回的完整系统弹窗流程；本次 HTTP/发现验证的是授权后的实际通信路径。
- 真实 ESP8266 mDNS 广播、固件驱动和外设接线；固件尚未开发，不把模拟控制当作实物完成。

本次没有自动推送 GitHub。临时模拟服务和本次启动的无窗口模拟器在验证后关闭，后续可按需重新启动。
