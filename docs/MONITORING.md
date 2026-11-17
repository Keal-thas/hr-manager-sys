# 监控与指标

## 1. 目标

系统需要回答四类问题:

1. 应用是否存活, 依赖是否正常
2. 接口是否变慢, 错误率是否升高
3. 业务是否在正常流转: 登录, 考勤上传, 考勤确认, 通知发送
4. JVM 是否健康: 堆内存, CPU, 线程

实现方式:

```text
Spring Boot Actuator
Micrometer
Prometheus registry
Prometheus (scrape + alert rules)
Grafana (dashboard)
```

## 2. 端点

默认端口 `8080`:

```text
GET /actuator/health       健康检查, 含 db, diskSpace, ping
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

## 3. 自定义业务指标

| 指标 | 类型 | 标签 | 含义 |
|---|---|---|---|
| `hr_auth_login_total` | counter | `result` | 登录结果: success, bad_password, locked, unknown_user, disabled, invalid |
| `hr_auth_password_change_total` | counter | `result` | 改密成功/失败 |
| `hr_attendance_upload_total` | counter | `mode` | 考勤上传方式: NEW, OVERWRITE, INCREMENTAL |
| `hr_attendance_confirm_total` | counter | `result` | 员工签字确认次数 |
| `hr_attendance_remind_total` | counter | `type` | 提醒类型: ATTENDANCE, ABNORMAL |
| `hr_attendance_penalty_upload_total` | counter | 无 | 异常清单上传次数 |
| `hr_attendance_penalty_rows_total` | counter | 无 | 异常清单导入行数 |
| `hr_notice_publish_total` | counter | `target` | 通知发布目标类型: ALL, DEPT, USER |
| `hr_notice_recipients_total` | counter | 无 | 通知触达人数累计 |
| `hr_message_send_total` | counter | `type` | 站内消息类型: ATTENDANCE, NOTICE, ATTENDANCE_REMIND, ATTENDANCE_ABNORMAL |
| `hr_users_active` | gauge | 无 | 有效用户数 |
| `hr_departments_active` | gauge | 无 | 有效部门数 |
| `hr_attendance_batches` | gauge | 无 | 已发布考勤批次数 |
| `hr_attendance_unconfirmed` | gauge | 无 | 未确认考勤记录数 |
| `hr_messages_unread` | gauge | 无 | 未读站内消息数 |

框架自动提供:

```text
http_server_requests_seconds_*     接口 QPS, 状态码, P50/P95/P99
jvm_memory_used_bytes              JVM 堆/非堆
jvm_threads_live_threads           线程数
process_cpu_usage                  进程 CPU
system_cpu_usage                   系统 CPU
hikaricp_connections_*             连接池
```

所有指标自动带 `application="hr-manager-sys"` 标签.

## 4. 本地验证

```bash
mvn spring-boot:run
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/prometheus
```

登录一次后可以看到业务计数:

```bash
curl -c /tmp/hr-cookie \
  -H 'Content-Type: application/json' \
  -d '{"employeeNo":"10001","password":"Admin1234"}' \
  http://localhost:8080/api/auth/login

curl http://localhost:8080/actuator/prometheus | grep hr_auth_login
```

## 5. Prometheus + Grafana

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
ops/monitoring/grafana/dashboards/hr-manager-overview.json
```

面板包含:

- HTTP 5xx 比例
- P95 延迟
- 有效用户数
- 未确认考勤
- 接口 QPS
- 登录结果分布
- 考勤/通知业务量
- JVM 堆
- 未读消息
- 进程 CPU

Prometheus 抓取目标:

```text
host.docker.internal:8080/actuator/prometheus
```

Linux 上 `extra_hosts` 已配置 `host.docker.internal:host-gateway`.
如果应用不在宿主机, 修改 `ops/monitoring/prometheus.yml` 的 targets.

## 6. 告警规则

`ops/monitoring/alert-rules.yml`:

| 告警 | 条件 | 级别 |
|---|---|---|
| `HighHttp5xxRate` | 5 分钟 5xx 比例 > 5% | warning |
| `LoginFailureSpike` | 10 分钟登录失败 > 20 次 | warning |
| `AttendanceUnconfirmedBacklog` | 未确认考勤 > 100 持续 30 分钟 | info |
| `AppDown` | Prometheus 抓取失败 2 分钟 | critical |

规则只定义在 Prometheus. 要真正发送通知, 需要接入 Alertmanager, 当前仓库不包含.
告警阈值是演示值, 生产按实际流量调整.

## 7. 排障

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
hr.auth.login                 -> hr_auth_login_total
hr.attendance.confirm         -> hr_attendance_confirm_total
http.server.requests          -> http_server_requests_seconds_*
```

## 8. 测试

`src/test/java/com/hrmanager/support/MonitoringApiTests.java` 覆盖:

- `/actuator/health` 为 UP
- 登录后 `/actuator/prometheus` 含 `hr_auth_login_total`
- 含 `hr_users_active` 和 `http_server_requests_seconds`
- `/actuator/metrics` 目录可读

E2E 在 `web/e2e/03-admin.spec.js` 中直接检查 health 和 prometheus.
