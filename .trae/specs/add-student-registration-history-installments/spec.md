# 学生报名历史与分期付款体验 Spec

## Why
当前报名表已经能完成新报名录入，但员工缺少一个以学生为中心的日常入口：查看已报名学生、回看某次报名表、继续补记后续分期付款。系统需要把“报名表提交后的长期运营”补齐，让已报名学生不只停留在一次性收据或后台汇总中。

## What Changes
- 在学生管理中提供已报名学生列表与报名历史入口，支持按学生、家长电话、校区、付款状态筛选。
- 点击学生后展示学生档案、报名订单列表、课程明细、付款进度、余额和收据入口。
- 点击某次报名后，以现有 `/registration` 纸质表单样式回看该报名快照，默认只读，保留打印能力。
- 报名后允许对未结清订单继续追加多笔付款，每笔可选择不同支付方式、金额、日期和备注。
- 分期付款第一版以“实际付款流水 + 订单余额”为核心，不新增复杂账期计划表；后续如需到期提醒再扩展计划能力。
- **BREAKING**：无。现有报名保存、打印、收据、付款记录接口应保持兼容。

## Impact
- Affected specs: 学生管理、报名表回看、报名订单、付款记录、收据打印、后台数据整理。
- Affected code:
  - `frontend/src/views/base-data/StudentsView.vue`
  - `frontend/src/views/registration/RegistrationIntakeView.vue`
  - `frontend/src/views/enrollment/EnrollmentOrdersView.vue`
  - `frontend/src/views/enrollment/ReceiptRecordsView.vue`
  - `frontend/src/api/base-data.ts`
  - `frontend/src/api/enrollment.ts`
  - `backend/src/main/java/com/classdeduction/system/modules/base`
  - `backend/src/main/java/com/classdeduction/system/modules/enrollment`

## ADDED Requirements
### Requirement: 已报名学生运营列表
The system SHALL provide a student-centered list for users to manage enrolled students and inspect their enrollment status.

#### Scenario: 查看已报名学生
- **WHEN** 管理员或员工进入学生管理
- **THEN** 系统展示学生姓名、家长电话、校区、最近报名日期、报名次数、应收、已收、余额和付款状态
- **AND** 用户可以按姓名、家长电话、校区、付款状态和有效状态筛选
- **AND** 列表默认优先展示最近报名或仍有余额的学生

#### Scenario: 区分未报名学生
- **WHEN** 学生档案存在但没有任何报名订单
- **THEN** 系统仍可展示该学生
- **AND** 系统应明确标记为 `No enrollment`
- **AND** 不应把该学生误计入已报名学生统计

### Requirement: 学生详情与报名历史
The system SHALL provide a student detail view that connects profile, enrollment orders, course items, payments, receipts, and balances.

#### Scenario: 打开学生详情
- **WHEN** 用户点击学生列表中的某个学生
- **THEN** 系统展示该学生的基础资料
- **AND** 系统展示该学生所有报名订单，包含报名号、报名日期、课程明细摘要、应收、已收、余额、付款状态和订单状态
- **AND** 每个订单提供 `View form`、`Record payment`、`View receipt` 操作

#### Scenario: 查看某次报名详情
- **WHEN** 用户点击某个报名订单
- **THEN** 系统展示该订单的课程明细、付款流水、收据记录、课包或班课关联信息
- **AND** 所有金额字段必须来自订单与付款记录，不应由前端重新猜测计算

### Requirement: 报名表快照回看
The system SHALL allow users to view a historical registration order in the same visual layout as the registration intake form.

#### Scenario: 回看历史报名表
- **WHEN** 用户从学生详情或订单详情点击 `View form`
- **THEN** 系统打开与当前报名页一致的纸质表单布局
- **AND** 表单字段填充自 `enrollment_order` 快照、`enrollment_order_item`、`payment_record` 和 `receiptPayload`
- **AND** 页面默认只读，不显示 `Save`、`Add course row`、`Add payment`、`Fees / discounts` 等编辑操作
- **AND** 页面保留 `Print` 和 `Show EIN` 能力

#### Scenario: 历史一致性
- **WHEN** 学生档案、课程价格或支付方式后续被修改
- **THEN** 历史报名表仍展示该次报名提交时的快照内容
- **AND** 系统不得用当前学生档案或当前课程价格覆盖历史报名表

### Requirement: 报名后追加分期付款
The system SHALL allow users to add additional payment records to an existing enrollment order until the order balance is settled.

#### Scenario: 对欠费订单补记付款
- **WHEN** 用户在学生详情或订单详情点击 `Record payment`
- **THEN** 系统展示当前应收、已收、余额和已有付款流水
- **AND** 用户可以新增一笔或多笔付款，每笔包含支付方式、金额、付款日期、经办人和备注
- **AND** 不同付款可以使用不同支付方式
- **AND** 提交后系统创建付款记录并更新订单已收、余额和付款状态

#### Scenario: 防止超额付款
- **WHEN** 用户录入的新增付款总额超过订单当前余额
- **THEN** 系统明确阻止提交并展示错误
- **AND** 系统不得静默截断金额或自动改写为余额

#### Scenario: 结清订单
- **WHEN** 新增付款后订单余额为 0
- **THEN** 系统将付款状态更新为 `PAID`
- **AND** 学生列表和后台数据整理中的余额与付款状态同步更新

### Requirement: 分期付款用户体验
The system SHALL present installment payments as a clear payment timeline rather than a single opaque paid amount.

#### Scenario: 展示付款时间线
- **WHEN** 用户查看学生详情、订单详情或历史报名表
- **THEN** 系统展示每笔付款的日期、方式、金额、经办人和备注
- **AND** 系统展示订单级 `Amount due`、`Paid`、`Balance`
- **AND** 欠费订单应有明显但不阻断浏览的 `Partial` 或 `Balance due` 状态

#### Scenario: 首次报名时多笔收款
- **WHEN** 员工在报名表中一次录入多笔付款
- **THEN** 系统将每一笔保存为独立付款流水
- **AND** 报名成功结果、学生详情、订单详情和历史报名表展示一致的付款明细

## MODIFIED Requirements
### Requirement: 学生管理不再只是基础资料配置
The system SHALL keep student profile editing, but it must also serve as the operational entry for enrolled student lookup and historical registration review.

#### Scenario: 学生管理入口
- **WHEN** 管理员或员工打开学生管理
- **THEN** 系统不应只提供基础资料编辑
- **AND** 系统应能直接回答“这个学生报过哪些课、交了多少钱、还欠多少钱、原报名表是什么样”

### Requirement: 报名表组件支持创建与只读回看两种模式
The system SHALL reuse the registration form visual structure for both new intake and historical review while separating editable and read-only behavior.

#### Scenario: 创建模式
- **WHEN** 用户从报名入口打开表单
- **THEN** 系统允许编辑学生、课程、费用、付款并保存

#### Scenario: 回看模式
- **WHEN** 用户从历史报名记录打开表单
- **THEN** 系统只允许查看和打印
- **AND** 系统不得展示会修改历史数据的按钮

## REMOVED Requirements
### Requirement: 无
**Reason**: 本次变更只补齐学生运营和付款体验，不移除现有报名、收据或订单能力。
**Migration**: 无。
