# Agent 工作流

> 当前 AI agent 提交约定见 `AGENTS.md`: 禁止运行测试, 构建或 CI 检查; 最终用户手动跑全量测试.

本文档给 AI agent 使用. 目标是把任务拆成可验证的小步, 每一步都留下证据, 避免"改了很多但没人知道是否真的能用".

## 1. 开工前

按顺序读:

```text
AGENTS.md
README.md
WORKFLOW.md
docs/ARCHITECTURE.md
docs/TESTING.md
SESSION-NOTES.md
```

然后确认:

```bash
git status --short
git log --oneline -5
```

要求:

- 不覆盖用户未提交的改动
- 主动提交并 push, 无需逐次确认
- 先定位模块, 再读文件, 不要一上来全仓库搜索
- 涉及前端先看 `web/src/views`, `web/src/layouts`, `web/src/api.js`
- 涉及数据库先看 `db/migration` 和 `MigrationConsistencyTest`

## 2. 任务分类

### 2.1 后端功能

适用: 新接口, 业务规则, 状态流转.

步骤:

1. 在对应模块 `controller/service/mapper/entity` 定位改动点
2. 保持 `ApiResponse` 结构
3. 业务错误抛 `BusinessException`
4. JDK 8 语法
5. 补 `@SpringBootTest + MockMvc` 测试
6. 如涉及模块或流程, 更新 `docs/ARCHITECTURE.md`

验证:

```bash
mvn -q -DskipTests compile
mvn -B -ntp test
```

### 2.2 数据库变更

步骤:

1. 在 `db/migration/h2` 和 `db/migration/mysql` 新增同版本迁移
2. 修改 entity / mapper
3. 修改 `DataInitializer` (如果种子数据受影响)
4. 补测试

验证:

```bash
mvn -B -ntp -Dtest=MigrationConsistencyTest test
mvn -B -ntp -Dtest=MySqlSchemaIntegrationTests test
mvn -B -ntp verify
```

`MySqlSchemaIntegrationTests` 需要 Docker, 没有 Docker 时自动跳过.
必须保持 H2 和 MySQL 表名, 列名一致. 不修改已应用的迁移.

### 2.3 前端功能

步骤:

1. 先看 `web/src/App.jsx` 的视图映射
2. 页面放 `web/src/views`, 共享布局放 `web/src/layouts`, 共享组件放 `web/src/components`
3. 统一用 `web/src/api.js`
4. 新交互元素加 `data-testid`
5. 需要新设计 token 时改 `web/src/theme.js` 或 `styles.css`
6. 更新 `docs/FRONTEND.md` 的目录或选择器清单

验证:

```bash
cd web
npm test
npm run build
```

### 2.4 E2E

步骤:

1. 在 `web/e2e` 新增或用例文件命名 `NN-场景.spec.js`
2. 复用 `web/e2e/support/auth.js`
3. 优先 `getByTestId`
4. 不依赖测试顺序和共享可变数据
5. 需要新种子数据时, 优先在 `DataInitializer` 增加稳定数据, 不要依赖上一次运行残留

验证:

```bash
cd web
npm run e2e
```

本机默认用系统 Chrome. CI 用 Playwright Chromium.

### 2.5 监控/指标

当前只做技术指标.

步骤:

1. 优先复用 Actuator/Micrometer 自动指标
2. 不在业务 service 中注入 metrics
3. 不新增业务健康埋点
4. 确实需要业务健康时, 先设计独立只读 exporter
5. 更新 `docs/MONITORING.md` 指标和告警说明
6. 补 `MonitoringApiTests`

验证:

```bash
mvn -B -ntp -Dtest=MonitoringApiTests test
curl http://localhost:8080/actuator/prometheus
```

### 2.5.1 企业微信

步骤:

1. 只改 `integration/wecom`, 不在业务 service 里写 HTTP
2. 新增推送场景使用 `WeComNotifier`, 不要绕开日志
3. 用户映射优先 `wecom_user_id`, 回退工号
4. secret 只从环境变量读取
5. 补 `WeComClientTest` 或 `WeComNotifierTest`
6. 更新 `docs/WECOM.md`

验证:

```bash
mvn -B -ntp -Dtest=WeComClientTest,WeComNotifierTest test
```

### 2.6 发布/流程

步骤:

1. 更新 `README.md`, `SESSION-NOTES.md`
2. 更新相关 docs
3. 检查 git 只包含源码
4. 运行完整检查

验证:

```bash
bash scripts/agent-check.sh full
```

## 3. 检查分层

`scripts/agent-check.sh` 提供三档:

```bash
bash scripts/agent-check.sh quick      # 后端编译 + 前端单测
bash scripts/agent-check.sh standard   # scripts/check.sh
bash scripts/agent-check.sh full       # standard + E2E
```

对应关系:

| 档位 | 内容 | 适用 |
|---|---|---|
| quick | `mvn compile`, `npm test` | 改一处逻辑, 快速反馈 |
| standard | 前端测试/构建, 静态产物未被跟踪, `mvn verify` | 提交前 |
| full | standard + Playwright E2E | 交付前或改动登录/考勤/通知主流程 |

## 4. 完成定义

一个任务只有同时满足以下条件才算完成:

```text
[ ] 代码可编译
[ ] 相关测试通过
[ ] 新增行为有测试
[ ] 文档已更新
[ ] git status 没有意外文件
[ ] 没有提交密钥, data/, target/, node_modules/, static 产物
[ ] 关键路径有可复现的验证命令和结果
```

## 5. 交接模板

最终回复或 PR 描述使用:

```text
## 改动
- ...

## 验证
- mvn -B -ntp verify: PASS
- cd web && npm test: PASS
- cd web && npm run e2e: PASS (11 tests)

## 风险
- ...

## 后续
- ...
```

## 6. 常见坑

- AntD 两个汉字的按钮可访问名会变成"保 存", E2E 用 `data-testid`
- AntD Form 的 `getByLabel` 在 Modal 内要限定 modal 范围
- 前端构建产物在 `src/main/resources/static`, 不要提交
- H2 和 MySQL Flyway 迁移必须同步, 不修改已应用迁移
- JDK 8 不支持 `var`, records, text blocks, `List.of`
- 新增 admin 接口时必须同步更新 `AuthInterceptor.requiredPermission`
- 改动作业前先看 `git status`, 不要把用户未完成的改动一起格式化
- E2E 服务端口固定 `18080`, 避免和开发环境 `8080` 冲突
- `npm run e2e` 会先 build, 再启动 Spring Boot, 最后跑 Playwright

## 7. 目录速查

```text
src/main/java/com/hrmanager   后端模块
src/test/java                 后端测试
src/main/resources            Flyway 迁移, 配置, 静态产物
web/src                       前端源码
web/e2e                       Playwright 用例
ops/monitoring                Prometheus, Grafana
scripts                       hooks, check, e2e
docs                          设计, API, 数据模型, 测试, 监控, 前端, agent 流程
```
