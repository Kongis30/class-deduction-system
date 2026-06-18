# 本周临时排课调整 Spec

## Why
当前 scheduling 页面可以修改某个学生某个课包的后续排课安排，但实际运营中学生经常只需要临时调整本周课程。系统需要支持只改本周排课，同时清楚提示本周多上或少上课时对后续课包消耗和排课完成时间的影响。

## What Changes
- 在 scheduling 的 1v1 课包排课区域增加“本周临时调整”能力。
- 用户可以只调整当前周内该学生该课包的未核销课次，不改变长期固定排课规则。
- 调整内容支持本周课次的时间、老师、教室和课时长度变化。
- 系统计算调整前后本周总课时差额，并提示对课包剩余课时与后续排课的影响。
- 后端记录本周临时调整日志，保留调整前后快照。
- 后端校验学生、老师、教室时间冲突，并阻止修改已核销或已取消课次。
- **BREAKING** 无。

## Impact
- Affected specs: scheduling 排课调整、lesson occurrence 管理、课包课时消耗预估。
- Affected code: `frontend/src/views/schedule/ScheduleAdjustmentsView.vue`、`frontend/src/api/schedule.ts`、`backend/src/main/java/com/classdeduction/system/modules/schedule/controller/ScheduleAdjustmentController.java`、`backend/src/main/java/com/classdeduction/system/modules/schedule/service/impl/ScheduleAdjustmentServiceImpl.java`、schedule DTO/VO、相关单元测试。

## ADDED Requirements
### Requirement: 本周临时调整入口
系统 SHALL 在学生 1v1 课包排课列表中提供本周临时调整入口，入口只作用于当前周，不修改 recurring schedule rule。

#### Scenario: 用户打开本周临时调整
- **WHEN** 用户选择某个学生的某个 1v1 课包并点击本周临时调整
- **THEN** 系统展示该课包当前自然周内未核销、未取消的课次
- **AND** 系统展示当前周原计划总课时

### Requirement: 只调整本周课次
系统 SHALL 支持用户编辑当前周内未核销课次的开始时间、结束时间、课时、老师和教室。

#### Scenario: 用户保存本周调整
- **WHEN** 用户修改本周课次并提交
- **THEN** 系统只更新本周被修改的 lesson occurrence
- **AND** 系统不更新该课包的 recurring schedule rule
- **AND** 系统不批量重生成本周之后的固定排课

### Requirement: 本周课时差额计算
系统 SHALL 计算调整前后本周总计划课时差额，并在保存前展示影响提示。

#### Scenario: 本周多上课
- **WHEN** 调整后本周总课时大于原本周总课时
- **THEN** 系统提示本周将多消耗课包课时
- **AND** 系统提示后续排课可能更早结束或更早耗尽课包余额

#### Scenario: 本周少上课
- **WHEN** 调整后本周总课时小于原本周总课时
- **THEN** 系统提示本周将少消耗课包课时
- **AND** 系统提示后续排课可能需要额外补课或课包结束时间顺延

#### Scenario: 本周课时不变
- **WHEN** 调整后本周总课时等于原本周总课时
- **THEN** 系统提示只改变本周时间安排，不影响课包总消耗节奏

### Requirement: 冲突与状态校验
系统 SHALL 在保存本周临时调整时校验课次状态和资源冲突。

#### Scenario: 调整不可修改课次
- **WHEN** 用户尝试修改已核销或已取消课次
- **THEN** 系统拒绝保存并返回明确错误

#### Scenario: 调整造成冲突
- **WHEN** 调整后的学生、老师或教室时间与其他有效课次重叠
- **THEN** 系统拒绝保存并说明冲突资源

### Requirement: 调整日志
系统 SHALL 为每次本周临时调整保存操作日志。

#### Scenario: 保存成功
- **WHEN** 本周临时调整保存成功
- **THEN** 系统记录每个被修改课次的调整前快照、调整后快照、调整原因和操作人

## MODIFIED Requirements
### Requirement: 现有单课次调课
现有单课次调课 SHALL 继续支持对单个 lesson occurrence 的时间、老师、教室或补课调整；本周临时调整 SHALL 复用一致的冲突校验和日志语义，但以批量周视图方式提交。

### Requirement: 课包后续排课说明
课包排课页面 SHALL 在本周临时调整中明确区分“只改本周”和“修改后续固定排课”，避免用户误以为本周临时调整会改变之后每周的固定安排。

## REMOVED Requirements
无。
