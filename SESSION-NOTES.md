# Session Notes

本文件记录本次会话交付的内容, 关键设计, 运行方式, 已验证项和已知缺口.

## 1. 交付概览

本次会话完成两部分:

1. Word (DOCX) 转 Agent 可读 Markdown 的工具链
2. 一个可运行的 HR 管理系统 MVP

代码仓库:

```text
https://github.com/Keal-thas/hr-manager-sys  (private)
```

另有一个公有 Release 仓库, 用于存密码保护的压缩包. 源仓库保持 private.

## 2. DOCX 转 Markdown 工具

位置:

```text
.local/export-pandoc/
  convert.py
  requirements.txt
  conversion-guide.md
  out/          # 已转换产物
  .build/       # 中间 AST
```

### 2.1 技术选型

- pandoc: docx -> JSON AST, AST -> GFM
- panflute: 在 AST 层做结构修正
- olefile: 读取 OLE 复合文档
- openpyxl: 读取/转换 xlsx

Word 正文解析没有手写. 旧版手写解析器已删除.

### 2.2 流程

```text
docx
  -> pandoc: docx -> JSON AST + 抽图片
  -> panflute:
       删 TOC
       标题顺序编号
       表格 colspan 展开和分组行拆分
       图片图注绑定
       内部锚点清理
  -> pandoc: AST -> GFM
  -> 后处理:
       表格空格压缩
       按 H3/H2 分节
       INDEX.md / manifest.json
       文本/TOC/链接校验
  -> out/
```

附件走旁路:

```text
document.xml 找 OLEObject
  -> olefile 取 package 流
  -> 修复 WPS 写出的 workbook visibility=hidden
  -> openpyxl 转每个 sheet 为 Markdown
```

### 2.3 运行

```bash
python .local/export-pandoc/convert.py
python .local/export-pandoc/convert.py other.docx
python .local/export-pandoc/convert.py other.docx --out .local/export-pandoc/out-other
python .local/export-pandoc/convert.py other.docx --title "文档标题"
```

依赖:

```bash
winget install JohnMacFarlane.Pandoc
python -m pip install -r .local/export-pandoc/requirements.txt
```

### 2.4 输出

```text
out/
  document.md
  INDEX.md
  manifest.json
  sections/section-01..20.md
  assets/media/            图片
  assets/attachments/      xlsx + 每个 sheet 的 Markdown
```

### 2.5 本次验证

```text
headings        87
tables          16
images          50
attachments     3
sections        20
text missing    0
toc match       23/23
broken links    0
html tables     0
html img tags   0
emf refs        0
```

### 2.6 硬编码与假设

常量:

- 标题最多 6 级
- pandoc `--columns=80`
- 图注正则 `^\d+(?:\.\d+)+-`
- Excel 密级列正则 `★[^★]{1,6}★`
- OLE package 流名 `package`
- `.emf` 作为预览图丢弃

启发式:

- 2 列表转 key-value
- 3 列及以上且无表头时, 第一行提升为表头
- `colspan == 列数` 的行作为分组行
- 文本为"目录"时删除到第一个标题
- 图注在图片下一段
- OLE 附件归属最近的标题
- 标题顺序编号

文档家族假设:

- 标题都有编号
- 分组行是整行合并
- 封面是 2 列键值表
- 附件在 4.1/4.2/4.3 下
- Excel 附件是 OLE 嵌入
- 标题可从 DLP 元数据取
- 页眉只有水印和版权

这些规则适合当前文档家族, 换文档类型时应该改成 profile.

### 2.7 已知限制

- 不做 OCR, 截图里的文字不可检索
- 公式/文本框/多栏/批注/修订未处理
- 非 Excel 的 OLE 附件未处理
- 图注格式变化时可能绑定失败
- 标题顺序编号假设所有标题都需要编号
- 1 列表格的分组行判断需要额外保护

## 3. HR 管理系统

### 3.1 技术栈

```text
JDK 8
Spring Boot 2.7.18
MyBatis Plus 3.5.7
H2 (开发) / MySQL 8 (生产)
React 18 + Vite + Ant Design 5
Apache POI
BCrypt
```

### 3.2 模块

```text
auth        登录, 密码修改
user        用户管理, 个人资料
dept        部门树
role        角色与权限
attendance  考勤批次, 明细, Excel, 异常, 签名
notice      通知, 模板, 站内消息
dashboard   首页统计
common      响应, 异常, 拦截器, 菜单
config      初始化数据, MyBatis Plus, 密码
```

### 3.3 登录与权限

- 工号 + 密码登录
- BCrypt 密码
- 连续 5 次错误锁定 15 分钟
- 初始密码强制修改
- 密码规则: 8-50 位, 包含大写/小写/数字, 不含汉字和空格
- Session 保存 `LoginUser`
- 拦截器保护 `/api/**`
- 角色权限用逗号分隔的菜单 code 表示
- 菜单: dashboard, my-attendance, my-messages, profile, admin-attendance, admin-notice, admin-template, admin-dept, admin-role, admin-user

### 3.4 考勤流程

上传方式:

- NEW: 新增某年某月考勤, 同月已存在则拒绝
- OVERWRITE: 覆盖已有月份, 删除旧明细和旧异常
- INCREMENTAL: 按工号增量新增/修改, 已确认状态保留

上传校验:

- 工号必须存在且为数字
- 姓名必须匹配
- 文件内工号不能重复
- 关键必填字段
- 数字字段格式
- Excel 表头自动识别

状态:

```text
UNREAD      未阅读
READ        已阅读
CONFIRMED   已确认
```

员工确认:

- 查看明细后状态 UNREAD -> READ
- 勾选同意 + 手写签名后 -> CONFIRMED
- 签名以 base64 存 `attendance_record.signature_image`

提醒:

- 考勤提醒: 未确认员工
- 异常提醒: 未阅读且有处罚记录的员工
- 站内消息, 不接企业微信

### 3.5 通知与消息

- 通知支持全员/部门/指定员工
- 部门目标包含子部门
- 发布时可同时保存为模板
- 发布后为每个目标用户插入 `sys_message`
- 员工消息列表和未读数

### 3.6 数据库

表:

```text
sys_department
sys_role
sys_user
attendance_batch
attendance_record
attendance_penalty
message_notice
notice_template
sys_message
```

关键设计:

- 考勤完整字段放 `attendance_record.data_json`
- 关键查询字段单独建列: employee_no, user_id, status, batch_id
- 部门最多 5 级, 10 位编码每级 2 位
- 逻辑删除用 status, 不做物理删除

### 3.7 前端

- React 18 + Vite + Ant Design 5
- 单页应用, 左侧菜单, 右侧内容
- 页面:
  - 登录
  - 首页
  - 我的考勤 (含 canvas 签名)
  - 消息通知
  - 个人中心
  - 考勤管理 (上传/明细/提醒/异常/导出)
  - 通知发布/模板
  - 部门/角色/用户管理
- 构建产物输出到 `src/main/resources/static`, 该目录 git 忽略
- 开发时 Vite 代理 `/api` 到 8080

### 3.8 默认账号

```text
10001 / Admin1234    管理员
10002 / Hr123456     人事
10003 / Emp123456    员工
10004 / Emp123456    员工
```

首次启动会写入:

- 4 个用户
- 5 个部门
- 3 个角色
- 上个月考勤批次和 4 条明细
- 欢迎通知和一条模板

### 3.9 运行

开发:

```bash
mvn spring-boot:run
```

打包:

```bash
mvn -DskipTests package
java -jar target/hr-manager-sys-0.0.1-SNAPSHOT.jar
```

访问:

```text
http://localhost:8080
```

H2 控制台:

```text
http://localhost:8080/h2-console
JDBC URL: jdbc:h2:file:./data/hr
User: sa
Password: 留空
```

MySQL:

```bash
java -jar target/hr-manager-sys-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

先执行 `src/main/resources/schema-mysql.sql`.

前端开发:

```bash
cd web
npm install
npm run dev
```

前端构建:

```bash
cd web
npm run build
```

### 3.10 取舍

- 不接企业微信, 所有提醒改站内消息
- 部门 5 级, 修正原需求里"6 级"和 10 位编码的矛盾
- 考勤字段用 JSON 存储, 降低表结构复杂度
- 签名 base64 存库, 生产建议对象存储
- 消息同步插入, 生产建议异步队列
- 不做 OCR, 不做外部附件
- 初始数据写上月, 方便用当前月测试上传

## 4. 已验证

黑箱冒烟, 从空 `data/` 启动:

```text
启动                 2 秒内
/api/health          200, UP
首页 /               200
前端 JS              200, 约 1.18 MB
管理员登录           成功, 10 个菜单
首页统计             4 用户, 5 部门, 最近考勤上月
用户列表             4 人
考勤批次列表         1 批, 4 条记录
考勤模板下载         200, 4299 bytes
上传当前月考勤       成功, 1 条记录
员工 10003 登录      成功
员工当前考勤         当前月, UNREAD
签名确认             成功, CONFIRMED
员工消息             3 条
发布通知             成功, 目标 4 人
个人资料             正常
退出登录             成功
退出后 /auth/me      401
```

## 5. 未验证 / 后续

- 部门/角色/用户全部增删改边界
- 考勤异常清单上传
- MySQL profile 自动集成测试
- 5000 条导入和 5000 人消息性能
- 非管理员前端页面级权限隔离
- 跨浏览器 E2E 矩阵
- Alertmanager 真实告警通知

建议后续:

1. 消息发送改异步
2. 签名图片改对象存储
3. 考勤导入改批量插入
4. 把文档家族规则抽成 profile
5. 增加 MySQL 迁移脚本版本管理
6. 增加性能基线和压测脚本

## 6. 公有发布

源仓库保持 private. 公有 Release 仓库:

```text
https://github.com/Keal-thas/hr-manager-sys-release
```

包含两个密码保护的压缩包:

```text
hr-manager-sys-code-v1.0.0.zip   # 源码, 前端, 静态资源, 文档, 转换工具
hr-manager-sys-spec-v1.0.0.zip   # 转换后的需求 Markdown, 章节, 图片, Excel 附件
```

压缩包密码由项目所有者单独提供, 不写入仓库.

压缩包内容:

- 项目源码
- 前端源码
- 构建后的静态资源
- README 和本文件
- DOCX 转换工具

不包含:

- 原始 `tmp.docx`
- `.git`
- `target/`
- `data/`
- `web/node_modules/`
- 转换后的需求文档输出

## 7. 开发流程完善

本轮只完善开发流程, 不新增业务功能.

### 7.1 自动化测试

后端新增:

```text
AuthApiTests             登录, 锁定, 改密, 登出, 401
AttendanceApiTests       批次, 明细, 确认, 模板, 导出
NoticeMessageApiTests    通知, 消息, 模板
AdminApiTests            部门, 角色, 用户, 初始密码
PasswordPolicyTest       密码规则
SchemaConsistencyTest    H2/MySQL schema 对齐
MonitoringApiTests       Actuator health, metrics, prometheus
AbstractApiTest          MockMvc 和登录辅助
```

前端新增:

```text
web/src/api.test.js      Axios 响应拦截和错误提示
```

测试数据使用 H2 内存库. 会写库的用例使用 `@Transactional` 回滚, 不污染其他用例.

### 7.2 质量门禁

- JaCoCo 生成覆盖率报告 `target/site/jacoco/index.html`
- Maven Enforcer 校验 JDK 8 和 Maven 3.9+
- `SchemaConsistencyTest` 阻止 H2/MySQL schema 漂移
- `scripts/check.sh` 统一本地检查: 前端测试和构建, 确认构建产物未被跟踪, 后端 verify
- `mvn package` 检查 `src/main/resources/static/index.html`, 防止打出没有页面的 jar
- pre-push 执行前端测试和构建, 检查构建产物未被跟踪, 再执行后端 verify

### 7.3 CI/CD

- `ci.yml`: 后端 test, 前端 test/build, package job 下载前端产物后打包 jar
- 校验 `src/main/resources/static` 没有被 git 跟踪
- `release.yml`: 推送 `v*` tag 后先构建前端, 再执行完整校验并发布 jar 和 sha256
- `dependabot.yml`: 每周检查 Maven, npm, GitHub Actions 依赖
- `pull_request_template.md`: 固定检查清单
- CI 开启并发取消和超时, 减少无效运行

### 7.4 验证

```text
cd web && npm test      6 tests, 0 failures
cd web && npm run build 成功
mvn -B -ntp verify      36 tests, 0 failures
cd web && npm run e2e   11 passed
```

构建产物已从 git 跟踪中移除, `src/main/resources/static` 只保留在本地和打包产物中.


## 8. 本轮增强: E2E, 前端设计, Agent 工作流, 监控

本轮在原有 MVP 和测试基础上补齐交付质量, 不改核心业务规则.

### 8.1 后端监控

新增依赖:

```text
spring-boot-starter-actuator
micrometer-registry-prometheus
```

监控范围:

```text
HTTP    http_server_requests_seconds_*
JVM     jvm_memory_*, jvm_gc_*, jvm_threads_*
进程    process_cpu_usage, process_uptime_seconds
系统    system_cpu_usage, system_load_average_1m
连接池  hikaricp_connections_*
健康    /actuator/health
```

设计取舍:

- 只做技术指标
- 业务 service 不注入 metrics
- 不新增业务健康埋点
- 业务健康如果后续需要, 用独立只读 exporter

配置:

```text
application.yml
  /actuator/health
  /actuator/info
  /actuator/metrics
  /actuator/prometheus
  HTTP P50/P95/P99 直方图
  application=hr-manager-sys 公共标签
```

测试:

```text
support/MonitoringApiTests
  health UP
  prometheus 含 http_server_requests_seconds
  prometheus 含 jvm_memory_used_bytes
  prometheus 含 process_cpu_usage
  prometheus 不含 hr_business
  metrics 目录可读
```

### 8.2 前端设计

新增:

```text
web/src/theme.js
web/src/layouts/AppLayout.jsx
web/src/components/ErrorBoundary.jsx
```

调整:

- 登录页改为左右分栏品牌页, 移动端单列
- 侧栏支持折叠和响应式断点
- 统一头部, 内容最大宽度, 页脚
- 页面渲染错误由 ErrorBoundary 兜底
- 浏览器标题跟随当前页面
- Vite 拆分 react / antd / axios chunk
- 关键交互元素补 `data-testid`

构建结果:

```text
index.html       0.55 kB
index.css        6.14 kB
react chunk      0.03 kB
index chunk     43.44 kB
axios chunk     59.15 kB
antd chunk    1119.12 kB
```

### 8.3 Playwright E2E

新增:

```text
web/playwright.config.js
web/e2e/01-auth.spec.js
web/e2e/02-employee-attendance.spec.js
web/e2e/03-admin.spec.js
web/e2e/support/auth.js
scripts/e2e-server.sh
scripts/e2e.sh
src/main/resources/application-e2e.yml
```

特点:

- 端口 18080, H2 内存库, 每次启动重置数据
- 本机默认复用系统 Chrome, CI 使用 Playwright Chromium
- 失败保留 trace 和 screenshot
- `npm run e2e` 自动 build 前端并启动后端

覆盖 11 个用例:

```text
登录页, 错误密码, 管理员菜单, 退出后 401
员工考勤阅读, 勾选, canvas 签名, 确认成功
消息列表, 全部已读, 个人资料保存
管理员用户/部门浏览, 发布通知, 下载模板, 查看明细
Actuator health 和 prometheus
```

### 8.4 Agent 工作流

新增:

```text
docs/AGENT-WORKFLOW.md
scripts/agent-check.sh
```

检查分层:

```bash
bash scripts/agent-check.sh quick      # 后端编译 + 前端单测
bash scripts/agent-check.sh standard   # scripts/check.sh
bash scripts/agent-check.sh full       # standard + E2E
```

文档包含任务分类, 每个任务的步骤, 完成定义和交接模板.

### 8.5 监控栈

新增:

```text
ops/monitoring/docker-compose.yml
ops/monitoring/prometheus.yml
ops/monitoring/alert-rules.yml
ops/monitoring/grafana/provisioning/datasources/prometheus.yml
ops/monitoring/grafana/provisioning/dashboards/dashboards.yml
ops/monitoring/grafana/dashboards/hr-manager-technical.json
```

能力:

- Prometheus 抓取 `/actuator/prometheus`
- Grafana 自动加载 HR Manager Technical
- 面板覆盖 5xx, P95, QPS, HTTP 状态码, JVM 堆, JVM 线程, CPU, Hikari
- 告警规则覆盖应用不可达, 5xx, P95, 进程 CPU, Hikari 饱和
- Prometheus 主动 drop `hr_business_.*`

### 8.6 文档

新增:

```text
docs/MONITORING.md
docs/FRONTEND.md
docs/AGENT-WORKFLOW.md
```

更新:

```text
README.md
AGENTS.md
WORKFLOW.md
docs/README.md
docs/ARCHITECTURE.md
docs/API.md
docs/TESTING.md
docs/CODING-RULES.md
.github/workflows/ci.yml
```

### 8.7 验证结果

```text
mvn -B -ntp verify          36 tests, 0 failures, BUILD SUCCESS
cd web && npm test          6 tests, 0 failures
cd web && npm run build     success
cd web && npm run e2e       11 passed
docker compose config       ok
/actuator/health            200
/actuator/prometheus        200
```

jar 已生成:

```text
target/hr-manager-sys-0.0.1-SNAPSHOT.jar
```

### 8.8 已知限制

- 本机没有下载 Playwright 自带 Chromium, 使用系统 Chrome; CI 会安装
- 监控栈 compose 配置已校验; 本机 Docker daemon 未运行, 未实际启动容器
- 告警规则未接 Alertmanager
- E2E 只跑 Chromium

### 8.9 业务指标取舍

最初尝试在 `BusinessMetrics` 中记录登录, 考勤, 通知等业务事件.

该方案已撤销, 原因:

- 业务 service 注入 metrics, 业务逻辑和监控耦合
- 每加一个业务指标都要改业务代码
- 业务指标口径变化快, 不适合和 Prometheus 技术指标混在一起
- 当前没有独立 exporter, 不适合直接上业务健康指标

最终决定:

- 监控只保留技术指标
- 业务健康如果后续需要, 使用独立只读 exporter 或日志平台
- 不在业务 service 中为监控加分支或埋点

`MonitoringApiTests` 增加了负向断言: `/actuator/prometheus` 不能出现 `hr_business`.
