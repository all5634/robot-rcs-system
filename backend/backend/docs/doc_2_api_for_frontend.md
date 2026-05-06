# 项目4 前后端联调接口文档（v2）

## 1. 文档说明
- 服务前缀：`/api/v1/robots`、`/api/v1/logs`
- 数据格式：`application/json`
- 认证方式：当前版本无鉴权（后续如增加 token，以网关文档为准）
- 时间格式：毫秒时间戳（Unix epoch ms）
- 兼容说明：`/api/v1/robots/{robotId}/commands` 等原有接口继续保留。

## 2. 统一响应结构
所有接口都返回同一结构：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

字段说明：
- `code`: 业务码，`0` 表示成功，非 `0` 表示失败。
- `message`: 提示信息。
- `data`: 业务数据；无返回内容时为 `null`。

## 3. 业务错误码
| code | HTTP 状态 | 含义 |
|---|---|---|
| 0 | 200 | 成功 |
| 4001 | 400 | 参数校验失败 |
| 4041 | 400 | 机器人不存在 |
| 4042 | 400 | 指令不存在 |
| 5000 | 500 | 服务内部错误 |

参数校验失败时，`message` 一般为：`字段名 + 校验提示`。
例如：`robotCode must not be blank`。

## 4. 数据结构定义

### 4.1 RegisterRobotRequest
```json
{
  "robotCode": "RBT-001",
  "model": "MEC-EX",
  "capabilities": "move,lift,scan"
}
```
- `robotCode`: string，必填，机器人业务编码（用于幂等注册）。
- `model`: string，必填，型号。
- `capabilities`: string，必填，能力描述（当前为字符串，可用逗号分隔）。

### 4.2 HeartbeatRequest
```json
{
  "timestamp": 1760000000000
}
```
- `timestamp`: number，选填，必须大于 0。
- 不传时后端自动使用服务端当前时间。

### 4.3 StatusUpdateRequest
```json
{
  "status": "WORKING",
  "battery": 76,
  "position": {
    "x": 12.5,
    "y": -3.2
  }
}
```
- `status`: string，必填。
- `battery`: integer，必填，范围 0-100。
- `position`: object，必填。
- `position.x`: number，必填。
- `position.y`: number，必填。

### 4.4 SendCommandRequest
```json
{
  "commandType": "MOVE_TO",
  "params": {
    "x": 20,
    "y": 8,
    "speed": 1.5
  }
}
```
- `commandType`: string，必填。
- `params`: object，选填，任意 JSON 对象。

### 4.5 RobotControlRequest（新增）
```json
{
  "type": "DISPATCH",
  "robotId": "6fb3e5b6-76f8-4e6f-a5f8-f89beeaab001",
  "position": {
    "x": 20,
    "y": 8
  }
}
```
- `type`: string，必填，仅支持 `DISPATCH`、`STOP`、`RESUME`。
- `robotId`: string，必填。
- `position`: object，`type=DISPATCH` 时必填；其他类型可不传。
- `position.x`: number，`type=DISPATCH` 时必填。
- `position.y`: number，`type=DISPATCH` 时必填。

### 4.6 列表项 RobotSummaryResponse
```json
{
  "robotId": "6fb3e5b6-76f8-4e6f-a5f8-f89beeaab001",
  "robotCode": "RBT-001",
  "model": "MEC-EX",
  "online": true
}
```

### 4.7 详情 RobotDetailResponse
```json
{
  "robotId": "6fb3e5b6-76f8-4e6f-a5f8-f89beeaab001",
  "robotCode": "RBT-001",
  "model": "MEC-EX",
  "online": true,
  "lastHeartbeat": 1760000000000,
  "battery": 76,
  "position": {
    "x": 12.5,
    "y": -3.2
  },
  "status": "WORKING"
}
```
- 说明：`battery` 和 `position` 在机器人尚未上报状态前可能为 `null`。

### 4.8 实时状态项 RobotRealtimeResponse（新增）
```json
{
  "id": "RBT-001",
  "name": "MEC-EX",
  "status": "运行中",
  "currentTask": "DISPATCH:(20.0,8.0)"
}
```
- 字段映射：`id <- robotCode`，`name <- model`。
- 状态中文映射：
  - `WORKING -> 运行中`
  - `IDLE -> 空闲`
  - `OFFLINE -> 离线`
  - `ERROR -> 故障`
- `currentTask` 可能为 `null`。

### 4.9 日志项 LogItemResponse（新增）
```json
{
  "type": "info",
  "msg": "control command accepted: type=DISPATCH, robotId=..."
}
```
- `type`: string，可能值 `info`、`warn`、`error`。
- `msg`: string，日志消息。

### 4.10 指令返回
`SendCommandResponse`
```json
{
  "commandId": "b4de9891-0e3b-42c3-8f6d-58f0cbf71f11"
}
```

`CommandStatusResponse`
```json
{
  "commandId": "b4de9891-0e3b-42c3-8f6d-58f0cbf71f11",
  "status": "SUCCESS"
}
```

状态流转（当前实现）：
- `QUEUED` -> `DISPATCHED` -> `SUCCESS`
- 异常时：`FAILED`

## 5. 接口清单与示例

## 5.1 注册机器人
- 方法：`POST`
- 路径：`/api/v1/robots`

请求示例：
```http
POST /api/v1/robots
Content-Type: application/json

{
  "robotCode": "RBT-001",
  "model": "MEC-EX",
  "capabilities": "move,lift,scan"
}
```

成功响应：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "robotId": "6fb3e5b6-76f8-4e6f-a5f8-f89beeaab001"
  }
}
```

说明：
- 同一个 `robotCode` 重复注册会返回同一个 `robotId`（幂等）。

## 5.2 心跳上报
- 方法：`POST`
- 路径：`/api/v1/robots/{robotId}/heartbeat`

请求示例：
```http
POST /api/v1/robots/6fb3e5b6-76f8-4e6f-a5f8-f89beeaab001/heartbeat
Content-Type: application/json

{
  "timestamp": 1760000000000
}
```

成功响应：
```json
{
  "code": 0,
  "message": "success",
  "data": null
}
```

## 5.3 状态上报
- 方法：`POST`
- 路径：`/api/v1/robots/{robotId}/status`

请求示例：
```http
POST /api/v1/robots/6fb3e5b6-76f8-4e6f-a5f8-f89beeaab001/status
Content-Type: application/json

{
  "status": "WORKING",
  "battery": 76,
  "position": {
    "x": 12.5,
    "y": -3.2
  }
}
```

成功响应：
```json
{
  "code": 0,
  "message": "success",
  "data": null
}
```

## 5.4 查询机器人列表
- 方法：`GET`
- 路径：`/api/v1/robots`

成功响应：
```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "robotId": "6fb3e5b6-76f8-4e6f-a5f8-f89beeaab001",
      "robotCode": "RBT-001",
      "model": "MEC-EX",
      "online": true
    }
  ]
}
```

## 5.5 查询机器人详情
- 方法：`GET`
- 路径：`/api/v1/robots/{robotId}`

成功响应：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "robotId": "6fb3e5b6-76f8-4e6f-a5f8-f89beeaab001",
    "robotCode": "RBT-001",
    "model": "MEC-EX",
    "online": true,
    "lastHeartbeat": 1760000000000,
    "battery": 76,
    "position": {
      "x": 12.5,
      "y": -3.2
    },
    "status": "WORKING"
  }
}
```

## 5.6 发送控制指令
- 方法：`POST`
- 路径：`/api/v1/robots/{robotId}/commands`

请求示例：
```http
POST /api/v1/robots/6fb3e5b6-76f8-4e6f-a5f8-f89beeaab001/commands
Content-Type: application/json

{
  "commandType": "MOVE_TO",
  "params": {
    "x": 20,
    "y": 8,
    "speed": 1.5
  }
}
```

成功响应：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "commandId": "b4de9891-0e3b-42c3-8f6d-58f0cbf71f11"
  }
}
```

## 5.7 获取机器人实时状态（新增）
- 方法：`GET`
- 路径：`/api/v1/robots/realtime`

成功响应：
```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": "RBT-001",
      "name": "MEC-EX",
      "status": "运行中",
      "currentTask": "DISPATCH:(20.0,8.0)"
    }
  ]
}
```

说明：
- 建议前端每 1 到 2 秒轮询一次该接口。

## 5.8 控制指令接口（新增）
- 方法：`POST`
- 路径：`/api/v1/robots/control`

请求示例 1（DISPATCH）：
```http
POST /api/v1/robots/control
Content-Type: application/json

{
  "type": "DISPATCH",
  "robotId": "6fb3e5b6-76f8-4e6f-a5f8-f89beeaab001",
  "position": {
    "x": 20,
    "y": 8
  }
}
```

请求示例 2（STOP）：
```http
POST /api/v1/robots/control
Content-Type: application/json

{
  "type": "STOP",
  "robotId": "6fb3e5b6-76f8-4e6f-a5f8-f89beeaab001"
}
```

请求示例 3（RESUME）：
```http
POST /api/v1/robots/control
Content-Type: application/json

{
  "type": "RESUME",
  "robotId": "6fb3e5b6-76f8-4e6f-a5f8-f89beeaab001"
}
```

成功响应：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "commandId": "b4de9891-0e3b-42c3-8f6d-58f0cbf71f11"
  }
}
```

## 5.9 查询日志列表（新增）
- 方法：`GET`
- 路径：`/api/v1/logs`

成功响应：
```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "type": "info",
      "msg": "command success: commandId=..."
    }
  ]
}
```

## 5.10 查询指令状态
- 方法：`GET`
- 路径：`/api/v1/robots/{robotId}/commands/{commandId}`

成功响应：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "commandId": "b4de9891-0e3b-42c3-8f6d-58f0cbf71f11",
    "status": "SUCCESS"
  }
}
```

## 6. 错误响应示例

机器人不存在：
```json
{
  "code": 4041,
  "message": "robot not found",
  "data": null
}
```

参数错误（示例）：
```json
{
  "code": 4001,
  "message": "battery must be less than or equal to 100",
  "data": null
}
```

## 7. 联调建议
- 建议前端使用 `code === 0` 作为业务成功判断，不以 HTTP 200/400 单独判断业务成功。
- 机器人在线判断可直接用 `online` 字段。
- 实时态页面建议优先使用 `/api/v1/robots/realtime`，并按 1 到 2 秒轮询。
- 若需要更稳妥在线态，可结合 `lastHeartbeat` 做兜底（例如超过 15 秒未更新提示“可能离线”）。
- 控制指令建议使用 `/api/v1/robots/control`；`DISPATCH` 时请传 `position`，不要再传 `zone`。
- 指令下发后建议前端轮询指令状态接口，直到 `SUCCESS` 或 `FAILED`。
- 详情接口中 `battery`、`position` 可能为 `null`，页面渲染需做空值保护。
