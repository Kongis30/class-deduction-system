# Tasks

- [x] Task 1: 数据模型扩展确认阶段字段：为 `lesson_record` 增加二阶段确认所需的状态与时间/人字段，并提供数据迁移。
  - [x] SubTask 1.1: 在 `lesson_record` 增加列：`confirm_stage`（`PENDING_ADMIN/CONFIRMED/REJECTED`）、`teacher_confirmed_at`、`teacher_confirmed_by_user_id`、`admin_confirmed_at`、`admin_confirmed_by_user_id`、`reject_reason`、`rejected_at`、`rejected_by_user_id`（写入 `doc/migrations/` 新迁移脚本，并同步 `doc/mysql_schema_from_db.sql`）。
  - [x] SubTask 1.2: 迁移脚本将历史已扣课时记录回填为 `confirm_stage='CONFIRMED'`，`admin_confirmed_at` 用 `confirmed_at` 回填，保证历史余额/台账/计薪不变。
  - [x] SubTask 1.3: 在 `LessonRecordEntity` 增加对应字段映射。

- [x] Task 2: 后端确认服务改造为二阶段状态机：拆分"老师初确认（不扣课时）"与"管理员终确认（扣课时）"。
  - [x] SubTask 2.1: 在 `LessonRecordServiceImpl` 中抽出"扣课时+台账+课包状态+顺延补课"为独立的"终确认结算"方法，供终确认与管理员单方确认复用。
  - [x] SubTask 2.2: 老师初确认路径（`TeacherSelfLessonServiceImpl` → 通用 service）仅创建 `PENDING_ADMIN` 记录、写实际师资/教室/时间/课时/备注，不扣课时、不写台账、不变更课包、不顺延，并把 occurrence 标记为待复核而非直接 `MERGED`。
  - [x] SubTask 2.3: 新增管理员终确认方法：对 `PENDING_ADMIN` 记录执行结算并置 `CONFIRMED`，记录 `admin_confirmed_at/by`。
  - [x] SubTask 2.4: 管理员单方兜底：管理员对老师未初确认课次确认时，直接创建 `CONFIRMED` 记录并结算（标记代确认）。
  - [x] SubTask 2.5: 新增管理员驳回方法：对 `PENDING_ADMIN` 记录置 `REJECTED`、记录原因，退回 occurrence 为可重新初确认状态，且不发生扣课时（无台账冲正，因初确认本就未扣）。
  - [x] SubTask 2.6: 调整 occurrence 状态流转：仅在 `CONFIRMED` 时标记 `MERGED`；`PENDING_ADMIN` 用独立中间标识；驳回后恢复 `SCHEDULED`。

- [x] Task 3: 后端接口与权限：暴露终确认/驳回端点并按角色约束。
  - [x] SubTask 3.1: `LessonRecordController` 增加终确认与驳回端点（`@RequireRoles({"ADMIN"})`，或 ADMIN/STAFF 视复核职责而定）。
  - [x] SubTask 3.2: 老师初确认走 `TeacherLessonController`（`@RequireRoles({"TEACHER"})`）；通用 `POST /v1/lessons/records` 按当前用户角色分流（TEACHER→初确认，ADMIN/STAFF→终确认或单方确认）。
  - [x] SubTask 3.3: 班课确认接口（`ClassGroupController` 及其 service）按同一二阶段语义改造。
  - [x] SubTask 3.4: 列表/详情 VO 增加 `confirmStage` 等字段，供前端按钮着色与状态展示。

- [x] Task 4: 工资结算过滤改造：只对 `CONFIRMED` 记录计薪。
  - [x] SubTask 4.1: 在 `TeacherPaymentServiceImpl.queryUnpaidLessons` 的查询条件增加 `confirm_stage='CONFIRMED'` 过滤，确保 `PENDING_ADMIN/REJECTED` 不被结算。

- [x] Task 5: 前端 Confirm 页双重确认交互：按角色区分初确认/终确认/驳回，并通过按钮颜色区分。
  - [x] SubTask 5.1: `frontend/src/api/lesson.ts`（及 `schedule.ts`）新增老师初确认、管理员终确认、驳回的 API 封装；`domain.ts`/相关类型增加 `confirmStage` 等字段。
  - [x] SubTask 5.2: `ScheduleAdjustmentsView.vue` 按当前用户角色（`stores/auth.ts` + `types/auth.ts` 的 `isTeacherUser`）渲染按钮：老师视角显示"老师确认"/"待管理员确认"；管理员视角对 `PENDING_ADMIN` 课次显示高亮变色确认按钮（提示"老师已确认，待复核"）、对未初确认课次显示普通确认按钮。
  - [x] SubTask 5.3: 管理员终确认弹窗复用确认表单并展示扣课预览；新增驳回弹窗（填写原因）。
  - [x] SubTask 5.4: 老师/班课两个 Tab 均覆盖上述交互。

- [x] Task 6: 测试与验证：覆盖二阶段状态机与计薪过滤。
  - [x] SubTask 6.1: 后端单测覆盖：老师初确认不扣课时、管理员终确认扣课时、管理员单方确认扣课时、驳回退回不扣课时、`PENDING_ADMIN` 不可计薪、`CONFIRMED` 可计薪。
  - [x] SubTask 6.2: 迁移脚本验证历史记录回填为 `CONFIRMED` 后余额与计薪不变。

# Task Dependencies
- Task 2 depends on Task 1
- Task 3 depends on Task 2
- Task 4 depends on Task 1
- Task 5 depends on Task 3
- Task 6 depends on Task 2, Task 3, Task 4
