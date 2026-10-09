# WiFi 外设控制协议 v1

状态：待实现的双方契约。传输：HTTP/1.1 + UTF-8 JSON。基础路径 `/api/v1`，协议标识 `protocolVersion: 1`。WiFi 是承载网络，命令控制外设属性。

## 1. 标识与限制

- `deviceId`：板端稳定标识；`bootId`：每次启动生成的会话标识。
- `resourceId`：外设稳定标识，例如 `led.main`、`relay.1`，不包含 GPIO 物理约定。
- `propertyId`：如 `on`、`level`、`temperature`。
- resourceId/propertyId 使用 ASCII 字母、数字、点、下划线、横线，长度 1–48；禁止路径分隔符。
- `requestId`：客户端 UUID；一次操作唯一。
- `revision`：本次启动内的全局非负递增整数；输出变化或采样值变化时增加，未改变的幂等写不增加。客户端只在同一 bootId 内比较 revision。
- 固件最多 8 个资源，每资源最多 4 个属性；请求体最多 1024 字节，响应最多 16 KiB。超出限制明确报错，不截断成成功数据。

## 2. 接口

| 方法与路径 | 含义 | 成功码 |
| --- | --- | --- |
| `GET /api/v1/device` | 获取身份、版本与全部资源属性描述 | 200 |
| `GET /api/v1/state` | 获取全部当前属性状态 | 200 |
| `PUT /api/v1/resources/{resourceId}/properties/{propertyId}` | 设置单一属性目标状态 | 200 |

全部响应 Content-Type 为 `application/json`，状态响应设 `Cache-Control: no-store`。v1 不分页，不提供批量原子写，也不提供 `toggle`：重复 toggle 可能使超时后的重试产生相反动作。首次连接先读取 device 校验版本，再读取 state。

## 3. 设备能力描述

```json
{
  "protocolVersion": 1,
  "deviceId": "esp8266-demo-01",
  "bootId": "boot-8f21",
  "name": "实验外设控制器",
  "firmwareVersion": "0.1.0",
  "resources": [
    {
      "id": "led.main",
      "name": "主 LED",
      "kind": "binary-output",
      "properties": [
        {"id": "on", "name": "开关", "type": "boolean", "readable": true, "writable": true}
      ]
    },
    {
      "id": "pwm.light",
      "name": "调光灯",
      "kind": "range-output",
      "properties": [
        {"id": "level", "name": "亮度", "type": "integer", "readable": true, "writable": true, "min": 0, "max": 100, "step": 1, "unit": "%"}
      ]
    },
    {
      "id": "sensor.room",
      "name": "温度传感器",
      "kind": "sensor",
      "properties": [
        {"id": "temperature", "name": "温度", "type": "number", "readable": true, "writable": false, "unit": "°C"}
      ]
    }
  ]
}
```

上述三项是协议/模拟样例，不表示真实板已连接这些外设。首个固件只注册实际存在的驱动。`kind` 是分组提示，控件主要按属性 `type`、读写能力和数值约束生成；不得依靠 `led.main` 名称硬编码。

支持的 type：boolean、integer、number、string。可写数值必须给出有限 min/max/正 step；v1 string 只读，最长 128 字符。可读属性包括可写输出的逻辑状态。不可读/不可写组合被视为无效能力描述。未知字段忽略，未知类型保留为只读的不支持项；未知主版本拒绝控制并显示版本错误。

## 4. 状态快照

```json
{
  "protocolVersion": 1,
  "deviceId": "esp8266-demo-01",
  "bootId": "boot-8f21",
  "revision": 12,
  "resources": [
    {"id": "led.main", "values": {"on": false}, "quality": {"on": "good"}},
    {"id": "pwm.light", "values": {"level": 35}, "quality": {"level": "good"}},
    {"id": "sensor.room", "values": {"temperature": 24.5}, "quality": {"temperature": "good"}}
  ]
}
```

`quality` 可为 good、stale、unavailable。无有效读数时 value 为 null，quality=unavailable；null 不能被解释为 0 或 false。输出值为 GPIO 驱动的逻辑状态，不保证外部电路真实状态。客户端记录本地接收时间，不要求 ESP8266 具有校准时钟。

同一 bootId 的迟到低 revision 快照不能覆盖新状态。bootId 改变时重新获取描述和完整状态。资源/属性缺失应显示不可用，并触发重新读取描述；禁止静默填充默认值。

## 5. 设置目标状态

请求 `PUT /api/v1/resources/led.main/properties/on`：

```json
{"requestId": "b294c92a-02e2-4b4f-b44e-254908bdcbf3", "expectedBootId": "boot-8f21", "value": true}
```

成功响应：

```json
{
  "protocolVersion": 1,
  "requestId": "b294c92a-02e2-4b4f-b44e-254908bdcbf3",
  "deviceId": "esp8266-demo-01",
  "bootId": "boot-8f21",
  "resourceId": "led.main",
  "propertyId": "on",
  "value": true,
  "revision": 13
}
```

只有校验通过且驱动成功设置后才返回 200；响应中的 value 是驱动确认的逻辑输出。相同目标值重复设置不触发“翻转”。v1 requestId 用于关联日志，**不承诺持久去重或 exactly-once**，不支持脉冲、步进、开锁等非幂等动作；这些操作需要另行制定命令与去重协议。

expectedBootId 必须与当前一致，否则返回 409 DEVICE_RESTARTED 且不操作驱动。多个客户端写入时采用服务器处理顺序的最后写入生效；首版不提供 revision 条件写，演示以一个控制客户端为主。

App 超时后不自动重发 PUT，应显示结果未知并 GET 状态。手动重试发送新的 requestId 和用户确认的明确目标值。读写串行化及会话检查阻止旧读取覆盖最新写结果。

## 6. 错误格式与校验

```json
{
  "protocolVersion": 1,
  "requestId": "b294c92a-02e2-4b4f-b44e-254908bdcbf3",
  "error": {"code": "INVALID_VALUE", "message": "属性 on 需要 boolean"}
}
```

无法解析 requestId 时返回 null。客户端按 code 分类，message 仅作为辅助文本。

| HTTP | code | 条件 |
| --- | --- | --- |
| 400 | INVALID_REQUEST | JSON 不合法、字段缺失、非法 ID |
| 404 | RESOURCE_NOT_FOUND / PROPERTY_NOT_FOUND | 外设或属性不存在 |
| 405 | METHOD_NOT_ALLOWED | 方法不支持 |
| 409 | DEVICE_RESTARTED | expectedBootId 不一致 |
| 413 | PAYLOAD_TOO_LARGE | 请求体超限 |
| 415 | UNSUPPORTED_MEDIA_TYPE | 非 JSON 控制请求 |
| 422 | INVALID_VALUE / READ_ONLY | 类型/范围/步长错误、写只读属性 |
| 500 | DRIVER_ERROR | 驱动执行失败 |
| 503 | DEVICE_BUSY | 设备暂时不能处理 |

先完成所有校验，再触碰驱动。boolean 不接受字符串 true，integer 不接受小数；拒绝 NaN/Infinity、数组和对象 value。number 步长采用明确的浮点容差测试。客户端预校验改善体验，但板端独立校验不可省略。错误响应不能改变输出。

## 7. 演进规则与契约样例

新增资源、只读属性和可选字段可以在 v1 内兼容扩展；删除字段、改变既有字段类型/行为或增加非幂等动作应升级协议并提供兼容策略。

安卓实施阶段创建 `protocol/fixtures/` 保存本页三个响应、boolean 写请求、错误响应以及未知类型、设备重启、无有效读数的样例。它们供 Android、模拟服务和固件契约测试共同使用，本轮仅定义样例，不创建运行时协议代码。
