# JIN V2 部署：修复历史收据编辑方案上限误拦截

- 部署时间：2026-10-22 00:25 Asia/Shanghai。
- 修复提交：`main_v2` `5b4267f`；部署兼容提交：`cac7bd4`，基于当前线上版本 `2bfcb07`。
- 发布目录：`/home/class-deduction-system/releases/v2-20261022-cac7bd4-edit-receipt`。
- 部署范围：仅 V2 后端 JAR；未更新前端，未执行数据库结构或业务数据迁移。
- 修复内容：历史收据条款未变时，不再用当前可变的方案数量上限阻止编辑收据的其他信息；方案、购买条款或实际购买数量发生变更时，仍按当前方案限制校验。
- 构建：从干净兼容 worktree 执行 `mvn -Dmaven.test.skip=true clean package` 成功。3 个定向回归测试通过：历史购买量保持不变时允许编辑；修改方案数量或实际购买次数超限时拒绝。
- 后端 JAR SHA-256：`e0cde35f4b1a860acbe0d37306d496274afa89255edd2af155afc6e8db562cae`。

## 生产验证

- `class-deduction.service` 为 active，`current` 指向本次发布，版本标记为 `cac7bd4`。
- 公网首页返回 HTTP 200，`/api/actuator/health` 返回 `{"status":"UP"}`。
- 部署后检查到的服务错误级日志无新增条目。

## 回滚

- 发布目录保存部署前的 `backend/v2.conf.before`、`backend/DEPLOY_VERSION.before`；`/home/class-deduction-system/current.previous-target` 指向此前发布目录。
- 恢复 systemd 配置、将 `current` 指回 `current.previous-target`，再执行 `systemctl daemon-reload && systemctl restart class-deduction.service`。
