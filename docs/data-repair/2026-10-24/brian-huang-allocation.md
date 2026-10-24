# Brian Huang 1-on-1 Allocated 恢复

- 核对日期：2026-10-24，Jin 线上 `class_deduction_system_v2`。
- 课包：`PK202609270026`，60h total，34h remaining。
- 当前排课规则：`2104091773195853825`；Mr. David 当前有效课位：`2113984610943234050`。
- 原因：10 月 18 日的历史分配修正曾把该课位从 40h 降到 36h，并将 4h 作为旧周期待处理课次排除。复核线上当前周期后，确认课次时间线应由同一 40h 分配覆盖；不应把这 4h 从 `Allocated` 中扣除。

## 修复与核对

- 仅将当前有效课位的 `allocated_units` 从 72（36h）恢复到 80（40h），并将版本从 0 增至 1。SQL 带有课位 ID、规则 ID、老师 ID、旧额度、版本及有效状态前置条件；更新前快照保存在 Jin `/tmp/jin_v2_brian_huang_slot_2113984610943234050_before_20261024.sql`。
- 没有更改课包余额、课程记录、消课流水、课次或排课时间。
- 核对结果：40h Allocated − 6h 已确认扣课 = 34h Remaining with；与课包的 34h Remaining 一致。当前未来 17 节各 2h，共 34h；另有 1 节正式请假记录，扣课为 0h。
- 可复核 SQL：[`brian-huang-allocation-restore.sql`](brian-huang-allocation-restore.sql)。
