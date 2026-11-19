# Workflow

本文档定义本仓库的人工开发流程. AI agent 规则见 `AGENTS.md`.

## 1. 环境

需要:

```text
JDK 8
Maven 3.9+
Node.js 20+
npm 10+
```

可选:

```text
MySQL 8
```

## 2. 首次设置

```bash
git clone <repo>
cd hr-manager-sys
bash scripts/install-hooks.sh
cd web && npm ci && cd ..
```

安装 hooks 后会设置 `core.hooksPath = .githooks`.

## 3. 本地检查

提交前:

```bash
mvn -q -DskipTests compile
```

推送前:

```bash
bash scripts/check.sh
```

`check.sh` 会执行:

1. `cd web && npm test`: 前端单元测试
2. `cd web && npm run build`
3. 检查 `src/main/resources/static` 没有被 git 跟踪
4. `mvn -B -ntp verify`: 后端测试 + JaCoCo 覆盖率 + 打包

先构建前端再执行 Maven, 保证打出的 jar 包含最新前端.

分档检查:

```bash
bash scripts/agent-check.sh quick      # 编译 + 前端单测
bash scripts/agent-check.sh standard   # check.sh
bash scripts/agent-check.sh full       # check.sh + E2E
```

E2E:

```bash
cd web && npm run e2e
```

## 4. Git hooks

### pre-commit

- 拒绝 `tmp.docx`, `data/`, `target/`, `web/node_modules/`, `src/main/resources/static/`, `__pycache__/`, 私钥, `.env`
- 拒绝大于 10 MB 的文件
- 扫描疑似密钥
- `git diff --check` 空白检查
- `mvn -q -DskipTests compile`

### pre-push

- `npm test`
- `npm run build`
- 如果 `src/main/resources/static` 被 git 跟踪, 拒绝推送
- `mvn -B -ntp verify`

## 5. 分支和提交

推荐分支:

```text
feature/<name>
fix/<name>
chore/<name>
```

提交信息:

```text
feat: add attendance reminder
fix: handle empty excel row
docs: update workflow
chore: upgrade dependency
test: add login test
```

规则:

- 一个提交只做一件事
- 不提交无关格式化
- 不提交生成目录
- 不提交原始需求 docx
- PR 使用 `.github/pull_request_template.md` 的检查清单
- PR 必须等 CI 通过后再合并

## 6. 数据库变更

数据库使用 Flyway 版本管理.

任何表结构变更必须同步:

```text
src/main/resources/db/migration/h2/V<version>__<name>.sql
src/main/resources/db/migration/mysql/V<version>__<name>.sql
entity / mapper
DataInitializer
tests
```

规则:

- H2 和 MySQL 使用相同版本号
- 已应用的迁移不能修改, 只能新增版本
- H2 和 MySQL 表名, 列名必须一致
- `MigrationConsistencyTest` 会校验
- `MySqlSchemaIntegrationTests` 会在 Docker 环境真实执行 MySQL 迁移

## 7. CI/CD

```text
.github/workflows/ci.yml        push / PR / 手动触发
.github/workflows/release.yml   v* tag 自动发布
.github/dependabot.yml          每周依赖更新
.github/pull_request_template.md
```

CI 包含:

- 后端 JDK 8: `mvn -B -ntp test`, 上传 surefire 和 JaCoCo 报告
- 前端 Node 20: `npm ci`, `npm test`, `npm run build`
- 校验 `src/main/resources/static` 没有被 git 跟踪
- e2e job 安装 Playwright Chromium 后执行 `npm run e2e`
- package job 下载前端产物后执行 `mvn -DskipTests package`, 上传 jar
- 同一分支新的 push 会取消旧的 CI 运行

Release 见 [docs/RELEASE.md](docs/RELEASE.md).

## 其他约定

- 编码约定: [docs/CODING-RULES.md](docs/CODING-RULES.md)
- 测试策略和清单: [docs/TESTING.md](docs/TESTING.md)
- 安全: [docs/SECURITY.md](docs/SECURITY.md)
- 发布: [docs/RELEASE.md](docs/RELEASE.md)
- 业务数据模型: [docs/DATA-SCOPE.md](docs/DATA-SCOPE.md)
- AI agent 规则: [AGENTS.md](AGENTS.md)
