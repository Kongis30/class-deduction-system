# 消课双重确认（老师初确认 + 管理员终确认）Spec

## Why
当前消课是"一步到位"：老师或管理员在 Confirm 页对某次课程点一次 `confirmLesson`，同一事务内立即写课程记录、扣减课时、变更课包状态、写台账，并使该课次进入可计薪范围。缺乏复核环节，老师单方确认即可直接影响课时余额与工资。业务上希望改为**双重确认**：老师先确认上课结果（初确认），管理员复核后做最终确认，最终确认通过才真正完成消课（扣课时）、才允许给老师计薪。

## What Changes
- 引入消课二阶段状态机：`PENDING_ADMIN`（老师已初确认，待管理员终确认）→ `CONFIRMED`（管理员已终确认，已扣课时）。新增 `REJECTED`（管理员驳回，退回老师）。
- **老师初确认**：老师登记上课结果（结果状态、实际师资/教室/时间、课时、备注），生成处于 `PENDING_ADMIN` 的消课记录，但**不扣课时、不写台账、不变更课包余额、不计薪**。
- **管理员终确认**：管理员复核后做最终确认，此时才真正扣减课时、写台账、更新课包状态、生成顺延补课，并使该记录进入可计薪范围。
- **管理员驳回**：管理员可驳回老师的初确认并填写驳回原因，课次退回未确认/可重新初确认状态，老师可修改后重新提交。
- **管理员单方兜底**：老师尚未初确认时，管理员可一步直接完成消课（直接进入 `CONFIRMED`，记录为管理员代确认）。
- **前端 Confirm 页按角色区分**：
  - 老师视角：对未确认课次显示"老师确认"按钮；初确认后该课次显示为"待管理员确认"。
  - 管理员视角：通过 confirm 按钮颜色区分——老师已初确认的课次按钮变色（高亮，提示"老师已确认，待复核"），管理员点击进入终确认；老师未确认的课次按钮为普通色（管理员单方兜底确认）。
- **工资侧**：只有处于 `CONFIRMED`（管理员终确认）的消课记录才可被工资结算查询到。`PENDING_ADMIN` 记录不可计薪。
- **撤销/驳回回滚**：复用现有 `revokeLesson` 的回滚范式处理"终确认后撤销"和"初确认被驳回"。
- **BREAKING**：`POST /v1/lessons/records`（管理员通用确认）的语义与既有数据假设变化——确认不再必然立即扣课时。需要数据迁移与历史记录回填确认状态。

## Impact
- Affected specs: 消课确认流程、课时扣减与台账、课包状态流转、老师工资结算可计薪范围、角色权限。
- Affected code:
  - 后端：`backend/.../modules/lesson/controller/LessonRecordController.java`、`TeacherLessonController.java`、`modules/lesson/service/impl/LessonRecordServiceImpl.java`、`TeacherSelfLessonServiceImpl.java`、`modules/lesson/entity/LessonRecordEntity.java`、相关 DTO/VO、`modules/finance/service/impl/TeacherPaymentServiceImpl.java`、班课 `modules/schedule/controller/ClassGroupController.java` 及其确认 service。
  - 数据库：`doc/mysql_schema_from_db.sql`、新增 `doc/migrations/` 迁移脚本（`lesson_record` 扩展确认状态字段）。
  - 前端：`frontend/src/views/schedule/ScheduleAdjustmentsView.vue`、`frontend/src/api/lesson.ts`、`frontend/src/api/schedule.ts`、`frontend/src/stores/auth.ts`、`frontend/src/types/auth.ts`、`frontend/src/types/domain.ts`。

## ADDED Requirements

### Requirement: 消课二阶段确认状态机
系统 SHALL 为每条消课记录维护确认阶段状态，取值至少包含 `PENDING_ADMIN`（老师已初确认待终确认）、`CONFIRMED`（管理员已终确认）、`REJECTED`（管理员已驳回）。课时扣减、台账写入、课包余额与状态变更、补课顺延、计薪可见 SHALL 仅在进入 `CONFIRMED` 时发生。

#### Scenario: 老师初确认不扣课时
- **WHEN** 老师对一节未确认课次提交确认
- **THEN** 系统创建一条 `PENDING_ADMIN` 的消课记录，记录上课结果、实际师资/教室/时间、课时与备注
- **AND** 系统不扣减课包剩余课时、不写课时台账、不变更课包状态、不生成顺延补课

#### Scenario: 管理员终确认完成消课
- **WHEN** 管理员对一条 `PENDING_ADMIN` 记录做最终确认
- **THEN** 系统按既有扣课规则扣减课包剩余课时、写课时台账、更新课包消耗与状态、按需生成顺延补课
- **AND** 系统将该记录置为 `CONFIRMED` 并记录终确认人与终确认时间

### Requirement: 老师初确认入口
系统 SHALL 在 Confirm 页为老师提供针对本人课次的初确认入口，老师初确认 SHALL 复用既有的结果状态、实际师资/教室/时间、课时与备注录入，并受"仅本人课次、限定结果状态、限定时间窗"的既有约束。

#### Scenario: 老师对本人课次初确认
- **WHEN** 老师对自己计划授课的未确认课次点击老师确认并提交
- **THEN** 系统校验该课次属于该老师本人且满足时间窗与结果状态约束
- **AND** 系统生成 `PENDING_ADMIN` 记录

#### Scenario: 老师对非本人课次
- **WHEN** 老师尝试对非本人计划授课的课次发起老师确认
- **THEN** 系统拒绝并返回明确错误

### Requirement: 管理员终确认入口与按钮颜色区分
系统 SHALL 在 Confirm 页为管理员区分展示课次确认状态：对处于 `PENDING_ADMIN`（老师已初确认）的课次，确认按钮 SHALL 以高亮/变色样式呈现并提示"老师已确认，待复核"；对老师尚未确认的课次，确认按钮 SHALL 以普通样式呈现。

#### Scenario: 管理员看到老师已确认的高亮按钮
- **WHEN** 老师已对某课次完成初确认
- **THEN** 管理员在 Confirm 页看到该课次的确认按钮变色高亮
- **AND** 管理员点击后进入终确认流程

#### Scenario: 管理员看到普通确认按钮
- **WHEN** 某课次老师尚未初确认
- **THEN** 管理员在 Confirm 页看到该课次的确认按钮为普通样式

### Requirement: 管理员驳回
系统 SHALL 允许管理员对 `PENDING_ADMIN` 记录驳回并填写驳回原因，驳回后该课次 SHALL 退回到可由老师重新初确认的状态，且不发生任何课时扣减。

#### Scenario: 管理员驳回老师初确认
- **WHEN** 管理员对一条 `PENDING_ADMIN` 记录提交驳回并填写原因
- **THEN** 系统将该课次退回为未确认/可重新初确认状态
- **AND** 系统不扣减课时、不写扣课台账
- **AND** 老师可修改后重新提交初确认

### Requirement: 管理员单方兜底消课
系统 SHALL 允许管理员对老师尚未初确认的课次一步直接完成消课，结果等同于终确认（直接进入 `CONFIRMED`、立即扣课时），并标记为管理员代确认。

#### Scenario: 管理员单方直接消课
- **WHEN** 管理员对一节老师未初确认的课次直接提交确认
- **THEN** 系统直接生成 `CONFIRMED` 记录并完成扣课时、台账、课包状态变更
- **AND** 系统标记该记录由管理员单方确认

### Requirement: 仅终确认记录可计薪
工资结算 SHALL 仅将处于 `CONFIRMED` 的消课记录纳入可计薪范围；`PENDING_ADMIN` 与 `REJECTED` 记录 SHALL 不可被工资结算查询到。

#### Scenario: 待终确认记录不计薪
- **WHEN** 老师已初确认但管理员尚未终确认某课次
- **THEN** 工资结算查询不返回该课次，不可结算工资

#### Scenario: 终确认记录可计薪
- **WHEN** 管理员已终确认且结果状态属于可计薪状态
- **THEN** 工资结算查询返回该课次，可被结算工资

## MODIFIED Requirements

### Requirement: 通用消课确认接口语义
`POST /v1/lessons/records` SHALL 不再无条件在确认时立即扣课时；其行为 SHALL 依确认阶段而定：老师初确认仅生成 `PENDING_ADMIN` 记录，管理员终确认或管理员单方确认才扣课时并进入 `CONFIRMED`。班课确认接口 SHALL 遵循一致的二阶段语义。

### Requirement: 历史消课记录迁移
既有的、已扣课时的消课记录 SHALL 在迁移后被视为 `CONFIRMED`，以保证历史课时余额、台账与已有计薪结果不被改变。

## REMOVED Requirements
无。
