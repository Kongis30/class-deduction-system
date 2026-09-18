# Spec：订单内课程级付款分摊（Payment Allocation per Course Item）

> 状态：待用户确认
> 来源：客户反馈 2026-09-18——学生一次报名多个 program，一次付款需要平摊到各课程，"一边一半"，各课程显示各自的已付/欠款，且不允许任何一级超付。

## 1. 背景与目标

### 1.1 客户场景

一次报名包含多个课程（同一张 enrollment_order 下多个 enrollment_order_item），收据一张、涵盖全部课程：

```
订单 EO202609110001（一次报名）
├─ 课后班  应付 $3000   ← 本次分摊 $2500 → 余额 $500
└─ 周六班  应付 $3000   ← 本次分摊 $2500 → 余额 $500
本次收款 $5000（一笔流水、一张收据）
```

### 1.2 现状差距（已核实代码）

| 差距 | 说明 |
|---|---|
| 无 item 级付款关联 | `payment_record` 只有 `enrollment_order_id`（订单级）与 `participant_index`（私教共享参与者级），课程项分摊数据无处可记 |
| item 无余额字段 | `enrollment_order_item` 只有价格字段（`price_amount` 等），没有 `amount_due` / `paid_amount` / `balance_amount` |
| 展示缺失 | 客户要的"每个课程各付多少、各剩多少"无法展示 |

### 1.3 目标

1. 付款时把支付金额自动分摊到订单内各课程项（默认策略见 §4）
2. 记录分摊明细（可追溯、可汇总）
3. 两级防超付硬校验：订单总额不超付；单个课程项不超付
4. 订单详情、收据展示各课程项的应付/已付/余额

### 1.4 非目标（本期不做）

- 不改收据张数：仍为一张 REGISTRATION 收据（与现状一致）
- 不做跨订单（多个 enrollment_order 之间）的付款分摊
- 不改 Data Summary 模块（该模块有并行开发在途）
- 缴费对话框中暂不支持 staff 手动改每项分摊额（后续可加，接口设计预留）

## 2. 数据模型

### 2.1 新表 `payment_allocation`（付款分摊明细）

```sql
CREATE TABLE `payment_allocation` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `payment_record_id` bigint NOT NULL COMMENT 'Source payment record ID',
  `enrollment_order_id` bigint NOT NULL COMMENT 'Enrollment order ID',
  `enrollment_order_item_id` bigint NOT NULL COMMENT 'Enrollment order item ID (course)',
  `amount` decimal(12,2) NOT NULL COMMENT 'Allocated amount for this course item',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_payment_alloc_payment` (`payment_record_id`),
  KEY `idx_payment_alloc_item` (`enrollment_order_item_id`),
  KEY `idx_payment_alloc_order` (`enrollment_order_id`),
  CONSTRAINT `fk_payment_alloc_payment` FOREIGN KEY (`payment_record_id`) REFERENCES `payment_record` (`id`),
  CONSTRAINT `fk_payment_alloc_item` FOREIGN KEY (`enrollment_order_item_id`) REFERENCES `enrollment_order_item` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Payment allocation per course item';
```

设计要点：
- 一笔付款（payment_record，可能含多 split）→ N 行 allocation（每课程项一行）
- 不加 `participant_index`：私教共享订单的付款继续走 participant 快照（§2.3），两条链路互不干扰

### 2.2 `enrollment_order_item` 新增 3 列

```sql
ALTER TABLE `enrollment_order_item` ADD COLUMN `amount_due` decimal(12,2) DEFAULT NULL COMMENT 'Amount due for this course item (gross + allocated fees - allocated discount)';
ALTER TABLE `enrollment_order_item` ADD COLUMN `paid_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Cumulative allocated payments for this course item';
ALTER TABLE `enrollment_order_item` ADD COLUMN `balance_amount` decimal(12,2) DEFAULT NULL COMMENT 'amount_due - paid_amount';
```

`amount_due` 的构成（保持恒等式 `SUM(item.amount_due) = order.amount_due`）：

```
item_gross   = item.price_amount（已有字段，创建/更正时即最终价）
fee_ratio    = item_gross / tuition_subtotal_amount（订单学费小计）
item_amount_due = item_gross
               + (pickup_fee + extra_fee) × fee_ratio
               - discount × fee_ratio
               （每项四舍五入到分；余差调整到最后一个 item，保证恒等式成立）
```

无附加费与折扣时（大多数订单）`item_amount_due = price_amount`。

### 2.3 存量数据回填（迁移脚本一次性处理）

- 为存量订单的所有 item 回填 `amount_due`（按 §2.2 公式），`balance_amount = amount_due - paid_amount`（初始 paid=0）
- 存量历史付款（已有 payment_record）**不**追溯生成 allocation（历史收据展示不变）；item 的 `paid_amount` 从 0 起算，只反映新分摊。订单级 received/balance 保持现状不动
- 说明：回填后存在"订单 balance ≠ SUM(item balance)"的过渡态（历史付款未摊到 item），前端展示以订单级为准，item 级标注"自该功能上线起累计"。若客户要求追溯历史分摊，再追加一次性分摊脚本（可选项，默认不做）

### 2.4 兼容初始化

`PrivateLessonSchemaCompatibilityInitializer` 增加：建 `payment_allocation` 表 + item 三列的 `addColumnIfMissing`（线上启动自愈，模式与现有一致）。

## 3. 分摊算法（后端唯一实现，`PaymentAllocationCalculator`）

### 3.1 输入

- `T`：本次付款总额（各 split 之和）
- items：订单下全部 `is_deleted=0` 且 `item_status` 非删除态的课程项，及其剩余应付 `R_i = item.amount_due - item.paid_amount`（`R_i` 下限截 0）

### 3.2 策略：平均分摊（EVEN）+ 上限封顶 + 超额顺延

1. **总额校验**：`T > SUM(R_i)` → 拒绝 `PAYMENT_EXCEEDS_AMOUNT_DUE`（订单级不超付）
2. **平均分摊**：`A_i = T / n`（n 为有效课程项数），四舍五入到分；分位余差（$0.01 级）依次补给前 k 项（例：$100/3 → $33.34 / $33.33 / $33.33）
3. **封顶顺延**：`A_i > R_i` 时截到 `R_i`，超出部分重新均分给还有剩余额度的项，循环直至分完或全部封满（例：3 课各余 $1000，付 $3000，其中一课只剩 $200 → 该课 $200，另两课各 $1400）
4. **最终校验**：`SUM(A_i) = T` 且每个 `A_i ≤ R_i`，否则拒绝（兜底，正常不会触达）
5. `R_i = 0` 的课程项不参与分摊且不占名额，**不生成 amount=0 的 allocation 行**

### 3.3 边界用例

| 用例 | 期望 |
|---|---|
| 3 个课程订单付 $3000，各课程余额充足 | 各 +$1000（客户场景） |
| 两课程各应付 $3000、余额均 $3000，付 $5000 | $2500 / $2500 |
| 单课程订单付 $5000 | 全额分给唯一 item（行为等同现状） |
| 3 课付 $3000，一课只剩 $200、另两课各余 $1400 | $200 / $1400 / $1400（封顶顺延） |
| 2 课 A 剩 $500、B 剩 $2000，付 $1200 | 平均 $600 超 A 上限 → A=$500、B=$700 |
| $100 分 3 课 | $33.34 / $33.33 / $33.33（分位余差补给前项） |
| 付 $7000 > 订单余额 $6000 | 拒绝 `PAYMENT_EXCEEDS_AMOUNT_DUE` |
| 订单余额 $1，两课程 R 各 $0.50，付 $1 | 各 $0.50，订单转 PAID |
| T = $0 或负数 | 拒绝 `INVALID_PAYMENT_AMOUNT` |
| 订单已无余额（balance ≤ 0） | 拒绝 `ORDER_ALREADY_PAID`（沿用现有语义） |
| 课程项 price_amount 为 NULL（脏数据） | item_amount_due 按 0 处理、R_i=0 不占名额；全部 item 均为 0 时拒绝 `PAYMENT_ALLOCATION_FAILED` |
| 私教共享订单（有 participant） | 不走 item 分摊（走既有 participant 链路），两条链路互斥由请求字段自然区分（带 participantIndex 走 participant，不带走 item 分摊） |

## 4. API 契约

### 4.1 分摊预览（新增）

```
POST /api/v1/enrollment/orders/{orderId}/payments/allocation-preview
Body: { "amount": 5000.00 }
Resp: {
  "allocations": [
    { "enrollmentOrderItemId": 123, "courseName": "After School", "itemAmountDue": 3000.00,
      "itemPaidAmount": 0.00, "itemBalanceAmount": 3000.00, "allocatedAmount": 2500.00 },
    { ... }
  ],
  "totalAmount": 5000.00,
  "orderBalanceAmount": 6000.00
}
```

### 4.2 付款（扩展现有 `POST /v1/enrollment/payment-records`）

请求不变（splits 结构不动）。后端行为变化：
- 校验通过后，对每笔 split 生成的 payment_record 自动计算并写入 payment_allocation（单 split 多 split 均按 §3 算法对总额一次性分摊，明细关联到各 split 的 payment_record——实现上按 split 金额独立走一遍 §3 算法，保证每笔流水的分摊自洽）
- 同步更新 item.paid_amount / balance_amount（事务内，乐观锁）
- 订单级 updateEnrollmentOrderPayment 逻辑不变

错误码新增：`PAYMENT_ALLOCATION_FAILED`（兜底）、沿用 `PAYMENT_EXCEEDS_AMOUNT_DUE` / `ORDER_ALREADY_PAID`。

### 4.3 收据 payload 扩展

`syncRegistrationReceipt` 的 payload 增加：

```json
"items": [
  { "enrollmentOrderItemId": 123, "courseName": "After School",
    "amountDue": 3000.00, "paidAmount": 2500.00, "balanceAmount": 500.00 }
]
```

收据模板（如需渲染课程级已付）由前端收据页读取 `items` 呈现。

### 4.4 退款（REFUND）联动

现有退款流程产生 `payment_type=REFUND` 流水并更新订单级 received/balance。item 级联动：按各 item 当前 `paid_amount` 占订单 received 的比例回冲（余差放最后一个非零 item），`SUM(item.paid) 与订单 received 保持同向变化`。MVP 实现于退款 service 内复用同一比例回填函数。

## 5. 前端改动

- `EnrollmentOrdersView.vue` 缴费对话框（Record Payment）：
  - 输入分摊金额后调用 4.1 预览接口，展示分摊表（课程名 / 应付 / 已付 / 本次分摊 / 剩余），实时刷新
  - 超付前置拦截：总额超订单余额时禁用提交并提示
- 订单详情对话框：课程列表展示各 item 的应付/已付/余额
- `src/api/enrollment.ts`：新增 `previewPaymentAllocation`，`createPaymentRecord` 请求体不变

## 6. 验收标准

1. 客户场景：两课程各 $3000 的订单付 $5000 → 两 item 各 paid $2500 / balance $500；订单 PARTIAL、balance $1000；收据 payload 含 items 且数值一致
2. 两级超付约束：§3.3 表中所有拒绝/封顶用例行为正确
3. 多支付方式（splits）付款：每笔流水各有自洽的 allocation 明细
4. 单课程订单付款行为与现状完全一致（回归）
5. 私教共享订单带 participantIndex 付款不受影响（回归）
6. 存量订单迁移后：订单级金额不变，item 三列回填完成，`SUM(item.amount_due) = order.amount_due`
7. 删除订单级联：deleteOrder 级联软删 payment_allocation（EnrollmentOrderServiceImpl.deleteOrderFinancialArtifacts 补一行）
8. 单元测试覆盖 §3.3 全部边界用例 + 分位余差用例；typecheck 通过

## 7. 实施范围（文件清单）

| 层 | 文件 |
|---|---|
| 迁移 | `doc/migrations/20260919_payment_allocation.sql`（建表 + item 三列 + 存量回填） |
| 实体 | 新 `PaymentAllocationEntity` + Mapper；`EnrollmentOrderItemEntity` 加 3 字段 |
| 初始化 | `PrivateLessonSchemaCompatibilityInitializer`（建表 + 补列） |
| 算法 | 新 `PaymentAllocationCalculator`（纯函数，可单测） |
| 服务 | `PaymentRecordServiceImpl`（付款写分摊、item 快照同步）、`EnrollmentOrderServiceImpl`（更正金额后重算 item.amount_due、删除级联、退款回冲）、收据 payload |
| 控制器 | `PaymentRecordController` 新增 preview 端点 |
| 前端 | `enrollment.ts` API、`EnrollmentOrdersView.vue` 缴费对话框 + 订单详情 |
