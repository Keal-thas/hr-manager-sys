# Session Notes

交付时间线. 当前实现和用法以 `README.md`, `docs/ARCHITECTURE.md`, `docs/DATA-SCOPE.md` 为准.

## 1. DOCX 工具 + HR MVP

- DOCX 转 Markdown: pandoc 转 AST, panflute 修正, olefile + openpyxl 处理 OLE 附件, 输出 `document.md`, 分节, 图片和 Excel. 规则见 `tools/export-pandoc/conversion-guide.md`.
- 可运行 MVP: 登录, 我的考勤 (手写签名确认), 消息通知, 个人中心, 考勤管理 (Excel 上传/提醒/异常/导出), 通知和模板, 部门/角色/用户.
- 源码仓库 (public) + 公有 Release 仓库 (密码保护压缩包).

## 2. 测试与质量门禁

- 后端集成测试: Auth, Attendance, Notice, Admin, PasswordPolicy, Monitoring.
- 前端单元测试, JaCoCo, Maven Enforcer, `scripts/check.sh`, pre-push.
- CI: 后端 test, 前端 test/build, package job; tag 触发 `release.yml` 发 jar.

## 3. E2E + 前端设计 + agent 工作流 + 监控

- Playwright E2E: 端口 18080, 本机 MySQL 8 (`hr_e2e`), 本地 Chrome, CI Chromium.
- `theme.js`, `AppLayout`, `ErrorBoundary`, 登录分栏, chunk 拆分, 交互元素 `data-testid`.
- `agent-check.sh` 分 quick / standard / full.
- 技术监控: Actuator + Micrometer + Prometheus, 只做技术指标; `ops/monitoring` 提供 Prometheus, Grafana 和告警.

## 4. 权限 + 生产种子 + MySQL 测试

- `AuthInterceptor` 对 `/api/admin/**` 按路径鉴权, 未映射路径默认拒绝.
- `app.seed.enabled`, prod profile 关闭演示数据.
- Excel 上传 E2E.

## 5. 企业微信 + Flyway

- `integration/wecom`: token 缓存, 异步推送, `SENT / FAILED / SKIPPED` 日志, 失败不影响站内消息.
- 只做应用消息推送, 不做 SSO, 通讯录同步, 电子签名.
- Flyway 取代手写 schema, 统一 MySQL 8, 单一 `db/migration/mysql` 迁移.
- 企微内嵌视图属于规划, 见 `docs/WECOM.md` 第 13 节.

## 6. 文档收敛 + v1.0.1 公有发布

- 删除 `docs/API.md` 和 `docs/DATA-MODEL.md`, 接口以代码/Swagger 为准, schema 以 Flyway 迁移为准.
- `docs/DECISIONS.md` 收敛为 final-state ADR; `docs/ARCHITECTURE.md` 压缩; `CLAUDE.md` 改为指向 `AGENTS.md`.
- 公有 Release `v1.0.1`: code + spec 两个 AES-256 加密包.

## 当前缺口

- 未实现需求: 企微工作台员工自助, 企微身份登录, 企微 H5, 签字过程回溯, 管理员查看企微签名, 企微消息文案/跳转, 5000 量级性能验证. 见 `README.md` TODO.
- 考勤确认没有开放时间和截止时间: 批次导入后员工任何时间都能确认, 是否限制等业务决定, 见 `docs/BUSINESS-DECISIONS.md` 第 9 条.
- 后续建议: 消息异步队列, 签名对象存储, 批量插入, 性能基线和压测.
- ADR-013 统一 MySQL 已实施.

## 发布

```text
源码仓库:   https://github.com/Keal-thas/hr-manager-sys
公有 Release: https://github.com/Keal-thas/hr-manager-sys-release
```

公有仓库只放密码保护压缩包, 密码由项目所有者单独提供, 不写入仓库.
