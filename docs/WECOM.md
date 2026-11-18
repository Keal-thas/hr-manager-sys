# 企业微信集成

## 1. 范围

当前只做企业微信应用消息推送:

```text
考勤通知
考勤提醒
考勤异常提醒
通知发布
```

需求说明书要求企微工作台员工自助、企微身份登录和企微侧电子签名, 本期未实现:

```text
企微工作台 "员工自助" 小程序
企微 H5 员工页面
企微身份登录 (自动带出工号)
企微侧电子签名和签字过程回溯
```

管理员线下签字上传已实现, 存放在考勤签名图片字段, 不等同于企微侧电子签名.

缺口清单见 [README TODO](../README.md#todo-未实现的需求).

非目标:

```text
企业微信通讯录同步
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


## 13. 企业微信内嵌视图规划 (未实现)

当前只做消息推送, 没有把 HR 页面放进企业微信.

如果后续要做, 不要把 PC 管理后台原样搬进去.

### 13.1 两个使用场景

```text
企业微信 PC 端    横向, 窗口宽, 可能是窄侧栏或独立窗口
企业微信手机端    竖向, 窄屏, 有安全区和键盘遮挡
```

不能假设:

- PC 企业微信一定有 1440px 宽度
- 手机端可以放下 30 多列的考勤表
- 鼠标 hover 在手机上可用

### 13.2 一个代码库, 两个 Shell

推荐结构:

```text
web/src/
  App.jsx                 现有 PC 管理端
  layouts/AppLayout.jsx   PC 侧栏布局
  wecom/
    WeComLayout.jsx       企业微信 H5 布局
    WeComHome.jsx
    WeComAttendance.jsx
    WeComMessages.jsx
    WeComProfile.jsx
  components/             共享组件
  api.js                  共享 API
  theme.js                共享主题
```

共享:

- API 封装
- 主题和设计 token
- 表单校验
- 业务展示组件

分开:

- 导航
- 布局
- 列表形态
- 签字交互
- 移动端安全区处理

### 13.3 手机端布局

```text
底部 Tab 导航
卡片列表
全屏签名
底部固定确认按钮
```

考勤明细分组显示:

```text
基础信息
出勤汇总
请假和异常
加班和调休
签字确认
```

不要渲染 34 列宽表. 宽表只保留在 PC 管理端.

### 13.4 PC 企业微信布局

```text
最大宽度 720-900
居中
卡片布局
不使用左侧管理菜单
```

即使横向, 也用员工视图, 不是管理员视图.

### 13.5 响应式断点

```text
< 600px     手机卡片
600-900px   平板或 PC 窄窗口
> 900px     可以两列卡片
```

现有 900px 和 560px 断点可以复用, 但企业微信 H5 需要独立 Shell.

### 13.6 OAuth 登录

企业微信 H5 不应再让员工输入工号和密码.

规划流程:

```text
企业微信打开 H5
  -> 跳转企业微信 OAuth
  -> 回调携带 code
  -> 后端用 code 换 userid
  -> 查 sys_user.wecom_user_id
  -> 建立会话
```

需要新增:

```text
/api/auth/wecom/authorize
/api/auth/wecom/callback
```

注意:

- code 只能用一次
- state 要校验, 防止 CSRF
- 换到的 userid 必须能在 sys_user 中匹配
- 匹配不到要给出明确错误
- 不要把 corp secret 下发到前端

当前没有实现 OAuth.

### 13.7 测试规划

Playwright 增加两个视口:

```text
mobile 390x844
desktop wecom 800x900
```

断言:

- 没有横向滚动
- 底部 Tab 可见
- 考勤卡片可读
- 签名画布全屏
- 按钮不被安全区遮挡

不做跨浏览器矩阵, 只覆盖 Chromium 的两个视口.

### 13.8 分阶段

```text
阶段 1  已完成  企业微信应用消息
阶段 2  未实现  企业微信 H5, OAuth, 我的考勤, 消息, 个人中心
阶段 3  未实现  企业微信内签字, JS-SDK 图片, 电子签名
```

电子签名是独立能力, 不和消息推送绑在一起.

## 14. 当前结论

```text
企业微信先当通知通道
H5 视图是后续独立阶段
横屏和竖屏用响应式加独立 Shell, 不是复制两套系统
管理后台保持 PC 不变
```
