# 安全说明

## 1. 认证

- 工号 + 密码登录
- 密码 BCrypt 存储
- Session Cookie 保存登录态
- 登录成功后调用 `changeSessionId` 防止 session fixation
- `/api/**` 由 `AuthInterceptor` 保护
- 未登录返回 401
- 初始密码未修改时, 只允许修改密码, me, logout

## 2. 密码策略

- 长度 8-50
- 必须包含大写字母
- 必须包含小写字母
- 必须包含数字
- 不能包含汉字
- 不能包含空格
- 不能与原密码相同
- 不能与初始密码相同
- 初始密码为 `bydpcic@123`

## 3. 锁定策略

- 连续 5 次密码错误, 包括修改密码时原密码错误
- 锁定 15 分钟
- 管理员重置密码可提前解锁

## 4. 授权

- 角色权限是菜单 code 的逗号分隔列表
- 前端按菜单隐藏页面
- 后端 `AuthInterceptor` 对 `/api/admin/**` 做权限校验
- 路径和权限映射:

```text
/api/admin/attendance   admin-attendance
/api/admin/notices      admin-notice
/api/admin/templates    admin-template
/api/admin/departments  admin-dept
/api/admin/roles        admin-role
/api/admin/menus        admin-role
/api/admin/users        admin-user
/api/admin/wecom        admin-notice
```

- 未映射的 `/api/admin/**` 默认拒绝
- 新增 admin 接口时必须同步更新 `AuthInterceptor.requiredPermission`
- `/api/dashboard`, `/api/menus`, `/api/my/**` 只要求登录

## 5. SQL 注入

- 使用 MyBatis Plus 参数化查询
- 禁止拼接 SQL
- 动态排序字段必须白名单

## 6. XSS

- React 默认转义文本
- 通知内容按文本展示
- 不要使用 `dangerouslySetInnerHTML`
- 如果未来支持富文本, 必须做 HTML 清洗

## 7. CSRF

当前是 Session + Cookie, 没有 CSRF Token.

生产建议:

- SameSite=Lax 或 Strict
- 增加 CSRF Token
- 或改为 Bearer Token

## 8. 文件上传

- 考勤导入只接受 Excel
- 线下签字接受图片或 PDF, 单文件上限 10MB
- 使用 Apache POI 解析
- 生产建议校验扩展名, MIME, 文件头
- 生产建议限制单文件大小和单次导入行数
- 当前 `application.yml` 已配置 10MB 上传上限

## 9. 敏感信息

禁止提交:

- `.env`
- `*.pem`
- `*.key`
- `id_rsa*`
- token / API key
- 数据库密码
- 压缩包密码
- 原始需求 docx

公有 Release 仓库只放密码保护的压缩包. 密码不写入仓库.

## 8.1 企业微信

- `WECOM_SECRET` 只放在环境变量或密钥管理系统
- 不把 corpId, agentId, secret 写入仓库
- 企业微信接口使用 HTTPS
- 应用消息内容只包含必要信息, 不发送身份证, 银行卡等敏感字段
- 发送日志保存内容快照, 生产需要评估保留期限和访问权限
- token 只在内存缓存, 不落库
- 企业微信失败不影响站内消息

## 9.1 Actuator 暴露面

`/actuator/health`, `/actuator/metrics`, `/actuator/prometheus` 默认不经过 `/api/**` 登录拦截器.

风险:

- `/actuator/prometheus` 会暴露应用名, 接口路径, JVM, 数据库连接池等运行信息
- `/actuator/health` 在 `show-details: always` 时会返回组件细节

生产要求:

- 只在内网或管理网暴露
- 反向代理限制来源 IP
- 或使用独立管理端口 `management.server.port=8081`
- 不要把 Actuator 暴露到公网
- 生产配置已将 `management.endpoint.health.show-details` 设为 `never`

## 10. 生产检查清单

- 关闭 H2 控制台
- 关闭或限制 Swagger UI
- 限制 Actuator 暴露面, 只允许监控系统访问
- 使用 HTTPS
- 修改默认账号密码
- 使用独立数据库账号, 最小权限; 账号密码通过 `DB_USERNAME` / `DB_PASSWORD` 环境变量注入
- 配置 Session 过期时间
- 配置日志脱敏
- 配置备份
- 配置限流和上传大小
- 消息发送异步化
- 企业微信 secret 使用环境变量
- 生产关闭种子数据
- 数据库变更走 Flyway, 不在生产手工改表
- 签名图片使用对象存储

## 11. 已知风险

- admin 路径权限映射需要随新接口维护, 未映射路径默认拒绝
- 没有 CSRF Token
- 生产环境必须关闭 `app.seed.enabled`, 避免写入演示账号
- 签名 base64 存在数据库, 数据量大
- 提醒同步插入, 5000 人时会慢
- 没有独立审计日志, 企业微信发送日志只覆盖推送结果
- 企业微信 UserID 依赖人工维护, 没有通讯录同步
