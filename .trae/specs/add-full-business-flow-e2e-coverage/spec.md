# 业务全链路 E2E 覆盖 Spec

## Why
当前已有 enrollment 与 course product 的主干 E2E 覆盖，但还缺少从创建课程、报名、排课、消课双确认到老师付款的跨页面完整业务链路。系统需要从用户长期使用和系统职责一致性的角度验证更多业务组合，降低复杂流程在联动场景下回归的风险。

## What Changes
- 增加跨页面 E2E 覆盖：Course Product → Enrollment → Course Packages/Schedule → Confirm → Payments。
- 补充不同课程产品类型、不同排课时间、课程取消/请假/正常消课、老师初确认/管理员终确认/驳回、老师付款等业务分支。
- 对关键流程做 UI 与 API/DB 数据一致性校验，确保课时、余额、订单、消课记录、工资待支付记录状态正确。
- 保留现有单页面 E2E 测试，并新增面向业务链路的测试文件与辅助方法。
- 不引入 **BREAKING** 变更。

## Impact
- Affected specs: course product, enrollment orders, course packages, schedule adjustment, lesson confirm, teacher payments。
- Affected code: `frontend/e2e/ui/base-data/`, `frontend/e2e/ui/enrollment/`, `frontend/e2e/ui/schedule/` 或新增跨模块 E2E 目录；共享 helper 包括 API、DB、Element Plus、session helpers。

## ADDED Requirements
### Requirement: 跨页面完整业务链路 E2E
The system SHALL provide E2E tests that create a course product, enroll students, generate schedules/packages, confirm lessons, and pay teachers through the UI where business-critical, with API/DB validation for each state transition.

#### Scenario: 1对1完整链路成功
- **WHEN** 管理员创建 1对1课程产品，给学生报名，选择老师和排课时段
- **THEN** 系统应生成订单、课包、课次计划，并在 Course Packages 与 Schedule/Confirm 页面可追踪
- **AND** E2E 应校验订单金额、课包课时、课次计划、消课记录与老师待支付记录一致

#### Scenario: 班课完整链路成功
- **WHEN** 管理员创建班课产品，配置教室/老师/固定时段，并完成学生班课报名
- **THEN** 系统应生成班级、班课进度、学生报名进度与对应课次
- **AND** E2E 应校验班课聚合进度、学生剩余课次、订单金额和消课状态一致

### Requirement: 消课双确认 E2E
The system SHALL provide E2E tests for lesson confirmation states, including teacher initial confirmation, admin final confirmation, admin direct confirmation, and rejection.

#### Scenario: 老师初确认后待管理员确认
- **WHEN** 老师在 Confirm 页面对本人课次提交初确认
- **THEN** 记录应进入 `PENDING_ADMIN`
- **AND** 课包余额、课时台账、老师工资待支付记录不应发生结算

#### Scenario: 管理员终确认后完成扣课与计薪
- **WHEN** 管理员对 `PENDING_ADMIN` 课次执行终确认
- **THEN** 记录应进入 `CONFIRMED`
- **AND** 系统应扣减课包余额、写入课时台账，并使该课次进入老师待支付范围

#### Scenario: 管理员驳回老师初确认
- **WHEN** 管理员驳回 `PENDING_ADMIN` 课次并填写原因
- **THEN** 记录应进入 `REJECTED` 或课次退回可重新确认状态
- **AND** 课包余额和老师待支付记录不应发生结算

### Requirement: 课程异常结果 E2E
The system SHALL provide E2E tests for normal completed lessons, student leave/cancel/no-show style outcomes, and teacher/system cancellation outcomes where supported by the current UI.

#### Scenario: 正常消课
- **WHEN** 用户对课次选择正常完成并确认
- **THEN** 系统应按规则扣课时并影响老师付款

#### Scenario: 请假或取消
- **WHEN** 用户对课次选择请假或取消类结果
- **THEN** 系统应按业务规则决定是否扣课、是否顺延、是否进入老师付款，并在 UI/API 中保持一致

### Requirement: 老师付款 E2E
The system SHALL provide E2E tests that verify only eligible confirmed lesson records appear in teacher payment workflows and can be paid.

#### Scenario: 已终确认课次进入老师付款
- **WHEN** 老师课次被管理员终确认
- **THEN** Payments 页面应可查询到对应待支付课次
- **AND** 完成付款后，该课次不应再次出现在待支付列表中，付款记录金额与课次一致

#### Scenario: 待管理员确认或已驳回课次不可付款
- **WHEN** 课次处于 `PENDING_ADMIN` 或 `REJECTED`
- **THEN** Payments 页面不应将该课次纳入可支付范围

### Requirement: 边界与长期使用场景 E2E
The system SHALL provide E2E tests for common long-running operational edge cases across product, enrollment, schedule, confirm, and payments.

#### Scenario: 不同课程产品与不同排课时间
- **WHEN** 用户创建不同课程类型、不同计费方式、不同老师/教室/时段的产品并报名
- **THEN** 系统应正确生成对应课包、班级、课次和冲突校验结果

#### Scenario: 冲突与非法操作
- **WHEN** 用户尝试创建冲突排课、关闭课包后继续操作、重复确认、重复付款或使用无效金额
- **THEN** 系统应给出明确错误提示，并保持后端数据不被错误修改

### Requirement: 驳回后重新确认闭环 E2E
The system SHALL provide E2E tests that cover the complete recovery path after an admin rejects a teacher initial lesson confirmation.

#### Scenario: 驳回后重新处理成功
- **WHEN** 老师初确认课次后，管理员驳回并填写原因
- **THEN** 系统应保持不扣课、不计薪，并让该课次回到可重新处理状态
- **AND** 老师应能重新初确认，管理员应能再次终确认，最终完成扣课、台账和计薪资格更新

### Requirement: 调课、补课与顺延业务链路 E2E
The system SHALL provide E2E tests for weekly temporary adjustments, makeup lessons, and package extension behavior.

#### Scenario: 本周临时调课
- **WHEN** 用户对本周未核销课次调整时间、老师、教室或课时
- **THEN** 系统应只更新本周课次，不修改固定排课规则，并写入调整日志
- **AND** 系统应正确提示多上、少上或课时不变对后续课包的影响

#### Scenario: 补课与顺延
- **WHEN** 用户对请假、取消或少上课时的课次创建补课或触发顺延
- **THEN** 系统应生成可追踪的补课课次或更新预计结束时间，并保持课包余额、课次状态和调整日志一致

### Requirement: 教师工资扩展场景 E2E
The system SHALL provide E2E tests for teacher pay rates, zero-rate handling, cross-period payments, and payment history details.

#### Scenario: Pay Rate 影响待支付金额
- **WHEN** 用户为老师和课程产品设置或修改 Pay Rate
- **THEN** 后续教师付款应按当前适用费率计算金额，历史付款不应被反向修改

#### Scenario: 零费率和跨周期付款
- **WHEN** 待支付课次缺少有效费率或跨多个周期查询
- **THEN** 系统应显示零费率警告或正确按周期筛选课次，并允许用户按业务规则继续或阻止付款

#### Scenario: 付款历史详情
- **WHEN** 用户创建并确认教师付款
- **THEN** Payment History 应展示 Pending/Paid 状态、详情明细、付款金额和付款时间，并确保已付款课次不再重复进入待支付

## MODIFIED Requirements
### Requirement: Existing Enrollment E2E Coverage
现有 enrollment E2E SHALL 保持通过，并作为跨页面 E2E 的基础数据与 helper 来源。新增全链路 E2E 不应削弱已有单页面覆盖。

### Requirement: Existing Course Product E2E Coverage
现有 course product E2E SHALL 保持通过，并补充能被 enrollment、schedule、confirm、payments 后续链路直接消费的产品创建数据。

## REMOVED Requirements
无。
