# Edit Receipt：现有实现梳理与纠正计划

> 状态：2026-10-22 核心更正链路已按本计划调整；本文第 1、2 节保留修改前的审查结论，第 5 节记录当前完成情况。目标行为以 [原报名更正业务规范](./edit-receipt-business-rules.md) 为准。

## 1. 当前调用链

```text
收据/报名详情入口
  → RegistrationIntakeView.loadHistoricalOrder / applyHistoricalOrder
  → 从收据 JSON 优先还原课程行，订单明细作回退
  → startCorrection 加载当前产品、方案、班级日期，并重新计算价格和付款草稿
  → saveCorrection / buildCorrectionPayload 将整张报名表提交 PUT /orders/{id}/correction
  → EnrollmentOrderServiceImpl.correctOrder 单事务更新订单、学生、课程行、权益/课包、付款和分摊
  → syncReceiptSnapshots 改写该订单的收据 JSON
  → Data Summary、班级名单、日历等从各自业务表重新读取
```

| 层次 | 关键代码 | 当前职责 |
| --- | --- | --- |
| 页面 | `frontend/src/views/registration/RegistrationIntakeView.vue`：`loadHistoricalOrder`、`applyHistoricalOrder`、`startCorrection`、`saveCorrection`、`buildPayload` | 复用新报名表单展示和提交历史收据；更正态仍沿用新报名的选项和计价函数。 |
| 接口 | `frontend/src/api/enrollment.ts`；`EnrollmentOrderController.java`；`EnrollmentOrderCorrectionRequest.java` | 用一份大 DTO 接收完整表单，课程和付款项复用新报名 DTO；没有明确的更正字段清单、付款 ID 或预期订单版本。 |
| 保存编排 | `EnrollmentOrderServiceImpl.correctOrder` | 锁订单和部分关联记录，更新主档、订单快照、参与人、课程、权益、付款、金额、分摊、收据。事务保证单次数据库提交原子性。 |
| 课程与权益 | 同类中的 `applyCorrectedItems`、`validateCorrectedV2GroupSelection`、`migrateCorrectedClassEnrollment`、`updateCorrectedOneOnOnePackage` | 校验现行方案，覆盖原课程行，调整班课权益或 1 对 1 课包。 |
| 财务与凭证 | 同类中的 `reconcileCorrectedPayments`、`applyCorrectedMoney`、`syncReceiptSnapshots` | 按付款列表位置更新/作废付款，重算订单金额并重放分摊，覆盖收据快照。 |
| 报名创建基线 | `RegistrationIntakeServiceImpl` | 新报名把 `package_quantity` 写为**本次实际选购数量**，并据此计算权益和期限。 |
| 下游读取 | `DataSummaryServiceImpl`、班级/课次服务 | Data Summary 的 Period 优先取当前班课权益期限；班级和 1 对 1 排课另有独立关联与状态。 |

## 2. 已确认的结构问题

### 2.1 加载历史数据会产生未经用户确认的变化

`applyHistoricalOrder` 在有收据 `items` 时优先读取收据 JSON，再用订单行兜底。但订单明细响应 `EnrollmentOrderItemVO` 没有提供 `packageUnit`、`packageQuantity`、`purchasedMonths`、`attendanceOption` 等完整更正基线；旧收据缺字段时，页面无法可靠区分“原始购买事实”和“当前配置”。`startCorrection` 会对每个班课调用 `loadGroupClassDates`：历史开始日期不在当前可选日期内时，它会改成首个可选日期，并调用 `recalculateLinePrice`；初始化付款草稿也可能改写表单中的付款金额。仅打开编辑页就可能生成与原单不同的提交值。

### 2.2 方案数量、实购数量在两条路径中语义冲突

新报名的 `RegistrationIntakeServiceImpl` 用 `selectedQuantity` 写订单行的 `packageQuantity`；更正页面的 `recalculateLinePrice` 却把它赋值为当前方案的 `configuredQuantity`，另用 `groupPurchasedMonths` / `groupSessionCount` 保存选购数量。后端 `validateCorrectedV2GroupSelection` 又把旧订单行 `packageQuantity`、旧收据的选购数量和提交数量交叉比较，并在判断变化后对照**当前**方案上限校验。这会把“旧快照缺失”“当前方案后来变化”“表单重算”混成一次购买条款变更。

截图中的 `GROUP_V2_PLAN_MISMATCH` 确实从上述校验抛出；**仅凭截图不能确定** Wenhao Ma 这一次到底是 `packageTermsChanged` 还是 `selectedCountChanged` 被触发，需保留实际请求体和更正前快照后再定点确认。Attendance 本身不在这两个条件里，但选择 Attendance 会触发前端重算，并且保存发送整个课程行，因此可间接触发数量校验。

### 2.3 更正没有表达“实际改了什么”

`saveCorrection` 除学生姓名外没有针对更正范围做字段校验，`buildCorrectionPayload` 复用 `buildPayload` 并发送全部课程、付款和费用。后端只要收到 `items` 就覆盖课程并可能重算权益；收到 `payments` 就对账；`moneyChanged`、`priceChanged` 也按“字段存在”而不是实际金额变化判定。结果是补填 Attendance 或电话可能触发课程、价格、付款与分摊的连锁写入。旧方案停用或调价时，无关资料更正也可能受当前选项约束。

### 2.4 页面与后端对课程行增删的承诺不一致

更正态有移除课程行操作，但 `applyCorrectedItems` 只遍历提交的已有 `orderItemId`：省略的原行不会删除，没有 ID 的新增行则直接报错。页面的新增按钮并非按更正场景设计。课程更换、班级变更时，当前逻辑侧重订单行和班课权益；没有完整核对学生未来课次、接送名单和 1 对 1 未来排课。`migrateCorrectedClassEnrollment` 还包含“孤儿权益自愈”分支，在编辑旧收据时可能重建 ACTIVE 权益；这不能作为通用更正动作，尤其要避开停课和续报后的旧订单。

### 2.5 付款、凭证和并发缺少稳定身份

`reconcileCorrectedPayments` 按付款时间及 ID 排序，再按**数组位置**复用旧付款；前端提交项没有付款 ID。改变列表顺序或缺一行，可能改写错误付款。`syncReceiptSnapshots` 遍历该订单的所有收据并覆盖 JSON，未按“本次原报名收据”和退款等独立凭证分流，也没有保留更正前版本。订单实体有版本字段，但订单详情和更正请求没有传递预期版本；行锁只让写入串行，不能提示后一个管理员其页面已经过期。部分可清空字段还依赖 `null`，而普通 `updateById` 对 `null` 的处理不统一。

## 3. 建议的纠正顺序

### 阶段 A：先固定事实与字段契约（阻断后续误修的前置工作）

1. 为指定订单做**只读诊断**：记录订单行、原收据 JSON、方案当时可证明的条款、班课权益/课包、付款及退款、已确认和未来课次。对 Wenhao Ma 只读对比前端实际 PUT 请求、后端 `previous` 与 `payload` 中的数量、方案和日期；明确究竟哪个变化条件触发报错。不要依据方案名或价格自动改历史权益。
2. 定义更正读取模型：订单行 ID、付款 ID、订单版本、实际购买数量、当时价格及 Attendance、关联权益状态都要显式返回；收据 JSON 用于展示旧凭证和历史回退，缺失项标记“待核对”，不能隐式采用当前方案上限或当前权益累计数量。
3. 统一 `package_quantity` 为**本订单行实际购买数量**，与创建路径一致；方案上限始终从方案字段单独读取。调查已经由旧更正流程写入的历史记录后，才决定是否需要一次性补正或增加最小快照字段。前端移除“打开更正就用当前方案上限覆盖订单数量”的逻辑。

### 阶段 B：让更正只修改用户明确改变的字段

1. 在进入更正时保存不可变原始基线。加载当前选项只供选择，历史值不在候选项内也保留显示；不自动换开始日期、收窄选购数量、重算原价或重排付款。
2. 前后端按字段差异执行：资料、Attendance、课程/数量、金额、付款各自有明确校验和写入范围。建议保留一个简单的更正 DTO，并让每条课程/付款携带稳定 ID 与明确变更字段；服务层按差异调用小范围方法，避免复制一套庞大的报名流程。
3. **先修 Attendance-only 场景**：原为空、补选与原价相符的档位时，只更新订单行及仍归本订单的权益 Attendance，保留原方案、实购数量、期限、价格与付款。已有的数量/期限矛盾单独展示待核对，不通过 Attendance 保存暗中修复。收费档位变化则预览应收差额，并要求可靠的原价依据或明确报价。
4. 调整班课校验：只有用户确实更改方案、数量或班级时才按目标方案规则校验；只改其他字段时保留原购买条款。报错指出课程行及实际冲突字段。取消普通收据更正时自动“孤儿权益自愈”，数据修复走单独受控流程。

### 阶段 C：补齐权益、排课与财务联动

1. 班课变更时只迁移该学生未来参与关系，保留全班物理课表及已确认历史；检查旧班和新班的未完成学生课次、接送名单、续报权益与重叠日期。1 对 1 更正课包时核对已消耗和未来排课，不能只改课包数字。
2. 课程行增删先选定一致产品行为：优先在更正页暂时禁用无后端支持的增删，并说明原因；若后续开放，再一次实现订单行、权益、款项和课次的完整增删，不能只隐藏前端行。
3. 付款按 ID 更正并记录前后值、操作者、时间及原因；真实补款/退款继续产生新流水。只在价格或付款实际变化时重算金额和分摊，保持已有退款和 Credit 台账不受旧收据覆盖。
4. 更正成功后生成有版本/审计关联的新原报名凭证视图，保留先前版本；限定同步的收据类型，不覆盖退款凭证。详情页提交订单版本，过期编辑返回明确冲突提示。

### 阶段 D：验收、迁移与清理

- 先覆盖“只补 Attendance”“历史方案已变化”“两个月与一月权益不一致”“换班及已确认课次”“已有续报/停课”“1 对 1 已排课”“多笔付款重排”“两人同时编辑”等场景，再做端到端核对。每次保存后对比订单行、权益期限、Data Summary Period、收据、付款分摊和未来名单。
- 历史异常只做清单与逐单核对。Wenhao Ma 若证据证明实际买了两个月，且无截止/续报冲突，再将权益调到 `2026-11-30`；不能把 Attendance 补正当作数量修复。
- 新契约稳定后删除更正路径对收据 JSON 的隐式回填、按数组位置匹配付款、自动自愈等旧分支；不要同时保留新旧两套数量推断。实施时保护当前工作区的其他未提交改动，按阶段提交可回退的更正范围。

## 4. 完成标准

Edit Receipt 修改的是**同一笔原报名**，页面操作与后端能力一致；未改的字段及关联数据保持原业务值；所改字段的订单、权益、未来参与、金额与收据一致；旧历史与真实资金流可追溯。Wenhao Ma 的 Attendance 能独立补正，原有月份差异被明确提示并进入单独核对流程，不再只得到笼统的“购买数量不符”。

## 5. 实施记录与人工核对边界（2026-10-22）

- 详情接口现返回订单版本、课程行实购数量、Attendance、价格快照和关联权益数量/期限。编辑页保留历史值，比较打开表单时的基线，只提交实际变动字段；无后端支持的课程行和付款行增删在编辑态被禁止。
- 后端以订单版本防止覆盖，以稳定的订单行和付款 ID 定位记录。只补 Attendance 只写订单行与本订单仍有效的班课权益，不重算购买数量、期限、价格、付款或分摊。其他课程字段、参与人资料和付款字段也按提交的差异写入。
- 更正购买条款时才重新校验目标方案和权益；换班如已有未来学生课次、或缩短期限使未来课次落到有效期外，会明确阻断并提示日期。1 对 1 课包变更会检查已消耗和未来排课。已取消的班课权益不会因编辑旧收据自动恢复。
- 金额或付款分摊确有变化时才重算账务；原付款按 ID 更正，需要填写原因。退款与纯 Credit 凭证不随原报名收据同步。更正前后业务快照及操作者写入系统现有的 `sys_operation_log`。
- 尚不自动修复历史报名与权益数量不一致的记录。Wenhao Ma 的一月/两月差异仍需核对原始证据、续报和课次后，按单据处理；本次代码不会据方案名或金额猜测购买期限。未来课次和排课存在冲突时采用明确阻断，未引入自动迁移。旧收据历史版本由操作日志保留，当前可打印收据仍展示更正后的内容。
- 已完成前端类型检查与后端编译；尚未在真实订单或线上环境执行编辑操作。本次未运行测试套件，也未修改线上数据。
