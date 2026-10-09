# Android_WIFI 工作入口

## 项目目标

通过 Android 手机的 WiFi 通信遥控 ESP8266 所连接的外设。WiFi 是通信手段，控制对象是 LED、继电器、PWM 或传感器等，不是 WiFi 的启停。安卓优先开发，ESP8266 后续按共享协议接入。

## 开始任务时

1. 检查 Git 状态并保留现有修改。
2. 按任务阅读 [`.agents/README.md`](.agents/README.md) 路由到相关规则。
3. 需求事实以 [`docs/requirements/lab12-android-wifi.md`](docs/requirements/lab12-android-wifi.md) 为依据；实现取舍见 [`docs/design/system-design.md`](docs/design/system-design.md)。
4. 通信变更先阅读 [`docs/protocol/wifi-control-v1.md`](docs/protocol/wifi-control-v1.md)，任务顺序见 [`docs/plans/implementation-plan.md`](docs/plans/implementation-plan.md)。

## 通用约束

- 规划不等于已实现；模拟测试不等于实物测试。报告真实检查结果和限制。
- 不硬编码某个外设 ID 与 GPIO 关系到安卓 UI；保持资源描述、驱动注册、控件映射的边界。
- 先设置明确目标状态，再按设备回报更新确认值；不能用“请求发送成功”替代控制成功。
- 继续使用现有 Kotlin/Compose 工程与 Wrapper；依赖版本变更需有构建或兼容性理由。
- 不提交 local.properties、真实 WiFi 密码、设备令牌、缓存、构建产物或用户本机路径配置。
- 未确认板型前不猜 GPIO 编号/有效电平；硬件阶段记录板级配置与供电。
- Git 同步前检查实际历史：前一次 API 上传产生的远端提交与本地 root commit 不同。不得假定 main 已跟踪 origin/main，不得自动强制推送或丢弃已有修改。
- 文档和代码保持一致，新增能力须更新协议样例与对应测试。

## 检查

安卓功能变更按范围运行 `gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`；UI/权限/网络变更增加对应设备验证。纯文档变更检查链接、事实、协议样例和内部一致性，无需运行整套安卓测试。
