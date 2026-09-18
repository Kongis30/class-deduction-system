-- ============================================================================
-- 20260919: 订单内课程级付款分摊（payment_allocation）
-- ----------------------------------------------------------------------------
-- 需求：一次报名多个课程（同一 enrollment_order 下多个 enrollment_order_item），
--       收据一张；缴费金额平摊到各课程项，两级防超付（订单总额 / 单课程）。
-- 变更：
--   ① 新增表 payment_allocation（付款流水 → 课程项的分摊明细）
--   ② enrollment_order_item 新增 3 列：amount_due / paid_amount / balance_amount
--   ③ 存量回填 item.amount_due（按比例分摊订单级附加费/折扣，余差归末项），
--      balance_amount = amount_due（历史付款不追溯分摊，paid 从 0 起算）
-- 恒等式：SUM(item.amount_due) = order.amount_due（is_deleted=0）
-- 数据保全：不动订单级金额与历史流水；幂等可重复执行。
-- ============================================================================

-- 1) 新表 payment_allocation
CREATE TABLE IF NOT EXISTS `payment_allocation` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `payment_record_id` bigint NOT NULL COMMENT 'Source payment record ID',
  `enrollment_order_id` bigint NOT NULL COMMENT 'Enrollment order ID',
  `enrollment_order_item_id` bigint NOT NULL COMMENT 'Enrollment order item ID (course)',
  `amount` decimal(12,2) NOT NULL COMMENT 'Allocated amount for this course item (negative for refund)',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` bigint DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` bigint DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` bigint DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  KEY `idx_payment_alloc_payment` (`payment_record_id`),
  KEY `idx_payment_alloc_item` (`enrollment_order_item_id`),
  KEY `idx_payment_alloc_order` (`enrollment_order_id`),
  CONSTRAINT `fk_payment_alloc_payment` FOREIGN KEY (`payment_record_id`) REFERENCES `payment_record` (`id`),
  CONSTRAINT `fk_payment_alloc_item` FOREIGN KEY (`enrollment_order_item_id`) REFERENCES `enrollment_order_item` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Payment allocation per course item';

-- 2) enrollment_order_item 新增 3 列（幂等）
SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollment_order_item' AND COLUMN_NAME = 'amount_due';
SET @sql = IF(@done = 0, 'ALTER TABLE `enrollment_order_item` ADD COLUMN `amount_due` decimal(12,2) DEFAULT NULL COMMENT ''Amount due for this course item (gross + allocated fees - allocated discount)'' AFTER `price_amount`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollment_order_item' AND COLUMN_NAME = 'paid_amount';
SET @sql = IF(@done = 0, 'ALTER TABLE `enrollment_order_item` ADD COLUMN `paid_amount` decimal(12,2) NOT NULL DEFAULT ''0.00'' COMMENT ''Cumulative allocated payments for this course item'' AFTER `amount_due`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollment_order_item' AND COLUMN_NAME = 'balance_amount';
SET @sql = IF(@done = 0, 'ALTER TABLE `enrollment_order_item` ADD COLUMN `balance_amount` decimal(12,2) DEFAULT NULL COMMENT ''amount_due - paid_amount'' AFTER `paid_amount`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 存量回填 item.amount_due：
--    item_gross + (pickup + extra - discount) × (item_gross / tuition_subtotal)
--    price_amount 为 NULL 视为 0；tuition_subtotal 为 0/NULL 时附加费与折扣归零
--    （余差在步骤 4 统一归末项，保证 SUM(item.amount_due) = order.amount_due）
UPDATE enrollment_order_item oi
JOIN enrollment_order o ON o.id = oi.enrollment_order_id
SET oi.amount_due = COALESCE(ROUND(
        COALESCE(oi.price_amount, 0)
        + (COALESCE(o.pickup_fee_amount, 0) + COALESCE(o.extra_fee_amount, 0) - COALESCE(o.discount_amount, 0))
          * COALESCE(oi.price_amount, 0) / NULLIF(o.tuition_subtotal_amount, 0)
    , 2), 0)
WHERE oi.is_deleted = 0
  AND o.is_deleted = 0
  AND oi.amount_due IS NULL;

-- 4) 余差归末项：SUM(item.amount_due) 对齐 order.amount_due
UPDATE enrollment_order_item oi
JOIN (
    SELECT o.id AS order_id,
           COALESCE(o.amount_due, 0) - COALESCE(SUM(COALESCE(oi2.amount_due, 0)), 0) AS diff,
           (SELECT oi3.id FROM enrollment_order_item oi3
            WHERE oi3.enrollment_order_id = o.id AND oi3.is_deleted = 0
            ORDER BY oi3.id DESC LIMIT 1) AS last_item_id
    FROM enrollment_order o
    LEFT JOIN enrollment_order_item oi2
           ON oi2.enrollment_order_id = o.id AND oi2.is_deleted = 0
    WHERE o.is_deleted = 0
    GROUP BY o.id, o.amount_due
) t ON t.last_item_id = oi.id
SET oi.amount_due = oi.amount_due + t.diff
WHERE t.diff <> 0;

-- 5) balance_amount = amount_due（历史付款不追溯分摊，paid 从 0 起算）
UPDATE enrollment_order_item
SET balance_amount = amount_due, paid_amount = 0.00
WHERE is_deleted = 0 AND amount_due IS NOT NULL;

-- 6) 验证：恒等式偏差应为 0（存在 amount_due 为 NULL 的脏 item 时允许误差）
SELECT o.id, o.amount_due, COALESCE(SUM(oi.amount_due), 0) AS items_total,
       o.amount_due - COALESCE(SUM(oi.amount_due), 0) AS diff
FROM enrollment_order o
JOIN enrollment_order_item oi ON oi.enrollment_order_id = o.id AND oi.is_deleted = 0
WHERE o.is_deleted = 0
GROUP BY o.id, o.amount_due
HAVING ABS(diff) > 0.01
LIMIT 5;
