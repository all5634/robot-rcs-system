# RCMS 项目对接说明

本项目是机器人管理核心后端服务（RCMS），提供机器人注册、心跳、状态上报、控制指令下发与查询能力，并通过 DataClient 对接外部数据存储服务。

## 1. 项目架构

- 前端监控看板：调用 RCMS REST API 展示机器人列表/详情、下发控制指令。
- 机器人端：调用 RCMS REST API 进行注册、心跳、状态上报，并轮询指令状态。
- RCMS 后端（本项目）：处理业务逻辑，维护内存态实时状态，转发持久化请求到外部数据服务。
- 外部数据存储服务：接收 RCMS 的持久化调用，负责落库与历史数据管理。

## 2. 快速启动

### 2.1 本地依赖

- Java 17
- Maven（项目内置 `mvnw`）

### 2.2 启动后端（RCMS API）

在 `project4` 目录执行：

```bash
./mvnw spring-boot:run
```

默认端口：`8080`

### 2.3 启动前端（无需 Python）

已内置开发期资源映射，启动后端后可直接访问前端：

- `http://localhost:8080/frontend/index.html`

说明：`frontend` 目录仍与后端代码物理分离，仅在运行时由后端映射为静态资源。

### 2.4 前端对接后端地址

当前推荐访问 `http://localhost:8080/frontend/index.html`（同域）。

同域模式下，可将 [../frontend/http-client.js](../frontend/http-client.js) 中 `BASE_URL` 保持为空字符串：

```js
const BASE_URL = '';
```

若你使用其他前端服务器（例如 5500 端口），请改为显式后端地址：

```js
const BASE_URL = 'http://localhost:8080';
```

如果通过同域反向代理部署，可保持 `''`。

### 2.5 常见启动冲突（8080 被占用）

如果启动时报错 `Port 8080 was already in use`：

```bash
lsof -iTCP:8080 -sTCP:LISTEN
kill $(lsof -tiTCP:8080 -sTCP:LISTEN)
```

如果普通 `kill` 后端口仍未释放，可执行：

```bash
kill -9 $(lsof -tiTCP:8080 -sTCP:LISTEN)
```

然后重新执行：

```bash
./mvnw spring-boot:run
```

### 2.6 数据客户端配置

配置文件在 [src/main/resources/application.properties](src/main/resources/application.properties)。

- `robot.data-client.type=http`：真实调用外部数据存储服务。

关键配置：

```properties
robot.data-client.type=http
robot.data-client.http.base-url=http://localhost:8081/api/v1
robot.data-client.http.timeout-ms=2000
robot.data-client.http.retry.max-attempts=3
robot.data-client.http.retry.backoff-ms=200
robot.data-client.http.circuit.failure-threshold=5
robot.data-client.http.circuit.open-ms=10000
```

## 3. 前端如何对接 RCMS

前端接口规范可参考 [docs/doc_2_api_for_frontend.md](docs/doc_2_api_for_frontend.md)。

### 3.1 统一响应结构

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

前端建议以 `code === 0` 作为业务成功判定，不单纯依赖 HTTP 状态码。

### 3.2 核心接口

服务前缀：`/api/v1/robots`

- `POST /api/v1/robots`：注册机器人
- `POST /api/v1/robots/{robotId}/heartbeat`：心跳上报
- `POST /api/v1/robots/{robotId}/status`：状态上报
- `GET /api/v1/robots`：查询机器人列表
- `GET /api/v1/robots/{robotId}`：查询机器人详情
- `POST /api/v1/robots/{robotId}/commands`：发送控制指令
- `GET /api/v1/robots/{robotId}/commands/{commandId}`：查询指令状态

### 3.3 前端联调流程建议

1. 列表页首次加载调用 `GET /api/v1/robots`。
2. 详情页按 `robotId` 调用 `GET /api/v1/robots/{robotId}`。
3. 下发指令后保存 `commandId`，轮询 `GET /api/v1/robots/{robotId}/commands/{commandId}`，直到 `SUCCESS` 或 `FAILED`。
4. 页面渲染时对 `battery`、`position` 做空值保护。

## 4. 机器人端如何对接 RCMS

机器人端可以作为 HTTP 客户端周期调用 RCMS 接口。

### 4.1 首次接入

1. 调用 `POST /api/v1/robots`，携带 `robotCode`、`model`、`capabilities`。
2. 保存响应中的 `robotId` 作为后续唯一标识。

请求示例：

```json
{
  "robotCode": "RBT-001",
  "model": "MEC-EX",
  "capabilities": "move,lift,scan"
}
```

### 4.2 心跳上报

建议每 3~5 秒上报一次：

- `POST /api/v1/robots/{robotId}/heartbeat`
- `timestamp` 可传毫秒时间戳，不传则服务端自动补当前时间

### 4.3 状态上报

建议按业务节奏（如 1~2 秒）上报：

- `POST /api/v1/robots/{robotId}/status`
- 字段：`status`、`battery(0-100)`、`position{x,y}`

### 4.4 指令执行闭环

当前版本指令由前端发起；机器人端若需要执行闭环，可按以下方式协作：

1. 前端下发指令到 RCMS，RCMS 生成 `commandId`。
2. 机器人端通过业务通道获取指令后执行。
3. 前端轮询 RCMS 查询状态（当前支持 `QUEUED -> DISPATCHED -> SUCCESS/FAILED`）。

## 5. 外部数据存储服务如何对接 RCMS

当 `robot.data-client.type=http` 时，RCMS 会调用外部数据服务进行持久化。

DataClient 契约定义在 [src/main/java/com/_project4/project4/client/DataClient.java](src/main/java/com/_project4/project4/client/DataClient.java)。

### 5.1 需要提供的 5 个接口

默认会调用以下路径（相对 `robot.data-client.http.base-url`）：

- `POST /robots`：保存机器人基础信息（saveRobot）
- `POST /robots/heartbeat`：保存心跳与在线状态（saveHeartbeat）
- `POST /robots/status`：保存状态记录（saveStatus）
- `POST /robots/commands`：保存指令创建记录（saveCommand）
- `POST /robots/commands/status`：更新指令状态（updateCommandStatus）

### 5.2 外部服务入参示例

`POST /robots`

```json
{
  "robotId": "uuid",
  "robotCode": "RBT-001",
  "model": "MEC-EX",
  "capabilities": "move,lift,scan",
  "online": true,
  "lastHeartbeat": 1760000000000
}
```

`POST /robots/heartbeat`

```json
{
  "robotId": "uuid",
  "timestamp": 1760000000000,
  "online": true
}
```

`POST /robots/status`

```json
{
  "robotId": "uuid",
  "status": "WORKING",
  "battery": 76,
  "position": {"x": 12.5, "y": -3.2},
  "timestamp": 1760000000000
}
```

`POST /robots/commands`

```json
{
  "commandId": "uuid",
  "robotId": "uuid",
  "commandType": "MOVE_TO",
  "params": {"x": 20, "y": 8},
  "status": "QUEUED",
  "createTime": 1760000000000
}
```

`POST /robots/commands/status`

```json
{
  "commandId": "uuid",
  "status": "DISPATCHED",
  "updateTime": 1760000000100
}
```

### 5.3 外部服务响应要求

- 返回任意 2xx 即视为成功。
- 非 2xx 或超时会触发 RCMS 的重试/熔断逻辑。

### 5.4 容错行为说明

- 同步关键链路（如注册、心跳、状态、创建指令）失败会向上抛错。
- 异步或后台链路（如离线扫描、异步状态更新）失败会降级记录告警日志，不阻断主线程。

## 6. 典型联调顺序（建议）

1. 启动外部数据服务。
2. 启动 RCMS。
3. 机器人调用注册接口获取 `robotId`。
4. 机器人持续上报心跳和状态。
5. 前端查看列表/详情。
6. 前端发送指令并轮询指令状态。
7. 检查 RCMS 审计日志与外部服务入库结果。

## 7. 常见问题

### 7.1 前端收到 4001

通常是 DTO 校验失败，请检查：

- `battery` 是否在 0~100
- `status` 是否符合大写格式（例如 `WORKING`）
- `commandType` 是否符合大写格式（例如 `MOVE_TO`）

### 7.2 切到 http 模式后接口报 5000

请检查：

- 外部服务是否可达（`robot.data-client.http.base-url`）
- 外部服务路径是否与本项目配置一致
- 外部服务是否返回 2xx

## 8. 相关文件

- 前端接口文档：[docs/doc_2_api_for_frontend.md](docs/doc_2_api_for_frontend.md)
- 主服务入口：[src/main/java/com/_project4/project4/Project4Application.java](src/main/java/com/_project4/project4/Project4Application.java)
- 控制器接口：[src/main/java/com/_project4/project4/controller/RobotController.java](src/main/java/com/_project4/project4/controller/RobotController.java)
- 外部服务契约：[src/main/java/com/_project4/project4/client/DataClient.java](src/main/java/com/_project4/project4/client/DataClient.java)
- 外部 HTTP 客户端：[src/main/java/com/_project4/project4/client/HttpDataClient.java](src/main/java/com/_project4/project4/client/HttpDataClient.java)
- MySQL 对接开发文档：[docs/EXTERNAL_DATA_SERVICE_MYSQL.md](docs/EXTERNAL_DATA_SERVICE_MYSQL.md)
- 配置文件：[src/main/resources/application.properties](src/main/resources/application.properties)

## 9. 真实部署最短路径（M5）

### 9.1 容器化文件

- Dockerfile：[Dockerfile](Dockerfile)
- Docker 构建忽略规则：[.dockerignore](.dockerignore)

### 9.2 环境配置模板

- 开发环境：[src/main/resources/application-dev.properties](src/main/resources/application-dev.properties)
- 测试环境：[src/main/resources/application-test.properties](src/main/resources/application-test.properties)
- 生产环境：[src/main/resources/application-prod.properties](src/main/resources/application-prod.properties)

### 9.3 一键部署示例

构建镜像：

```bash
docker build -t rcms:latest .
```

启动容器（prod）：

```bash
docker run -d --name rcms \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SERVER_PORT=8080 \
  -e JAVA_OPTS="-Xms256m -Xmx512m" \
  -e ROBOT_DATA_CLIENT_HTTP_BASE_URL="http://external-data-service:8081/api/v1" \
  rcms:latest
```

### 9.4 健康检查

- 地址：`/actuator/health`
- 检查命令：

```bash
curl -f http://localhost:8080/actuator/health
```

### 9.5 日志采集

- 生产日志文件：`/var/log/rcms/application.log`
- 容器日志：`docker logs rcms`
- 审计日志关键字：`api_audit`

### 9.6 完整部署文档

详见：[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)

## 10. 无额外依赖接口冒烟测试（推荐先跑）

项目已提供脚本：`scripts/api_smoke_test.sh`。

该脚本仅依赖系统自带 `bash + curl`，覆盖以下接口可行性校验：

- 注册机器人
- 列表查询、详情查询
- 心跳上报、状态上报
- 指令下发、指令状态查询
- 异常参数场景（battery=999，期望 400）

### 10.1 运行方式

先启动后端服务（在 `project4` 目录）：

```bash
./mvnw spring-boot:run
```

执行冒烟测试：

```bash
chmod +x scripts/api_smoke_test.sh
./scripts/api_smoke_test.sh
```

如果服务地址不是本机 8080，可通过环境变量指定：

```bash
BASE_URL=http://127.0.0.1:8080 ./scripts/api_smoke_test.sh
```

### 10.2 结果判定

- 输出 `Summary: pass=X, fail=0` 表示接口冒烟通过。
- 只要存在失败项，脚本会以非 0 退出码结束，可直接用于 CI 检查。
