# 监控与指标

## 1. 范围

本项目的监控只包含技术指标:

```text
HTTP 请求
JVM
进程和系统
HikariCP 连接池
应用健康检查
```

不包含业务健康指标.

原因:

- 业务指标必须在业务事件发生处主动埋点
- 埋点会让 service 依赖 metrics, 业务逻辑和监控耦合
- 业务指标口径变化快, 容易污染监控链路
- 当前阶段没有独立 exporter, 不适合把业务数据放进 Prometheus

业务健康如果需要, 后续用独立只读 exporter 或日志平台实现, 不写进业务 service.

## 2. 技术方案

```text
Spring Boot Actuator
Micrometer
micrometer-registry-prometheus
Prometheus
Grafana
```

- Actuator 暴露监控端点
- Micrometer 在进程内维护 meter
- Prometheus registry 输出 Prometheus 文本格式
- Prometheus 每 15 秒拉取一次
- Grafana 查询 Prometheus

这是 pull 模型, 应用不主动 push.

依赖:

```text
spring-boot-starter-actuator
micrometer-registry-prometheus
```

业务代码不感知这些依赖.

## 3. 端点

默认端口 `8080`:

```text
GET /actuator/health       健康检查
GET /actuator/info         应用名, 描述, 版本
GET /actuator/metrics      指标目录
GET /actuator/prometheus   Prometheus 文本格式
```

`/api/health` 仍然保留, 返回 `ApiResponse`, 供简单探活使用.
Actuator 端点不经过 `/api/**` 的登录拦截器.

生产环境建议:

- 只在内网或管理网暴露 `/actuator`
- 通过反向代理限制来源 IP
- 或使用独立管理端口 `management.server.port=8081`
- 不要把 `/actuator/prometheus` 暴露到公网

## 4. 自动采集的指标

### 4.1 HTTP

由 Spring MVC metrics filter 自动记录:

```text
http_server_requests_seconds_count
http_server_requests_seconds_sum
http_server_requests_seconds_bucket
```

标签:

```text
method
uri
status
outcome
exception
```

`uri` 使用路由模板, 例如 `/api/my/attendance/{batchId}`, 不会因为动态 ID 造成高基数.

`application="hr-manager-sys"` 是公共标签, 在 `application.yml` 配置:

```yaml
management:
  metrics:
    tags:
      application: hr-manager-sys
```

P95/P99 依赖直方图桶:

```yaml
management:
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
```

没有这个配置时, Prometheus 里只有 count/sum, 不能计算分位数.

### 4.2 JVM

由 Spring Boot 自动注册:

```text
jvm_memory_used_bytes
jvm_memory_max_bytes
jvm_gc_pause_seconds
jvm_threads_live_threads
jvm_classes_loaded_classes
```

### 4.3 进程和系统

```text
process_cpu_usage
process_uptime_seconds
system_cpu_usage
system_load_average_1m
```

### 4.4 连接池

HikariCP 自动绑定:

```text
hikaricp_connections_active
hikaricp_connections_idle
hikaricp_connections_pending
hikaricp_connections_max
hikaricp_connections_timeout_total
```

## 5. 本地验证

```bash
mvn spring-boot:run
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/prometheus | grep http_server_requests
```

确认没有业务指标:

```bash
curl -s http://localhost:8080/actuator/prometheus | grep hr_business || echo "no business metrics"
```

## 6. Prometheus + Grafana

启动:

```bash
cd ops/monitoring
docker compose up -d
```

访问:

```text
Prometheus   http://localhost:9090
Grafana      http://localhost:3000
```

Grafana 默认账号:

```text
admin / admin
```

可以用环境变量覆盖, 不要把自定义密码提交到仓库:

```bash
GRAFANA_ADMIN_PASSWORD='<local-password>' docker compose up -d
```

Grafana 会自动加载:

```text
ops/monitoring/grafana/dashboards/hr-manager-technical.json
```

面板:

- HTTP 5xx 比例
- P95 延迟
- 进程 CPU
- Hikari pending
- 接口 QPS
- HTTP 状态码分布
- JVM 堆
- JVM 线程数
- Hikari 连接数

Prometheus 抓取目标:

```text
host.docker.internal:8080/actuator/prometheus
```

Linux 上 `extra_hosts` 已配置 `host.docker.internal:host-gateway`.
如果应用不在宿主机, 修改 `ops/monitoring/prometheus.yml` 的 targets.

Prometheus job 名称:

```text
hr-manager-technical
```

配置里会主动 drop `hr_business_.*`, 作为业务指标不进监控链路的保护.

## 7. 告警规则

`ops/monitoring/alert-rules.yml`:

| 告警 | 条件 | 级别 |
|---|---|---|
| `AppDown` | Prometheus 抓取失败 2 分钟 | critical |
| `HighHttp5xxRate` | 5 分钟 5xx 比例 > 5% | warning |
| `HighP95Latency` | P95 > 1.5s 持续 10 分钟 | warning |
| `HighProcessCpu` | 进程 CPU > 85% 持续 10 分钟 | warning |
| `HikariPoolSaturation` | pending > 5 或 active/max > 90% | warning |

规则只定义在 Prometheus. 要真正发送通知, 需要接入 Alertmanager, 当前仓库不包含.
告警阈值是演示值, 生产按实际流量调整.

## 8. 排障

应用启动失败或没有指标:

```text
1. /actuator 是否 404
2. application.yml 是否暴露 prometheus
3. micrometer-registry-prometheus 是否在 classpath
4. Prometheus targets 页面是否 UP
5. 容器内是否能访问 host.docker.internal:8080
```

Prometheus 目标不健康:

```bash
curl http://localhost:8080/actuator/prometheus
docker compose logs prometheus
```

Grafana 没有数据:

```text
1. 检查 datasource Prometheus 是否指向 http://prometheus:9090
2. 检查 dashboard 时间范围
3. 检查指标名是否被 Prometheus 改写过
```

Micrometer 命名规则:

```text
http.server.requests   -> http_server_requests_seconds_*
jvm.memory.used        -> jvm_memory_used_bytes
```

## 9. 测试

`src/test/java/com/hrmanager/support/MonitoringApiTests.java` 覆盖:

- `/actuator/health` 为 UP
- `/actuator/prometheus` 含 HTTP, JVM, 进程指标
- `/actuator/prometheus` 不含 `hr_business`
- `/actuator/metrics` 目录可读

E2E 在 `web/e2e/03-admin.spec.js` 中直接检查 health 和 prometheus.

## 10. 后续如果要做业务健康

推荐:

```text
独立只读 exporter
  -> 定时查询数据库或日志平台
  -> 计算积压, 失败, 积压趋势
  -> 暴露独立 metrics 端点
```

不推荐:

```text
业务 service 直接注入 metrics
业务 service 为了监控加 if/else
用日志字符串正则统计业务指标
```

业务报表和审计也不进 Prometheus:

| 类型 | 用途 | 存放 |
|---|---|---|
| 技术监控 | 可用性, 性能, 容量 | Prometheus |
| 业务健康 | 流程积压, 异常趋势 | 独立 exporter, 后续再做 |
| 业务报表 | 部门确认率, 月度趋势 | SQL / 报表库 |
| 审计日志 | 谁在什么时候做了什么 | 审计表 / 日志系统 |
