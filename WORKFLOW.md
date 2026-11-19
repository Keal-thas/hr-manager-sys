# Workflow

本文档定义本仓库的强制开发流程. AI agent 和人类开发者都必须遵守.

> 当前 AI agent 提交约定见 `AGENTS.md`: 禁止运行测试, 构建或 CI 检查; 最终用户手动跑全量测试.

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

同时更新:

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
- Swagger: `/swagger-ui.html`, `/v3/api-docs`.
- 密码必须 BCrypt.
- 不记录明文密码, token, 密钥.

## 8. 前端约定

- React 18 + Ant Design 5.
- 统一使用 `web/src/api.js`.
- 新增页面沿用现有布局和表格风格.
- 修改后执行:
  ```bash
  cd web && npm test && npm run build
  ```
- `src/main/resources/static` 是构建产物, 已被 git 忽略, 不要提交.
- 构建 jar 前必须先构建前端, 否则 jar 里没有页面.
- `mvn package` 会检查 `src/main/resources/static/index.html`, 缺失会直接失败.
- 不要手工编辑构建产物.

## 9. 测试

- 后端行为变更必须补测试.
- 当前后端测试:
  ```text
  HrManagerApplicationTests     上下文, 登录, OpenAPI
  AuthApiTests                  登录, 锁定, 改密, 登出, 401
  AttendanceApiTests            批次, 明细, 签名确认, 模板, 导出
  NoticeMessageApiTests         通知发布, 消息已读, 模板生命周期
  AdminApiTests                 部门, 角色, 用户, 初始密码强制修改
  PasswordPolicyTest            密码规则
  AuthorizationApiTests         admin 路径权限校验
  MigrationConsistencyTest      H2/MySQL 迁移一致性
  MySqlSchemaIntegrationTests   Testcontainers MySQL 8
  WeComClientTest               token 缓存, 发送, 重试
  WeComNotifierTest             SENT/FAILED/SKIPPED 日志
  ```
- 当前前端测试:
  ```text
  web/src/api.test.js           Axios 响应拦截, 错误提示, 401 事件
  ```
- 当前 E2E:
  ```text
  web/e2e/01-auth.spec.js                 登录, 菜单, 退出
  web/e2e/02-employee-attendance.spec.js  考勤签名, 消息, 资料
  web/e2e/03-admin.spec.js                用户/部门, 通知, 模板, Excel 上传, 监控
  ```
- E2E 使用 `application-e2e.yml`, H2 内存库, 端口 18080.
- 本机默认用系统 Chrome; CI 用 Playwright Chromium.
- 监控测试在 `MonitoringApiTests`.
- 集成测试使用 `src/test/resources/application-test.yml` 的 H2 内存库.
- 需要修改数据的测试使用 `@Transactional`, 结束后回滚.
- `mvn -B -ntp verify` 必须通过.
- `cd web && npm test` 必须通过.
- JaCoCo 报告在 `target/site/jacoco/index.html`.
- 不允许为了过 CI 删除或弱化测试.

## 10. CI/CD

GitHub Actions:

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

Release:

- 推送 `v*` tag 时先构建前端, 再执行完整校验
- 成功后把 jar 和 sha256 校验文件附加到 GitHub Release
- 公有 Release 仓库的密码压缩包仍由项目所有者手动维护

PR 必须等 CI 通过. 不要绕过 CI 直接合入.

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
- 按 `docs/AGENT-WORKFLOW.md` 的任务分类执行
- 遵守 JDK 8 限制
- 修改后运行 `bash scripts/agent-check.sh quick|standard|full`
- 新增 `/api/admin/**` 接口时同步 `AuthInterceptor.requiredPermission`
- 生产 profile 不允许写入演示数据
- 前端改动补 `data-testid` 和 E2E 断言
- 监控只做技术指标, 不在业务 service 中加埋点
- 更新文档
- 不绕过 hooks
- 不提交禁止文件
- 不把密码写进仓库

Agent 不允许:

- 擅自改技术栈
- 擅自加企业微信或其他外部集成
- 擅自删除测试
- 擅自改变 API 返回结构
- 修改或提交 `src/main/resources/static` 构建产物

## 13. 发布

私有源仓库:

```text
https://github.com/Keal-thas/hr-manager-sys
```

公有 Release 仓库只存放密码保护的压缩包, 由项目所有者手动维护.

发布前:

```bash
bash scripts/check.sh
```

自动发布:

```bash
git tag v1.0.0
git push origin v1.0.0
```

`release.yml` 会先构建前端, 再执行 `mvn verify`, 生成带版本号的 jar 和 sha256, 并创建 GitHub Release.

手工冒烟:

```text
/api/health
/api/auth/login
/api/dashboard
/api/admin/users
/api/admin/attendance/template
```
