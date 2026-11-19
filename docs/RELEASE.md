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
bash scripts/agent-check.sh full
```

`full` 包含前端测试/构建, 后端 verify 和 Playwright E2E.

手工冒烟:

```text
/api/health
/api/auth/login
/api/dashboard
/api/admin/attendance/template
```

## 3. 自动构建和 GitHub Release

推送 `v*` tag 会触发 `.github/workflows/release.yml`:

```bash
git tag v1.0.0
git push origin v1.0.0
```

流程:

1. 检出代码
2. 安装前端依赖, 执行 `npm test` 和 `npm run build`
3. 安装 Playwright Chromium, 执行 `npm run e2e`
4. 使用 JDK 8 执行 `mvn -B -ntp verify`
5. 复制 jar 为 `hr-manager-sys-<tag>.jar`
6. 生成 sha256 校验文件
7. 创建 GitHub Release 并附加 jar 和校验文件

自动 Release 只发布可执行 jar. 公有 Release 仓库的加密源码压缩包仍然手动维护.

## 4. 压缩包

由项目所有者手动生成和维护.

建议包含:

- 项目源码
- 前端源码
- 构建后的 `src/main/resources/static` (不在 git 中, 需要先执行 `npm run build`)
- README
- SESSION-NOTES
- DOCX 转换工具

不要包含:

- `tmp.docx`
- `.git`
- `data/`
- `target/`
- `web/node_modules/`
- `tools/export-pandoc/out` (除非明确需要)
- 密码文件

## 5. 加密

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

## 6. 发布到公有仓库

```bash
gh release create v1.0.0 <zip> \
  --repo Keal-thas/hr-manager-sys-release \
  --title "v1.0.0" \
  --notes "Password-protected source snapshot. Password provided separately."
```

## 7. 更新公有仓库

公有仓库是手动维护的快照, 不是源码主仓库.

更新步骤:

1. 私有仓库打 tag
2. 生成新的加密压缩包
3. 创建新的 Release
4. 在公有仓库 README 更新版本说明
5. 不提交密码

## 8. 回滚

- 保留上一个 Release
- 不删除旧版本压缩包
- 只追加新版本
