# RCMS 部署文档

## 1. 部署目标

本文档用于最短路径完成 RCMS 部署，覆盖：

- 本地运行（dev）
- 测试环境运行（test）
- 生产容器部署（prod）

## 2. 环境准备

- JDK 17
- Docker 24+
- 可访问的外部数据存储服务（prod 场景）

## 3. 配置说明

- 公共配置：src/main/resources/application.properties
- 开发配置：src/main/resources/application-dev.properties
- 测试配置：src/main/resources/application-test.properties
- 生产配置：src/main/resources/application-prod.properties

关键变量：

- robot.data-client.type：mock 或 http
- robot.data-client.http.base-url：外部服务地址
- SPRING_PROFILES_ACTIVE：dev/test/prod
- SERVER_PORT：服务端口

## 4. 本地运行

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

启动后检查：

```bash
curl -f http://localhost:8080/actuator/health
```

## 5. 容器构建与运行

### 5.1 构建镜像

```bash
docker build -t rcms:latest .
```

### 5.2 生产运行示例

```bash
docker run -d --name rcms \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SERVER_PORT=8080 \
  -e JAVA_OPTS="-Xms256m -Xmx512m" \
  -e ROBOT_DATA_CLIENT_HTTP_BASE_URL="http://external-data-service:8081/api/v1" \
  rcms:latest
```

## 6. 健康检查与探针

- 健康检查地址：/actuator/health
- 建议用于 K8s 或编排系统的 liveness/readiness 探针

探测示例：

```bash
curl -f http://localhost:8080/actuator/health
```

## 7. 日志采集

- prod 默认日志文件：/var/log/rcms/application.log
- 容器日志：docker logs rcms

建议采集字段：

- api_audit 日志（包含 method/path/code/robotId/commandId/costMs）
- ERROR/WARN 异常日志

## 8. 外部数据服务联调检查清单

1. RCMS 配置 robot.data-client.type=http
2. base-url 与路径配置可达
3. 外部服务五个接口均返回 2xx
4. 注册、心跳、状态、指令创建、指令状态更新均有落库记录

## 9. 回滚策略

- 保留上一个稳定镜像标签
- 发布失败时回滚到上一个镜像
- 配置错误时优先回退到 mock 模式进行止血
