# 统一 MySQL 方案

状态: 已决定, 待实施

取代: ADR-006 (H2 开发, MySQL 生产)

## 1. 背景

当前 H2 和 MySQL 两套数据库并行:

```text
本地开发    H2 file   ./data/hr
自动化测试  H2 mem    hr_test
E2E         H2 mem    hr_e2e
UAT/生产    MySQL 8
```

由此维护两份迁移:

```text
src/main/resources/db/migration/h2/V<version>__<name>.sql
src/main/resources/db/migration/mysql/V<version>__<name>.sql
```

已经出现漂移:

| 差异 | 说明 |
|---|---|
| 索引数量 | MySQL 8 个, H2 5 个. `idx_department_parent`, `idx_user_role`, `idx_batch_ym` 只存在于 MySQL |
| `sys_role.code` | H2 `VARCHAR(10)`, MySQL `VARCHAR(50)` |
| 长文本列 | H2 `TEXT`, MySQL `LONGTEXT` (`signature_image`, `data_json`) |
| 时间列 | H2 `TIMESTAMP`(16 列), MySQL `DATETIME` |

`MigrationConsistencyTest` 只比较迁移版本, 表名和列名, 覆盖不到类型, 可空性, 默认值, 约束和索引.

## 2. 决策

- 所有环境统一使用 MySQL 8: 本地开发, 自动化测试, E2E, UAT, 生产.
- 移除 H2: 依赖, 迁移目录, 配置分支, 相关测试.
- 只保留一份迁移 `db/migration/mysql`.
- 本地自动化测试使用本机 MySQL 的临时 schema, 每次运行新建, 不依赖容器.

## 3. 环境拓扑

| 环境 | 数据库 | schema | 生命周期 |
|---|---|---|---|
| 本地开发 | 本机 MySQL 8 | `hr_dev` | 持久, 手工清理 |
| 自动化测试 | 本机 MySQL 8 | `hr_test_<runId>` | 每次运行新建 |
| E2E | 本机 MySQL 8 | `hr_e2e_<runId>` | 每次运行新建 |
| UAT | 固定 MySQL 8 | `hr_manager` | 持久 |
| 生产 | 固定 MySQL 8 | `hr_manager` | 持久 |

本地开发和测试都需要本机有 MySQL 8 在运行.

## 4. 测试库生命周期

原则: **启动时清理, 不依赖结束时删除.**

```text
1. 连到服务器(不指定库), 清理历史遗留:
   DROP DATABASE IF EXISTS hr_test_*   (逐个匹配)

2. 建本次运行用的库:
   CREATE DATABASE hr_test_<runId>
     CHARACTER SET utf8mb4
     COLLATE utf8mb4_0900_ai_ci
   runId 取时间戳或 UUID, 保证并行运行不冲突

3. 应用和 Flyway 连接 hr_test_<runId>, 执行 db/migration/mysql

4. 测试结束不删除
```

原因:

- 进程被 kill, IDE 停止, CI 超时时没有"结束"这个时机, 结束时删除会漏.
- 结束时连接池可能还持有该库的连接, `DROP DATABASE` 会失败.
- 启动时清理时没有任何连接, 删除一定成功, 且崩溃残留能自愈.

建库不能交给 Flyway: JDBC URL 里带库名, 库不存在时连接阶段就失败.

## 5. 连接与会话保证

会话级差异通过连接串固定, 不依赖服务器当前值:

```text
jdbc:mysql://localhost:3306/hr_test_<runId>
  ?sessionVariables=sql_mode='STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION',time_zone='+08:00'
  &connectionTimeZone=Asia/Shanghai
  &forceConnectionTimeZoneToSession=true
  &characterEncoding=utf8
  &useSSL=false
  &allowPublicKeyRetrieval=true
```

要点:

- `sql_mode` 固定为严格模式, 超长或非法值报错, 不静默截断.
- 时区用 `+08:00`, 不用 `Asia/Shanghai`. 官方镜像和部分实例没有加载 `mysql.time_zone_name`, 用名字会报 `Unknown or incorrect time zone`.
- `connectionTimeZone` 只影响驱动解释, 需要配合 `forceConnectionTimeZoneToSession=true` 才对会话生效 (Connector/J 8.0.23+).
- 连接串只约束走这个 DataSource 的连接, 不约束运维手工执行的 SQL.
- 不要单独配置 `spring.flyway.url`, 否则会话参数丢失. Flyway 复用 datasource.

连接串无法覆盖的服务器级参数:

| 参数 | 影响 | 处理 |
|---|---|---|
| `lower_case_table_names` | 表名大小写敏感性, 初始化后不可改 | 标识符全小写; 启动断言 |
| `character_set_server` | 建库建表未指定字符集时的默认值 | DDL 显式写 `utf8mb4`; 建库时显式指定 |
| `max_allowed_packet` | 大字段和批量插入的上限 | 本地和 UAT/生产保持一致 |
| `@@version` | 语法和功能可用性 | 统一 MySQL 8 次版本; 启动断言 |

## 6. 启动断言

测试和开发启动时校验服务器环境, 不一致直接失败:

```sql
SELECT @@version,
       @@sql_mode,
       @@lower_case_table_names,
       @@character_set_server,
       @@time_zone;

SELECT COUNT(*) FROM mysql.time_zone_name;
```

预期值写入 `docs/TESTING.md`, 断言失败时提示具体差异项.

## 7. 改动清单

| 文件 | 改动 |
|---|---|
| `pom.xml` | 移除 `com.h2database:h2` 依赖 |
| `src/main/resources/application.yml` | datasource 改 MySQL; flyway locations 改 `db/migration/mysql` |
| `src/main/resources/application-e2e.yml` | datasource 改 MySQL 临时 schema; flyway locations 改 `db/migration/mysql` |
| `src/main/resources/application-prod.yml` | 不变 |
| `src/test/resources/application-test.yml` | 改 MySQL 临时 schema; flyway locations 改 `db/migration/mysql` |
| `src/test/resources/application-mysql-test.yml` | 与 test 配置合并后删除 |
| `src/main/resources/db/migration/h2/` | 删除 |
| `src/test/java/com/hrmanager/support/MigrationConsistencyTest.java` | 删除 |
| `src/test/java/com/hrmanager/support/MySqlSchemaIntegrationTests.java` | 合并进常规测试或改为 schema 对比 |
| `src/test/java/com/hrmanager/support/AbstractApiTest.java` | 测试 profile 指向 MySQL |
| `scripts/e2e-server.sh` | e2e 启动前需要可用的 MySQL |
| `.github/workflows/ci.yml` | backend 和 e2e job 增加 MySQL service |
| `README.md` | 技术栈, 启动方式, 测试说明 |
| `WORKFLOW.md`, `AGENTS.md` | 环境要求和迁移规则 |
| `docs/TESTING.md` | 测试数据库说明和预期服务器配置 |
| `docs/DATA-MODEL.md` | 只保留一份迁移路径 |
| `docs/ARCHITECTURE.md` | 数据库说明 |
| `docs/AGENT-WORKFLOW.md` | 迁移规则 |
| `docs/DECISIONS.md` | ADR-006 标记为被 ADR-013 取代 |

## 8. 需要先修复的测试

MySQL `DATETIME` 默认精度为秒, 同一秒内多条记录会让 `ORDER BY created_at DESC LIMIT 1` 结果不确定. H2 的 `TIMESTAMP` 有微秒, 掩盖了该问题.

以下位置需要补 `id` 作为排序兜底:

```text
AttendanceService.latestBatch           AttendanceService.java:91, 95
AttendanceService.findByYearMonth       AttendanceService.java:105
AttendanceService 记录查询              AttendanceService.java:397
UserService 列表                        UserService.java:52
NoticeService 列表和模板                NoticeService.java:113, 128
MessageService 列表                     MessageService.java:57
WeComController 日志                    WeComController.java:40
```

## 9. 新的护栏

删除 `MigrationConsistencyTest` 后, 保留一项结构校验:

```text
1. 在同一个 MySQL 实例新建 scratch schema hr_schema_check
2. 执行 db/migration/mysql
3. 与 UAT/生产的实际 schema 比较 表, 列, 可空性, 默认值, 索引
```

它能发现跨库对比发现不了的问题:

- 生产被手工 `ALTER TABLE` 修改
- MySQL DDL 不支持事务, 迁移中途失败留下半执行 schema
- 生产落后版本或被 `flyway repair` 过

比较时注意:

- 连接参数加 `nullCatalogMeansCurrent=true`, 否则 metadata 会扫到其他 schema.
- 类型按语义归一 (`DATETIME`/`TIMESTAMP` 视为同类), 否则会产生噪声.

## 10. 后果

正面:

- 迁移只维护一份.
- 测试和生产使用同一数据库引擎, 不存在结构漂移.
- 不再需要 H2 和 MySQL 的类型映射对比.
- 测试能暴露排序, 字符集, 严格模式等真实差异.

负面:

- 本机必须有 MySQL 8, 这是开发和测试的新前置条件.
- 自动化测试比 H2 慢, 每次运行都要建库并执行迁移.
- 发布物不再免依赖运行: `java -jar` 需要可用的 MySQL. 演示和交付方式需要调整.
- `DATETIME` 秒级精度会暴露既有查询的排序不确定问题, 需要按第 8 节修复.

## 11. 验收

- `mvn test` 在本机 MySQL 上通过, 每次运行使用新的 schema.
- 人为中断一次测试后, 下次运行能自动清理残留 schema.
- `mvn verify` 和 pre-push 流程在新环境下通过.
- `npm run e2e` 在 MySQL 上通过.
- `grep -r "h2" src` 无业务残留, `db/migration` 下只有 `mysql`.
- `flyway validate` 通过.
- scratch schema 与 UAT/生产 schema 对比无差异.

## 12. 不在本方案范围

- 迁移到 Liquibase.
- 支持 MySQL 以外的数据库.
- 监控和数据备份策略调整.
