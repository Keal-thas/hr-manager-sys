# CLAUDE.md

`AGENTS.md` 和 `WORKFLOW.md` 是权威规则, 改代码前先读.

要点:

- 只用 JDK 8.
- 保持 `ApiResponse` 结构 `{code, message, data}`.
- 数据库变更用 Flyway: `db/migration/h2` 和 `db/migration/mysql` 同版本新增, 不修改已应用的迁移.
- 不提交 `tmp.docx`, `data/`, `target/`, `web/node_modules/`, `src/main/resources/static/`, `__pycache__/`, 密钥, 或超过 10 MB 的文件.
- 监控只做技术指标, 不在业务 service 注入 metrics.
- 交互元素加 `data-testid`.
- 企业微信只做应用消息推送, 不加 SSO, 通讯录同步或电子签名.
- 公有压缩包密码不写入仓库.

不运行测试, 构建或 CI; 直接提交并 push, 最终用户手动跑全量测试.
