# 线上 1-on-1 “Course name not recorded” 课次清单

- 盘点时间：2026-10-20（Jin 线上 `class_deduction_system_v2`，只读查询）。
- 范围：未软删除、属于 1-on-1 课包、在消课页面可见的课次；已排除已取消且没有确认记录的占位课次。
- 初次盘点为只读查询；后续已按下方执行记录回填其中 7 条可明确推断的课次。

## 结果摘要

- 初次盘点发现 **10 条会显示 “Course name not recorded”**，分布在 6 个学生课包中；10 条均已有已确认的课程记录。
- 数据库中另外有 19 条空 `lesson_occurrence.subject_name` 的已取消占位记录，页面不显示为待处理课次，故不列为本次待修复项。
- 另有 1 条未来课次的快照为空，但 API 可从其关联排课时段读取 `SHSAT ELA`，当前不会显示占位文案。
- Bella Huang 此前修复的 4 条不再出现在这份清单中。

## 后续修复执行情况

- 已回填 7 条有来源时段与唯一匹配活跃时段交叉验证的常规课次：Beau Hayworth 1 条、Darian Lin 2 条、David Jin 3 条、Terry Huang 1 条。
- 仅更新 `lesson_occurrence.subject_name`，并递增 occurrence 版本及更新时间；没有改动 lesson record、消课状态、扣课时或排课余额。线上复查这 7 条课程名均已写入。
- 已在 `course-name-occurrences-before-backfill.sql` 保存更新前的 7 条 occurrence 记录；执行 SQL 为 `course-name-backfill-confirmed.sql`。
- Brian Huang 的 2 条 `G6 ELA` 因用户要求忽略，未修改。
- Vincent Wang 的 1 条 `ADHOC` 临时课次仍为空。复核发现它不是软删除记录，且有已确认的消课记录；当前代码逻辑应将其包含在 Lesson records 中。用户暂未在页面找到，故先核对可见性，不回填课程名。
- 原清单中 9 条常规记录的回填建议已经执行其中 7 条；剩余 2 条 Brian 记录按要求跳过。19 条已取消占位记录没有改动。

## 待修复记录

“候选课程名”来自已保存的课次来源时段；对于有后续活跃排课完全匹配的记录，也核对了相同排课规则、星期、时间和老师。其中 7 条的回填已在“后续修复执行情况”中记录。

| 学生 | 课包 | 日期 / 时间 | 老师 | 课次状态 | 课次 ID | 候选课程名与核对说明 |
|---|---|---|---|---|---:|---|
| Beau Hayworth | `PK202609270029` · G1-G5 · 40h | 2026-10-03 15:00–17:00 | Ms. Angela | Completed | `2104382388139831298` | `ELA`；旧来源时段及同规则活跃时段一致 |
| Brian Huang | `PK202609270026` · G1-G5 · 60h | 2026-10-03 13:00–15:00 | Mr. David | Completed | `2104091773208436738` | `G6 ELA`；旧来源时段及同规则活跃时段一致，但课包年级写 G1-G5，需先确认此名称是否符合该学生实际课程 |
| Brian Huang | `PK202609270026` · G1-G5 · 60h | 2026-10-10 13:00–15:00 | Mr. David | Completed | `2104091773212631042` | `G6 ELA`；同上，修复前需核对年级差异 |
| Darian Lin | `PK202609270025` · G1-G5 · 40h | 2026-10-03 11:00–13:00 | Mr. David | Completed | `2104090983181918209` | `G3 ELA&MATH`；旧来源时段及同规则活跃时段一致 |
| Darian Lin | `PK202609270025` · G1-G5 · 40h | 2026-10-10 11:00–13:00 | Mr. David | Completed | `2104090983186112513` | `G3 ELA&MATH`；同上 |
| David Jin | `PK202609260003` · G1-G5 · 40h | 2026-09-26 16:00–18:00 | Mr. Erik | Completed | `2103953603544338433` | `ELA`；旧来源时段及同规则活跃时段一致 |
| David Jin | `PK202609260003` · G1-G5 · 40h | 2026-10-03 16:00–18:00 | Mr. Erik | Completed | `2104067397268295681` | `ELA`；同上 |
| David Jin | `PK202609260003` · G1-G5 · 40h | 2026-10-10 16:00–18:00 | Mr. Erik | Completed | `2104067397276684290` | `ELA`；同上 |
| Terry Huang | `PK202609240004` · G1-G5 · 120h | 2026-09-24 18:00–20:00 | Mr. Tony | Completed | `2103130135944810497` | `MATH`；旧来源时段及同规则活跃时段一致；对应截图中 9/24 的缺名课次 |
| Vincent Wang | `PK202609220001` · G6-G8 · 60h | 2026-10-10 18:00–20:00 | Mr. Blum | Completed | `2110057863310852098` | **无法可靠推断**；属于 `ADHOC`，没有排课规则或时段来源，创建日志也未记录课程名，需人工确认 |

## 为什么仍然会显示

1. **9 条常规课次是历史快照缺失。** `lesson_occurrence.subject_name` 为空，关联的旧排课时段已软删除；旧时段的 `updated_at` 是 2026-10-19，晚于课程日期。后端的历史回退逻辑会拒绝仅凭后来更新的时段覆盖过去课程；这些课次也没有对应的 `SCHEDULE_DETAILS_UPDATE` 课程名称变更历史。因此 API 返回的课程名仍为空。数据库仍保留旧时段名称，且有同规则、同星期/时间/老师/名称的活跃时段可交叉核验。
2. **1 条临时课次在创建时没有课程名。** Vincent Wang 的记录是 `ADHOC`，没有 `schedule_rule_id` 或 `schedule_slot_id`。创建临时课次时系统只在课包存在唯一活跃科目时自动填名；该课包同时有 `SHSAT MATH` 和 `SHSAT ELA`，无法唯一选择，于是 occurrence 和创建日志都没有课程名。相邻课程名不能证明这节由另一位老师教授的临时课到底是什么。

对应逻辑：`LessonOccurrenceServiceImpl.resolveOneOnOneSubjectName()` 对历史课次使用保守回退；`ScheduleAdjustmentServiceImpl.adjustWeekly()` / `buildCreatedOccurrence()` 为临时新增课次从唯一课包科目取值，且当前 Weekly adjustment 的新增项不要求填写 subject。

## 后续统一修复建议

1. 先复现 Vincent Wang 课包在 Lesson records 中无法找到该 occurrence 的情况；若确认页面确实不展示，再检查生产 API 响应与当前用户选择的课包是否一致。
2. 确认该临时课次有效且找回可靠课程名后，再按 occurrence ID 精确回填。
3. 后续代码修复应让临时新增课次明确保存课程名（由用户输入或明确选择来源），不要在多科目课包里静默写空；单独评估历史回退规则，避免把后来变化的课程名套到过去课次。

