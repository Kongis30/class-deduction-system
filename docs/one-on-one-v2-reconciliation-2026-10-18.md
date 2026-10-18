# 2026-10-18 JIN V2 私教课时核对与分批修复

以 `class_deduction_system_v2` 为数据源。字段定义以 [one-on-one-hours-business-rules.md](one-on-one-hours-business-rules.md) 为准；本次不更改任何课包余额、已确认消课、老师分配快照或用户设计的现有课次。

## 已确认并修复

| 范围 | 根因 | 修复及验证 |
| --- | --- | --- |
| Chenxi Lin / Yuanhang Liu 的共享课组 | 2026-09-24 09:37 和 09:40 建立了内容完全相同的两条 ACTIVE 规则。后创建的规则没有课次，却被视为当前周期，原规则的 10h 已消课未进入老师级 Deducted。 | 将空的重复规则 `2103117340461289474` 标为 ENDED；保留承载所有课程及消课的规则 `2103116509590638594`。两位学生均为 Angel 16/2/14h、Henry 30/8/22h，老师剩余额合计 36h，与各自课包余额相同。修复前两条规则的备份：`/tmp/jin_v2_duplicate_group_rules_before_20261018.sql`。代码新增同一课包重复创建 ACTIVE 排课的阻断。课次仍超排 4h，见下方待处理项。 |
| Muze Song / Ms. Claudia | 当前周期课位分配 20h，原有有效课次 18h；旧规则复用 ID，续排将旧周期课次误计入本周期额度，最后 2h 无法生成。 | 代码续排按既有的 `PrivateSchedulePeriod` 周期归属扣减额度；另用现有带余额、重复和冲突检查的 `20261009_04_muze_restore_12_12_lesson.sql` 补入 12 月 12 日 09:00–11:00 的 2h。核对结果：当前有效课次 10 节、20h，Allocated 20h。 |

## 待逐包核对，不能改字段凑数

- **共享课组超排**：Chenxi Lin / Yuanhang Liu 各自有 36h 课包余额和 36h 老师剩余额，但有效的未消课课次合计 40h，其中 Mr. Henry 未消课课次 26h，高于其 Remaining with 22h。重复规则修复没有移动或删除这些课次。不能通过把 Henry 的 Allocated 从 30h 改为 34h 来掩盖超排；须核对原始排课意图，再决定是否取得 4h 的有效新增课时或调整最后两节未来课。
- **旧周期消课在新排课后才确认**：Brian Zheng / Mr. Blum 的 10 月 5 日 2h 课程在 10 月 6 日 16:40 排课调整后、17:41 才确认。课程日期属于旧周期；按规范不能把该 2h 混入新周期 Deducted。老师快照 14h、课包随后减少 2h 的差额，须根据当时额度转移凭证决定后续业务处理。Erika Yu / Ms. Sarah 的 16h 分配额已由用户明确要求保留，不能改成 10h。
- **老师余额超过课包余额**：Angela Li 多 4h；Brian Zheng、Elly Qu、Gloria Deng、Jinghe Hu、Zhen Shen Zou 各多 2h；Erika Yu 多 6h（用户已明确保留 16h 分配）。这些是历史分配快照、旧周期后确认消课或人工调整之间的真实差额，不得通过改当前周期 Deducted 凑数。Jennie Yu / Ms. Kiko 的 2h ADHOC 例外计入后，Jennie 老师余额合计为 70h，与课包 70h 一致，并非异常。
- **购买依据核验**：上述七个课包各自关联的报名订单项均只有一项，课包 `bonus_units` 仅 Erika 有已计入总课时的 2h；现有订单项未显示可直接补入这些差额的另一笔购买。不能自动给课包加课时。
- **其它课次与课位快照不一致**：Bella Huang、Brian Huang、Darian Lin、David Jin 的差额涉及保留的旧周期未确认课次；Katherine Chen、Kylie Yan、Liekkas Lin、Nelson Jin、Tylus Lee 等存在有效课次超过老师剩余额的情况。须先逐条核实补课、保留例外、历史自动续排及是否有新增课时凭证。无业务凭证不得缩减已保存的 Allocated、补写 Deducted 或移动课程时间。
- **技术取消不是收费取消**：`lesson_occurrence.planning_status=CANCELLED` 是排课重建时的技术状态，不计入 Deducted。只有管理员最终确认、`lesson_record.should_deduct=1` 的学生取消才计入；审计时二者必须分开。

每次后续数据改动须先备份指定行、用课次与流水证明周期归属，再核对 `Allocated = Deducted + Remaining with`、课包余额和当前有效课次；不得为了满足数值关系重排用户课程。
