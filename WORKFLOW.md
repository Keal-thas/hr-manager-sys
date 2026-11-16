# Workflow

本文档定义本仓库的强制开发流程. AI agent 和人类开发者都必须遵守.

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

安装 hooks 后会设置:

```text
core.hooksPath = .githooks
```

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

1. `mvn test`
2. `cd web && npm run build`
3. 检查 `src/main/resources/static` 是否已提交

## 4. Git hooks

### pre-commit

- 拒绝 `tmp.docx`, `data/`, `target/`, `web/node_modules/`, `__pycache__/`, 私钥, `.env`
- 拒绝大于 10 MB 的文件
- 扫描疑似密钥
- `git diff --check` 空白检查
- `mvn -q -DskipTests compile`

### pre-push

- `mvn test`
- `npm run build`
- 如果构建改变了 `src/main/resources/static`, 拒绝推送

如果确实需要临时跳过, 必须得到项目所有者同意, 不要默认使用 `--no-verify`.

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

## 6. 数据库变更

任何表结构变更必须同步:

```text
src/main/resources/schema.sql
src/main/resources/schema-mysql.sql
entity / mapper
DataInitializer
tests
```

H2 和 MySQL 必须保持一致. 开发环境 H2 使用 `MODE=MySQL`.

同时更新:

- `docs/DATA-MODEL.md`
- 相关测试
- `SESSION-NOTES.md` 如果交付范围变化

## 7. 后端约定

- JDK 8 语法. 禁止 `var`, records, text blocks, `List.of`, `Map.of`, switch expression.
- 统一返回 `ApiResponse<T>`:
  ```json
  {"code":0,"message":"ok","data":{}}
  ```
- 业务错误抛 `BusinessException`.
- 分页统一 `PageResult`.
- 新增接口尽量放在已有模块下.
- 新增或修改接口后更新 `docs/API.md`.
- Swagger: `/swagger-ui.html`, `/v3/api-docs`.
- 密码必须 BCrypt.
- 不记录明文密码, token, 密钥.

## 8. 前端约定

- React 18 + Ant Design 5.
- 统一使用 `web/src/api.js`.
- 新增页面沿用现有布局和表格风格.
- 修改后执行:
  ```bash
  cd web && npm run build
  ```
- 提交 `src/main/resources/static` 的构建结果.
- 不要手工编辑构建产物.

## 9. 测试

- 后端行为变更必须补测试.
- 当前测试:
  ```text
  HrManagerApplicationTests
  ```
- 集成测试使用 `src/test/resources/application-test.yml` 的 H2 内存库.
- `mvn test` 必须通过.
- 不允许为了过 CI 删除或弱化测试.

## 10. CI

GitHub Actions:

```text
.github/workflows/ci.yml
```

包含:

- 后端 `mvn -B -q test`
- 前端 `npm ci && npm run build`
- 校验静态资源已提交

PR 必须等 CI 通过.

## 11. 安全

禁止提交:

- `.env`
- `*.pem`
- `*.key`
- `id_rsa*`
- token, API key
- 数据库密码
- 压缩包密码
- 原始需求 docx

公有 Release 仓库只放密码保护的压缩包.
压缩包密码由项目所有者单独提供, 不写入任何仓库文件.

## 12. AI agent 规则

Agent 必须:

- 先读 `AGENTS.md`, `README.md`, `WORKFLOW.md`, `SESSION-NOTES.md`
- 遵守 JDK 8 限制
- 修改后运行检查
- 更新文档
- 不绕过 hooks
- 不提交禁止文件
- 不把密码写进仓库

Agent 不允许:

- 擅自改技术栈
- 擅自加企业微信或其他外部集成
- 擅自删除测试
- 擅自改变 API 返回结构
- 手工修改 `src/main/resources/static`

## 13. 发布

私有源仓库:

```text
https://github.com/Keal-thas/hr-manager-sys
```

公有 Release 仓库只存放密码保护的压缩包, 由项目所有者手动维护.

发布前:

```bash
bash scripts/check.sh
mvn -DskipTests package
java -jar target/hr-manager-sys-0.0.1-SNAPSHOT.jar
```

冒烟:

```text
/api/health
/api/auth/login
/api/dashboard
/api/admin/users
/api/admin/attendance/template
```
