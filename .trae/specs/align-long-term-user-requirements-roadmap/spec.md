# 长期对齐用户需求路线图 Spec

## Why

当前系统已经完成报名、课程矩阵、1 对 1 消课、教室日历和报表等主链路，但仍存在用户需求未完全闭环、入口认知不够稳定、数据口径一致性不足的问题。后续开发需要有一个长期计划，按用户真实工作流逐步收敛，而不是继续零散修页面。

## What Changes

- 建立面向用户工作流的长期开发路线，优先保证报名、课程、消课、报表的数据可信度。
- 将剩余需求分为必须实现、需要产品确认、体验优化和长期扩展四类。
- 为每个阶段定义可验收结果，避免只完成代码但用户仍不知道怎么用。
- 明确所有后续实现必须遵循：代码简洁、逻辑清晰、最小必要改动、用户视角优先。
- **BREAKING**：无。本规格不移除现有能力，只定义后续实施顺序和验收标准。

## Impact

- Affected specs:
  - `plan-new-requirements-refactor-roadmap`
  - `define-operational-data-integrity-model`
  - `audit-course-structure-fit`
  - `audit-common_business_lifecycle_coverage`
  - `add-student-registration-history-installments`
  - `add-dual-confirm-lesson-deduction`
- Affected code:
  - `frontend/src/utils/menu.ts`
  - `frontend/src/views/registration/RegistrationIntakeView.vue`
  - `frontend/src/views/base-data/CourseProductsView.vue`
  - `frontend/src/views/system/CoursePriceOptionsView.vue`
  - `frontend/src/views/one-on-one/OneOnOneDeductionView.vue`
  - `frontend/src/views/data-summary/DataSummaryView.vue`
  - `frontend/src/views/base-data/StudentsView.vue`
  - `frontend/src/views/schedule/*`
  - `backend/src/main/java/com/classdeduction/system/modules/enrollment/service/impl/*`
  - `backend/src/main/java/com/classdeduction/system/modules/lesson/service/impl/*`
  - `backend/src/main/java/com/classdeduction/system/modules/schedule/service/impl/*`
  - `backend/src/main/java/com/classdeduction/system/modules/finance/service/impl/*`

## Current Gap Priority Plan

本计划对齐新需求整理、报名改动清单、数据完整性规格和课程矩阵规格。Task 1 只固化规格，不实现业务代码。

### 当前状态清单

| 状态 | 事项 | 用户入口 | 受影响数据对象 | 优先级 |
| --- | --- | --- | --- | --- |
| 已完成 | 报名表单闭环基础能力：学生复用、订单、课程明细、课包/班课资格、付款、收据、1 对 1 账号和初始课次。 | Registration、Receipt、Students | `student`、`enrollment_order`、`enrollment_order_item`、`course_package`、`class_enrollment`、`payment_record`、`receipt_record`、`user_account`、`lesson_occurrence` | P0 基线 |
| 已完成 | 课程产品与价格行矩阵作为报名价格来源，报名按结构化课程选择生成 Detail 和 Price。 | Course Products、Course Pricing、Registration | `course_product`、`course_price_option`、`enrollment_order_item` | P0 基线 |
| 已完成 | 1 对 1 双确认消课基础闭环，管理员终确认后扣课、写台账并进入教师工资候选。 | 1-on-1 Deduction、Teacher Payments | `lesson_occurrence`、`lesson_record`、`hour_ledger`、`course_package`、`teacher_payment` | P0 基线 |
| 部分完成 | Students、Orders、Reports 对取消、退款、已收、余额的统计口径仍需统一。 | Students、Orders、Data Summary / Reports | `enrollment_order`、`payment_record`、`receipt_record`、Reports 派生数据 | P0 |
| 部分完成 | 后续补款已支持订单级追加，但多课包订单的课包级实收语义不清。 | Student history、Orders payment timeline、Reports | `payment_record`、`enrollment_order`、`course_package`、`enrollment_order_item` | P0 |
| 部分完成 | 报名价格校验已依赖价格矩阵，但缺价格、0 元、手动改价的阻断和解释需进一步收敛。 | Registration、Course Pricing | `course_price_option`、`enrollment_order_item`、`receipt_record`、Reports 派生数据 | P0 |
| 部分完成 | 排课和本周调整已有冲突校验，但返回给前端的冲突详情不够结构化。 | Recurring Schedule、Adjust This Week、Calendar | `lesson_occurrence`、`recurring_schedule_rule`、老师/学生/教室占用 | P1 |
| 部分完成 | 报名初始 1 对 1 课次已校验老师冲突，但缺学生同时间冲突校验。 | Registration、1-on-1 Lessons、Calendar | `lesson_occurrence`、`student`、`course_package` | P1 |
| 部分完成 | 教室占用视图已有基础能力，但缺“待补教室 -> 保存校验 -> Room Calendar 复查”的闭环验收。 | Classroom Management、Room Calendar、Class Group | `class_group`、`lesson_occurrence`、`classroom`、教室占用派生数据 | P1 |
| 未完成 | Reports 一致性检查缺口：付款流水 vs 订单金额、课包余额 vs 课时台账。 | Data Summary / Reports、Student history、Course Package detail | `payment_record`、`enrollment_order`、`course_package`、`hour_ledger`、`package_balance_snapshot` | P0 |
| 未完成 | 老师工资统计完整运营视图仍需按老师、日期范围、学生费率和终确认课时汇总。 | Teacher Payments、1-on-1 Deduction | `lesson_record`、`teacher_pay_rate`、`teacher_payment`、`course_package` | P1 |
| 未完成 | Reports 业务回归测试仍需覆盖取消/退款、快照优先、欠费、低课时、到期和一致性异常。 | Data Summary / Reports | Reports 派生数据、`enrollment_order`、`payment_record`、`course_package`、`hour_ledger` | P1 |

### P0 缺口

| 缺口 | 当前状态 | 用户入口 | 受影响数据对象 | 执行方向 |
| --- | --- | --- | --- | --- |
| 取消、退款、已收、余额口径不一致。 | 部分完成 | Students、Orders、Reports | `enrollment_order`、`payment_record`、`receipt_record`、Reports 派生数据 | 统一 CANCELLED / REFUNDED 订单在所有入口的应收、已收、余额规则。 |
| 多课包订单后续补款可能放大课包级实收。 | 部分完成 | Student history、Orders payment timeline、Reports | `payment_record`、`enrollment_order`、`course_package`、`enrollment_order_item` | 明确补款只更新订单级，或按明细/课包分摊；禁止重复计入每个课包。 |
| 缺价格、错误 0 元、手动改价边界不清。 | 部分完成 | Registration、Course Pricing | `course_price_option`、`enrollment_order_item`、`receipt_record` | 区分“缺价格”和“明确 0 元”；缺价格阻断，手动改价必须保存原因。 |
| Reports 缺跨源一致性检查。 | 未完成 | Data Summary / Reports | `payment_record`、`enrollment_order`、`course_package`、`hour_ledger`、`package_balance_snapshot` | 增加差异订单、差异课包、异常来源和修复入口。 |

### P1 缺口

| 缺口 | 当前状态 | 用户入口 | 受影响数据对象 | 执行方向 |
| --- | --- | --- | --- | --- |
| 老师工资统计未形成完整视图。 | 未完成 | Teacher Payments、1-on-1 Deduction | `lesson_record`、`teacher_pay_rate`、`teacher_payment` | 按老师和日期范围汇总终确认课时、费率、应付金额和结算状态。 |
| 教室后补闭环不完整。 | 部分完成 | Classroom Management、Room Calendar | `class_group`、`lesson_occurrence`、`classroom` | 提供待补列表、保存前冲突校验、保存后占用复查。 |
| 排课冲突详情不结构化。 | 部分完成 | Recurring Schedule、Adjust This Week、Calendar | `lesson_occurrence`、`recurring_schedule_rule`、老师/学生/教室占用 | 返回冲突资源、时间、已有课次入口，供前端稳定展示。 |
| 报名初始 1 对 1 课次缺学生冲突校验。 | 部分完成 | Registration、1-on-1 Lessons | `lesson_occurrence`、`student`、`course_package` | 初始课次生成纳入学生时间冲突，失败时给出换时间/查看日历入口。 |
| Reports 业务测试不足。 | 未完成 | Data Summary / Reports | Reports 派生数据及其源表 | 补充数据口径和一致性回归测试。 |

### P2 缺口

| 缺口 | 当前状态 | 用户入口 | 受影响数据对象 | 执行方向 |
| --- | --- | --- | --- | --- |
| 固定排课续期失败后的修复入口不明确。 | 部分完成 | Recurring Schedule、Calendar | `recurring_schedule_rule`、`lesson_occurrence` | 暴露失败原因、最近失败时间和重试入口。 |
| Excel 导出范围未确认。 | 需产品确认 | Reports、Students、Teacher Payments | Reports 派生数据、学生付款时间线、教师工资数据 | 明确导出对象、字段、权限、脱敏和排序后再实现。 |
| 支票号结构化字段未确认。 | 需产品确认 | Registration、Payment timeline、Receipt | `payment_record`、`receipt_record` | 确认 check number 是否必填、可否后补、展示和查询位置。 |
| 线上课税号规则未确认。 | 需产品确认 | Receipt | `receipt_record`、校区/线上课配置 | 确认线上课 EIN 来源和 Show EIN 展示规则。 |

### Product Confirmation Queue

| 对应任务 | 用户入口 | 必须确认的问题 | 未确认前系统保守处理原则 |
| --- | --- | --- | --- |
| 4.1 部分退款 | Orders、Student history、Reports、Receipt、Teacher Payments | 退款粒度按订单、课程明细还是课包；已消课后是否回补课时；是否影响教师工资；收据是否重开、冲销或备注；Reports 中已收、退款、余额如何展示。 | 只保留整单取消/退款和人工备注口径，不自动拆分退款、不修改课包余额、不回滚课时台账、不调整教师工资。 |
| 4.2 credit / 预存款 | Registration、Payment timeline、Reports、Receipt | credit 是否是学生级账户；是否允许跨订单抵扣；是否可退款；是否进入收据；Reports 是否计入已收、余额或单独列示。 | 继续阻止超额付款，不把多付金额静默记为付款或预存款，不生成 credit 账户或跨订单抵扣。 |
| 4.3 冲刺班与复杂班课 | Course Pricing、Registration、Class Group、Calendar、Reports | 冲刺班完整价格矩阵；班课是否允许部分购买；跨月补课如何生成课次和费用；停课后续费是否顺延周期、价格和资格。 | 不隐式生成班课资格、补课课次、顺延周期或续费规则；只按已配置的课程价格和明确报名明细处理。 |
| 4.4 Excel、税号、支票号 | Reports、Students、Teacher Payments、Receipt、Registration、Payment timeline | Excel 导出的对象、字段、排序、权限和脱敏；线上课收据使用哪个 EIN 或是否不显示；支票号是否必填、可否后补、在哪里展示和查询。 | 先保证页面数据和收据可解释；不提前固化 Excel 导出字段、不为线上课猜测税号、不强制新增 check number 规则。 |

#### 产品确认队列边界

- 部分退款：确认退款粒度、课时回滚、已消课工资、收据修正、Reports 口径前，不实现按课程、按课包或按金额的自动退款。
- credit / 预存款：确认账户归属、跨订单抵扣、退款、收据和 Reports 归集前，不把超额付款转成余额或抵扣金。
- 冲刺班和班课复杂运营：确认冲刺班价格矩阵、班课部分购买、跨月补课、停课续费前，不自动生成额外资格、补课课次或续费状态。
- Excel 导出、线上课税号、支票号：确认导出字段/权限/脱敏/排序、线上课 EIN 来源和展示规则、支票号必填/后补/查询规则前，不新增结构化字段或导出文件口径。

## ADDED Requirements

### Requirement: 长期计划必须以用户工作流排序

系统后续开发 SHALL 按用户实际操作路径排序，而不是按技术模块排序。

#### Scenario: 用户从报名到消课到报表

- **WHEN** 员工完成一次报名
- **THEN** 系统应能清楚解释该报名生成了学生、订单、课程明细、课包或班课资格、付款、收据、账号和初始课次中的哪些对象
- **AND** 后续可从学生历史、课程包、1 对 1 消课、Calendar 和 Reports 找到同一批数据
- **AND** 每个入口的字段含义和金额/课时口径一致

### Requirement: 第一阶段优先修复数据可信度

系统 SHALL 优先解决会污染金额、余额、课时、报表的 P0/P1 风险。

#### Scenario: 报名或补款影响多个统计入口

- **WHEN** 用户提交报名、补记付款、取消订单或查看 Reports
- **THEN** Students、Orders、Course Packages、Reports 中的应收、已收、余额、课时和状态口径应一致
- **AND** 系统应阻止缺价格、错误 0 元、超额付款、课包余额与台账不一致等高风险数据进入或静默展示

### Requirement: 第二阶段补齐用户明确提出但未完整闭环的功能

系统 SHALL 补齐老师工资统计、教室后补闭环、结构化冲突详情、班课运营边界等用户高频需求。

#### Scenario: 用户查看老师工资

- **WHEN** 管理员按老师和日期范围查看消课统计
- **THEN** 系统展示已终确认的课时、学生/课程维度费率、应付金额和已结算状态
- **AND** 未终确认、已驳回、请假不计薪等记录不会进入应付工资

#### Scenario: 用户处理教室后补

- **WHEN** 班课或课次缺教室
- **THEN** 系统提供待补教室列表
- **AND** 保存前校验教室状态和时间冲突
- **AND** 保存后可在 Room Calendar 复查占用结果

### Requirement: 第三阶段只在产品规则确认后实现复杂业务

系统 SHALL 把部分退款、credit、冲刺班完整矩阵、跨月补课、停课续费、Excel 导出等需求放入产品确认队列，未确认前不得用隐式规则实现。

#### Scenario: 用户要求部分退款

- **WHEN** 用户需要只退某个课程、只退部分金额或已消课后退费
- **THEN** 系统应要求先明确退款范围、课时处理、老师工资影响、收据修正和 Reports 口径
- **AND** 未确认规则前只保留整单退款或人工备注，不自动修改课包余额或教师工资

### Requirement: 每次迭代必须保持交互低学习成本

系统 SHALL 每次改动都从用户视角验证入口、下一步动作和异常提示。

#### Scenario: 页面新增或重构

- **WHEN** 新增或修改任意页面
- **THEN** 页面必须回答用户的三个问题：我现在在哪里、我下一步该做什么、出错后如何修正
- **AND** 不应暴露不必要的内部 ID、技术字段或重复入口
- **AND** 表单应优先使用结构化选择、自动带出、明确提示，而不是自由文本和猜测

## MODIFIED Requirements

### Requirement: 原新需求路线图进入维护阶段

`plan-new-requirements-refactor-roadmap` SHALL be treated as the completed first delivery roadmap. 后续需求 SHALL 由本长期路线图按阶段拆分实施，并在具体功能开始前创建更小的专项规格。

### Requirement: 数据完整性规格作为验收基线

`define-operational-data-integrity-model` SHALL be used as the data correctness baseline. 后续任何报名、付款、消课、报表、退款相关改动都必须对照其主数据、快照数据、派生数据边界。

### Requirement: 课程矩阵规格作为课程配置基线

`audit-course-structure-fit` SHALL remain the baseline for course setup. 课程产品是父级课程，价格行是年级/时长/时段/价格矩阵，报名消费价格行，执行侧生成 1 对 1 课包或班课资格。

## REMOVED Requirements

### Requirement: 一次性大重构路线

**Reason**: 第一轮路线图已完成，继续用大范围路线推进会导致任务过大、验收不清晰。

**Migration**: 后续按本长期路线图拆成小规格、小任务、小验证，每个阶段只解决一组明确用户问题。
