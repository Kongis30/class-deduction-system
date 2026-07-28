# 适配课程产品价格矩阵 Spec

## Why

PPT 中的课程结构不是单一“课程产品=一个价格”，而是“课程产品 -> 年级/档位 -> 时长/周期/时段 -> 价格”的矩阵。当前系统已有 `Course Product` 和 `Course Price Option` 两层，但页面和数据约束还没有把这套矩阵清晰表达出来，报名、收费、排课和消课也需要统一消费同一套结构。

## What Changes

- 将课程设置从单行产品维护调整为课程产品矩阵：产品为父级，年级/时长/时段/价格为子级价格行。
- 将 `Course Price Option` 作为可报名价格行，替代手填 Product ID 的配置体验。
- 明确 1 对 1 与班课的结构差异：1 对 1 价格行生成课包课时；班课价格行必须能绑定或选择具体班级以支撑排课和消课资格。
- 让报名页按“课程 -> 年级/档位 -> 时长/周期/时段 -> 价格”逐级选择，并自动带出 Detail 和 Price。
- 为课程设置提供更清晰的矩阵维护、引用状态、失效提示和下游影响说明。
- 标记需要产品确认的规则，不在没有规则时隐式实现班课部分购买、跨月补课或 credit。

## Impact

- Affected specs: Course Products、Course Pricing、Registration intake、Course Packages、Class Groups、Lesson confirmation、Recurring schedules、Reports。
- Affected code: `frontend/src/views/base-data/CourseProductsView.vue`、`frontend/src/views/system/CoursePriceOptionsView.vue`、`frontend/src/views/registration/RegistrationIntakeView.vue`、`CourseProductServiceImpl`、`CoursePriceOptionServiceImpl`、`RegistrationIntakeServiceImpl`、`ClassGroupServiceImpl`、`course_product`、`course_price_option`、`enrollment_order_item`。

## PPT Course Matrix

PPT 提到的课程结构应作为课程设置的首版标准矩阵：

| Register Class | 年级/档位 | 时长/周期 | 时段 | 结构含义 |
| --- | --- | --- | --- | --- |
| 课后班 | G1-G5、G6-G8、ESL | 2 个月、6 个月、10 个月 | 周一到周五 3-6pm | 班课，按月/周期收费，价格随年级和周期变化。 |
| 周六班 | G1-2、G3-G6、SHSAT、PSAT、SAT | 秋季 13 周、春季 20 周、全年 33 周 | 半天 9am-12pm 或 1pm-4pm；全天 9am-4pm | 班课，价格随档位、周期和半天/全天变化。 |
| 暑假班 | G1-G6、SHSAT、PSAT、SAT、ESL | 7/6-8/21，7 周 | 半天 9am-12pm 或 1pm-4pm；全天 9am-4pm | 班课，固定暑期周期，价格随档位和半天/全天变化。 |
| 冲刺班 | 待产品确认 | 待产品确认 | 待产品确认 | PPT 仅列为报名类型，完整矩阵需补齐后再配置。 |
| 1 对 1 | G1-G5、G6-G8、G9-G12、AP | 40 小时、60 小时、120 小时 | 由排课确定 | 课时包，价格随年级/档位和购买小时数变化。 |

## Target Data Model

### Course Product

`Course Product` SHALL represent the course family, not every price. It should answer: “这是什么课，以及它是 1 对 1 还是班课？”

Recommended fields:

- `courseType` / `registerClass`: fixed values such as AFTER_SCHOOL、SATURDAY、SUMMER、SPRINT、ONE_ON_ONE。
- `productName`: display name, for example `Saturday Class` or `1 on 1 Tutoring`。
- `teachingMode`: `ONE_ON_ONE` or `GROUP`。
- `status`: ACTIVE / INACTIVE。
- common operational defaults: notes, optional default teacher/classroom/schedule only when they apply to the whole product.

`listPrice` on `Course Product` SHALL no longer be treated as the authoritative sale price when grade and duration have different prices. Historical snapshots remain unchanged.

### Course Price Option

`Course Price Option` SHALL represent one sellable matrix row under a product:

- parent `courseProductId` selected from a dropdown, not typed as a numeric ID.
- `courseType` inherited or validated against the parent product.
- `gradeBand`, such as G1-G5 or AP.
- `durationLabel`, such as 2 months, 13 weeks, 40 hours, Full day.
- `periodLabel`, such as Fall, Spring, Full year, Summer 2026.
- `timeSlotLabel`, such as Mon-Fri 3-6pm or Sat 9am-12pm.
- `oneOnOneHours` for 1 对 1 price rows.
- `priceAmount` as the authoritative configured price.
- `status`, `sortOrder`, and optional notes.

For ACTIVE rows, missing price SHALL block registration unless the user explicitly performs a manual price override with a visible reason.

### Class Group

`Class Group` SHALL represent the actual operational class for group courses:

- actual teacher.
- actual classroom, which may be filled later if the center has not assigned rooms yet.
- actual weekly schedule.
- active enrolled students.
- generated lesson occurrences.

For group course registration, the price option can define the commercial package, but the student must ultimately be linked to a concrete class group before class deduction can be correct.

## ADDED Requirements

### Requirement: 课程产品必须支持矩阵化价格

The system SHALL let administrators maintain one course product with multiple grade/duration/time/price rows.

#### Scenario: 管理 1 对 1 价格矩阵

- **WHEN** 管理员打开 1 对 1 course product。
- **THEN** 页面展示 G1-G5、G6-G8、G9-G12、AP 等年级/档位下的 40/60/120 小时价格行。
- **AND** 每行可维护购买小时数、价格、状态和排序。

#### Scenario: 管理班课价格矩阵

- **WHEN** 管理员打开课后班、周六班或暑假班 product。
- **THEN** 页面展示该产品下的年级/档位、周期、半天/全天或时段、价格和状态。
- **AND** 管理员可以清楚看到该价格行是否已有可报名的班级。

### Requirement: Course Pricing 不允许手填 Product ID

The system SHALL replace numeric `Course product ID` input with a searchable product selector and validate teaching mode compatibility.

#### Scenario: 选择 1 对 1 产品

- **WHEN** 管理员创建 1 对 1 price option。
- **THEN** product selector only allows ACTIVE `ONE_ON_ONE` products.
- **AND** 保存时校验 `oneOnOneHours` 大于 0。

#### Scenario: 选择班课产品

- **WHEN** 管理员创建 group price option。
- **THEN** product selector only allows ACTIVE `GROUP` products.
- **AND** 页面提示该 price option 还需要绑定或选择具体班级后才能完整支持排课和消课。

### Requirement: 报名页必须按矩阵逐级选择课程

The system SHALL guide staff through course type, product, grade, duration/period/time, and price selection instead of exposing raw IDs or free-text course details.

#### Scenario: 员工选择课程价格行

- **WHEN** 员工在报名表中选择 course type and product。
- **THEN** 系统只展示该 product 下 ACTIVE 的 grade/duration/time price rows。
- **AND** 选择 price row 后自动生成 Detail and Price。
- **AND** 手动改价必须显示 `Manual price` and preserve both configured price and final price.

#### Scenario: 价格配置缺失

- **WHEN** 某课程没有可用 ACTIVE price row。
- **THEN** 报名页提示 “Course pricing is missing”。
- **AND** 提供打开 Course Pricing 的下一步，不允许静默用 0 元提交。

### Requirement: 1 对 1 price row 必须生成正确课包

The system SHALL use the selected 1 对 1 price row to create package units, pricing snapshot, teacher assignment, and payment records.

#### Scenario: 购买 60 小时 1 对 1

- **WHEN** 报名行选择 1 对 1、G6-G8、60 hours price row。
- **THEN** 系统创建 120 units 的 course package because 1 unit = 0.5 hour。
- **AND** 订单明细保存 configured price、final price、grade band、duration and selected teacher。

### Requirement: 班课 price row 必须连接收费与班级资格

The system SHALL connect group price rows with class enrollment eligibility so that payment, active period, lesson confirmation, and renewal reminders use the same structure.

#### Scenario: 购买周六班半天秋季 13 周

- **WHEN** 报名行选择 Saturday Class、grade band、Fall 13 weeks、half-day price row and class group。
- **THEN** 系统创建 class enrollment with the selected order item and purchased period/quantity snapshot。
- **AND** class deduction only includes the student while the enrollment is active and eligible for that occurrence。

#### Scenario: 班课尚未补教室

- **WHEN** 班课 price row and class group do not yet have a classroom。
- **THEN** 报名可继续 only if classroom is configured as optional for that stage。
- **AND** Classrooms 页面 must show a pending classroom assignment item with conflict-check next step.

### Requirement: 课程设置必须提供清晰低学习成本体验

The system SHALL present course setup as a spreadsheet-like matrix aligned with the paper registration workflow.

#### Scenario: 管理员维护价格

- **WHEN** 管理员进入 Course Settings。
- **THEN** 左侧 shows the five course categories。
- **AND** 中间 shows product list。
- **AND** 右侧 shows editable grade/duration/time/price rows for the selected product。

#### Scenario: 产品已被使用

- **WHEN** a course product or price row has been used by an order item, course package, or class enrollment。
- **THEN** destructive structural fields are locked。
- **AND** the UI suggests copying the product or adding a new price row instead of editing historical structure.

## MODIFIED Requirements

### Requirement: Course Product and Course Pricing relationship

`Course Product` SHALL be the parent course family. `Course Price Option` SHALL be the child sellable price matrix row. Registration SHALL consume price rows; execution SHALL create `Course Package` for 1 对 1 or `Class Enrollment` for group classes.

### Requirement: Historical snapshots

Historical order items, receipts, course packages, and class enrollments SHALL keep their original selected course structure and price snapshots after course settings change.

### Requirement: Reports and student history

Reports and student history SHALL show the selected course type, product, grade/duration/time detail, configured price, final price, payment status, and for 1 对 1 the remaining hours; for group courses, the enrollment period or purchased quantity.

## REMOVED Requirements

### Requirement: Course Product list price as enrollment price

**Reason**: The PPT requires price to vary by grade and duration under the same course product.

**Migration**: Keep existing `listPrice` as legacy/reference display only. New registration pricing SHALL come from active `Course Price Option` rows or an explicit manual override.
