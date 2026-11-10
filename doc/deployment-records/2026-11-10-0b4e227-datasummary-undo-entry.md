# JIN V2 deployment: 修正 Data Summary 撤销退款入口位置

- Source code commit: `main_v2` `0b4e227cf9ba570a266c197bfef0d2b49ca8b7db`（`0b4e227 修正 Data Summary 撤销退款入口位置`）。相对上次线上 `cee9fb6` 只新增这一个提交，纯前端改动，无后端 Java 变更。
- 改动内容：`Undo refund` 入口从 Discontinued Student 表的行内按钮移到 Refund Student 行的右键菜单（`openRefundUndoFromMenu`）；退款行补回 `studentName` 渲染分支，并在移动端提供 `MoreFilled` 操作按钮打开同一菜单；右键菜单高度估算由 132 调整为 190。仅修改 `frontend/src/views/data-summary/DataSummaryView.vue`。
- Release: `/home/class-deduction-system/releases/v2-20261110-0b4e227-datasummary-undo-entry`.
- Backend JAR SHA-256: `b8354c3d2e72b5b215b373172935f0054793519edc598b985b8ccd91423800cf`（本地与服务器一致）。本次无 Java 改动，JAR 因重新构建时间戳与上一版不同，属正常差异。
- Frontend index SHA-256: `bf305be0b44e20559afd44c38145dd3b44fc610f2a899c6c555d33a9411b97f4`（本地、release 与公网实际返回一致）。
- Database schema/data: no migration or data changes（`git diff cee9fb6..0b4e227` 无 `*.sql` 或 migration 文件）。
- Pre-deployment backup: `/root/jin-v2-backups/pre-0b4e227-20261110T082633Z.sql.gz`，SHA-256 `0114318a751a235fbdf0f37b5fd321d300f4a2f91735ece8c06ebf7dcc8fbfd7`，`gzip -t` 通过，mysqldump stderr 为空。备份使用服务器本地 root socket 完成，未在命令行或对话中传递数据库口令；仅备份 `class_deduction_system_v2`，未操作 `sat_exam`、`shsat_exam`、`shsat_exam_v2` 等其他系统数据库。
- Build: 独立干净 worktree（`.worktrees/deploy-0b4e227`，detached `0b4e227`）构建。后端 `mvn -DskipTests clean package`（JDK 17）通过；前端 `npm ci` + `npm run build`（含 typecheck）通过，产物已确认包含 `openRefundUndoFromMenu` 与 `Undo refund` 文案。
- After deployment: service `active`（ActiveEnterTimestamp `2026-11-10 03:35:08 EST`）；`http://127.0.0.1:8084/api/health` 返回 200 / `UP`；公网 `https://class.jinbesttutoring.com/api/health` 返回 200；公网首页 sha256 与 release 内 `index.html` 一致；运行进程 `ps` 参数确认指向新 release 的 `backend.jar`；未带 token 访问 `/api/v1/data-summary/refund-candidates` 返回 401、`/api/v1/enrollment/orders` 返回 401；`POST /api/v1/auth/login`（不存在的用户）返回 400，说明登录查询已实际打到数据库；`nginx -t` 通过；重启后 journal 中无 `ERROR`/`Exception`/`failed to` 条目。
- Rollback release: `/home/class-deduction-system/releases/v2-20261110-cee9fb6-course-delete-keep-payment`。新 release 内已保存旧的 systemd drop-in（`backend/v2.conf.before`）与旧的 `front/dist` 符号链接目标（`previous-targets.txt`）。
- 回滚数据库：本次未改库结构，正常情况下无需回滚数据；如需，可从上述备份 gzip 恢复 `class_deduction_system_v2`。
- 已知未验证项：真实 MySQL 并发集成与 Playwright 浏览器验收仍未执行（见 `specs/005-undo-refund/quickstart.md`）；本次未在浏览器中目视确认 Refund Student 右键菜单与移动端入口的实际展示。
