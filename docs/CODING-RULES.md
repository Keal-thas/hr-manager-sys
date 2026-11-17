# 编码约束

## 1. 后端

- JDK 8 语法.
- 禁止 `var`, records, text blocks, `List.of`, `Map.of`, switch expression.
- 包结构按模块: `com.hrmanager.<module>`.
- Controller 只做参数接收和返回.
- 业务规则放 Service.
- 数据访问用 Mapper.
- 统一异常 `BusinessException`.
- 统一响应 `ApiResponse<T>`.
- 分页统一 `PageResult<T>`.
- 写操作加 `@Transactional`.
- 参数校验在 Service 层做业务校验, 简单格式校验可以用注解.
- 不打印密码, token, 密钥.
- 时间统一 `LocalDateTime`.

## 2. 命名

```text
Controller  XxxController
Service     XxxService
Mapper      XxxMapper
Entity      Xxx
表          sys_xxx / xxx_xxx
字段        小写下划线
```

## 3. 数据库

- 表名和字段名小写下划线.
- 主键 `id BIGINT AUTO_INCREMENT`.
- 时间字段 `created_at`, `updated_at`.
- 逻辑删除用 `status`.
- 变更必须使用 Flyway 迁移.
- H2 和 MySQL 各新增同版本迁移文件.
- 不修改已应用的迁移.
- 必须更新 `docs/DATA-MODEL.md`.

## 4. 前端

- React 18 函数组件 + hooks.
- Ant Design 5.
- 统一使用 `web/src/api.js`.
- 不在组件里直接写 axios.
- 表格分页由后端控制.
- 表单提交后刷新列表.
- 不手工编辑 `src/main/resources/static`.
- `src/main/resources/static` 是构建产物, 不提交到 git.
- 新交互元素加 `data-testid`, 供 Playwright 使用.
- 设计 token 放在 `web/src/theme.js` 和 `web/src/styles.css`.
- 修改后执行 `npm test && npm run build`.
- 主流程改动执行 `npm run e2e`.

## 5. Git

- 一个提交只做一件事.
- 提交信息使用 conventional commits.
- 不提交生成目录和密钥.
- 不绕过 hooks.
- 大文件先确认.

## 6. 文档

行为或结构变更必须更新:

- `README.md` 如果运行方式变化
- `docs/DATA-MODEL.md` 如果表结构变化
- `docs/API.md` 如果接口变化
- `docs/ARCHITECTURE.md` 如果模块或流程变化
- `docs/FRONTEND.md` 如果前端结构或选择器约定变化
- `docs/MONITORING.md` 如果指标, 告警或监控端点变化
- `docs/AGENT-WORKFLOW.md` 如果检查分层或 agent 流程变化
- `SESSION-NOTES.md` 如果交付范围变化

## 7. 监控

- 只做技术指标: HTTP, JVM, 进程, HikariCP, health.
- 不在业务 service 中注入 metrics.
- 不为监控在业务逻辑里加分支.
- 指标标签只放低基数字段.
- 禁止把用户 ID, 工号, 时间戳放进标签.
- 业务健康如需监控, 先设计独立只读 exporter.
- 技术指标或告警变更同步更新 `docs/MONITORING.md`.

## 8. 企业微信

- 只在 `integration/wecom` 中调用企业微信接口.
- 不在业务 service 中直接写 HTTP.
- token 只在内存缓存.
- secret 只能用环境变量或密钥管理.
- 企业微信失败不能回滚站内消息.
- 新增推送场景必须写 `wecom_message_log`.

## 9. DOCX 工具

- 位置 `.local/export-pandoc/`.
- 修改转换规则时更新 `conversion-guide.md`.
- 不提交原始 docx.
- 不把转换器规则写死到无关模块.
