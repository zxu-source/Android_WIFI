# Android_WIFI：通过 WiFi 遥控外设

使用自己的 Android 手机作为遥控器，ESP8266 作为带 WiFi 的下位机控制器，通过无线命令控制实际外设并读取状态。WiFi 是通信通道；本项目不以开启/关闭手机或开发板的 WiFi 为控制目标。

当前代码是 Kotlin + Jetpack Compose 初始模板。以下文档是已整理的需求与待实施方案，不代表功能已经实现。

## 文档入口

- [实验要求整理](docs/requirements/lab12-android-wifi.md)：完整整理两页 PDF，并区分原文与项目解释。
- [系统设计](docs/design/system-design.md)：上下位机职责、通信方案、可扩展性与界面设计。
- [通信协议 v1](docs/protocol/wifi-control-v1.md)：设备能力、读写接口、错误和一致性规则。
- [实施计划](docs/plans/implementation-plan.md)：安卓优先的阶段、任务、依赖和验收标准。
- [验收与真机联调](docs/testing/acceptance.md)：模拟验证、实物演示和实验报告证据。
- [代理协作入口](AGENTS.md)及[目录说明](.agents/README.md)：后续开发的约束与工作流程。

## 首版路径

手机手动连接 ESP8266 热点 → App 连接下位机 HTTP 服务 → 获取外设能力 → 发送明确的目标状态 → 下位机操作外设 → App 显示设备回报。

先完成模拟模式中的安卓功能，再实现 ESP8266 固件与真机联调。第一项实物演示使用板载 LED 或低压 LED；继电器和其他外设沿用同一能力模型。

## 尚需确认的实物信息

手机型号与 Android 版本、ESP8266 开发板型号和引脚标注、首个外设及其供电和有效电平。它们影响真机验证和板级配置，不阻塞安卓端协议、模拟模式及界面开发。

## 本地构建基线

现有配置：namespace/applicationId `edu.wifi.light`，minSdk 23、compileSdk/targetSdk 37、Gradle Wrapper 9.6.0、AGP 9.4.1、Kotlin 2.2.10，Gradle daemon toolchain 25。以上为文件读取结果，尚未验证构建成功。

后续从仓库根目录使用 `gradlew.bat`，按计划检查 JDK/SDK 和依赖兼容性。`local.properties`、构建缓存、真实网络凭据和设备密钥不提交。
