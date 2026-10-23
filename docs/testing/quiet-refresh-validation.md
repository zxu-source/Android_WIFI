# v1.1.2 静默刷新验证

日期：2026-10-16。版本：1.1.2，versionCode=4。设计见[静默轮询与稳定布局](../design/quiet-refresh-design.md)。本轮保留前次取消手动清单入口的修改，不恢复该按钮。

## 修复内容

原来每次周期读取都会切换 refreshing，UI 据此插入/移除进度条并整页置灰。现在前台轮询与回前台读取静默进行，只有主动读取使用可见刷新状态；进度条有固定4dp占位，外设待确认提示保留固定一行。列表静态区段及外设使用稳定 key。

静默读取不再禁用控件，因此控制调度同步改为：显示待确认目标后等待当前读取结束，再发送一次明确目标；同时只允许一条待处理控制。等待后检查会话、前台代次、设备启动标识、清单/属性能力和确认质量，拒绝过期目标。断开或快速切后台再回来都不能复活排队命令。

验证还覆盖了清单变化伴随非法状态版本回退的响应：此时保留旧面板、停止控制并报告错误，不能因为忽略旧状态而继续向旧能力发送排队命令。协议字段和样例未改变，仍要求清单变化时状态版本递增。

## 实际检查

| 检查 | 结果 |
| --- | --- |
| testDebugUnitTest | 50 项通过，0 失败、0 跳过 |
| connectedDebugAndroidTest | Android 17 / API37 模拟器13项通过，0失败、0跳过 |
| lintDebug | 0 errors，25 warnings |
| assembleDebug | 成功，版本1.1.2 / code4 |
| APK 签名 | 验证通过，包含v1、v2签名 |
| 文档和差异检查 | 本地 Markdown 链接与 git diff --check 检查 |

JVM 新增12项调度测试，覆盖静默轮询/回前台读取、主动读取反馈、读取期间控制只发送一次、重复点击、断开和新会话、离开前台与快速返回、重启、清单变化、读取失败、状态失效以及能力变化伴随状态版本回退。

模拟器新增3项布局回归：

1. 主动读取前、中、后，连接卡片与进度槽边界完全相同。
2. 等待控制确认前、中、后，外设卡片边界完全相同。
3. 连续5次确认时间/状态更新后，精确输入草稿、控件可用性与输入位置保持不变，未显示周期进度条。

完整构建与回归命令（先启动本机模拟服务并选择模拟器）：

```powershell
$env:WIFI_SIMULATOR_TEST_PORT='18080'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.simulatorPort=18080' --console=plain
```

本轮 JVM 和 Android HTTP 集成用例实际访问本机模拟服务；本地 NSD 测试不代表真实手机热点跨设备发现。本轮未重新执行 Python/Schema 专项，因为服务与清单字段未变更。

## 安装与限制

已签名安装包为仓库根目录下 `app/build/outputs/apk/debug/app-debug.apk`，不提交构建产物。SHA-256：

```text
e3af18b7f8d2022c61bfabf831cf13cd54613739965346f87e60418bb8d35c0c
```

未在真实手机/ESP8266 上验证观感与硬件联调。以上是模拟器边界/交互断言，不等于所有手机上的主观观感均已验证。错误提示、模式切换或真实外设增删等业务变化仍可能合理改变布局；修复目标是正常轮询不再周期性推挤页面和反复置灰。
