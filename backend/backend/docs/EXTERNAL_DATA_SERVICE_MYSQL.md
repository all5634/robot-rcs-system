# 外部数据存储服务（MySQL）对接开发文档

本文档用于指导开发一个外部数据存储服务（独立服务），供 RCMS 通过 HTTP 调用并写入 MySQL。

## 1. 对接目标

- RCMS 作为调用方，向外部服务发送 5 类持久化请求。
- 外部服务将请求数据写入 MySQL，并返回 2xx。
- RCMS 只要求外部服务返回 HTTP 2xx，不强制响应体格式。
- 文档同时提供两种落地模式：
  - 基础落库模式（仅承接 RCMS 写入）。
  - 查询增强模式（在基础模式上增加可查询表，供前端/报表查询）。

## 2. RCMS 侧契约来源

请以以下代码为准：

- 数据客户端接口：`src/main/java/com/_project4/project4/client/DataClient.java`
- HTTP 调用实现：`src/main/java/com/_project4/project4/client/HttpDataClient.java`
- 配置项定义：`src/main/resources/application.properties`
- 调用链路与降级策略：`src/main/java/com/_project4/project4/service/RobotServiceImpl.java`
- WebClient Bean 定义：`src/main/java/com/_project4/project4/common/WebClientConfig.java`

## 3. RCMS -> 外部服务 HTTP 契约

RCMS 在 `robot.data-client.type=http` 时生效。

实现现状说明：

- 当前项目仅提供 `http` 模式的 `DataClient` 实现（`HttpDataClient`）。
- 若将 `robot.data-client.type` 配成其他值，应用启动阶段会因缺少 `DataClient` Bean 失败。

Base URL（默认）：

- `http://localhost:8081/api/v1`

路径配置（默认）：

- `POST /robots`
- `POST /robots/heartbeat`
- `POST /robots/status`
- `POST /robots/commands`
- `POST /robots/commands/status`

说明：

- 当前 RCMS 不会新增第 6 类写入接口给外部服务；如需支持前端查询，请在外部服务内部从现有 5 类写入中派生“查询快照/日志”。

### 3.1 保存机器人基础信息

- Method: `POST`
- Path: `/robots`

请求体示例：

```json
{
  "robotId": "6fb3e5b6-76f8-4e6f-a5f8-f89beeaab001",
  "robotCode": "RBT-001",
  "model": "MEC-EX",
  "capabilities": "move,lift,scan",
  "online": true,
  "lastHeartbeat": 1760000000000
}
```

### 3.2 保存心跳

- Method: `POST`
- Path: `/robots/heartbeat`

请求体示例：

```json
{
  "robotId": "6fb3e5b6-76f8-4e6f-a5f8-f89beeaab001",
  "timestamp": 1760000000000,
  "online": true
}
```

### 3.3 保存状态

- Method: `POST`
- Path: `/robots/status`

请求体示例：

```json
{
  "robotId": "6fb3e5b6-76f8-4e6f-a5f8-f89beeaab001",
  "status": "WORKING",
  "battery": 76,
  "position": {
    "x": 12.5,
    "y": -3.2
  },
  "timestamp": 1760000000000
}
```

说明：

- `timestamp` 由 RCMS 在发送时以 `System.currentTimeMillis()` 生成。

### 3.4 保存指令创建记录

- Method: `POST`
- Path: `/robots/commands`

请求体示例：

```json
{
  "commandId": "b4de9891-0e3b-42c3-8f6d-58f0cbf71f11",
  "robotId": "6fb3e5b6-76f8-4e6f-a5f8-f89beeaab001",
  "commandType": "DISPATCH",
  "params": {
    "x": 20,
    "y": 8
  },
  "status": "QUEUED",
  "createTime": 1760000000000
}
```

### 3.5 更新指令状态

- Method: `POST`
- Path: `/robots/commands/status`

请求体示例：

```json
{
  "commandId": "b4de9891-0e3b-42c3-8f6d-58f0cbf71f11",
  "status": "DISPATCHED",
  "updateTime": 1760000000100
}
```

## 4. MySQL 表设计（建议）

说明：

- 接口字段是 camelCase，数据库字段建议使用 snake_case。
- 时间戳建议保留 BIGINT（毫秒），避免精度损失。

```sql
CREATE TABLE IF NOT EXISTS robot_info (
  robot_id         VARCHAR(64)  NOT NULL,
  robot_code       VARCHAR(64)  NOT NULL,
  model            VARCHAR(128) NOT NULL,
  capabilities     VARCHAR(512) NOT NULL,
  online           TINYINT(1)   NOT NULL,
  last_heartbeat   BIGINT       NOT NULL,
  created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (robot_id),
  UNIQUE KEY uk_robot_code (robot_code),
  KEY idx_last_heartbeat (last_heartbeat)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS robot_heartbeat_log (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  robot_id         VARCHAR(64)  NOT NULL,
  ts_ms            BIGINT       NOT NULL,
  online           TINYINT(1)   NOT NULL,
  created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_robot_ts (robot_id, ts_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS robot_status_log (
  id               BIGINT         NOT NULL AUTO_INCREMENT,
  robot_id         VARCHAR(64)    NOT NULL,
  status           VARCHAR(32)    NOT NULL,
  battery          INT            NULL,
  position_x       DECIMAL(12,4)  NULL,
  position_y       DECIMAL(12,4)  NULL,
  ts_ms            BIGINT         NOT NULL,
  created_at       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_robot_ts (robot_id, ts_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS robot_command (
  command_id       VARCHAR(64)   NOT NULL,
  robot_id         VARCHAR(64)   NOT NULL,
  command_type     VARCHAR(64)   NOT NULL,
  params_json      JSON          NULL,
  status           VARCHAR(32)   NOT NULL,
  create_time_ms   BIGINT        NOT NULL,
  update_time_ms   BIGINT        NULL,
  created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (command_id),
  KEY idx_robot_create (robot_id, create_time_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS external_inbox_dedup (
  dedup_key        VARCHAR(128)  NOT NULL,
  created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (dedup_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 查询增强模式：机器人实时快照（推荐）
CREATE TABLE IF NOT EXISTS robot_runtime_state (
  robot_id           VARCHAR(64)    NOT NULL,
  robot_code         VARCHAR(64)    NOT NULL,
  model              VARCHAR(128)   NOT NULL,
  online             TINYINT(1)     NOT NULL,
  status_raw         VARCHAR(32)    NULL,
  battery            INT            NULL,
  position_x         DECIMAL(12,4)  NULL,
  position_y         DECIMAL(12,4)  NULL,
  current_task       VARCHAR(256)   NULL,
  last_heartbeat_ms  BIGINT         NULL,
  updated_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (robot_id),
  KEY idx_robot_code (robot_code),
  KEY idx_updated_at (updated_at),
  KEY idx_online_status (online, status_raw)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 查询增强模式：系统事件日志（推荐）
CREATE TABLE IF NOT EXISTS robot_event_log (
  id                 BIGINT         NOT NULL AUTO_INCREMENT,
  robot_id           VARCHAR(64)    NULL,
  command_id         VARCHAR(64)    NULL,
  type               VARCHAR(16)    NOT NULL,
  msg                VARCHAR(1024)  NOT NULL,
  created_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_created_at (created_at),
  KEY idx_robot_time (robot_id, created_at),
  KEY idx_type_time (type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

字段约定（查询增强模式）：

- `status_raw` 存英文原值（`WORKING/IDLE/OFFLINE/ERROR`），中文映射由上层 API 完成。
- `current_task` 建议按业务字符串持久化，例如 `DISPATCH:(20,8)`。
- `type` 建议限定为 `info/warn/error`。
- 若担心 `msg` 过长，可改为 `TEXT`。

## 5. 接口实现建议（外部服务）

### 5.1 通用要求

- 入参校验失败返回 `400`。
- 写库成功返回 `200` 或 `204`。
- RCMS 侧对响应体不做格式约束，只关心 HTTP 是否为 `2xx`。
- 不建议返回 `5xx` 后吞错；出现真实异常应返回 `5xx`，便于 RCMS 重试。

### 5.2 幂等处理建议

- `/robots`：按 `robot_id` upsert。
- `/robots/heartbeat`：日志表插入 + 主表 `last_heartbeat/online` 更新。
- `/robots/status`：日志表插入 + 主表状态快照更新（可选）。
- `/robots/commands`：按 `command_id` 幂等插入（重复请求应成功返回）。
- `/robots/commands/status`：按 `command_id` 更新状态和 `update_time_ms`。

建议使用事务：

- 心跳、状态接口若同时写“日志表 + 主表”，建议单事务提交。

### 5.3 查询增强模式的数据派生建议

若外部服务要对前端提供查询能力，建议在处理上述 5 类写入时同步维护以下数据：

- 处理 `/robots`：upsert `robot_info`，并 upsert `robot_runtime_state` 的 `robot_code/model`。
- 处理 `/robots/heartbeat`：插入 `robot_heartbeat_log`，并更新 `robot_info.last_heartbeat/online` 与 `robot_runtime_state.last_heartbeat_ms/online`。
- 处理 `/robots/status`：插入 `robot_status_log`，并更新 `robot_runtime_state.status_raw/battery/position_x/position_y`。
- 处理 `/robots/commands`：插入 `robot_command`，若 `commandType=DISPATCH` 且 `params` 含 `x/y`，更新 `robot_runtime_state.current_task`。
- 处理 `/robots/commands/status`：更新 `robot_command`，必要时将 `STOP` 成功后清空 `robot_runtime_state.current_task`。
- 以上关键变更建议追加 `robot_event_log` 记录，供日志查询。

### 5.4 字段映射建议

- `robotId -> robot_id`
- `robotCode -> robot_code`
- `lastHeartbeat -> last_heartbeat`
- `commandType -> command_type`
- `createTime -> create_time_ms`
- `updateTime -> update_time_ms`

查询增强新增映射建议：

- `status -> status_raw`
- `position.x -> position_x`
- `position.y -> position_y`
- `currentTask -> current_task`

## 6. RCMS 容错与调用行为（你需要知道）

RCMS 默认配置：

- 超时：`2000ms`
- 重试：`3` 次
- 退避：`200ms`
- 熔断：连续 `5` 次失败后熔断 `10000ms`

补充说明：

- `max-attempts=3` 表示单次业务调用内最多尝试 3 次（含首次 + 重试）。
- 重试退避采用固定 sleep（`Thread.sleep(200ms)`）。
- 熔断开启期间会直接拒绝调用并抛出 `ExternalDataClientException`。

当前代码中的业务影响（以 `RobotServiceImpl.persistToExternal(..., failFast=false)` 为准）：

- `saveRobot/saveHeartbeat/saveStatus/saveCommand/updateCommandStatus` 失败均为降级告警，不阻断主流程。
- 这些失败会记录 warn 日志与业务日志（`external persist degraded`），但对外 API 多数仍返回成功。
- 离线扫描任务（`scanOfflineRobots`）在机器人超时后会上报 `saveHeartbeat(..., online=false)`，该链路失败同样是降级。

仍可能触发 RCMS `5000` 的典型场景：

- 命令异步执行线程池拒绝任务（`RejectedExecutionException`）。
- 其它未捕获异常进入全局异常处理。

## 7. 外部服务最小 API 验收清单

- `/robots` 重复写入同 `robot_id` 不报错。
- `/robots/heartbeat` 高并发下可持续写入，且主表在线状态可更新。
- `/robots/status` 可接受 `position` 字段。
- `/robots/commands` 重复同 `command_id` 不产生脏数据。
- `/robots/commands/status` 在指令不存在时返回 4xx（建议 404）。
- 人工断开数据库后，外部服务返回 5xx，RCMS 可触发重试。
- 外部服务短时不可用时，RCMS 侧应出现 `external persist degraded` 告警日志。

查询增强模式额外验收：

- `robot_runtime_state` 可在 RCMS 重启后保留最新快照。
- `robot_event_log` 可按时间倒序分页查询，最近数据延迟小于 2 秒。
- 状态中文化不入库，查询 API 输出时再做映射。

## 8. 联调步骤（推荐）

1. 启动 MySQL 并建表。
2. 启动外部数据服务（监听 `:8081`，前缀 `/api/v1`）。
3. 将 RCMS 改为 http 模式：

```properties
robot.data-client.type=http
robot.data-client.http.base-url=http://localhost:8081/api/v1
```

4. 启动 RCMS 后依次调用：注册 -> 心跳 -> 状态 -> 下发指令 -> 查询状态。
5. 基础模式：检查四张业务表是否有对应记录。
6. 查询增强模式：额外检查 `robot_runtime_state`、`robot_event_log` 是否随写入实时更新。
7. 可选验证：停止外部服务后再次调用注册/心跳/状态，确认 RCMS 业务接口仍可返回（降级），同时记录告警日志。

## 9. 常见问题

### 9.1 外部服务挂了，但 RCMS 接口仍返回成功

这是当前实现的预期行为之一：

- `persistToExternal` 统一按降级处理（`failFast=false`）。
- 你会在日志看到 `external persist degraded`，表示外部落库失败但主流程继续。

建议：

- 结合 RCMS 日志监控告警。
- 外部服务侧做好可观测性与补偿机制（重试/对账）。

### 9.2 RCMS 报 internal server error

优先检查 RCMS 本身：

- 线程池是否打满导致命令任务被拒绝。
- 入参校验与业务对象是否完整。
- 未捕获异常是否进入全局异常处理。

其次检查外部服务（虽然通常只会降级）：

- URL/路径是否和配置一致。
- 是否因为 SQL 异常持续触发失败并导致告警堆积。

### 9.3 指令状态记录不完整

这是可预期现象：

- RCMS 的状态更新链路包含异步和降级逻辑，失败时不会阻断主流程。
- 建议外部服务对 `/robots/commands/status` 做重试或落盘补偿。

### 9.4 新增前端实时/日志接口后，外部库是否必须改

结论分两种：

- 若前端只查 RCMS：外部库不强制改（可维持基础落库模式）。
- 若前端要查外部服务或要求重启后保留实时态：建议启用查询增强模式，并增加 `robot_runtime_state`、`robot_event_log`。
