# Brian Zheng：Mr. Blum 历史课次接入 Mr. Roland 时间线

- 数据库：JIN V2 `class_deduction_system_v2`。
- 学生：Brian Zheng，`STU202609170043`。
- 课包：`PK202609270007`，package ID `2104063336141836290`。
- 当前生效排课版本：rule ID `2113797489234145282`；Mr. Roland 当前课位 ID `2113980207590928385`，lineage ID `2113980207565762563`。

## 根因

该课包由旧逻辑完成老师和周时段调整。旧 Mr. Blum 排课版本的两个历史课位 lineage（`2104064098133626882`、`2107571685491748865`）没有接到当前 Mr. Roland 课位 lineage。1-on-1 页面按当前课位 lineage 归属完整时间线，因此只显示 Roland 的 4 条未来课次，Blum 的历史课次未出现在新老师入口。

## 修复

仅将上述两个已软删除历史课位的 `slot_lineage_id` 更新为 `2113980207565762563`。历史课位仍保持软删除，历史 occurrence、`lesson_record`、日期、计划/实际老师、状态和扣课数均未修改。这样沿用当前代码的历史时段读取与 lineage 归属逻辑，将这些记录接入 Roland 的时间线，同时保留记录中的 Mr. Blum 授课历史。

涉及历史记录：

- 2026-09-29：已完成，4 units 扣除，计划/实际老师 Mr. Blum。
- 2026-10-05：已完成，4 units 扣除，计划/实际老师 Mr. Blum。
- 2026-10-12：正式请假，0 units 扣除，计划老师 Mr. Blum。
- 原排课中 2026-11-16 的取消记录也随该课位 lineage 一并显示为取消状态。

## 修复前后核对

- 修复前，Roland 当前 lineage 只关联 4 条未来 SCHEDULED occurrence；Blum 两个历史 lineage 下有上述 3 条已确认 lesson records 和 1 条 CANCELLED occurrence。
- 修复后，当前 lineage 查询可同时匹配 3 条历史 lesson records、4 条 Roland 未来 SCHEDULED occurrence 和 1 条 CANCELLED occurrence。实际授课老师仍为 Blum；未来课次仍属于 Roland。
- 课包余额未变化：`total_units=120`、`consumed_units=88`、`remaining_units=32`。
- Roland 分配额未变化：当前课位 `allocated_units=16`（8 小时）。没有更新 allocation、课包余额、消课记录或任何教师分配额。

## 回滚

执行前备份位于 JIN：`/home/class-deduction-system/backups/brian-zheng-blum-to-roland-lineage-20261024.sql`（权限 `0600`）。如需回滚，将历史课位 `2104064098133626882`、`2107571685491748865` 的 `slot_lineage_id` 恢复为各自 ID；无需改动其他数据。
