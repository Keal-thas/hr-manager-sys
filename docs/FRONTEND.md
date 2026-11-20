# 前端设计

## 1. 技术栈

```text
React 18
Vite 5
Ant Design 5
Axios
Vitest (单元测试)
Playwright (E2E)
```

前端源码在 `web/`, 构建产物输出到 `src/main/resources/static/`.
该目录被 git 忽略, 只用于本地运行和打包.

## 2. 目录结构

```text
web/
  index.html
  vite.config.js            构建, 开发代理, Vitest 配置
  playwright.config.js      E2E 配置和 webServer
  src/
    main.jsx                React 入口, AntD locale 和 theme
    App.jsx                 会话, 菜单, 视图切换, 初始密码弹窗
    api.js                  Axios 实例和统一响应处理
    theme.js                AntD 设计 token
    styles.css              全局设计变量, 布局和响应式
    layouts/
      AppLayout.jsx         侧栏, 顶部栏, 内容区, 页脚
    components/
      ErrorBoundary.jsx     页面级错误兜底
    views/
      Login.jsx             登录页
      Dashboard.jsx         首页统计
      MyAttendance.jsx      我的考勤和手写签名
      MyMessages.jsx        站内消息
      Profile.jsx           个人资料和改密
      AdminAttendance.jsx   考勤管理
      AdminNotices.jsx      通知和模板
      AdminSystem.jsx       部门, 角色, 用户
  e2e/
    01-auth.spec.js
    02-employee-attendance.spec.js
    03-admin.spec.js
    support/auth.js         登录和菜单辅助
```

## 3. 页面结构

```text
App
  Login                       未登录
  AppLayout                   已登录
    Sider                     品牌区 + 菜单
    Header                    页面标题 + 用户信息 + 退出
    Content
      ErrorBoundary
        Dashboard | MyAttendance | MyMessages | Profile
        AdminAttendance | AdminNotices | AdminSystem
    Footer
  初始密码 Modal                mustChangePassword 时强制弹出
```

当前没有引入 React Router. 菜单 code 就是视图 key, 由 `App.jsx` 控制.
这样保持单页演示简单, 后续如果要做深链接, 可以替换为 React Router 而不改页面 API.

### 3.1 考勤与消息页面要点

- 我的考勤: 年/月只读并带查询按钮; 打开签字前标记已阅读; 有处罚时展示处罚明细.
- 消息通知: 标题检索, 状态筛选, 序号和发布日期, 未读加粗, 详情/阅读入口.
- 考勤管理: 批次支持年/月筛选; 仅最新批次显示提醒; 非最新批次支持线下签字上传.
- 通知管理: 发布时可选模板; 模板支持标题/状态查询, 停用和恢复.

## 4. 设计系统

`src/theme.js` 定义 Ant Design token:

```text
主色         #1769ff
成功色       #12a150
警告色       #e58a00
错误色       #e5484d
布局背景     #f3f6fb
圆角         10
```

`styles.css` 定义:

```text
--app-brand, --app-ink, --app-muted, --app-surface, --app-bg
登录页居中卡片和渐变背景
侧栏品牌块
统一头部, 内容最大宽度, 页脚
签名画布
900px 和 560px 两个响应式断点
```

侧栏在 `lg` 以下自动收起, 顶部按钮可以手动展开/收起.

## 5. 状态与错误处理

- 会话状态只在 `App.jsx` 维护, 登录和 `/auth/me` 返回的 `menus` 决定可见菜单.
- 统一请求封装在 `api.js`:
  - `code === 0` 返回 `data`
  - blob 原样返回
  - 401 触发 `app:unauthorized`, App 清空会话
  - 403 显示警告
  - 其他错误显示服务端消息
- `ErrorBoundary` 捕获页面渲染异常, 提供刷新按钮.
- 页面切换时同步 `document.title`.
- 通知发布页读取 `/api/admin/wecom/status`, 显示企业微信是否启用.
- 用户管理页可以编辑企业微信 UserID.

## 6. 测试选择器约定

E2E 优先使用 `data-testid`, 避免依赖 Ant Design 的中文空格和内部 DOM:

```text
login-employee-no
login-password
login-submit
page-title
view-<view>
menu-<view>
dashboard-page
my-attendance-page
attendance-query
attendance-sign-open
attendance-agree
signature-canvas
attendance-sign-submit
my-messages-page
message-keyword
message-status
profile-page
profile-save
admin-attendance-page
attendance-records-open
admin-notices-page
notice-publish-submit
admin-system-page
wecom-status
```

新增交互元素时优先补 `data-testid`, 再写 E2E.
表格内容断言可以用 role 和文本, 但要注意 AntD 会在两个汉字的按钮里插入空格.

## 7. 构建与开发

开发:

```bash
cd web
npm run dev
```

Vite 监听 `5173`, 代理 `/api` 到 `8080`.

构建:

```bash
cd web
npm test
npm run build
```

构建产物:

```text
src/main/resources/static/
```

不要手工编辑该目录, 不要提交到 git.

依赖拆分:

```text
react   React 运行时
antd    Ant Design 和图标
axios   HTTP 客户端
```

## 8. E2E

```bash
cd web
npm run e2e
```

默认使用本机 Chrome (`channel: 'chrome'`), 避免下载 Playwright 自带浏览器.
E2E 只跑 Chromium, 这是有意选择, 不维护跨浏览器矩阵.
CI 或本机没有 Chrome 时:

```bash
cd web
npx playwright install chromium
E2E_CHANNEL=bundled npm run e2e
```

或者一键安装并运行:

```bash
E2E_INSTALL_BROWSER=1 bash scripts/agent-check.sh full
```

E2E 使用 `application-e2e.yml`:

```text
本机 MySQL 8 的 hr_e2e 库
端口 18080
每次启动重置 hr_e2e 并写入种子数据
```

覆盖:

- 未登录访问, 错误密码, 管理员菜单, 退出后 401
- 员工阅读考勤, 勾选同意, canvas 签名, 确认成功
- 消息列表, 全部已读, 个人资料保存
- 管理员用户/部门浏览, 发布通知, 下载模板, 查看明细
- Actuator health 和 prometheus

报告:

```text
web/playwright-report/index.html
web/test-results/
```

两者都已加入 `.gitignore`.


## 9. 企业微信 H5 规划 (未实现)

当前前端只服务 PC 管理端和普通浏览器. 还没有企业微信内嵌 H5.

如果后续接入, 原则是:

```text
共享 api.js, theme.js, 校验和业务组件
独立 WeComLayout 和员工页面
不把 AppLayout 和管理端宽表搬进企业微信
```

规划目录:

```text
web/src/wecom/
  WeComLayout.jsx
  WeComHome.jsx
  WeComAttendance.jsx
  WeComMessages.jsx
  WeComProfile.jsx
```

布局:

```text
手机企业微信    底部 Tab, 卡片, 全屏签名
PC 企业微信     最大宽度 720-900, 居中, 卡片
管理后台        保持现有侧栏和宽表
```

断点:

```text
< 600px
600-900px
> 900px
```

考勤明细按组展示:

```text
基础信息
出勤汇总
请假和异常
加班和调休
签字确认
```

E2E 规划:

```text
390x844 手机视口
800x900 企业微信 PC 窄窗口
```

只覆盖 Chromium, 不扩跨浏览器矩阵.

OAuth 和签名属于后续阶段, 见 [WECOM.md](WECOM.md).
