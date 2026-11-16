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
