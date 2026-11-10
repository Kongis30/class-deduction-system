# JIN V2 deployment: 编辑收据删除课程时保留已收款项

- Source code commit: `main_v2` `cee9fb667711ab5ed9e6100e4d27185770ec4ee1`（`cee9fb6 更新部署记录的 main_v2 快进状态`）。相对上次线上 `d75f407` 新增：`1d536fe` 编辑收据删除课程时保留已收款项、`5e07f13` 合并提交、`b387a33`/`cee9fb6` 部署记录更新。
- Release: `/home/class-deduction-system/releases/v2-20261110-cee9fb6-course-delete-keep-payment`.
- Backend JAR SHA-256: `53be5c5d2ca1b040b82760d7eddb5b696469144660c8669ba1485d0604390be9`（本地与服务器一致）。
- Frontend index SHA-256: `bbfc4782e0d4b3278069a91e90c3a5f5a3084608edab10c5f88ea5455160a6c1`（本地、release 与公网实际返回一致）。
- Database schema/data: no migration or data changes（`git diff d75f407..cee9fb6` 无 `*.sql`/migration 文件）。
- Pre-deployment backup: `/root/jin-v2-backups/pre-cee9fb6-20261110T033138Z.sql.gz`，SHA-256 `c7c523f105856acfbf44671162d966ef05e8b1e5a74d2f2c3a48293f5e102d82`，`gzip -t` 通过，mysqldump stderr 为空。备份使用服务器本地 root socket 完成，未在命令行或对话中传递数据库口令；仅备份 `class_deduction_system_v2`，未操作 `sat_exam`、`shsat_exam`、`shsat_exam_v2` 等其他系统数据库。
- Build: 干净 worktree（`cee9fb6`，detached）编译。`EnrollmentOrderServiceImplTest` 68 例 + `DataSummaryServiceImplTest` 39 例，共 107 例，仅 `shouldCorrectV2ReceiptWhenProjectGradeDiffersFromActualClass` 报错——该用例在 `main_v2` `0f14653` 基线同样失败，与本次改动无关，未修改。前端 `npm ci` + `npm run build`（含 typecheck）通过。
- After deployment: service `active`；`http://127.0.0.1:8084/api/health` 返回 200 / `UP`；公网 `https://class.jinbesttutoring.com/api/health` 返回 200；公网首页 sha256 与 release 内 `index.html` 一致；未带 token 访问 `/api/v1/data-summary/refund-candidates` 与 `/api/v1/enrollment/orders` 均返回 401；`POST /api/v1/auth/login`（不存在的用户）返回 400 `INVALID_CREDENTIALS`，说明登录查询已实际打到数据库；`nginx -t` 通过；重启后 journal 中无 `ERROR`/`Exception`/`failed to` 条目。
- Rollback release: `/home/class-deduction-system/releases/v2-20261109-d75f407-undo-refund`。新 release 内已保存旧的 systemd drop-in（`backend/v2.conf.before`）与旧的 `current`/`front/dist` 符号链接目标（`previous-targets.txt`、`current.before.target`、`front-dist.before.target`）。
- 回滚数据库：本次未改库结构，正常情况下无需回滚数据；如需，可从上述备份 gzip 恢复 `class_deduction_system_v2`。
- 已知未验证项：真实 MySQL 并发集成与 Playwright 浏览器验收仍未执行（见 `specs/005-undo-refund/quickstart.md`）。
