# 常见业务生命周期完整度审计 Spec

## Why

教培系统的真实运营不会只有一次报名和固定 2 小时上课：家长可能分多次、不同方式付款；实际课程可能比计划更长或更短；学生可能请假、缺勤、退课或要求退款；课程可能已经消耗部分课时。系统需要审计这些高频场景当前是否有正确的数据处理和足够简单的用户交互，防止金额、余额、课时、排课和报表出现不一致。

## What Changes

- 审计报名、付款、实际课时、排课调整、退课退款、消课和报表的完整业务生命周期。
- 对每个场景标注 `Covered`、`Partial`、`Missing` 或 `Needs product decision`。
- 定义每种场景的权威数据、状态转换、禁止操作和用户下一步。
- 特别核查多课程订单分期付款、实际课时偏差、整单退款与部分退款、credit、已消课课程退课。
- 不在本次审计中直接新增退款、credit 或付款分摊数据结构；先形成可实施的规则和缺口清单。
- **BREAKING**：无。

## Impact

- Affected specs: Registration intake、Student registration history、Payment records、Refunds、Course packages、Class enrollment、Lesson confirmation、Schedule adjustment、Reports。
- Affected code:
  - `backend/src/main/java/com/classdeduction/system/modules/enrollment/service/impl/RegistrationIntakeServiceImpl.java`
  - `backend/src/main/java/com/classdeduction/system/modules/enrollment/service/impl/PaymentRecordServiceImpl.java`
  - `backend/src/main/java/com/classdeduction/system/modules/enrollment/service/impl/EnrollmentOrderServiceImpl.java`
  - `backend/src/main/java/com/classdeduction/system/modules/lesson/service/impl/LessonRecordServiceImpl.java`
  - `frontend/src/views/registration/RegistrationIntakeView.vue`
  - `frontend/src/views/base-data/StudentsView.vue`
  - `frontend/src/views/enrollment/EnrollmentOrdersView.vue`
  - `frontend/src/views/one-on-one/OneOnOneDeductionView.vue`
  - `frontend/src/views/schedule/ScheduleAdjustmentsView.vue`
  - `frontend/src/views/data-summary/DataSummaryView.vue`

## Current Coverage Summary

| 场景 | 当前状态 | 当前处理 | 审计结论 |
| --- | --- | --- | --- |
| 报名时多笔付款 | Covered | 同一订单可创建多条 payment record，实时计算 Paid / Balance。 | 满足基础分期付款。 |
| 报名后补款 | Partial | 可追加多笔付款并限制不超过订单余额。 | 多课包订单会把整笔补款加到每个 package received，口径不安全。 |
| 实际课时短于/长于计划 | Covered with boundary | 终确认时以 actual units 结算，差额反向调整未来 scheduled occurrences。 | 有未来课次时可保持计划总量；没有未来课次时会报错，需要用户可见的解决路径。 |
| 请假 / 缺勤 | Covered | Formal Leave 不扣课并顺延； No Show 按规则扣计划课时。 | 已有规则和确认流程，需继续保证状态解释清楚。 |
| 整单退款 | Covered | 写负数退款流水、取消订单、关闭课包或释放班课报名、订单已收归零。 | 支持基础整单退款。 |
| 部分退款 / 已消课退课 | Missing | 当前没有按订单明细、按课包或按剩余课时退款的规则。 | 不能以整单退款替代，必须先产品确认。 |
| 学费 credit / 预存款 | Missing | 当前超额付款被阻止。 | 页面政策提到 credit，但系统没有 credit 账户或抵扣流水。 |
| 退款政策与系统行为一致性 | Partial | 页面/收据表述开课前 50% refund、余款 credit；后端只实现整单已收归零退款。 | 当前文案与可执行规则不一致，属于高风险。 |
| 课程取消 / 排课变更 | Covered | 当前周可新增、修改、取消课次；写调整日志并校验冲突。 | 适用于课次调整，不等同于订单级退课退款。 |
| 报表金额、余额和课时解释 | Partial | Reports 从订单、明细、课包和班课报名派生。 | 缺付款流水、订单余额、课包余额、课时台账的一致性审计。 |

## Task 3 Audit Notes

### Full refund verification

- `EnrollmentOrderServiceImpl.refundOrder` creates one negative `payment_record` for the current order `receivedAmount`, sets the order to `CANCELLED / REFUNDED`, and resets the order received amount to zero.
- All course packages belonging to the order are closed, their received amounts are reset to zero, future `SCHEDULED` and `ADJUSTED` occurrences are cancelled, and recurring schedules are deactivated.
- All active class enrollments tied to the order's class or order items are withdrawn so the student loses future class eligibility.
- Reports exclude `CANCELLED` orders. A completed full refund therefore no longer contributes received amount or outstanding balance to the Registration report. The negative payment record remains the financial audit trail.
- Full refund is now blocked when the student has recorded lessons. A withdrawal after lessons are recorded is not a full refund and requires a separately defined manual process.

### Explicitly unsupported paths

- Partial refunds, refunds for a selected course/order item, withdrawals after lessons are recorded, and tuition credit/prepaid balances are `Missing`.
- The refund API accepts no amount or course selection. It refunds only the current full received amount and cannot be used to emulate any of the unsupported paths.
- Registration, receipt, and refund-dialog copy must describe only this Full refund behavior. Staff-review guidance is allowed, but 50% refund, automatic credit, or suspension-fee formulas must not be presented as system behavior.

### Product decisions required before implementation

1. Define whether eligibility is per order item, course package, class enrollment, or a combination when a multi-course order has a partial refund.
2. Define the refundable amount after confirmed, no-show, leave, makeup, or pending lessons, including rounding, discounts, fees, and installment-payment allocation.
3. Define whether teachers' earned or pending pay is reversed, retained, or adjusted for each refund and withdrawal state.
4. Define receipt correction, refund-document numbering, tax treatment, and whether original receipts remain printable with a linked refund record.
5. Define Reports treatment for gross receipts, refunds, net revenue, outstanding balance, and historical enrollment counts.
6. Define a credit ledger: owner, expiration, transferability, issuance approval, redemption order, refund conversion, and audit history.

## ADDED Requirements

### Requirement: 分期付款必须按订单真实流水结算

The system SHALL treat every customer payment as an immutable payment record and derive the order payment status from the order-level balance.

#### Scenario: 家长分多次付款

- **WHEN** 员工为未结清订单录入一笔或多笔付款
- **THEN** 系统保存每一笔 payment record 的方式、金额、日期、经办人和备注
- **AND** 订单级 Paid、Balance、payment status 同步更新
- **AND** 学生历史、订单详情、历史报名表和 Reports 展示同一组付款流水

#### Scenario: 多课程订单补款

- **WHEN** 一个订单包含多个 1-on-1 课包或班课明细且发生后续补款
- **THEN** 系统必须使用明确的付款归属规则
- **AND** 不得把同一笔补款完整重复写入每个课包的 received amount
- **AND** 用户界面应明确显示付款是订单级、按明细分摊，还是按课包指定

### Requirement: 实际课时必须与计划和余额保持可解释关系

The system SHALL record planned hours, actual hours, deducted hours, and future schedule impact separately.

#### Scenario: 实际课程短于计划

- **WHEN** 老师或管理员确认的实际课时小于计划课时
- **THEN** 系统按实际扣课规则更新课包和台账
- **AND** 系统解释剩余课时如何体现在未来课次或预计结束日期中
- **AND** 用户可以查看计划、实际、扣课和后续排课变化

#### Scenario: 实际课程长于计划

- **WHEN** 实际课时大于计划课时
- **THEN** 系统在管理员终确认时检查课包可扣余额和未来课次是否足以保持计划平衡
- **AND** 如果未来没有可调整课次，系统不得静默失败
- **AND** 前端应提示用户选择：减少未来课时、取消未来课次、增加补课/续费，或拒绝本次实际课时变更

### Requirement: 退课和退款必须按业务状态分流

The system SHALL distinguish cancellation, full refund, partial refund, and tuition credit instead of treating them as one operation.

#### Scenario: 整单全额退款

- **WHEN** 订单未被退款且存在已收金额，员工选择 Full refund
- **THEN** 系统创建负数退款流水
- **AND** 取消未发生的排课、关闭关联课包或释放班课报名
- **AND** 订单、付款、课包、班课和 Reports 使用一致的 `CANCELLED / REFUNDED` 口径

#### Scenario: 部分退款或已消课退课

- **WHEN** 员工需要只退部分金额、只退某个课程明细，或学生已消课后退课
- **THEN** 系统不得把该操作伪装为 Full refund
- **AND** 系统应提示当前规则尚未定义，阻止错误提交
- **AND** 后续实现必须先明确退款范围、剩余课时处理、老师工资处理、收据修正和 Reports 口径

#### Scenario: 页面退款政策

- **WHEN** Registration 或 Receipt 展示退款政策
- **THEN** 文案必须与系统当前可执行规则一致
- **AND** 不得展示系统尚未支持的 50% refund 或 credit 规则为已生效功能

### Requirement: 订单、课包、排课和消课状态必须可追溯

The system SHALL show a concise lifecycle timeline for operationally important state transitions.

#### Scenario: 用户查看学生或订单

- **WHEN** 用户打开学生历史或订单详情
- **THEN** 系统展示报名、付款、排课、确认、取消、退款等时间线
- **AND** 每个状态显示操作人、时间、原因和对余额/课时的影响

#### Scenario: 用户尝试不可逆操作

- **WHEN** 用户发起退款、取消未来排课、管理员终确认或实际课时变更
- **THEN** 系统展示将改变哪些金额、课时、课次和工资资格
- **AND** 用户必须明确确认后才提交

### Requirement: 常见业务异常必须有简单下一步

The system SHALL provide user-facing remediation instead of only returning a generic error.

#### Scenario: 付款异常

- **WHEN** 付款超过订单余额、订单已退款或订单已取消
- **THEN** 系统说明原因
- **AND** 提供查看付款时间线、查看退款记录或返回学生历史的入口

#### Scenario: 课时或排课异常

- **WHEN** 实际课时无法平衡未来排课、课包余额不足、课次已确认或排课冲突
- **THEN** 系统说明冲突对象和影响
- **AND** 提供调整未来课次、续费、查看课包或取消本次修改的入口

#### Scenario: 报表异常

- **WHEN** 订单金额、付款流水、课包余额或课时台账存在不一致
- **THEN** Reports 标记该记录为 `Needs review`
- **AND** 用户可进入订单、付款、课包或台账详情定位差异

## MODIFIED Requirements

### Requirement: 退款不是单一订单操作

The system SHALL keep the existing full refund flow, but treat partial refund and tuition credit as separate, explicitly defined capabilities.

#### Scenario: 当前系统不支持部分退款

- **WHEN** 用户打开退款操作
- **THEN** 系统仅允许当前已定义的 Full refund
- **AND** 页面明确说明该操作会取消报名并退款当前已收金额

### Requirement: 实际课时可以偏离计划但不能破坏余额

The system SHALL allow actual lesson duration to differ from scheduled duration only when the resulting package balance and future schedule can be reconciled.

#### Scenario: 管理员复核实际课时

- **WHEN** 管理员修改实际课时
- **THEN** 系统预览扣课和未来课次影响
- **AND** 无法平衡时阻止确认并给出解决方案

## REMOVED Requirements

### Requirement: 将政策文案视为已实现功能

**Reason**: 退款政策中的 50% refund 和 tuition credit 尚未形成可执行、可审计的数据规则。
**Migration**: 在规则实现前，收据和报名页只展示当前可执行的 Full refund 说明，或将未实现政策明确标记为需人工处理。
