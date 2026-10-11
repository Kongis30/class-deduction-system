# Feature Specification: 组班课权益一致性（收据更正加固 + 数据巡检修复）

> 状态：待确认（先文档后代码）。关联问题：Data Summary 中 Ian Cen 的报名周期只显示到 `9/30/2026`。

## 背景与目标

### 现象
Data Summary 中，学生 Ian Cen 的 `period` 列显示 `9/26/2026 - 9/30/2026`，但该学生购买的是全年课程（订单行 `SESSION × 33`，方案 `FULL_YEAR`，覆盖 `2026-09-12 ~ 2027-06-12`）。

### 根因
- 报表的班级课周期取自学生权益快照 `class_enrollment.enrollment_date ~ expiry_date`
  （`DataSummaryServiceImpl.resolveV2PeriodEndDate:1172`，优先于方案覆盖期）。
- Ian 名下唯一的权益是一条**已作废、且错误**的记录：
  `status=CANCELLED, billing_type=SESSION, purchased_quantity=1, enrollment_date=2026-09-26, expiry_date=2026-09-30`。
- 更严重的是：该权益 `CANCELLED` 导致**该学生在班级名单/考勤/续费提醒中都不在册**（`ClassGroupServiceImpl.java:207-212` 等按 `status='ACTIVE'` 过滤），而他仍在正常交费、订单为 `CONFIRMED`、无退款。

### 目标
1. 修正"订单行 ⇄ 学生权益"的一致性问题（治本）。
2. 加固收据更正（Edit Receipt）逻辑，杜绝再产生"孤儿/作废权益"，并让历史孤儿记录可被修复。
3. 报表做防御性兜底，坏数据也不再显示错误周期。
4. 提供可审计、可重复的一致性巡检与定向修复手段。

## 应然业务逻辑与不变量（核心决策）

### 数据模型
- 订单行 `enrollment_order_item`：定义"买了什么"——`class_id`、`group_project_id`、`group_registration_plan_id`、`package_unit`、`package_quantity`、`start_date`、`attendance_option`。
- 学生权益 `class_enrollment`：定义"学生在册"——`enrollment_date`、`expiry_date`、`purchased_quantity`、`billing_type`、`status`、外键。组班课 V2 不使用 `total_units/consumed_units/remaining_units`（现网均为 NULL）。

### 不变量（INV）
- **INV1** 每条 `status=ACTIVE` 的组班课订单行（`course_type<>'ONE_ON_ONE'` 且 `group_registration_plan_id` 非空）有且仅有 **一条** `status='ACTIVE'` 的权益（历史 `CANCELLED/EXPIRED` 可留档）。
- **INV2** 权益 `expiry_date`：`FIXED_DATES` 方案 = `plan.coverage_end_date`；`ROLLING_MONTHS` = 开始月滚动推算。
- **INV3** 权益 `purchased_quantity` = 订单行 `package_quantity`（或按 `sessions/purchasedMonths` 校正后的值）。
- **INV4** 权益 `enrollment_date` ∈ `[plan.coverage_start_date, plan.coverage_end_date]`，且不早于 `item.start_date`（当其落在覆盖期内）。
- **INV5** 权益 `billing_type` 与方案单位对应：`SESSION→PER_SESSION`，`MONTH→MONTHLY`。
- **INV6** 权益 `class_id / group_project_id / group_registration_plan_id` 与订单行一致。

### 收据更正应遵循的状态机
输入：订单行 before/after（class、plan、unit、quantity、start、attendance）。

1. **解析候选权益**：按 `enrollment_order_item_id`、`is_deleted=0` 取**全部**权益（**包含 `CANCELLED/EXPIRED`**），不再只看 `ACTIVE/EXPIRED`。
2. **仅金额/信息变化**（class/plan/unit/quantity/start 均不变）→ 只更新金额，权益不动。`(R1)`
3. **同班但计划/单位/数量/开始日变化** → 原地重算并更新该有效权益。`(R2)`
4. **换班** → 旧有效权益置 `CANCELLED`，新建一条 `ACTIVE`。`(R3)`
5. **订单行当前无有效权益**（只有 `CANCELLED`，或没有）：
   - 若 after 状态与「item+plan 应有状态」不一致 → **按 item+plan 重建一条 `ACTIVE` 权益（治愈）**，而不是抛 `CORRECTION_ENROLLMENT_DETACHED`；`(R4)`
   - 若一致 → 不动。
6. **收口断言**：更正在提交前校验 INV1–INV6；不满足则按策略修复或报错（不允许静默放行）。

### 续报
- 续报将已有权益**转挂**到新订单行（`enrollment_order_item_id` 更新），旧收据不再拥有该权益，不能再通过旧收据修改它（保留现有语义）。

## 现有逻辑缺陷（定位）

| 编号 | 位置 | 问题 |
| --- | --- | --- |
| P1 | `EnrollmentOrderServiceImpl.java:1742-1748` | 先无条件 `updateById(item)`，再处理权益；两者一致性依赖后续分支，缺统一断言 |
| P2 | `EnrollmentOrderServiceImpl.java:1888-1891` | 候选权益只取 `ACTIVE/EXPIRED`；只剩 `CANCELLED` 时要么静默返回、要么抛 `CORRECTION_ENROLLMENT_DETACHED`，**无法修复孤儿权益** |
| P3 | `EnrollmentOrderServiceImpl.java:2112+` vs legacy 分支 | 有 `group_project_id/plan` 时应统一走 `computeCorrectedV2ClassBilling` 派生窗口；历史 legacy 按次窗口（`SESSION`）与 V2 方案窗口不一致 |
| P4 | `DataSummaryServiceImpl.java:1165-1178` | `resolveV2PeriodEndDate` 无条件信任 `class_enrollment.expiry_date`（不校验权益是否 `ACTIVE`） |
| P5 | 全局 | 缺少一致性巡检/自愈，孤儿权益长期存在 |

## 修复范围

### A. 代码加固（治本）
1. `migrateCorrectedClassEnrollment` 候选集改为**包含全部状态**，按 R1–R4 决策；对无有效权益的情况走"重建"而非报错。
2. 更正在事务提交前执行 **INV 断言**（至少 INV1/INV2/INV3/INV6）。
3. 只要订单行带 `group_project_id/plan`，窗口一律用 `computeCorrectedV2ClassBilling` 派生，禁止 legacy 按次窗口写回。
4. （可选）新增内部一致性修复服务，供巡检/定向修复复用。

### B. 报表兜底（防御）
- `resolveV2PeriodEndDate`：命中权益非 `ACTIVE`（或缺失）时：
  - `FIXED_DATES` → 用 `plan.coverage_start_date ~ coverage_end_date`；
  - 否则回退订单行/班级/订单日期。
- 同理审视其它直接读 `class_enrollment.expiry_date` 的展示点。

### C. 数据巡检与定向修复（治标 + 收尾）
- 先出巡检清单（只读），再对命中记录按 item+plan 重建/修正权益；原作废记录保留为历史。
- 修复必须可审计、可重复、幂等；修复前后均做备份与复核。

## 数据巡检 SQL（只读）

```sql
-- Q1：ACTIVE 组班课订单行、且存在未删除的权益、但没有 ACTIVE 权益（真正的孤儿）
--     （排除仅剩 is_deleted=1 的退班记录，如 WITHDRAWN）
SELECT i.id AS item_id, i.detail, i.class_id, i.package_unit, i.package_quantity, i.start_date,
       i.group_registration_plan_id, o.enrollment_no, o.student_id
FROM enrollment_order_item i
JOIN enrollment_order o ON o.id = i.enrollment_order_id AND o.is_deleted = 0
WHERE i.is_deleted = 0 AND i.item_status = 'ACTIVE'
  AND i.course_type <> 'ONE_ON_ONE'
  AND i.group_registration_plan_id IS NOT NULL
  AND EXISTS (
    SELECT 1 FROM class_enrollment ce
    WHERE ce.enrollment_order_item_id = i.id AND ce.is_deleted = 0)
  AND NOT EXISTS (
    SELECT 1 FROM class_enrollment ce
    WHERE ce.enrollment_order_item_id = i.id AND ce.is_deleted = 0 AND ce.status = 'ACTIVE');

-- Q2：ACTIVE 权益的有效期 ≠ 方案覆盖期（FIXED_DATES）
SELECT ce.id, ce.student_id, ce.enrollment_order_item_id, ce.expiry_date,
       p.coverage_start_date, p.coverage_end_date, p.plan_name
FROM class_enrollment ce
JOIN group_registration_plan p ON p.id = ce.group_registration_plan_id
WHERE ce.is_deleted = 0 AND ce.status = 'ACTIVE' AND p.coverage_type = 'FIXED_DATES'
  AND ce.expiry_date <> p.coverage_end_date;

-- Q3：ACTIVE 权益数量 ≠ 订单行数量
SELECT ce.id, ce.purchased_quantity, i.package_quantity, i.package_unit, i.id AS item_id
FROM class_enrollment ce
JOIN enrollment_order_item i ON i.id = ce.enrollment_order_item_id AND i.is_deleted = 0
WHERE ce.is_deleted = 0 AND ce.status = 'ACTIVE'
  AND ce.purchased_quantity <> i.package_quantity;

-- Q4：同一 (item, student) 多条 ACTIVE 权益
SELECT enrollment_order_item_id, student_id, COUNT(*) AS active_count
FROM class_enrollment WHERE is_deleted = 0 AND status = 'ACTIVE'
GROUP BY enrollment_order_item_id, student_id HAVING COUNT(*) > 1;

-- Q5：权益的班级/项目/方案与订单行不一致
SELECT ce.id, ce.class_id, i.class_id AS item_class_id,
       ce.group_project_id, i.group_project_id AS item_project_id,
       ce.group_registration_plan_id, i.group_registration_plan_id AS item_plan_id
FROM class_enrollment ce
JOIN enrollment_order_item i ON i.id = ce.enrollment_order_item_id AND i.is_deleted = 0
WHERE ce.is_deleted = 0 AND ce.status = 'ACTIVE'
  AND (ce.class_id <> i.class_id
    OR ce.group_project_id <> i.group_project_id
    OR ce.group_registration_plan_id <> i.group_registration_plan_id);
```

## 巡检结果（线上 `class_deduction_system_v2`，只读）

| 检查 | 命中 | 说明 |
| --- | --- | --- |
| Q1 孤儿权益 | **1 条** | Ian Cen（订单行 `2101381584487260161`）。另一条 Jingyu Wu 的权益为 `WITHDRAWN` 且 `is_deleted=1`（正常退班），已通过修正后的 Q1 条件排除。 |
| Q2 有效期≠方案覆盖 | 0 | ACTIVE 权益的 `expiry_date` 均等于方案 `coverage_end_date`。 |
| Q3 数量≠订单数量 | 3 | Wenhao Ma(1 vs 2)、Zhi Yan Xu(12 vs 13)、kevin Chen(9 vs 10)。多与按比例/假日/中途入班相关，**需个案确认，不自动修复**。 |
| Q4 多条 ACTIVE | 0 | 无重复有效权益。 |
| Q5 关联不一致 | 0 | ACTIVE 权益的 class/project/plan 均与订单行一致。 |

结论：**真正的孤儿权益只有 Ian Cen 一条**；核心问题是 P1/P2（更正无法修复孤儿）与 P4（报表信任作废权益）。

## 定向修复（Ian Cen，草案）

- 目标记录：学生 `2101381584466288641`，订单行 `2101381584487260161`，班级 `2096404843075297281`，方案 `102082445821608118`（`FULL_YEAR`，覆盖 `2026-09-12 ~ 2027-06-12`）。
- 规则：保留原 `CANCELLED` 记录作历史，**新建一条 ACTIVE 权益**（与更正状态机一致）：
  - `enrollment_date` = `item.start_date`（`2026-09-19`；若为空则用 `plan.coverage_start_date`）——**决策点：是否统一取方案起始 `2026-09-12` 以与同班全年记录一致**。
  - `expiry_date` = `plan.coverage_end_date` = `2027-06-12`
  - `billing_type` = `PER_SESSION`；`purchased_quantity` = `33`；`attendance_option` = `MORNING`
  - `group_project_id` = `102082445821608100`；`group_registration_plan_id` = `102082445821608118`；`status` = `ACTIVE`
- 执行方式：优先通过后端"权益修复"接口/脚本执行（可审计、可重复），而非裸 SQL；执行前整库备份，执行后用 Q1–Q5 复核归零。

## 受影响模块/文件
- 后端：
  - `modules/enrollment/service/impl/EnrollmentOrderServiceImpl.java`（更正状态机、INV 断言）
  - `modules/enrollment/service/impl/DataSummaryServiceImpl.java`（报表兜底）
  - `modules/schedule/service/impl/ClassGroupServiceImpl.java`（名单/考勤；复核）
  - （新）一致性巡检/修复服务 + 单元测试
- 数据：定向修复 Ian Cen（及 Q1–Q5 命中记录）
- 文档：本 spec + 迁移/修复记录

## 边界情况
- 换班后旧班作废 + 新班生效；续报转挂后的旧收据不应被计入。
- `ROLLING_MONTHS` 方案不套用 `coverage_end_date`。
- 1 对多/共享不涉及本流程（一对一走 `course_package`）。
- 历史 `EXPIRED` 权益不应被误判为"孤儿"。
- 学生确已退班（如整班删除 `deleteClassGroup` 置 `CANCELLED`+`is_deleted=1`）不应被自动重建。

## 验收标准
1. 修复后 Ian Cen：班级名单出现；Data Summary `period` 显示为全年（`2026-09-12/09-19 ~ 2027-06-12`，取决于决策点）；无退款语义不变。
2. 巡检 Q1–Q5 归零（或给出经确认的例外清单）。
3. 更正回归：
   - 仅改金额 → 权益不变；
   - 同班改课时/开始日 → 权益原地更新且满足 INV2–INV4；
   - 换班 → 旧 `CANCELLED` + 新 `ACTIVE`；
   - 对有孤儿权益的订单行再次更正 → 能重建而非报死结。
4. 报表兜底：非 ACTIVE 权益/无权益时不再显示错误周期。
5. 后端单测 + 前端 `typecheck` 通过；生产执行前有备份、执行后有复核记录。

## 风险与回滚
- 风险：重建权益会影响名单/考勤/消课与续费提醒；更正加固改动触及订单保存主流程。
- 缓解：先只读巡检；修复用小范围、幂等、可审计的方式；后端改动补单测；上线前备份。
- 回滚：代码回滚分支；数据回滚使用执行前备份。

## 待确认项
1. Ian 修复的 `enrollment_date` 取 `2026-09-19`（其报名开始）还是 `2026-09-12`（方案起始，与同班全年记录一致）？
2. 报表兜底与代码加固是否同一批上线，还是先上报表兜底（低风险）？
3. Q3 的 3 条数量差异（Wenhao Ma / Zhi Yan Xu / kevin Chen）是否属于正常（按比例/假日/中途入班），需要逐个确认后再决定是否修复。
