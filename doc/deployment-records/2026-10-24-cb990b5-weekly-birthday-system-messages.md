# JIN V2 部署：每周生日系统消息

- 源码提交：`cb990b5`（`main_v2`）。
- 发布目录：`/home/class-deduction-system/releases/v2-20261024-cb990b5-weekly-birthday-messages`。
- 部署范围：前端、后端及 `system_message` 表迁移。
- 数据库迁移前：确认 `class_deduction_system_v2.system_message` 不存在；发布目录保存了迁移前 schema 快照。迁移只新增表，没有修改现有业务数据。

## 验证

- 干净提交 worktree 的前端 `npm ci && npm run build` 成功，共 88 个文件。
- 后端使用 JDK 17 执行 `mvn -Dmaven.test.skip=true clean package` 成功。定向后端测试 3/3、生日消息浏览器用例 4/4 已在提交前通过；本次打包跳过 Maven 测试。
- 后端 JAR SHA-256：`e4d183ddbb102b300c4a2d52595710fdc80671849b61669e4a036e0fc05fb4c8`。
- 前端 `index.html` SHA-256：`46881b3596fafebf1ed9c4addd16c705489200fe730caf74aa579defd6b68352`。
- 生日消息 UI 所在 `index-12Cs_KYY.js` SHA-256：`6cab999fafa2f9817c2b10955063fdedb3c8b7d0c44f42c5e85acd25fc40e922`；线上公网文件校验和一致。
- 数据库中 `system_message` 表存在；`class-deduction.service` 与 Nginx 均 active；本机和公网健康接口返回 `{"status":"UP"}`；Nginx 配置检查通过。
- 部署后 10 分钟 systemd 错误级日志无记录，应用启动日志无新错误。

## 回滚

将 `/home/class-deduction-system/front/dist` 指回发布目录中 `front/dist.previous-target` 记录的路径；恢复发布目录 `backend/v2.conf.before` 到 `/etc/systemd/system/class-deduction.service.d/v2.conf`，恢复 `backend/DEPLOY_VERSION.before`，执行 `systemctl daemon-reload && systemctl restart class-deduction.service`。新建的 `system_message` 表可保留，不影响旧版本。
