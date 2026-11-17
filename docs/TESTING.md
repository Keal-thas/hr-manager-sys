# 测试说明

## 1. 测试分层

```text
单元测试      密码规则, 前端响应拦截
接口测试      @SpringBootTest + MockMvc, 覆盖主要 API
一致性测试    H2 和 MySQL 迁移版本, 表, 列对比
浏览器 E2E    Playwright 覆盖登录, 员工考勤, 管理员主流程
监控测试      Actuator health, metrics, prometheus
手工冒烟      发布前浏览器和接口检查
```

后端集成测试使用:

```text
src/test/resources/application-test.yml
jdbc:h2:mem:hr_test
```

测试会自动执行 Flyway H2 迁移和 `DataInitializer`, 不依赖外部数据库.
会修改数据的测试类使用 `@Transactional`, 每个用例结束后回滚.

## 2. 后端自动化测试

```text
src/test/java/com/hrmanager/HrManagerApplicationTests.java
  Spring 上下文, 管理员登录, 错误密码, OpenAPI

src/test/java/com/hrmanager/auth/AuthApiTests.java
  /api/health 公开访问
  未登录访问受保护接口返回 401
  登录后可读取 /api/auth/me
  登出后会话失效
  连续 5 次密码错误锁定账号
  弱密码和相同密码被拒绝

src/test/java/com/hrmanager/auth/PasswordPolicyTest.java
  密码长度, 大小写, 数字, 空格, 汉字规则

src/test/java/com/hrmanager/auth/AuthorizationApiTests.java
  员工不能访问 admin 接口
  HR 不能访问角色和菜单管理
  管理员可以访问全部 admin 接口

src/test/java/com/hrmanager/attendance/AttendanceApiTests.java
  管理员查看批次和明细
  考勤模板, 异常模板, 导出下载
  员工阅读考勤后状态变为 READ
  签名确认后状态变为 CONFIRMED
  未勾选同意时拒绝确认
  非法月份拒绝上传

src/test/java/com/hrmanager/notice/NoticeMessageApiTests.java
  全员通知发布到 4 个有效用户
  员工收到通知消息
  未读数量和全部已读
  通知模板创建, 列表, 停用
  空标题拒绝发布

src/test/java/com/hrmanager/admin/AdminApiTests.java
  部门树, 角色列表, 用户列表
  新建用户使用初始密码
  初始密码未修改时只能改密
  有有效用户的部门不能删除
  同级同名部门拒绝
  重复角色编码拒绝

src/test/java/com/hrmanager/support/MigrationConsistencyTest.java
  H2 和 MySQL 迁移版本一致
  表名和列名一致
  wecom_user_id 和 wecom_message_log 存在

src/test/java/com/hrmanager/support/MonitoringApiTests.java
  /actuator/health 为 UP
  /actuator/prometheus 暴露 HTTP, JVM, 进程指标, 不含业务指标
  /actuator/metrics 目录可读

src/test/java/com/hrmanager/support/MySqlSchemaIntegrationTests.java
  Testcontainers 启动 MySQL 8
  Flyway 执行 db/migration/mysql
  DataInitializer 在 MySQL 下写入种子数据
  管理员登录成功
  校验 wecom_user_id 列
  本机没有 Docker 时自动跳过, CI 会执行

src/test/java/com/hrmanager/integration/wecom/WeComClientTest.java
  token 获取和缓存
  发送文本
  token 失效重试一次

src/test/java/com/hrmanager/integration/wecom/WeComNotifierTest.java
  SENT / FAILED / SKIPPED 日志
  未启用不产生任务
```

## 3. 前端自动化测试

```text
web/src/api.test.js
```

覆盖:

- `code: 0` 时返回 `data`
- blob 响应原样返回
- 业务错误提示并 reject
- 401 触发 `app:unauthorized` 事件
- 403 显示警告
- 其他错误显示服务器消息

运行:

```bash
cd web
npm test
```

浏览器 E2E:

```text
web/e2e/01-auth.spec.js
  未登录只能看到登录页
  错误密码不进入系统
  管理员登录后看到全部菜单和首页统计
  退出后 /api/auth/me 返回 401

web/e2e/02-employee-attendance.spec.js
  员工阅读考勤, 勾选同意, canvas 签名, 确认成功
  消息列表, 全部已读
  个人资料保存

web/e2e/03-admin.spec.js
  用户和部门数据浏览
  发布全员通知
  下载考勤模板, 查看批次明细
  Actuator health 和 prometheus
```

E2E 使用:

```text
web/playwright.config.js
src/main/resources/application-e2e.yml
scripts/e2e-server.sh
```

默认端口 `18080`, H2 内存库, 每次启动写入种子数据.
本机默认使用系统 Chrome. CI 使用 `npx playwright install --with-deps chromium`.

## 4. 运行

后端:

```bash
mvn test
```

完整校验:

```bash
mvn -B -ntp verify
```

`verify` 会执行测试, 生成 JaCoCo 报告并打包.

前端:

```bash
cd web
npm test
npm run build
```

E2E:

```bash
cd web
npm run e2e
```

一键检查:

```bash
bash scripts/check.sh
```

`check.sh` 会先跑前端测试和构建, 再检查 `src/main/resources/static` 没有被 git 跟踪, 最后跑 `mvn verify`.

分档检查:

```bash
bash scripts/agent-check.sh quick
bash scripts/agent-check.sh standard
bash scripts/agent-check.sh full
```

`full` 会额外执行 Playwright E2E.

覆盖率报告:

```text
target/site/jacoco/index.html
```

## 5. 新增测试原则

- 行为变更必须补测试.
- 优先使用 `@SpringBootTest` + `MockMvc` 覆盖接口.
- 纯逻辑可以写单元测试.
- 需要写库的测试加 `@Transactional`, 避免用例互相影响.
- 不要依赖测试执行顺序.
- 不要依赖生产 `data/` 目录.
- 不要在测试里使用真实密码或密钥.
- 新增表或列时同时新增 H2 和 MySQL 迁移, `MigrationConsistencyTest` 会失败提醒.

## 6. 常用测试类型

### 接口测试

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SomeControllerTest {
}
```

可以继承 `com.hrmanager.support.AbstractApiTest`, 它提供 `MockMvc` 和 `login` 辅助方法.

### 业务规则测试

- 密码规则
- 部门编码生成
- Excel 表头解析
- 考勤状态流转
- 通知目标解析

### 前端

- 优先测试不依赖 DOM 的纯逻辑.
- 后续可以加 Vitest + React Testing Library 覆盖表单和表格.
- 端到端用 Playwright, 见 `web/e2e`.

## 7. CI

`.github/workflows/ci.yml`:

- 后端 JDK 8: `mvn -B -ntp test`, 上传 surefire 和 JaCoCo 报告
- GitHub Runner 有 Docker, `MySqlSchemaIntegrationTests` 会在 CI 执行
- 前端 Node 20: `npm ci`, `npm test`, `npm run build`
- 校验 `src/main/resources/static` 没有被 git 跟踪
- package job 下载前端产物后打包 jar 并上传
- e2e job 安装 Playwright Chromium 后执行 `npm run e2e`, 失败时上传报告

`.github/workflows/release.yml`:

- 推送 `v*` tag 时先构建前端, 再执行完整校验
- 把版本化 jar 和 sha256 附加到 GitHub Release

PR 必须等 CI 通过.

## 8. 手工冒烟清单

```text
1. 启动应用
2. GET /api/health = UP
3. 管理员 10001 / Admin1234 登录
4. 首页统计有数据
5. 下载考勤模板
6. 上传当前月考勤
7. 员工 10003 / Emp123456 登录
8. 查看我的考勤并签字确认
9. 员工消息列表可读
10. 管理员发布通知
11. 退出后 /api/auth/me = 401
```

## 9. 已知缺口

- 没有性能测试和压测基线
- MySQL 集成测试依赖 Docker, 本地无 Docker 时会跳过
- 没有 5000 条导入和 5000 人消息的压测
- admin 路径权限映射需要随新接口维护
- E2E 只覆盖 Chromium, 这是有意选择, 不做跨浏览器矩阵
- Prometheus 告警不接 Alertmanager, 通知链路不在当前范围
