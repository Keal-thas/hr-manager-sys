# API 说明

## 1. 通用约定

- Base URL: `/api`
- 认证: Session Cookie
- 请求和响应: JSON
- 统一响应:

```json
{"code":0,"message":"ok","data":{}}
```

- `code=0` 成功, `code!=0` 业务失败.
- HTTP 401 未登录, 403 无权限或需要修改初始密码, 400 参数或业务错误.
- 分页:

```json
{"records":[],"total":0,"page":1,"size":10}
```

Swagger:

```text
/swagger-ui.html
/v3/api-docs
```

## 2. 认证

### 登录

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{"employeeNo":"10001","password":"Admin1234"}
```

返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "user": {"id":1,"employeeNo":"10001","name":"管理员"},
    "permissions": ["dashboard"],
    "menus": [{"code":"dashboard","name":"首页"}],
    "mustChangePassword": false
  }
}
```

### 当前用户

```http
GET /api/auth/me
```

### 退出

```http
POST /api/auth/logout
```

### 修改密码

```http
POST /api/auth/change-password
```

```json
{"oldPassword":"Admin1234","newPassword":"NewPass1234"}
```

密码规则: 8-50 位, 包含大写/小写/数字, 不含汉字和空格, 不能与原密码或初始密码相同.

## 3. 我的

```text
GET  /api/dashboard
GET  /api/menus

GET  /api/my/attendance/current
GET  /api/my/attendance/history?page=1&size=10
GET  /api/my/attendance/{batchId}
POST /api/my/attendance/{batchId}/confirm
GET  /api/my/messages?page=1&size=10&type=&unreadOnly=
GET  /api/my/messages/{id}
GET  /api/my/messages/unread-count
POST /api/my/messages/{id}/read
POST /api/my/messages/read-all
GET  /api/my/profile
PUT  /api/my/profile
```

确认考勤:

```http
POST /api/my/attendance/1/confirm
```

```json
{"signatureImage":"data:image/png;base64,...","agree":true}
```

## 4. 考勤管理

```text
GET  /api/admin/attendance/batches?page=1&size=10
GET  /api/admin/attendance/batches/{id}
GET  /api/admin/attendance/latest
POST /api/admin/attendance/upload
GET  /api/admin/attendance/batches/{id}/records?page=1&size=10&keyword=&status=
GET  /api/admin/attendance/records/{id}
POST /api/admin/attendance/batches/{id}/remind?type=ATTENDANCE
POST /api/admin/attendance/batches/{id}/penalties/upload
GET  /api/admin/attendance/batches/{id}/penalties?page=1&size=10&keyword=
GET  /api/admin/attendance/batches/{id}/export
GET  /api/admin/attendance/template
GET  /api/admin/attendance/penalty-template
```

上传:

```http
POST /api/admin/attendance/upload
Content-Type: multipart/form-data
```

字段:

```text
year   2026
month  11
mode   NEW | OVERWRITE | INCREMENTAL
file   xlsx
```

提醒类型:

```text
ATTENDANCE  未确认员工
ABNORMAL    未阅读且有处罚记录的员工
```

## 5. 通知

```text
GET  /api/admin/notices?page=1&size=10
GET  /api/admin/notices/{id}
POST /api/admin/notices
GET  /api/admin/templates
POST /api/admin/templates
PUT  /api/admin/templates/{id}
DELETE /api/admin/templates/{id}
```

发布:

```json
{
  "title": "通知标题",
  "content": "通知内容",
  "targetType": "ALL | DEPT | USER",
  "targetIds": "1,2",
  "saveAsTemplate": false
}
```

## 6. 系统管理

```text
GET    /api/admin/departments
GET    /api/admin/departments/tree
POST   /api/admin/departments
PUT    /api/admin/departments/{id}
DELETE /api/admin/departments/{id}

GET    /api/admin/roles
POST   /api/admin/roles
PUT    /api/admin/roles/{id}
DELETE /api/admin/roles/{id}

GET  /api/admin/menus
GET  /api/admin/users?page=1&size=10&keyword=&departmentId=&roleId=&status=
GET  /api/admin/users/{id}
POST /api/admin/users
PUT  /api/admin/users/{id}
POST /api/admin/users/{id}/reset-password
POST /api/admin/users/{id}/status
```

## 7. 错误示例

```json
{"code":1,"message":"工号不存在，请重新输入","data":null}
```

```json
{"code":1,"message":"密码错误5次，请稍后再试","data":null}
```

```json
{"code":1,"message":"请先修改初始密码","data":null}
```

```json
{"code":1,"message":"无权限操作","data":null}
```

`/api/admin/**` 会按角色权限返回 403.

## 8. 监控端点

Actuator 端点不在 `/api` 下, 不经过登录拦截器:

```text
GET /actuator/health
GET /actuator/info
GET /actuator/metrics
GET /actuator/metrics/{name}
GET /actuator/prometheus
```

示例:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/prometheus
```

技术指标说明见 [MONITORING.md](MONITORING.md).

生产环境建议只在内网暴露 Actuator, 或使用独立管理端口.

## 9. 健康检查

保留简单探活接口:

```text
GET /api/health
```

响应:

```json
{"code":0,"message":"ok","data":"UP"}
```

该接口公开, 不需要登录.
