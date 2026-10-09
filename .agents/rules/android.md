# Android 开发规则

以 [系统设计](../../docs/design/system-design.md) 和 [协议 v1](../../docs/protocol/wifi-control-v1.md) 为契约。

- UI → ViewModel → Repository；Composable 不执行 HTTP，主线程不做阻塞 IO。
- 单一 app 模块先按包分层；Mock 与 HTTP 共享 Repository 和领域模型。
- 外设控件按属性类型和读写约束创建；未知类型安全降级。
- 分离确认值、待确认目标、请求进行状态、错误；超时后结果未知，读取确认。
- 写操作按资源串行，轮询不与写争抢；迟到响应检查会话、bootId、revision。
- 切后台暂停轮询，切模式取消旧请求；旋转不重复执行控制。
- minSdk 23、targetSdk 37 的权限和明文通信按实际 OS 分支；热点无互联网时检验 WiFi 路由。
- 真实/模拟模式在 UI 明确区分；保存地址偏好，不保存自动执行命令。
- 核验协议模型、Repository 网络行为、主要 UI 状态；系统权限和路由需要真机证据。
