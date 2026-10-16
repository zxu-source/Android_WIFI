# 局域网模拟设备

此服务使用 `protocol/fixtures/` 的能力描述，模拟外设，不访问真实硬件。Python 3.10+，基本 HTTP 模式只使用标准库。

默认使用 `capability-list.json` / `capability-state.json`，提供两盏不同名称的开关灯、风扇、调光灯和温度传感器；并携带匹配的capabilitiesRevision。旧式device/state样例仍保留用于兼容测试。

在仓库根目录运行：

```powershell
python tools/mock-device/server.py
```

默认仅监听 `127.0.0.1:8080`。安卓模拟器可通过 `10.0.2.2:8080` 访问。手机测试时显式绑定 PC 的局域网 IPv4 地址，让手机与 PC 处于相同网络：

```powershell
python tools/mock-device/server.py --host 192.168.1.20 --port 8080
```

上面的 IP 是示例，替换为实际 PC 地址；若防火墙限制访问，按实际网络范围配置。App 选择“真实设备”，输入服务 IP/端口。手机使用热点供 PC 加入时选择“系统路由”；普通 WLAN 可选“自动”。

可通过 `--delay 2` 增加延迟，或 `--lost-response` 模拟控制已经执行、确认响应丢失。后者应在 App 中触发结果未知与重新读取，而不是自动重发。

可选 mDNS 发现需要安装 `zeroconf`，再附加 `--advertise-ip <PC局域网IPv4>`。该依赖不是 HTTP 测试前提；未安装时使用手动地址。广告服务为 `_wifi-control._tcp.local.`，TXT `protocolVersion=1`。此实验服务类型是本项目约定，未宣称已注册为公共标准。

测试：

```powershell
python -m unittest discover -s tools/mock-device -p test_server.py
```

停止使用 Ctrl+C；重新启动改变 bootId，输出恢复初始值。

## 功能清单校验

Schema为 `protocol/capability-list.schema.json`，详细字段见[下位机对接规范](../../docs/protocol/capability-list.md)。可选依赖仅用于这项校验，HTTP服务仍无需第三方库：

```powershell
python -m pip install -r tools/mock-device/schema-requirements.txt
python tools/mock-device/validate_capabilities.py
python tools/mock-device/validate_capabilities.py path/to/your-device.json
python -m unittest discover -s tools/mock-device -p test_capability_schema.py
```

未安装jsonschema时，Schema专项测试会明确跳过，不能把跳过计为校验通过。工具还检查ID唯一和min≤max；最终固件响应长度及device/state一致性需要联调验证。
