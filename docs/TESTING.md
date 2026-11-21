# 测试说明

> 当前 AI agent 提交约定见 `AGENTS.md`: 禁止运行测试, 构建或 CI 检查; 最终用户手动跑全量测试.

## 1. 测试分层

```text
单元测试      密码规则, 前端响应拦截
接口测试      @SpringBootTest + MockMvc, 覆盖主要 API
浏览器 E2E    Playwright 覆盖登录, 员工考勤, 管理员主流程
监控测试      Actuator health, metrics, prometheus
手工冒烟      发布前浏览器和接口检查
```

后端集成测试需要本机 MySQL 8, 每次运行创建临时 schema `hr_test_<runId>` 并清理超过 6 小时的历史残留, 启动时 Flyway 执行 `db/migration/mysql` 并运行 `DataInitializer`. 连接参数默认 `localhost:3306` 的 `root` 空密码, 可用 `TEST_DB_HOST` / `TEST_DB_PORT` / `TEST_DB_USER` / `TEST_DB_PASSWORD` 覆盖.

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

src/test/java/com/hrmanager/attendance/AttendanceUploadApiTests.java
  新增/覆盖/增量三种上传, 人数, 状态和数据
  最新月份覆盖, 历史月份只增量
  通知已生成/已调整, 历史增量不通知
  非法月份/未来月份/上传方式/重复人员/未知工号/姓名不匹配

src/test/java/com/hrmanager/attendance/AttendancePenaltyApiTests.java
  编号为工号加两位月份加三位流水
  修改按编号匹配, 限定在选定年月
  编号不匹配时整批取消
  工号必须在批次内, 姓名必须一致
  覆盖上传清空处罚

src/test/java/com/hrmanager/attendance/AttendanceStateMachineApiTests.java
  未阅读 -> 已阅读 -> 已确认, 已确认不回退
  确认需要同意和签名
  线下补签仅非最新批次, 支持图片和 PDF
  已确认记录不能补签

src/test/java/com/hrmanager/attendance/AttendanceRemindApiTests.java
  催办仅限最新批次
  考勤提醒发给未确认的人
  异常提醒只发未阅读且有处罚的人

src/test/java/com/hrmanager/attendance/AttendanceExcelApiTests.java
  文本和数值单元格混用, 前导零工号必须为文本
  空行忽略, 缺失表头或必填列拒绝
  数字格式和六位小数校验
  处罚导入方式/编号/日期/月份/长度校验

src/test/java/com/hrmanager/support/XlsxFixture.java
src/test/java/com/hrmanager/support/AttendanceSheetFixture.java
  在内存里生成 xlsx, 不提交二进制样例

src/test/java/com/hrmanager/notice/NoticeMessageApiTests.java
  全员通知发布到 4 个有效用户
  员工收到通知消息
  未读数量和全部已读
  通知模板创建, 列表, 停用
  空标题拒绝发布

src/test/java/com/hrmanager/notice/NoticeTargetApiTests.java
  全员/部门/指定人员三种范围
  停用用户不接收通知
  消息详情即已读, 不能读他人消息
  模板修改, 恢复, 校验

src/test/java/com/hrmanager/admin/AdminApiTests.java
  部门树, 角色列表, 用户列表
  新建用户使用初始密码
  初始密码未修改时只能改密
  有有效用户的部门不能删除
  同级同名部门拒绝
  重复角色编码拒绝

src/test/java/com/hrmanager/admin/UserRuleApiTests.java
  用户创建字段, 部门和角色校验
  工号创建后不可修改
  重置密码, 状态校验, 查询筛选
  个人资料手机号和邮箱规则

src/test/java/com/hrmanager/admin/RoleRuleApiTests.java
  角色编码和名称必填, 权限默认空
  更新, 停用, 不存在拒绝
  停用角色不授予权限

src/test/java/com/hrmanager/admin/DeptRuleApiTests.java
  部门编码生成, 最多 5 级
  同级同名拒绝
  有子部门不能删除, 删除后不可再操作

src/test/java/com/hrmanager/support/MonitoringApiTests.java
  /actuator/health 为 UP
  /actuator/prometheus 暴露 HTTP, JVM, 进程指标, 不含业务指标
  /actuator/metrics 目录可读

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

默认端口 `18080`, 连接本机 MySQL 8 的 `hr_e2e` 库, `scripts/e2e-server.sh` 每次启动会重置该库并写入种子数据.
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
- 不要在测试里使用真实密码或密钥.
- 新增表或列时在 `db/migration/mysql` 新增迁移.

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

- 后端 JDK 8: `mvn -B -ntp test`, 上传 surefire 和 JaCoCo 报告; job 起 MySQL 8 service 容器
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
- 后端测试依赖本机 MySQL 8
- 没有 5000 条导入和 5000 人消息的压测
- admin 路径权限映射需要随新接口维护
- E2E 只覆盖 Chromium, 这是有意选择, 不做跨浏览器矩阵
- Prometheus 告警不接 Alertmanager, 通知链路不在当前范围
