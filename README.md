# Android_WIFI：通过 WiFi 遥控外设

使用自己的 Android 手机作为遥控器，ESP8266 作为带 WiFi 的下位机控制器，通过无线命令控制实际外设并读取状态。WiFi 是通信通道；本项目不以开启/关闭手机或开发板的 WiFi 为控制目标。

安卓首版已实现：Kotlin + Jetpack Compose 操作界面、按能力描述生成外设控件、模拟模式和故障注入、HTTP 控制、局域网设备发现、版本化权限处理与网络路由。ESP8266 固件及真实手机/外设联调尚未完成。

## 文档入口

- [实验要求整理](docs/requirements/lab12-android-wifi.md)：完整整理两页 PDF，并区分原文与项目解释。
- [系统设计](docs/design/system-design.md)：上下位机职责、通信方案、可扩展性与界面设计。
- [通信协议 v1](docs/protocol/wifi-control-v1.md)：设备能力、读写接口、错误和一致性规则。
- [实施计划](docs/plans/implementation-plan.md)：安卓优先的阶段、任务、依赖和验收标准。
- [验收与真机联调](docs/testing/acceptance.md)：模拟验证、实物演示和实验报告证据。
- [安装与操作说明](docs/android/usage.md)：APK、模拟体验、手机热点与局域网操作。
- [安卓首版验证记录](docs/testing/android-first-release.md)：已通过检查和未实测范围。
- [2026-10-16 续跑结果](docs/testing/2026-10-16-resume-validation.md)：21 项 JVM、7 项模拟器与 4 项服务测试，以及实际运行截图。
- [网络与权限补充设计](docs/design/network-and-permissions.md)：手机热点为默认拓扑、mDNS 发现和系统权限。
- [代理协作入口](AGENTS.md)及[目录说明](.agents/README.md)：后续开发的约束与工作流程。

## 首版路径

手机开启热点，ESP8266 加入（或手机与板端处于普通 WLAN） → 发现/手动选择下位机 HTTP 地址 → 获取外设能力 → 发送明确的目标状态 → 下位机操作外设 → App 显示设备回报。

先完成模拟模式中的安卓功能，再实现 ESP8266 固件与真机联调。第一项实物演示使用板载 LED 或低压 LED；继电器和其他外设沿用同一能力模型。

## 尚需确认的实物信息

手机型号与 Android 版本、ESP8266 开发板型号和引脚标注、首个外设及其供电和有效电平。它们影响真机验证和板级配置，不阻塞安卓端协议、模拟模式及界面开发。

## 本地构建基线

现有配置：namespace/applicationId `edu.wifi.light`，minSdk 23、compileSdk/targetSdk 37、Gradle Wrapper 9.6.0、AGP 9.4.1、Kotlin 2.2.10，Gradle daemon toolchain 25。当前完整项目已通过 debug 构建、JVM 测试和 lint（无错误，存在警告）。

后续从仓库根目录使用 `gradlew.bat`，按计划检查 JDK/SDK 和依赖兼容性。`local.properties`、构建缓存、真实网络凭据和设备密钥不提交。
