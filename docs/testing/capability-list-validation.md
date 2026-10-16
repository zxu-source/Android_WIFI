# v1.1 功能清单与动态面板验证

日期：2026-10-16。版本：1.1，versionCode=2，debug APK。实现依据为[功能清单设计](../design/capability-list-design.md)和[下位机对接规范](../protocol/capability-list.md)。

## 已实现

- 连接时自动获取 device 清单，再读取 state，校验一致后开放控制。
- 二态开关、范围调节、只读传感三类标准外设，可分别自定义名称；允许同类多个实例，按属性能力生成控件，不绑定 GPIO。
- 可选 capabilitiesRevision 成对校验；状态版本变化时自动重拉清单，提供手动更新入口，兼容旧固件。清单不一致或回退时停用控制。
- 模拟设备包含不同名称的灯、小数步长风扇、整数调光、温度和未知类型；模拟新增床头灯可自动显示并控制。
- 提供完整[清单](../../protocol/fixtures/capability-list.json)、[状态](../../protocol/fixtures/capability-state.json)、[JSON Schema](../../protocol/capability-list.schema.json)和可选校验工具。PC 模拟服务默认使用这套五外设样例；安卓内置模拟额外包含未知类型兼容示例。

## 实际执行结果

| 检查 | 结果 | 主要覆盖 |
| --- | --- | --- |
| JVM 单元/HTTP 集成测试 | 38 项通过，0 失败、0 跳过 | 字段/名称/约束校验、旧清单、空清单、自定义类型、自动拉取顺序、动态新增/删除、版本不一致和回退、重启、原有写回报/超时/HTTP 测试 |
| Android 17 / API 37 模拟器 | 9 项通过，0 失败、0 跳过 | 启动与旋转、动态新增灯后操作、风扇自定义名称和小数步长输入校验、原有权限/HTTP/NSD 测试 |
| Python 服务与 Schema 测试 | 9 项通过 | 4 项服务测试、5 项 Schema/语义测试；实际安装可选 jsonschema 依赖后执行，无跳过 |
| lintDebug | 0 errors，25 warnings | 完整 lint 报告保留在本地构建输出，不将警告说成零问题 |
| assembleDebug | 成功 | versionName=1.1，versionCode=2，minSdk=23 |
| APK 签名检查与安装 | 通过 | 签名验证通过，模拟器安装成功并执行测试 |

JVM 测试包含真实访问本机 PC 模拟服务的用例；模拟器 HTTP 测试通过虚拟机宿主地址访问该服务。NSD 测试使用模拟器本地注册的假服务，不代表已验证手机热点上的跨设备组播发现。

主要构建命令（先设置 JAVA_HOME、Android SDK 和运行中的本机模拟服务）：

```powershell
$env:WIFI_SIMULATOR_TEST_PORT='18080'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.simulatorPort=18080' --console=plain
```

Python 校验的安装与执行方法见[模拟服务说明](../../tools/mock-device/README.md)。本轮依赖安装到被 Git 忽略的 build 目录内独立虚拟环境，未要求修改全局 Python。

APK 位于仓库根目录下 `app/build/outputs/apk/debug/app-debug.apk`，为已编译、已签名的安装包，不提交构建产物。SHA-256：

```text
821361be2fbf7aefeeeb3b4c35cbd1736ab7deb6231287f9fb9d4e0e9fd46c71
```

## 界面证据

以下为重复执行两项启动/动态清单测试后保存、人工检查过的模拟器原始截图：

- [连接后功能清单摘要、版本和主 LED](evidence/2026-10-16-capabilities/evidence/mock-control.png)
- [Activity 重建后保留确认状态](evidence/2026-10-16-capabilities/evidence/mock-after-recreation.png)
- [新增床头灯自动出现并已确认开启](evidence/2026-10-16-capabilities/evidence/capability-new-light.png)

首次截图时模拟器桌面进程 Pixel Launcher 出现无响应对话框，遮挡了界面；同目录上一层的三张同名截图保留该环境异常记录，不作为干净界面证据。停止该模拟器桌面进程后重复测试，两项均通过，以上链接指向重跑截图。未对截图作修图处理。

## 尚未验证

未实现或烧录 ESP8266 固件；未连接真实手机、真实 LED/继电器/电机；未验证真实热点路由、组播发现和各手机厂商权限界面，也未逐版本运行 Android 6–16。minSdk=23 是构建兼容范围，不等于这些版本均有真机验证。

本轮使用客户端主动拉取，不增加设备推送通道。输出状态仍表示驱动确认的逻辑值，没有反馈传感器时不等同于负载实测。
