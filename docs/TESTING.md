# 测试说明

## 1. 当前测试

```text
src/test/java/com/hrmanager/HrManagerApplicationTests.java
```

覆盖:

- Spring 上下文启动
- 管理员登录
- 错误密码拒绝

测试库:

```text
src/test/resources/application-test.yml
jdbc:h2:mem:hr_test
```

测试会自动执行 `schema.sql` 和 `DataInitializer`, 不依赖外部数据库.

## 2. 运行

```bash
mvn test
```

或者:

```bash
bash scripts/check.sh
```

`check.sh` 会额外构建前端并校验静态资源.

## 3. 新增测试原则

- 行为变更必须补测试.
- 优先使用 `@SpringBootTest` + `MockMvc` 覆盖接口.
- 纯逻辑可以写单元测试.
- 不要依赖生产 `data/` 目录.
- 不要在测试里使用真实密码或密钥.
- 不要让测试依赖执行顺序.

## 4. 常用测试类型

### 接口测试

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SomeControllerTest {
}
```

### 业务规则测试

- 密码规则
- 部门编码生成
- Excel 表头解析
- 考勤状态流转
- 通知目标解析

### 前端

当前没有自动化前端测试. 后续建议:

- Vitest + React Testing Library
- Playwright 端到端
- 至少覆盖登录, 我的考勤, 考勤上传, 通知发布

## 5. 手工冒烟清单

```text
1. 启动应用
2. GET /api/health = UP
3. 管理员 10001 / Admin1234 登录
4. 首页统计有数据
5. 下载考勤模板
6. 上传当前月考勤
7. 员工 10003 / Emp123456 登录
8. 查看我的考勤并签字确认
9. 员工消息列表可读
10. 管理员发布通知
11. 退出后 /api/auth/me = 401
```

## 6. CI

`.github/workflows/ci.yml`:

- 后端 `mvn -B -q test`
- 前端 `npm ci && npm run build`
- 校验 `src/main/resources/static` 已提交

## 7. 已知缺口

- 没有前端 E2E
- 没有性能测试
- 没有 MySQL 集成测试
- 没有并发/5000 条导入测试
- 没有密码锁定时间的自动化测试
