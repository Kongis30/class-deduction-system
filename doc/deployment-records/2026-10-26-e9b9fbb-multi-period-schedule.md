# JIN V2 部署：多 period 班课排课

- 源码：`main_v2` 提交 `e9b9fbb`（完善多 period 排课校验与字段提示）。
- 发布目录：`/home/class-deduction-system/releases/v2-20261026-e9b9fbb-multi-period-schedule`。
- 部署范围：前端、后端和 V2 数据库 period 日期迁移。
- 迁移前备份：`/home/class-deduction-system/releases/v2-20261026-e9b9fbb-multi-period-schedule/database/class_deduction_system_v2-before.sql.gz`，权限为 `0600`，并已通过 gzip 完整性检查。

## 功能

- 班课时段可按 period 分段，缺少日期或周时段时，保存按钮仍可点击；页面标出需补字段并滚动到首个错误。
- 课次覆盖不足时，提示管理员增加周课时或延长课程结束日期后重新预览。
- 迁移为 `class_group_schedule_slot` 增加 period 起止日期，并仅使用班级已有的明确起止日期回填旧时段，不推测缺失周期。

## 验证

- 从该提交的干净 worktree 执行前端 `npm ci && npm run build` 成功；后端 `mvn -Dmaven.test.skip=true clean package` 成功。
- 多 period 定向后端测试、排课页面 Playwright 测试通过；`git diff --check` 通过。
- 迁移新增 `effective_start_date`、`effective_end_date`，回填 208 条可从班级周期明确推导的时段。余下 278 条活跃时段均属于已软删除班级，没有纳入回填；活跃班级没有缺失 period 日期的时段。
- 后端 JAR SHA-256：`1892061f11d0aacc68d67783caae63407a4708c2fd853fbf87390e6ff0a9a64d`。
- 前端 `index.html` SHA-256：`6d349c7d58cbef7c0b97d70d4e50b54a72d4aeec0441de3c3dcb7a641a7d1fb8`。
- 排课编辑器 JS（`GroupClassScheduleEditor-KtgoXJYR.js`）SHA-256：`3c882370a89381af9e08e718ddd5f20710e150ae9f8a7297f17ff6af43deab0e`。
- 线上 `class-deduction.service` 与 Nginx 均为 active，`nginx -t` 成功，健康接口返回 `{"status":"UP"}`。公网首页、排课编辑器 JS 和后端 JAR 的校验和与构建产物一致。

## 回滚

- 前端与 current 指针恢复到 `/home/class-deduction-system/releases/v2-20261025-a77aef1-adjust-options`。
- 将本发布目录的 `backend/v2.conf.before` 和 `backend/DEPLOY_VERSION.before` 恢复到原位置，执行 `systemctl daemon-reload && systemctl restart class-deduction.service`。
- period 日期列及已回填日期为兼容性扩展，回滚旧应用时可保留；无需回滚数据库数据。
