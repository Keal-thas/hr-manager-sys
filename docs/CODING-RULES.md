# 编码约束

只保留不显然、容易踩坑的约束. 通用规范和流程见 `AGENTS.md`.

## 后端

- JDK 8 语法. 禁止 `var`, records, text blocks, `List.of`, `Map.of`, switch expression.
- Controller 只做参数接收和返回, 业务规则放 Service, 数据访问用 Mapper.
- 统一 `ApiResponse<T>`, 分页 `PageResult<T>`, 业务错误抛 `BusinessException`.
- 写操作加 `@Transactional`.
- 时间统一 `LocalDateTime`.
- 不打印密码, token, 密钥.

## 命名

- 类后缀 `Controller` / `Service` / `Mapper` / `Entity` 用实体名.
- 表名 `sys_xxx` / `xxx_xxx`, 字段小写下划线.

## 数据库

- 主键 `id BIGINT AUTO_INCREMENT`, 时间字段 `created_at`, `updated_at`.
- 逻辑删除用 `status`.
- schema 以 `src/main/resources/db/migration` 的 Flyway 迁移为唯一来源.
- 变更必须新增迁移, 不修改已应用的迁移; H2 和 MySQL 同版本.

## 前端

- React 18 函数组件 + hooks, Ant Design 5.
- 统一用 `web/src/api.js`, 不在组件里直接写 axios.
- 新交互元素加 `data-testid`.
- `src/main/resources/static` 是构建产物, 不手工编辑, 不提交.

## 企业微信

- 只在 `integration/wecom` 调用企业微信接口; token 内存缓存; secret 用环境变量.
- 企业微信失败不能回滚站内消息; 新增推送场景必须写 `wecom_message_log`.

## DOCX 工具

- 位于 `tools/export-pandoc/`, 不提交原始 docx.
