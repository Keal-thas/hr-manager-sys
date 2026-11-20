# 架构说明

## 分层

| 层 | 位置 | 职责 |
|---|---|---|
| 前端 | `web/src` | React 页面, 表单, 表格, 签名画板 |
| 控制层 | `*/controller` | 参数接收, 权限入口, 返回 `ApiResponse` |
| 服务层 | `*/service` | 业务规则, 事务, 校验, 状态流转 |
| 数据层 | `*/mapper` | MyBatis Plus `BaseMapper` |
| 实体 | `*/entity` | 表映射 |
| 通用 | `common` | 响应, 异常, 拦截器, 菜单 |
| 配置 | `config` | 初始化数据, 分页, BCrypt |

前后端同源部署, 开发时 Vite 把 `/api` 代理到 8080.

## 模块

| 模块 | 说明 |
|---|---|
| auth | 登录, 登出, me, 修改密码 |
| user | 用户 CRUD, 个人资料 |
| dept | 部门树, 部门编码 |
| role | 角色, 菜单权限 |
| attendance | 批次, 明细, Excel, 异常, 签名, 提醒 |
| notice | 通知, 模板, 站内消息 |
| integration | 企业微信 client, token 缓存, 异步推送, 发送日志 |
| dashboard | 首页聚合统计 |
| common | ApiResponse, 异常, 拦截器, 菜单 |
| config | DataInitializer, MyBatis Plus, PasswordEncoder |

## 关键流程

- 登录: 工号 + 密码 -> 校验锁定和密码 -> 载入权限 -> Session 保存 `LoginUser`.
- 考勤上传: 解析 Excel -> 校验 -> 按 NEW / OVERWRITE / INCREMENTAL 落库 -> 刷新 `latest` -> 生成站内消息.
- 考勤状态: UNREAD -> READ -> CONFIRMED. 员工查看变 READ, 勾选同意并签名变 CONFIRMED; 管理员线下签字也变 CONFIRMED.
- 通知发布: 按 ALL / DEPT / USER 解析接收人 -> 写通知 -> 每人插入一条 `sys_message`.
- 企微推送: 站内消息写入后异步推送, 按 `wecom_user_id` 映射, 结果写 `wecom_message_log`; 企微失败不影响站内消息.

## 数据库

- Flyway 迁移是 schema 唯一来源: `db/migration/mysql`.
- 无物理外键, 由应用层保证一致性.
- 站内消息和考勤提醒共用一个 `sys_message` 收件箱, 用类型区分.
- 业务字段和约束见 [DATA-SCOPE.md](DATA-SCOPE.md) 和 [FIELD-CONTRACT.md](FIELD-CONTRACT.md).

## 前端

`web/src` 下按 `views`, `layouts`, `components`, `api.js`, `theme.js` 组织, 没有路由库, 通过 `view` 状态切换页面. 菜单由 `/api/auth/me` 返回. 交互元素带 `data-testid`.

## 可观测性

Actuator 暴露 `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`. 只做技术指标, 业务 service 不注入 metrics. 详见 [MONITORING.md](MONITORING.md).

## 部署

```text
开发: Spring Boot 8080 + 本机 MySQL 8 + Vite 5173 (proxy /api)
生产: MySQL 8 + jar, Flyway 启动时执行 db/migration/mysql, Nginx 可选
```

生产建议: 关闭或限制 Swagger, HTTPS, Session 用 Redis 或粘性会话.

## 非目标

企微 SSO / 通讯录同步 / 电子签名 / 签字回溯, 审批流, OCR, 复杂报表, 多租户. 未实现清单见 [README TODO](../README.md#todo-未实现的需求).
