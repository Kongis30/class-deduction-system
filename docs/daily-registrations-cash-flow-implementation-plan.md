# Daily Registrations：收支流水业务规则与实施计划

> 状态：待实施，2026-10-25。页面标题、菜单名称和 `/daily-registrations` 路由保持不变。业务需求见 [每日资金流水需求](daily-cash-flow-requirements.md)。

## 1. 这一页承担什么职责

本页按**纽约业务日期**回答两类不同问题：

1. **当天登记了什么报名？**沿用目前的报名历史：按 `enrollment_order.created_at` 查课程行，查看订单、学生、录入人和当前订单金额状态。
2. **当天发生了什么收支？**新增逐笔资金流水：按每笔付款、退款或已确认工资的实际发生时间查记录，查看金额方向、经手人、关联对象、原始凭证及更正／撤销情况。

页面内用“收支流水”和“报名记录”两个标签区分，默认展示收支流水；页面和导航仍叫 **Daily Registrations**。日期选择器共用，筛选、统计、Excel 和空状态按当前标签独立工作。原报名历史继续存在，不能用它的 `Collected` 充当当天收入。

## 2. 当前真实业务链路

| 操作 | 目前写入 | 需要在本页看到 |
| --- | --- | --- |
| 新报名时收款 | `RegistrationIntakeServiceImpl.createPayments` 写 `payment_record`；一笔拆分付款写一行，`payment_type=PAYMENT`。旧建单入口 `EnrollmentOrderServiceImpl.createPaymentRecord` 也写该表。 | 每条有效付款按自身 `paid_at` 在对应日期显示一次。没有付款的报名只在“报名记录”显示。 |
| Edit Receipt 中补缴／续费收款 | `EnrollmentOrderServiceImpl.correctOrderInternal → reconcileCorrectionPaymentRows → createCorrectionPayment` 在**原订单**新增付款行；同一事务重算订单金额、同步原收据，并写 `EDIT_RECEIPT` 审计快照。 | 补款 $1,600 的当天出现一条 +$1,600，关联原订单／收据；原报名日不会多一笔钱。 |
| Edit Receipt 更正原付款 | 同一更正流程按 `paymentId` 改原行的金额、方式、日期、经手人等；`sys_operation_log` 存订单及付款前后快照。 | 原交易在更正后的有效日期和金额展示；更正操作本身显示审计信息，但不再算一次收款。 |
| 订单付款入口补缴 | `PaymentRecordServiceImpl.createPaymentRecord` 插入一条或多条付款行，并更新订单及收据。 | 每笔拆分单独显示，按 `paid_at` 入账。 |
| 退款 | `EnrollmentOrderServiceImpl.refundOrder` 写 `payment_type=REFUND`、负金额、退款时间，并生成独立退款收据。 | 原路退钱为当天出账；转 Student Credit 则显示内部转账，不计对外出账。 |
| 删除退款收据 | `deleteRefundReceipt` 当前将退款行设 `VOIDED` 并软删，可能按快照恢复订单，也可能走旧记录回退路径。 | 可见“退款已撤销”的操作记录；有效出账排除该退款，旧值及撤销人、时间、原因可查。 |
| Credit 充值与删充值收据 | 充值有真实方式的付款行和 Credit 台账；`deleteCreditTopUpReceipt` 在余额可全额冲回时可能连同付款软删，部分已使用时则保留付款、隐藏订单／收据。 | 充值只算一次真实入账；即使订单被隐藏，仍能查付款。撤销充值必须区分“录错”和“钱已退回”。 |
| 删订单／课程、强制作废 | 普通删除要求订单／课程净收款归零，但级联仍可能软删或改写历史付款；`forceVoidOrder` 保留付款并隐藏订单。 | 已发生的收付款不能随着订单／课程隐藏而从历史消失；真正退款以独立负向流水体现。 |
| 教师工资 | `TeacherPaymentServiceImpl` 创建 `PENDING`，确认时设 `PAID` 和 `paid_at`。 | 只在确认付款日显示工资出账，待支付记录不计。 |

## 3. 单一计数规则

### 3.1 基本单位和分类

- **一行 = 一个来源交易 ID**。课程分摊 `payment_allocation` 仅用于说明该笔交易涉及哪些课程，不能拆成多笔完整金额。`receipt_record` 是凭证，不是另一笔入账；订单 `received_amount` 是累计快照，不是交易。
- `payment_record` 的 `PAYMENT/PAID`、金额大于零且方式不是 `STUDENT_CREDIT`：对外**入账**；`REFUND/REFUNDED`、金额小于零且方式不是 `STUDENT_CREDIT`：对外**出账**，页面出账列显示绝对值。其他状态先列为待核对，不能凭金额正负强行计入。
- `STUDENT_CREDIT` 付款与退款转 Credit 是**内部余额流转**：可在明细查到，但入账、出账、净流入均不加。真实渠道给 Credit 充值只看对应付款行一次，不能把 Credit 台账再加一次。
- `teacher_payment.status=PAID` 且有 `paid_at`：出账。工资记录目前没有支付方式字段，页面显示“未记录”，不能猜成现金。
- `VOIDED`、软删且确认属于**误录／撤销**的交易不计当前有效合计；只改价、改课、发收据、创建待支付工资单没有收支。若钱实际退回，必须新增退款，不能通过作废原付款表现出账。
- `refund disposition=CREDIT`、Credit 充值冲回、手工余额调整需显示为**内部事件**或审计记录。不同事件的金额不能被同一笔交易同时加到入账与出账。

### 3.2 日期、经手人和有效视图

- 报名标签继续按订单 `created_at`；流水标签按付款／退款 `paid_at`、工资 `paid_at`。数据中这些时间是纽约本地时间；查询使用纽约 `[当天 00:00, 次日 00:00)`，前后端标明 ET。`created_at` 和审计 `operate_at` 用来说明补录／更正发生在何时，不能替代真实付款日。
- 经手人是该笔付款／退款的 `handled_by_user_id`，工资是 `paid_by`。不等同于原报名的 `registration_operator_user_id`。旧记录缺人时显示“未记录”，仍计入总额。
- 默认展示**当前有效事实**，同时在行详情展示该交易的补录、更正、撤销历史。更正使过去某日有效合计变化时，详情必须能看到旧日期／旧金额、新日期／新金额、操作人、操作时间与原因。另提供“含已撤销”筛选供核对；已撤销行不计有效合计。
- 日报以单日完整明细为单位读取；页面列筛选后的表格、顶部合计和 Excel 必须使用同一批筛选结果，不得出现表格只剩三笔、页头仍统计全部交易的情况。
- 原订单、学生或收据即使软删，交易仍可按稳定 ID 查到；关联内容缺失时显示订单号或“历史对象已隐藏”，不丢弃这笔交易。多人订单使用 `participant_index`；历史缺失时标明“订单级／待核对”。

### 3.3 对账约束

收支流水的入账、出账和净流入必须是当前筛选下有效明细的逐笔求和：`净流入 = 入账 - 出账`。同一天按支付方式、经手人、类型分组后再次求和，结果应相同。报名标签的应收、累计已收、欠款独立显示；它们是订单状态，不与日资金合计强行相等。

## 4. 关键业务修正

1. **Edit Receipt 的两种操作要区分。**“Add payment”是新收款，插入新的付款 ID；编辑已有付款仅用于纠正原记录的录入错误。若实际又收到钱，必须新增付款，不得把原付款金额直接调大。原 `EDIT_RECEIPT` 订单级审计继续保留，新增按付款 ID 可检索的变更摘要：旧／新金额、方式、日期、经手人、操作人、原因，与更正事务一起提交。仅备注等非财务字段改变不生成“现金收支”。
2. **退款和撤销是不同事件。**实际退款写新的负向 `payment_record`，其退款收据链接该记录。删除退款收据只能撤销**误录或未真正付款**的退款，应保留原退款 ID、值和撤销日志；若钱已经退给家长，不能靠删记录恢复，应另走真实收款流程。旧退款撤销的快照回滚／兼容路径都要写相同审计事实。
3. **Credit 充值删除必须先确定钱的去向。**当前“Credit 余额全额可冲回”并不证明付款也已退回。删除时若只是误录，记为作废并留痕；若真实收过款后又退钱，保留原充值入账并新增退款出账；若 Credit 已被使用而只隐藏收据，原入账仍显示。不能直接软删真实付款来制造金额归零。
4. **订单／课程删除保留资金事实。**删除课程后，已付又已退的两笔流水都保留，原课程关联可显示“已删除”；支付分摊可调整显示归属，但不能为删除课程改写原 `payment_record.amount` 或 `payment_no`。整单删除、强制作废也不能清掉实际收支。任何确属误录的付款撤销须在同事务留审计原因。
5. **失败或重试不能产生幽灵交易。**新付款、退款、Credit 台账、订单金额、收据和审计在同一数据库事务内提交；失败全部回滚。对重复提交沿用稳定交易号／幂等约束，页面以来源类型加来源 ID 去重；不能靠前端按钮禁用保证唯一性。

## 5. 代码修改范围

| 范围 | 具体修改 |
| --- | --- |
| 数据读取 | 新增独立 `DailyCashFlowService`／响应 VO 和查询 mapper；从 `payment_record`、`teacher_payment` 读取交易，批量补齐学生、订单、课程分摊、退款凭证和教师信息。查询需要能关联软删订单／付款，以展示撤销历史；不能通过 Data Summary 的课程行反推。保留 `DailyRegistrationServiceImpl` 现有报名查询。 |
| 接口 | 在 `DailyRegistrationController` 下新增 `GET /v1/daily-registrations/cash-flow`，沿用 `ADMIN`／`STAFF` 权限；现有 `GET /v1/daily-registrations` 行为不变。新接口返回汇总、经手人小计、交易行与可核对的操作状态。 |
| 支付写入 | 核查并统一 `RegistrationIntakeServiceImpl.createPayments`、旧建单入口、`PaymentRecordServiceImpl.createPaymentRecord`、`EnrollmentOrderServiceImpl.createCorrectionPayment`：付款身份、真实方式、金额、`paid_at`、经手人完整，交易后能立即被新查询看到。 |
| 更正／退款／撤销 | 修改 `EnrollmentOrderServiceImpl.reconcileCorrectionPaymentRows`、`refundOrder`、`deleteRefundReceipt`、`deleteCreditTopUpReceipt`、`deleteOrderItemPayments`、`deleteOrderFinancialArtifacts`、`forceVoidOrder` 的资金保留与审计行为；在现有 `sys_operation_log` 中用明确操作码记录每笔变更，不重建一份重复的付款金额账本。 |
| Credit 和工资 | 使用 `StudentCreditServiceImpl` 的 Credit 台账校验内部转账归类；工资只读取 `TeacherPaymentServiceImpl.confirmPayment` 后的 `PAID/paid_at`。这些服务原则上只做必要的字段或审计补齐，不改其余额／工资计算。 |
| 数据库 | 新增以 `payment_record.paid_at`、`teacher_payment.paid_at` 和 `sys_operation_log.operate_at` 为首列的查询索引；现有 `sys_operation_log` 存更正前后值。若发现旧软删付款缺可检索的稳定业务号，应新增只读历史读取 SQL，不恢复错误交易。迁移需幂等。 |
| 前端 | `DailyRegistrationsView.vue` 增加“收支流水／报名记录”标签、当日入／出／净额汇总、可识别的内部流转及撤销状态、逐笔表格与详情入口；`daily-registrations.ts` 增加新接口类型和调用。保持路由、菜单、页面标题原样，两个标签各自导出 Excel。 |
| 验收 | 扩展后端日报查询／付款更正／退款撤销用例，增加前端页面场景。只围绕资金事件及日报口径建立必要验证；不改无关排课和课程计算。 |

## 6. 分步骤实施与每步完成条件

### 第一步：锁定来源和异常数据

盘点所有创建、更新、软删 `payment_record` 的入口及 `teacher_payment` 确认入口；读取历史样本核对 `paid_at`、退款方式、Credit 标记、多学生归属、软删订单与收据。列出无法仅凭旧数据认定是否实际收／退的记录，标注待人工核对；不自动修补金额。**完成条件：**每个写入口都有明确的入账、出账、内部、纠错或作废分类。

### 第二步：先完成独立日报读取

加查询索引、后端归一化模型与 `/cash-flow` 接口，按来源 ID 去重，先读取有效付款、退款、已支付工资；另查询 Credit 归类和必要的历史／已撤销状态。任何关联学生或订单失败的交易仍返回，附“待核对”标签。**完成条件：**对指定日期，返回逐笔明细和一致的入／出／净额，旧报名日的补款不会误归到原日。

### 第三步：补齐所有资金写入及撤销审计

沿写入口逐个核对事务、支付时间、经手人；先处理 Edit Receipt 新增／更正，再处理退款／撤销，再处理 Credit 充值收据及订单／课程删除。沿用订单更正日志，在 `sys_operation_log` 增加逐笔变更索引所需的操作码和前后快照。重写会抹掉真实现金历史的删除分支，明确“误录撤销”和“实际退款”两种结果。**完成条件：**每个操作提交后，新日报有且只有该有的一条交易或一条不计合计的变更记录；事务失败无残留。

### 第四步：调整页面与导出

保留 Daily Registrations 标题和日期工具栏；收支流水默认可见、报名记录可切换。流水行显示时间、方向、学生／教师、用途／课程、方式、金额、经手人和凭证；更正及撤销可展开核查。页面汇总、经手人小计和 Excel 都使用后端统一金额口径。**完成条件：**运营人员不查看 Edit Receipt 也能在收款／退款发生日找到对应交易，并能打开原凭证。

### 第五步：按业务场景验收并核对历史

逐一验证：报名立即付款、报名零付款、旧订单次日 Edit Receipt 补 $1,600、拆分支付、已有付款纠正金额／日期、同日或次日退 $1,000、退款转 Credit、Credit 充值和删除两条分支、删课程／整单、强制作废、多人订单、教师工资待付／已付、跨纽约夏令时日期。逐日对照来源交易 ID 与页面／Excel 汇总，关注已软删订单；无法确定的旧数据输出待核对清单。**完成条件：**所有可确认的来源交易有唯一对应明细，合计可重算；历史不确定项明示来源与原因。

## 7. 交付边界

本计划只把**系统已记录或通过本系统实际执行**的收支纳入 Daily Registrations。房租、采购、银行手续费等当前没有录入来源的事项，需另做通用收支录入功能才能纳入；本次不能以“金额差额”生成虚构流水。现有未提交的排课等改动不属于本计划修改范围。
