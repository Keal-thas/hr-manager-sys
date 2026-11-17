# 企业微信集成

## 1. 范围

当前只做企业微信应用消息推送:

```text
考勤通知
考勤提醒
考勤异常提醒
通知发布
```

不做:

```text
企业微信 SSO
企业微信通讯录同步
企业微信电子签名
审批流
```

站内消息仍然保留. 企业微信是第二个通知通道, 不是替代.

## 2. 流程

```text
业务动作
  -> MessageService.send
     -> 写入 sys_message
     -> WeComNotifier.send (异步)
        -> 查 sys_user.wecom_user_id
        -> WeComClient
           -> 获取并缓存 access_token
           -> POST /cgi-bin/message/send
        -> 写入 wecom_message_log
```

关键点:

- 站内消息同步落库, 不依赖企业微信
- 企业微信在独立线程池异步发送
- 企业微信失败不回滚业务事务
- 每次发送都有日志

## 3. 配置

`application.yml`:

```yaml
app:
  wecom:
    enabled: ${WECOM_ENABLED:false}
    corp-id: ${WECOM_CORP_ID:}
    agent-id: ${WECOM_AGENT_ID:}
    secret: ${WECOM_SECRET:}
    base-url: https://qyapi.weixin.qq.com
    connect-timeout-ms: 3000
    read-timeout-ms: 5000
    use-employee-no-fallback: true
    max-content-length: 2000
```

生产环境使用环境变量:

```bash
export WECOM_ENABLED=true
export WECOM_CORP_ID=...
export WECOM_AGENT_ID=...
export WECOM_SECRET=...
```

不要把 corpId, agentId, secret 写进仓库.

默认关闭. 关闭时:

- 只发送站内消息
- 不创建企业微信异步任务
- 不写企业微信发送日志

## 4. 用户映射

`sys_user` 新增 `wecom_user_id`.

解析顺序:

```text
1. sys_user.wecom_user_id
2. 如果 use-employee-no-fallback=true, 使用 employee_no
3. 都没有, 记录 SKIPPED, 不发送
```

管理页面:

```text
系统管理 -> 用户管理 -> 企业微信 UserID
```

留空表示跟随工号.

## 5. 消息格式

企业微信应用消息使用 `text` 类型:

```json
{
  "touser": "wecom-user-id",
  "msgtype": "text",
  "agentid": 1000002,
  "text": {
    "content": "标题\n内容"
  },
  "safe": 0
}
```

内容会按 `max-content-length` 截断.

## 6. Token 和重试

- `gettoken` 获取 `access_token`
- 内存缓存, 提前 120 秒过期
- `errcode` 为 40014, 42001, 41001 时认为 token 失效
- 清空缓存, 重新获取 token, 重试一次
- 其他错误直接失败并记录日志

## 7. 异步和降级

线程池:

```text
core=2
max=4
queue=500
reject=CallerRunsPolicy
```

说明:

- 企业微信发送慢不会阻塞正常请求, 除非队列满
- 队列满时退化为调用线程执行
- 企业微信不可用时只影响推送, 不影响站内消息
- 不自动无限重试, 避免雪崩

生产建议:

- 5000 人通知改批量接口或消息队列
- 增加失败重试表和定时补偿
- 监控 `wecom_message_log` 的失败率

## 8. 发送日志

表:

```text
wecom_message_log
  user_id
  employee_no
  wecom_user_id
  scene
  title
  content
  status       SENT / FAILED / SKIPPED
  errcode
  errmsg
  created_at
```

查询接口:

```text
GET /api/admin/wecom/logs?page=1&size=10
```

## 9. 管理接口

```text
GET /api/admin/wecom/status
GET /api/admin/wecom/logs?page=1&size=10
```

`status` 返回:

```json
{
  "enabled": true,
  "configured": true,
  "ready": true,
  "agentId": "1000002",
  "useEmployeeNoFallback": true
}
```

不返回 secret 和 corpId 明文.

权限:

```text
/api/admin/wecom/**  -> admin-notice
```

管理员和 HR 可以查看, 普通员工 403.

## 10. 前端

通知发布页面顶部显示企业微信状态:

```text
企业微信推送已启用
企业微信推送未启用, 当前仅发送站内消息
企业微信已开启但配置不完整
```

用户管理页面可以编辑企业微信 UserID.

## 11. 测试

```text
WeComClientTest
  token 获取和缓存
  发送文本
  token 失效重试一次

WeComNotifierTest
  发送成功日志
  无映射 SKIPPED
  发送失败 FAILED
  未启用不产生任务

AuthorizationApiTests
  员工不能访问 WeCom 管理接口
  HR 和管理员可以访问

MySqlSchemaIntegrationTests
  Flyway 创建 wecom_user_id 和 wecom_message_log
```

本地默认 `app.wecom.enabled=false`, 不会请求真实企业微信.

## 12. 生产检查

- 企业微信应用已创建并获取 corpId, agentId, secret
- 应用可见范围包含所有需要通知的员工
- 用户 `wecom_user_id` 已维护, 或者确认工号就是 WeCom UserID
- 企业微信可信 IP 已配置
- 通过 HTTPS 访问企业微信接口
- secret 只放在环境变量或密钥管理系统
- 观察 `wecom_message_log` 的 FAILED 比例
- 大批量通知走队列或批量接口
