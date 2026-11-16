# 发布流程

## 1. 仓库

私有源码:

```text
https://github.com/Keal-thas/hr-manager-sys
```

公有 Release 仓库:

```text
https://github.com/Keal-thas/hr-manager-sys-release
```

公有仓库只存放密码保护的压缩包和说明, 不存放源码.

## 2. 发布前检查

```bash
bash scripts/check.sh
mvn -DskipTests package
```

手工冒烟:

```text
/api/health
/api/auth/login
/api/dashboard
/api/admin/attendance/template
```

## 3. 压缩包

由项目所有者手动生成和维护.

建议包含:

- 项目源码
- 前端源码
- `src/main/resources/static`
- README
- SESSION-NOTES
- DOCX 转换工具

不要包含:

- `tmp.docx`
- `.git`
- `data/`
- `target/`
- `web/node_modules/`
- `.local/export-pandoc/out` (除非明确需要)
- 密码文件

## 4. 加密

推荐使用 7-Zip AES-256:

```bash
7z a -tzip -p<PASSWORD> -mem=AES256 hr-manager-sys-v1.0.0.zip <staging-dir>
```

密码由项目所有者单独提供, 不写入:

- README
- 代码
- 提交信息
- Release Notes
- 公有仓库

## 5. 发布到公有仓库

```bash
gh release create v1.0.0 <zip> \
  --repo Keal-thas/hr-manager-sys-release \
  --title "v1.0.0" \
  --notes "Password-protected source snapshot. Password provided separately."
```

## 6. 更新公有仓库

公有仓库是手动维护的快照, 不是源码主仓库.

更新步骤:

1. 私有仓库打 tag
2. 生成新的加密压缩包
3. 创建新的 Release
4. 在公有仓库 README 更新版本说明
5. 不提交密码

## 7. 回滚

- 保留上一个 Release
- 不删除旧版本压缩包
- 只追加新版本
