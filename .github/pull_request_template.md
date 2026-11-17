## 变更说明

<!-- 说明改了什么, 为什么改, 影响范围 -->

## 检查清单

- [ ] `cd web && npm test` 通过
- [ ] `cd web && npm run build` 通过
- [ ] `mvn -B -ntp verify` 通过
- [ ] 主流程改动 `cd web && npm run e2e` 通过
- [ ] 监控/指标变更已更新 `BusinessMetrics` 和 `docs/MONITORING.md`
- [ ] `src/main/resources/static` 没有被 git 跟踪
- [ ] 数据库变更已同步 `schema.sql`, `schema-mysql.sql`, 实体和 `docs/DATA-MODEL.md`
- [ ] 接口变更已更新 `docs/API.md`
- [ ] 模块或流程变更已更新 `docs/ARCHITECTURE.md`
- [ ] 前端结构或选择器变更已更新 `docs/FRONTEND.md`
- [ ] 新增交互元素带 `data-testid`
- [ ] 测试覆盖了行为变更
- [ ] 没有提交 `tmp.docx`, `data/`, `target/`, `web/node_modules/` 或密钥
- [ ] 没有绕过 hooks
