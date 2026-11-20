# Agent 工作流

> 规则以 `AGENTS.md` 为准: agent 不运行测试, 构建或 CI 检查; 直接提交并 push, 最终用户手动跑全量测试.

本文档只描述任务怎么做, 不重复规则和检查命令.

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
- 涉及数据库先看 `db/migration/mysql`

## 2. 任务分类

### 2.1 后端功能

适用: 新接口, 业务规则, 状态流转.

1. 在对应模块 `controller/service/mapper/entity` 定位改动点
2. 保持 `ApiResponse` 结构
3. 业务错误抛 `BusinessException`
4. JDK 8 语法
5. 补 `@SpringBootTest + MockMvc` 测试
6. 如涉及模块或流程, 更新 `docs/ARCHITECTURE.md`

### 2.2 数据库变更

1. 在 `db/migration/mysql` 新增迁移
2. 修改 entity / mapper
3. 修改 `DataInitializer` (如果种子数据受影响)
4. 补测试

后端测试需要本机 MySQL 8. 不修改已应用的迁移.

### 2.3 前端功能

1. 先看 `web/src/App.jsx` 的视图映射
2. 页面放 `web/src/views`, 共享布局放 `web/src/layouts`, 共享组件放 `web/src/components`
3. 统一用 `web/src/api.js`
4. 新交互元素加 `data-testid`
5. 需要新设计 token 时改 `web/src/theme.js` 或 `styles.css`
6. 更新 `docs/FRONTEND.md` 的目录或选择器清单

### 2.4 E2E

1. 在 `web/e2e` 新增或用例命名 `NN-场景.spec.js`
2. 复用 `web/e2e/support/auth.js`
3. 优先 `getByTestId`
4. 不依赖测试顺序和共享可变数据
5. 需要新种子数据时, 优先在 `DataInitializer` 增加稳定数据

本机默认用系统 Chrome, CI 用 Playwright Chromium. E2E 端口固定 18080.

### 2.5 监控/指标

当前只做技术指标.

1. 优先复用 Actuator/Micrometer 自动指标
2. 不在业务 service 中注入 metrics
3. 不新增业务健康埋点
4. 确实需要业务健康时, 先设计独立只读 exporter
5. 更新 `docs/MONITORING.md` 指标和告警说明
6. 补 `MonitoringApiTests`

### 2.6 企业微信

1. 只改 `integration/wecom`, 不在业务 service 里写 HTTP
2. 新增推送场景使用 `WeComNotifier`, 不要绕开日志
3. 用户映射优先 `wecom_user_id`, 回退工号
4. secret 只从环境变量读取
5. 补 `WeComClientTest` 或 `WeComNotifierTest`
6. 更新 `docs/WECOM.md`

### 2.7 发布/流程

1. 更新 `README.md`, `SESSION-NOTES.md`
2. 更新相关 docs
3. 检查 git 只包含源码
4. 提交并 push

## 3. 交接

最终回复说明:

```text
## 改动
- ...

## 文档
- ...

## 风险
- ...

## 后续
- ...
```

验证结果由最终用户执行, 不在 agent 回复里声称 PASS.

## 4. 常见坑

- AntD 两个汉字的按钮可访问名会变成"保 存", E2E 用 `data-testid`
- AntD Form 的 `getByLabel` 在 Modal 内要限定 modal 范围
- 前端构建产物在 `src/main/resources/static`, 不要提交
- 数据库迁移只维护 `db/migration/mysql`, 不修改已应用迁移
- JDK 8 不支持 `var`, records, text blocks, `List.of`
- 新增 admin 接口时必须同步更新 `AuthInterceptor.requiredPermission`
- 改动作业前先看 `git status`, 不要把用户未完成的改动一起格式化
- E2E 服务端口固定 `18080`, 避免和开发环境 `8080` 冲突
- `npm run e2e` 会先 build, 再启动 Spring Boot, 最后跑 Playwright
