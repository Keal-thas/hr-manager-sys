# 人事综合管理系统

[![CI](https://github.com/Keal-thas/hr-manager-sys/actions/workflows/ci.yml/badge.svg)](https://github.com/Keal-thas/hr-manager-sys/actions/workflows/ci.yml)

一个简化的 HR 管理系统, 覆盖考勤确认, 消息通知, 部门/角色/用户管理和权限菜单.

需求来源是一份较粗的需求文档, 本项目按"能开发, 能演示, 保持简单"的原则做了取舍. 需求范围包含人事综合管理系统 PC 端和"企微同步实行"两部分; 本期只接入企业微信应用消息推送, 企微工作台员工自助、企微身份登录、企微侧电子签名和签字回溯仍未实现, 清单见 [TODO: 未实现的需求](#todo-未实现的需求).

## 技术栈

- JDK 8
- Spring Boot 2.7.18
- MyBatis Plus 3.5.7
- H2 (开发默认) / MySQL 8 (生产)
- React 18 + Vite + Ant Design 5
- Vitest (前端单元测试)
- Playwright (浏览器 E2E)
- Testcontainers (MySQL 集成测试)
- Flyway (数据库版本管理)
- 企业微信应用消息 (可选, 默认关闭)
- Spring Boot Actuator + Micrometer + Prometheus
- Prometheus + Grafana (可选监控栈)
- Apache POI (Excel 导入导出)
- BCrypt 密码加密

## 功能

- 登录: 工号 + 密码, 连续 5 次错误锁定 15 分钟, 初始密码强制修改
- 我的考勤: 查看最新考勤, 阅读即已读, 手写签名确认, 历史考勤
- 消息通知: 站内消息, 未读状态, 全部已读
- 个人中心: 联系方式修改, 密码修改
- 考勤管理: 月度考勤 Excel 上传 (新增/覆盖/增量), 明细查看, 提醒, 异常清单上传, 导出
- 通知管理: 全员/部门/指定员工发布, 通知模板
- 系统管理: 部门树, 角色权限, 用户管理, 重置密码, 启停用
- 企业微信: 站内消息同步落库, 应用消息异步推送, 发送日志, 用户 UserID 映射
- 可观测性: 健康检查, HTTP/JVM/连接池技术指标, Prometheus 抓取, Grafana 面板
- 质量保障: 后端集成测试, 前端单元测试, Playwright E2E, CI 三档校验

## 快速启动

前端构建产物不提交到 git. 先构建前端:

```bash
cd web
npm ci
npm run build
cd ..
```

打包时会检查 `src/main/resources/static/index.html`, 缺失会直接失败.

开发模式:

```bash
mvn spring-boot:run
```

或者打包运行:

```bash
mvn -DskipTests package
java -jar target/hr-manager-sys-0.0.1-SNAPSHOT.jar
```

访问:

```text
http://localhost:8080
```

接口文档:

```text
http://localhost:8080/swagger-ui.html
http://localhost:8080/v3/api-docs
```

监控:

```text
http://localhost:8080/actuator/health
http://localhost:8080/actuator/metrics
http://localhost:8080/actuator/prometheus
```

本地 Prometheus + Grafana:

```bash
cd ops/monitoring
docker compose up -d
```

Grafana: `http://localhost:3000`, Prometheus: `http://localhost:9090`.

H2 控制台:

```text
http://localhost:8080/h2-console
JDBC URL: jdbc:h2:file:./data/hr
User: sa
Password: 留空
```

首次启动会自动建表并写入演示数据. 数据文件在 `data/`, 已加入 `.gitignore`.

## 默认账号

| 角色 | 工号 | 密码 |
|---|---|---|
| 系统管理员 | 10001 | Admin1234 |
| 人事专员 | 10002 | Hr123456 |
| 普通员工 | 10003 | Emp123456 |
| 普通员工 | 10004 | Emp123456 |

## 前端开发

前端源码在 `web/`, 构建产物输出到 `src/main/resources/static/`. 这个目录只用于本地构建和打包, 已被 git 忽略.

```bash
cd web
npm install
npm run dev
```

开发地址 `http://localhost:5173`, Vite 会代理 `/api` 到 `8080`.

修改前端后重新构建:

```bash
cd web
npm test
npm run build
cd ..
mvn -DskipTests package
```

浏览器 E2E:

```bash
cd web
npm run e2e
```

默认使用本机 Chrome. 没有 Chrome 时:

```bash
cd web
npx playwright install chromium
E2E_CHANNEL=bundled npm run e2e
```

CI 使用 Playwright Chromium.

## MySQL 部署

1. 创建数据库 `hr_manager`
2. 设置 `DB_USERNAME` / `DB_PASSWORD` 环境变量, 按需修改连接串
3. 构建前端并打包:

```bash
cd web && npm ci && npm run build && cd ..
mvn -DskipTests package
```

4. 启动:

```bash
java -jar target/hr-manager-sys-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

Flyway 会在启动时自动执行 `db/migration/mysql` 下的迁移, 不再手工执行 schema 文件.

企业微信推送通过 `WECOM_*` 环境变量配置, 默认关闭. 详见 [docs/WECOM.md](docs/WECOM.md).

`prod` profile 默认关闭演示数据:

```yaml
app:
  seed:
    enabled: false
```

首次部署需要自行创建管理员账号或临时开启种子数据.

## 设计取舍

- 企业微信本期只做应用消息推送, 站内消息和 WeCom 同时发送. 需求里的企微员工自助、企微身份登录、企微侧电子签名和签字回溯未实现, 见 TODO.
- 部门最多 5 级. 原因是部门编码 10 位, 每级 2 位; 原需求里"6 级"和 10 位编码矛盾, 这里按 5 级实现.
- 考勤数据当前实现把 Excel 中识别到的非空字段原样存入 `data_json`; 空单元格和未识别列不落库. 关键字段单独建列用于查询和状态流转. 是否拆列或建子表尚未决策.
- 签名图片以 base64 存库, 方便演示; 生产建议换对象存储.
- 消息发送是同步插入, 5000 人会慢; 生产建议改异步队列.
- 不处理 OCR, 文档截图, 外部附件.
- 密码规则: 8-50 位, 必须包含大写/小写/数字, 不含汉字和空格.

## TODO: 未实现的需求

需求说明书的支持范围是"人事综合管理系统 PC 端及企微同步实行". 当前只完成企业微信应用消息推送, 以下需求项未实现:

- [ ] 企微工作台 "员工自助" 小程序: "我的考勤" 和 "消息通知" 入口, 未读数角标, 点击跳转对应页面. 需求 2.2.1.2.
- [ ] 企微身份登录: 在企微内打开时自动带出工号, 用户只输入密码, 不再手工录入工号. 需求 2.2.1.3.3 第 5 条.
- [ ] 企微 H5 员工页面: 我的考勤、消息通知、修改密码, 适配手机企微和 PC 企微. 需求 2.2.1.3, 2.2.1.4.
- [ ] 签字过程回溯: 点击签名后记录整个签字过程, 支持回放和审计. 需求 2.2.1.3.3 第 22 条.
- [ ] 管理员查看企微电子签名: 考勤明细点击 "员工本人签字确认" 显示企微侧签名图片. 当前只能查看本系统 canvas 签名. 需求 2.1 考勤管理.
- [ ] 企微消息文案和跳转对齐需求: 按需求文案区分考勤生成、考勤调整和考勤异常, 并支持从企微消息跳回对应页面. 需求 2.2.1.1.3.
- [ ] 5000 条导入和 5000 人通知的性能验证: 需求要求批量上传 180 秒内完成, 当前为逐条插入加逐人发消息, 未做压测. 需求 3.1, 3.2.

## 目录结构

```text
src/main/java/com/hrmanager
  auth        登录, 密码
  user        用户
  dept        部门
  role        角色
  attendance  考勤批次, 明细, Excel, 异常
  notice      通知, 模板, 站内消息
  integration 企业微信客户端, 异步推送, 发送日志
  dashboard   首页统计
  common      响应, 异常, 拦截器, 菜单
  config      初始化数据, MyBatis Plus, 密码
src/main/resources
  db/migration/h2     Flyway H2 迁移
  db/migration/mysql  Flyway MySQL 迁移
  static/             React 构建产物, git 忽略
src/test/java        后端集成测试和单元测试
web/                  React 源码
web/e2e/              Playwright E2E
ops/monitoring/       Prometheus, Grafana, 告警规则
.github/workflows/   CI 和 Release
```

## 开发与工作流

> AI agent 当前只改代码并直接提交, 不运行测试, 构建或 CI. 最终用户手动跑全量测试, 见 [AGENTS.md](AGENTS.md).

- [docs/](docs/README.md): 文档索引
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): 模块, 登录/考勤/通知流程
- [docs/TESTING.md](docs/TESTING.md): 测试策略和冒烟清单
- [docs/FRONTEND.md](docs/FRONTEND.md): 前端结构, 设计系统, E2E 选择器
- [docs/MONITORING.md](docs/MONITORING.md): 指标, Prometheus, Grafana, 告警
- [docs/WECOM.md](docs/WECOM.md): 企业微信集成配置, 用户映射, 失败处理
- [docs/AGENT-WORKFLOW.md](docs/AGENT-WORKFLOW.md): AI agent 任务工作流
- [docs/SECURITY.md](docs/SECURITY.md): 认证, 密码, 上传, 密钥
- [docs/DECISIONS.md](docs/DECISIONS.md): 关键决策记录
- [docs/UNIFIED-MYSQL.md](docs/UNIFIED-MYSQL.md): 统一 MySQL 方案, 测试和环境的数据库拓扑
- [docs/CODING-RULES.md](docs/CODING-RULES.md): 编码约束
- [docs/RELEASE.md](docs/RELEASE.md): 私有源码和公有压缩包流程
- [WORKFLOW.md](WORKFLOW.md): 强制开发流程, hook, CI, 数据库和提交规则
- [AGENTS.md](AGENTS.md): AI agent 必读规则
- [SESSION-NOTES.md](SESSION-NOTES.md): 本次交付, 设计取舍, 冒烟结果和后续事项

首次设置:

```bash
bash scripts/install-hooks.sh
cd web && npm ci && cd ..
```

提交前:

```bash
mvn -q -DskipTests compile
```

推送前:

```bash
bash scripts/check.sh
```

`check.sh` 会依次执行前端 `npm test`, 前端构建, 检查 `src/main/resources/static` 没有被 git 跟踪, 最后执行后端 `mvn verify`.

CI 在 `.github/workflows/ci.yml`, 打 `v*` tag 时由 `.github/workflows/release.yml` 自动构建并发布 jar. 依赖更新由 Dependabot 每周检查.

## 测试

后端:

```bash
mvn test
```

完整校验 (测试 + JaCoCo 覆盖率 + 打包):

```bash
mvn -B -ntp verify
```

覆盖率报告:

```text
target/site/jacoco/index.html
```

前端:

```bash
cd web
npm test
npm run build
```

E2E:

```bash
cd web
npm run e2e
```

三档 agent 检查:

```bash
bash scripts/agent-check.sh quick
bash scripts/agent-check.sh standard
bash scripts/agent-check.sh full
```

集成测试默认使用 H2 内存库, Flyway 自动执行 `db/migration/h2`. `MigrationConsistencyTest` 会校验 H2 和 MySQL 迁移版本, 表和列保持一致.

`MySqlSchemaIntegrationTests` 使用 Testcontainers 启动 MySQL 8, 真实执行 Flyway `db/migration/mysql` 并验证种子数据和登录. 本机没有 Docker 时该用例自动跳过, CI 会执行.

## 公有 Release

密码保护的压缩包在单独的公有仓库:

```text
https://github.com/Keal-thas/hr-manager-sys-release
```

压缩包密码由项目所有者单独提供, 不写入仓库.

## 说明

- 仓库不包含原始需求文档和 docx 文件.
- `data/`, `target/`, `web/node_modules/`, `src/main/resources/static/` 已在 `.gitignore` 中忽略.
- git 只跟踪源码, 构建产物不提交.
