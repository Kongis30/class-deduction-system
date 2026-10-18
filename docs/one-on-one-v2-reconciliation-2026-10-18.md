# 2026-10-18 JIN V2 私教课时核对与分批修复

以 `class_deduction_system_v2` 为数据源。字段定义以 [one-on-one-hours-business-rules.md](one-on-one-hours-business-rules.md) 为准；本次不更改任何课包余额、已确认消课、老师分配快照或用户设计的现有课次。

## 已确认并修复

| 范围 | 根因 | 修复及验证 |
| --- | --- | --- |
| Chenxi Lin / Yuanhang Liu 的共享课组 | 2026-09-24 09:37 和 09:40 建立了内容完全相同的两条 ACTIVE 规则。后创建的规则没有课次，却被视为当前周期，原规则的 10h 已消课未进入老师级 Deducted。 | 将空的重复规则 `2103117340461289474` 标为 ENDED；保留承载所有课程及消课的规则 `2103116509590638594`。两位学生均为 Angel 16/2/14h、Henry 30/8/22h，老师剩余额合计 36h，与各自课包余额相同。修复前两条规则的备份：`/tmp/jin_v2_duplicate_group_rules_before_20261018.sql`。代码新增同一课包重复创建 ACTIVE 排课的阻断。 |
| Muze Song / Ms. Claudia | 当前周期课位分配 20h，原有有效课次 18h；旧规则复用 ID，续排将旧周期课次误计入本周期额度，最后 2h 无法生成。 | 代码续排按既有的 `PrivateSchedulePeriod` 周期归属扣减额度；另用现有带余额、重复和冲突检查的 `20261009_04_muze_restore_12_12_lesson.sql` 补入 12 月 12 日 09:00–11:00 的 2h。核对结果：当前有效课次 10 节、20h，Allocated 20h。 |

## 待逐包核对，不能改字段凑数

- **旧周期消课在新排课后才确认**：Brian Zheng / Mr. Blum 的 10 月 5 日 2h 课程在 10 月 6 日 16:40 排课调整后、17:41 才确认。课程日期属于旧周期；按规范不能把该 2h 混入新周期 Deducted。老师快照 14h、课包随后减少 2h 的差额，须根据当时额度转移凭证决定后续业务处理。Erika Yu / Ms. Sarah 的 16h 分配额已由用户明确要求保留，不能改成 10h。
- **现有课次与课位快照不一致**：Angela Li、Elly Qu、Gloria Deng、Jennie Yu、Jinghe Hu、Zhen Shen Zou 等须分别核查时段版本、保留的旧周期课次、请假与确认时间。无业务凭证不得缩减已保存的 Allocated、补写 Deducted 或移动课程时间。Jennie Yu / Ms. Kiko 的 2h ADHOC 例外不能按普通周课位漏算。
- **技术取消不是收费取消**：`lesson_occurrence.planning_status=CANCELLED` 是排课重建时的技术状态，不计入 Deducted。只有管理员最终确认、`lesson_record.should_deduct=1` 的学生取消才计入；审计时二者必须分开。

每次后续数据改动须先备份指定行、用课次与流水证明周期归属，再核对 `Allocated = Deducted + Remaining with`、课包余额和当前有效课次；不得为了满足数值关系重排用户课程。
