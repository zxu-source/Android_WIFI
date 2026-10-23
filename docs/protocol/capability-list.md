# ESP8266 功能清单：下位机对接规范

日期：2026-10-16。配合[控制协议 v1](wifi-control-v1.md)使用。本清单回答“这台控制器连接了哪些外设、各自叫什么、允许怎样控制”；不包含 GPIO 或 WiFi 密码。

## 1. 获取方式和完整流程

采用 **安卓主动拉取**，不要求 ESP8266 主动推送：

```text
手机与 ESP8266 网络互通，App 已选择设备地址
 → 用户点击“连接”
 → App 自动 GET /api/v1/device 获取功能清单
 → App 校验版本、字段、ID、名称和调节约束
 → App 自动 GET /api/v1/state 获取当前确认状态
 → 校验 deviceId、bootId、capabilitiesRevision 一致
 → 按清单生成控制卡片并开放有效控件
 → 用户操作时 PUT 对应 resourceId/propertyId 的明确目标值
```

不需要先点额外的“加载清单”。连接上 WLAN/热点只解决网络可达性，不代表 App 已选定某个 ESP8266；mDNS 发现后仍需选择设备并连接。

清单接口：`GET /api/v1/device`，成功 HTTP200，`Content-Type: application/json; charset=utf-8`，`Cache-Control: no-store`。UTF-8 响应最多16 KiB。JSON 字段顺序不作要求，数组顺序就是界面的显示顺序。

## 2. 标准外设类型与自定义名称

| kind | 标准分类 | 常见外设 | 应声明的属性能力 | 安卓面板 |
| --- | --- | --- | --- | --- |
| binary-output | 二态开关 | 顶灯、台灯、LED、继电器 | 可读写 boolean，例如 on | 自定义名称 + 开关 + 确认状态 |
| range-output | 范围调节 | 风扇、电机调速、灯光亮度 | 可读写 integer/number，明确 min/max/step | 自定义名称 + 滑条 + 数值输入 + 单位 |
| sensor | 只读传感 | 温度、湿度、反馈输入、状态文本 | boolean/number/integer/string；writable=false | 自定义名称 + 只读值/状态 |

每项都可有自己的 name：例如“客厅顶灯”和“书桌台灯”都使用 binary-output，“桌面风扇”和“排风电机”都可使用 range-output。安卓不得根据这些名字判断类型，更不得写死某项名称对应的 GPIO。

kind 为分类展示，具体控件按每个属性的 `type + readable + writable` 及数值约束生成，保持 v1 可组合性。例如风扇可同时有 boolean 电源和 number 风速。未知 kind 显示“自定义外设”，其已知属性类型仍可复用通用控件；未知属性 type 仅显示提示，不发送控制。

“无级调节”表示滑条交互，最终精度仍由 step 约束，不承诺数学意义的无限精度。对电机的限幅、驱动和保护由下位机负责。

## 3. 顶层字段

| 字段 | 类型 | 必填 | 定义/约束 |
| --- | --- | --- | --- |
| protocolVersion | integer | 是 | 固定1，其他主版本不开放控制 |
| deviceId | string | 是 | 非空、设备稳定身份，不随一次连接改变 |
| bootId | string | 是 | 非空、每次启动改变；用于识别重启 |
| capabilitiesRevision | integer | 新固件应提供 | 本次启动内非负、单调递增的功能清单版本；兼容旧固件时可省略/null |
| name | string | 是 | 控制器显示名称，1–64个 Unicode 字符，不可全空白或含控制字符 |
| firmwareVersion | string | 是 | 固件版本标识，例如0.1.0 |
| resources | array | 是 | 0–8项外设；空数组表示当前无可展示外设，不虚构一个开关 |

## 4. resources 中每一项

| 字段 | 类型 | 必填 | 定义/约束 |
| --- | --- | --- | --- |
| id | string | 是 | 1–48个 ASCII 字母、数字、点、横线、下划线；清单内唯一 |
| name | string | 是 | 外设自定义名称，1–64个 Unicode 字符，不能全空白/含控制字符 |
| kind | string | 是 | 上表标准分类或兼容的自定义分类；与id相同字符限制 |
| properties | array | 是 | 1–4项属性；每项属性id在该外设内唯一 |

id 是通信标识，改名不应改id；同类外设必须有不同id。不同外设可以有相同显示名称，但建议使用能区分的名称。

## 5. properties 中每一项

| 字段 | 类型 | 必填 | 定义/约束 |
| --- | --- | --- | --- |
| id | string | 是 | 同上id字符限制；不强制叫on/level/speed |
| name | string | 是 | 控件名称，例如电源、照明、亮度、风速、温度；同上名称限制 |
| type | string | 是 | boolean / integer / number / string；未知类型保留提示但不控制 |
| readable | boolean | 是 | v1必须为true，控制操作需要设备确认值 |
| writable | boolean | 是 | true允许控制，false只读；v1的string必须只读 |
| min | number | 可写数值必填 | 最小允许值，有限数字 |
| max | number | 可写数值必填 | 最大允许值，有限数字；必须min≤max |
| step | number | 可写数值必填 | 正的有限步长；相对min对齐 |
| unit | string | 否 | 默认空字符串；最多16个 Unicode 字符，无控制字符，例如%、°C、rpm |

integer 的 min/max/step 都必须为整数，绝对值≤9007199254740991，保证现有客户端数值模型可精确表达。number 可使用小数，例如0–100、step=0.5。boolean必须是真正的JSON布尔值，不接受字符串"true"或整数1。

可写数值遵守：`min ≤ value ≤ max`，并且 `(value - min) / step` 接近整数，误差容限1e-6；integer额外要求value为整数。下位机独立校验，不只信任安卓输入限制。

## 6. 可直接使用的完整样例

- [功能清单JSON](../../protocol/fixtures/capability-list.json)：两盏开关灯、一台调速风扇、一盏调光灯和温度传感器。
- [匹配的状态JSON](../../protocol/fixtures/capability-state.json)：同一deviceId、bootId、capabilitiesRevision及全部属性当前状态。
- [JSON Schema](../../protocol/capability-list.schema.json)：字段、类型、长度、数量与数值必填约束。

Schema允许未来附加字段；ID唯一、min≤max、有限数字、最终响应字节长度和清单/状态一致性仍需应用层检查。校验工具与测试见[模拟服务说明](../../tools/mock-device/README.md)。

## 7. 状态读取与控制示例

`GET /api/v1/state` 返回的 resources 使用清单中的id，各属性放在 values 中，并有 good/stale/unavailable 质量。没有有效值时返回null且quality=unavailable，不用false/0伪装。

打开“书桌台灯”：`PUT /api/v1/resources/relay.desk/properties/on`：

```json
{"requestId":"c9f8272e-637e-48f4-bda2-60633624c11a","expectedBootId":"boot-8f21","value":true}
```

将“桌面风扇”设为40.5%：`PUT /api/v1/resources/fan.desk/properties/speed`：

```json
{"requestId":"b04b6161-6952-4a0e-9da1-6c2effad7d8e","expectedBootId":"boot-8f21","value":40.5}
```

同学只需按协议实现这类属性读写，无需为每种灯、风扇编写不同的安卓接口。成功响应、错误码和超时处理沿用[控制协议](wifi-control-v1.md)。

## 8. 清单更新与兼容规则

新固件应同时在device与state响应中提供capabilitiesRevision。新增/删除外设、改名称、修改类型/读写标志/范围/步长/单位/顺序时，该版本递增；普通开关状态或传感器值变化只递增state.revision。修改清单同时使state.revision递增，并提供与新清单匹配的状态。

安卓前台轮询state时发现capabilitiesRevision变化，会重拉device和state；只有身份与版本匹配才一次性替换面板，删除已消失外设的旧信息。连续变化造成两者不一致时保留旧面板、停用控制、提示重新获取，不对旧资源盲写。

旧固件可在两类响应中同时省略该字段；只在一边提供是错误。旧固件的纯改名/改范围不一定能被状态轮询识别，需要主动获取时断开后重新连接，或直接点击“重新连接”；界面不提供独立的手动刷新清单按钮。“读取状态”不强制重载未变化的清单。设备重启可重新计数，但必须更换bootId。

本轮不增加“设备主动推送清单”，也不把mDNS服务发现当成清单本身。清单没有事务锁；资源在控制期间消失时，服务器应返回RESOURCE_NOT_FOUND/PROPERTY_NOT_FOUND，安卓重新加载且不重放命令。

## 9. 下位机实现检查表

- [ ] 用资源注册表生成清单，列出实际存在的驱动，而不是固定返回样例。
- [ ] 每个外设有唯一id、自定义name和合理kind；属性类型、读写能力和范围真实。
- [ ] 实现GET device、GET state、PUT单属性；先校验参数再触碰GPIO。
- [ ] 使用UTF-8 JSON，响应≤16 KiB，请求≤1024字节；不发送WiFi密码和本机调试路径。
- [ ] device/state身份一致；新固件的capabilitiesRevision成对出现并正确递增。
- [ ] 输出回报是驱动确认的逻辑值；有实物反馈传感器时另注册只读属性。
- [ ] 固件使用默认STA加入手机热点，广播mDNS便于发现，实际IP以DHCP/串口为准。
- [ ] 用完整样例及校验工具对照，并完成真机外设联调。
