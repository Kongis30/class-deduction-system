# 部署记录：2026-11-12 dc78de9 — Edit Receipt 课程行操作（P0/P1/P2）

- **commit**: dc78de9（main_v2），包含 da257b3（P0 死快照行删除闭环 + P1 × 按钮分派）
- **release**: `/home/class-deduction-system/releases/v2-20261112-dc78de9-edit-receipt-row-ops`
- **产物校验和**:
  - backend.jar `f77a0580b2db8a90409a9892f2f29f790e71e5952b771e5af9bb7c7e59f150a3`
  - front/dist/index.html `c8a0bba824bd51aa456d99c088b5374ae38c2fd96edf3c5ea78af4957b332892`
- **DB 备份**: `/root/jin-v2-backups/pre-dc78de9-20261111T174947Z.sql.gz`（1.35MB，68 表，gzip 校验通过）
- **迁移**: 无 DDL，无 schema_migrations 执行
- **验证**:
  - systemd `class-deduction.service` active，8084 监听
  - `GET /api/v1/enrollment/payment-methods/active` 200 正常返回
  - `GET /api/v1/auth/me` 401（未带 token，符合预期）
  - 前端 dist md5 与本地构建一致（`f7e09678c987e7a6e447214924ae7b9b`），nginx 返回 200
- **部署事故与修复**: 首次写 v2.conf 时 heredoc 双引号导致 `${DB_V2_*}` 被本地 shell 展开为空，后端启动失败 5 次重启循环（DatabaseTargetGuard 拦截 datasource 为空）。已修正转义后重启成功，服务恢复约 2 分钟内完成。

## 本次上线内容

1. **P0 死快照行删除闭环**：收据快照行对应订单行已软删时（线上 Ze Mu Ma 案例），前端标记 `orderItemDetached`，× 删除走客户端移除，保存时经 `removedReceiptItemLineNos` 通知后端从收据快照移除，不再误报 "Original course rows cannot be removed or replaced"。
2. **P1 × 按钮三分派**：编辑模式活行 → 后端删除接口（级联取消排课）；死行/新行 → 直接删行；仅剩一行 → 清空本行字段但保留行身份（重新选课即换课更正）。
3. **P2 月数守卫**：
   - `CORRECTION_MONTHS_BELOW_CONSUMED`：按月班课月数不能缩到已确认消课日期之前；
   - 续月超出班级结束日 → 提示先延长班级周期；正常购买仍由 cap 截断（修复 db9103e 回归）。
