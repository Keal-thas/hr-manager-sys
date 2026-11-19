## 变更说明

<!-- 说明改了什么, 为什么改, 影响范围 -->

## 检查清单

- [ ] `cd web && npm test` 通过
- [ ] `cd web && npm run build` 通过
- [ ] `mvn -B -ntp verify` 通过
- [ ] 主流程改动 `cd web && npm run e2e` 通过
- [ ] 技术指标变更已更新 `docs/MONITORING.md`
- [ ] 没有为监控在业务 service 中新增埋点
- [ ] 企业微信 secret 没有提交, 配置来自环境变量
- [ ] `src/main/resources/static` 没有被 git 跟踪
- [ ] 数据库变更已新增 H2/MySQL Flyway 迁移, 并更新实体
- [ ] 没有修改已应用的迁移
- [ ] 模块或流程变更已更新 `docs/ARCHITECTURE.md`
- [ ] 前端结构或选择器变更已更新 `docs/FRONTEND.md`
- [ ] 新增交互元素带 `data-testid`
- [ ] 测试覆盖了行为变更
- [ ] 没有提交 `tmp.docx`, `data/`, `target/`, `web/node_modules/` 或密钥
- [ ] 没有绕过 hooks
