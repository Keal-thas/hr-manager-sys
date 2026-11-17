# AGENTS.md

This file is mandatory for AI agents working in this repository.

## Read first

1. `README.md`
2. `WORKFLOW.md`
3. `docs/DATA-MODEL.md`
4. `docs/ARCHITECTURE.md`
5. `docs/API.md`
6. `docs/TESTING.md`
7. `docs/FRONTEND.md`
8. `docs/MONITORING.md`
9. `docs/AGENT-WORKFLOW.md`
10. `SESSION-NOTES.md`

## Stack constraints

- JDK 8. Do not use `var`, records, text blocks, `List.of`, `Map.of`, switch expressions, or any Java 9+ API.
- Spring Boot 2.7.x, MyBatis Plus 3.5.x.
- React 18, Vite 5, Ant Design 5.
- H2 for development, MySQL for production.
- Keep API responses in the `ApiResponse` shape `{code, message, data}`.
- Keep existing module boundaries: `auth`, `user`, `dept`, `role`, `attendance`, `notice`, `dashboard`, `common`, `config`.
- Do not add WeCom or other external integrations unless explicitly requested.

## Required workflow

1. Install hooks once:
   ```bash
   bash scripts/install-hooks.sh
   ```
2. Before commit:
   ```bash
   mvn -q -DskipTests compile
   ```
   The pre-commit hook runs this automatically.
3. Before push:
   ```bash
   bash scripts/check.sh
   ```
   The pre-push hook runs frontend tests and build, checks generated assets are not tracked, then runs backend `mvn verify`.

4. For a full gate including browser E2E:
   ```bash
   bash scripts/agent-check.sh full
   ```

   Tiered checks:

   ```bash
   bash scripts/agent-check.sh quick
   bash scripts/agent-check.sh standard
   bash scripts/agent-check.sh full
   ```
5. Do not bypass hooks with `--no-verify` unless the user explicitly asks.

## Forbidden in git

- `tmp.docx` and any original requirements docx
- `data/`
- `target/`
- `web/node_modules/`
- `src/main/resources/static/`
- `__pycache__/`
- `.env`, private keys, tokens, passwords
- files larger than 10 MB
- archive passwords in code, docs, commit messages, or the public release repo

## Database changes

When a table or column changes, update all of:

- `src/main/resources/schema.sql`
- `src/main/resources/schema-mysql.sql`
- entities and mappers
- `DataInitializer` if seed data is affected
- tests if behavior changes

Keep H2 and MySQL schemas consistent.

Always update:

- `docs/DATA-MODEL.md` for table or enum changes
- `docs/API.md` for endpoint changes
- `docs/ARCHITECTURE.md` for module or flow changes

## Frontend

- Use Ant Design components and existing `api` wrapper.
- Keep layout and shared components in `web/src/layouts` and `web/src/components`.
- Add `data-testid` to interactive elements used by E2E.
- Follow the design tokens in `web/src/theme.js`.
- After changing frontend code, run:
  ```bash
  cd web && npm test && npm run build
  ```
- `src/main/resources/static` is generated and git-ignored. Do not commit it.
- Build the frontend before packaging the jar, otherwise the jar has no UI.
- Do not edit `src/main/resources/static` by hand.

## Tests

- Add or update tests for behavior changes.
- `mvn -B -ntp verify` must pass.
- `cd web && npm test` must pass.
- For login, attendance, notice, or layout changes, add or update `web/e2e/*.spec.js` and run `cd web && npm run e2e`.
- Keep `schema.sql` and `schema-mysql.sql` in sync; `SchemaConsistencyTest` enforces it.
- Do not weaken or delete tests to make CI pass.

## DOCX conversion tooling

- Location: `.local/export-pandoc/`.
- If conversion rules change, update `conversion-guide.md` and rerun the converter.
- Do not add the original docx to git.
- The public release archive is maintained manually. Never put its password in the repository.

## Monitoring

- Business events must update `BusinessMetrics`.
- Do not add high-cardinality labels (user id, employee no, timestamps).
- Add DB snapshot gauges in `MetricsConfig`.
- Update `docs/MONITORING.md` when metrics change.
- Actuator endpoints are `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`.

## Commit style

Use concise conventional commits:

```text
feat: add ...
fix: correct ...
docs: update ...
chore: add ...
test: cover ...
```

Do not mix unrelated changes in one commit.
