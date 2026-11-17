# CLAUDE.md

Read `AGENTS.md` and `WORKFLOW.md` before changing code.

Mandatory rules:

- JDK 8 only. No Java 9+ syntax or APIs.
- Keep `ApiResponse` shape `{code, message, data}`.
- Use Flyway migrations for DB changes: add matching `db/migration/h2` and `db/migration/mysql` files. Never edit an applied migration.
- Run `bash scripts/install-hooks.sh` once.
- Run `bash scripts/check.sh` before push.
- Do not commit `tmp.docx`, `data/`, `target/`, `web/node_modules/`, `src/main/resources/static/`, `__pycache__/`, secrets, or files over 10 MB.
- After frontend changes, run `cd web && npm test && npm run build`. Do not commit the generated `src/main/resources/static`.
- After login, attendance, notice, or layout changes, run `cd web && npm run e2e`.
- Run `bash scripts/agent-check.sh full` before delivery.
- Monitoring is technical only. Do not inject metrics into business services. Update `docs/MONITORING.md` when technical metrics change.
- Add `data-testid` to interactive elements used by E2E.
- WeCom outbound app messages are supported in `integration/wecom`. Do not add SSO, directory sync, or e-signature unless requested.
- Never store the public archive password in the repository.
