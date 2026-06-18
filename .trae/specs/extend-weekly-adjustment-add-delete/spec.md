# 本周排课增删调整 Spec

## Why
现有 `Adjust This Week` 只支持调整本周已有课次的时间、老师、教室和课时，但实际用户可能需要本周临时加一节课、取消一节课，或把本周课程数量做整体调整。系统需要把用户想改的“本周实际上课安排”和系统必须维护的“课包余额、冲突校验、后续排课预测、审计记录”分清楚。

## What Changes
- `Adjust This Week` 支持三类本周临时操作：修改已有课次、新增本周课次、删除/取消本周未核销课次。
- 新增和删除只作用于当前自然周，不修改长期 recurring schedule rule。
- 删除本周课次采用取消课次语义，不物理删除记录，保留审计和后续追踪能力。
- 系统计算本周调整后的总课时差额，并说明对课包余额和后续排课预测的影响。
- 系统在保存前要求用户确认影响，并要求为新增/删除提供原因。
- 后端统一校验课包归属、当前周范围、课次状态、学生/老师/教室冲突，并记录每个增删改动作的日志。
- **BREAKING** 无。

## Impact
- Affected specs: `add-weekly-schedule-adjustment`、scheduling 排课调整、lesson occurrence 管理、课包余额预测。
- Affected code: `frontend/src/views/schedule/RecurringScheduleView.vue`、`frontend/src/views/schedule/ScheduleAdjustmentsView.vue`、`frontend/src/api/schedule.ts`、`backend/src/main/java/com/classdeduction/system/modules/schedule/dto/WeeklyScheduleAdjustmentRequest.java`、`backend/src/main/java/com/classdeduction/system/modules/schedule/service/impl/ScheduleAdjustmentServiceImpl.java`、schedule VO、后端相关测试。

## ADDED Requirements
### Requirement: 本周课次新增
系统 SHALL 支持用户在 `Adjust This Week` 中为当前学生当前课包新增当前自然周内的临时课次。

#### Scenario: 用户新增本周课次
- **WHEN** 用户在 `Adjust This Week` 弹窗中点击新增课次并填写时间、课时、老师、教室和原因
- **THEN** 系统创建一个只属于当前周的 lesson occurrence
- **AND** 该课次不写入 recurring schedule rule
- **AND** 该课次参与本周课时差额计算

### Requirement: 本周课次删除
系统 SHALL 支持用户在 `Adjust This Week` 中取消当前周未核销、未取消的课次。

#### Scenario: 用户删除本周未核销课次
- **WHEN** 用户标记某个本周未核销课次为删除并填写原因
- **THEN** 系统将该课次标记为取消
- **AND** 系统不物理删除该课次记录
- **AND** 该课次不再计入调整后的本周课时

#### Scenario: 用户尝试删除已核销课次
- **WHEN** 用户尝试删除已核销课次
- **THEN** 系统拒绝操作并提示已核销课次不能通过本周调整删除

### Requirement: 本周增删改统一保存
系统 SHALL 在一次保存中支持混合提交本周课次的新增、修改和删除动作。

#### Scenario: 用户一次保存多个动作
- **WHEN** 用户同时新增一节课、修改一节课并删除一节课后保存
- **THEN** 系统按同一事务校验并保存所有动作
- **AND** 任一动作校验失败时，整次保存失败且不产生部分变更

### Requirement: 用户职责与确认
系统 SHALL 让用户明确表达本周临时变更原因，并在保存前确认对后续的影响。

#### Scenario: 新增或删除缺少原因
- **WHEN** 用户新增或删除课次但未填写原因
- **THEN** 系统阻止保存并要求填写原因

#### Scenario: 保存前确认影响
- **WHEN** 用户保存本周增删改
- **THEN** 系统展示本周原计划课时、调整后课时、差额、动作数量
- **AND** 系统用英文说明多上、少上或不变对后续课包余额和排课预测的影响

### Requirement: 系统职责与后续排课影响
系统 SHALL 不自动改变长期固定排课规则，但 SHALL 明确维护本周课时差额带来的后续预测影响。

#### Scenario: 本周增加课时
- **WHEN** 调整后本周课时大于原计划课时
- **THEN** 系统提示课包余额会更快消耗，后续课程可能更早结束
- **AND** 系统在结果中返回课时差额，供前端刷新和后续预测使用

#### Scenario: 本周减少课时
- **WHEN** 调整后本周课时小于原计划课时
- **THEN** 系统提示本周少消耗的课时可能需要补课或使预计结束时间顺延
- **AND** 系统不自动创建未来补课，除非用户明确新增课次

### Requirement: 冲突与边界校验
系统 SHALL 对新增和修改后的本周课次执行学生、老师、教室冲突校验。

#### Scenario: 新增课次造成冲突
- **WHEN** 用户新增的本周课次与学生、老师或教室的其他有效课次重叠
- **THEN** 系统拒绝保存并说明冲突资源

#### Scenario: 新增课次超出当前周
- **WHEN** 用户新增课次的时间不在当前自然周内
- **THEN** 系统拒绝保存

### Requirement: 审计日志
系统 SHALL 为本周增删改的每个动作记录调整日志。

#### Scenario: 保存成功后记录日志
- **WHEN** 本周增删改保存成功
- **THEN** 系统为新增、修改、删除分别记录动作类型、before/after 快照、原因、操作人和时间

## MODIFIED Requirements
### Requirement: 本周临时调整
系统 SHALL 将 `Adjust This Week` 从“只修改已有本周课次”扩展为“管理本周临时课次计划”，支持新增、修改、删除当前周课次，但仍不修改 recurring schedule rule，不批量重生成后续排课。

### Requirement: 本周课时差额计算
系统 SHALL 将新增课次计入调整后课时，将删除/取消课次从调整后课时中移除，并继续使用差额提示后续影响。

## REMOVED Requirements
无。
