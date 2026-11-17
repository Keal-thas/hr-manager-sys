# 关键决策记录

## ADR-001 DOCX 转 Markdown 使用 pandoc + panflute

背景:

- 需要把 Word 需求文档转成 agent 可读结构.
- 手写 DOCX 解析器维护成本高.
- 需要保留标题编号, 表格, 图片, 附件.

决策:

- pandoc 负责 DOCX -> AST -> GFM.
- panflute 负责 AST 修正.
- olefile + openpyxl 处理 OLE 和 Excel.

后果:

- 减少自写解析代码.
- 需要维护 pandoc 和 panflute 依赖.
- 复杂表格和标题编号仍需要自定义规则.

## ADR-002 不接企业微信

背景:

- 原需求包含企业微信消息和跳转.
- 当前目标是 Web 可运行 MVP.

决策:

- 所有提醒改为站内消息.
- 不引入外部 HTTP 集成.

后果:

- 系统可独立运行.
- 未来接入企业微信时, `MessageService` 是扩展点.

## ADR-003 部门最多 5 级

背景:

- 原需求写"最多 6 级".
- 部门编码 10 位, 每级 2 位, 只能支持 5 级.

决策:

- 按 5 级实现.
- 编码规则: 每级 2 位, 共 10 位.

后果:

- 与原文"6 级"不一致, 需要在需求澄清时确认.
- 编码规则简单可控.

## ADR-004 考勤字段用 data_json

背景:

- 考勤模板有 30+ 字段.
- 全部建列会让表非常宽, 维护成本高.

决策:

- 完整字段存 `attendance_record.data_json`.
- 关键字段单独建列: employee_no, user_id, status, batch_id.

后果:

- 表结构简单.
- JSON 字段不参与查询.
- 需要额外做数据校验.

## ADR-005 签名存 base64

背景:

- 需要演示签名确认.

决策:

- 前端 canvas 生成 PNG base64.
- 存 `signature_image`.

后果:

- 实现简单.
- 生产建议改对象存储, 数据库只存 URL.

## ADR-006 H2 开发, MySQL 生产

背景:

- 开发要快, 不依赖外部数据库.
- 生产用 MySQL.

决策:

- 默认 H2 file 模式.
- 生产 profile 使用 MySQL.
- 两套 schema 文件.

后果:

- 本地启动简单.
- 必须保持两套 schema 一致.

## ADR-007 私有源码 + 公有密码压缩包

背景:

- 源码仓库需要保持私有.
- 需要一个公有的分发位置.

决策:

- 私有源码仓库: `hr-manager-sys`.
- 公有 Release 仓库: 只放密码保护的压缩包.
- 密码不写入仓库.

后果:

- 源码不公开.
- 分发依赖密码.
- 公有仓库需要手动维护.


## ADR-008 监控只做技术指标

背景:

- 业务指标需要在业务事件处埋点.
- 埋点会让 service 依赖 metrics, 业务逻辑和监控耦合.

决策:

- Prometheus 只采集技术指标: HTTP, JVM, 进程, HikariCP, health.
- 不在业务 service 中注入 metrics.
- 业务健康如果后续需要, 用独立只读 exporter.

后果:

- 业务代码保持干净.
- 业务健康趋势暂不可见.
- 需要业务健康时必须单独设计 exporter 和口径.

## ADR-009 admin 接口按路径鉴权

背景:

- 前端菜单隐藏不等于后端鉴权.
- 普通员工可能直接调用 admin 接口.

决策:

- `AuthInterceptor` 对 `/api/admin/**` 做权限校验.
- 路径映射到菜单 code.
- 未映射的 admin 路径默认拒绝.

后果:

- 员工和 HR 的访问边界由后端强制执行.
- 新增 admin 接口必须同步更新权限映射.

## ADR-010 生产关闭演示数据

背景:

- `DataInitializer` 会写入演示账号和演示考勤.
- 生产数据库如果为空, 会写入已知密码账号.

决策:

- 增加 `app.seed.enabled`.
- 默认开启, `application-prod.yml` 关闭.

后果:

- 生产首次部署需要自行创建管理员.
- 测试和开发仍可直接启动.
