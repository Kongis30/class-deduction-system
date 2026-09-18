-- ============================================================================
-- 20260919: 课程级优惠与付款分摊
-- ----------------------------------------------------------------------------
-- 课程级折扣和付款归属写入 enrollment_order_item / payment_allocation。
-- 历史订单保持订单金额与支付流水不变；课程级初始值按课程价格权重分配，余差归末项。
-- 脚本可重复执行；只在快照为空时回填，不重置已有付款快照。
-- ============================================================================

-- 1) 每笔付款到课程项的分摊账本
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

-- 2) 课程级金额快照列（均幂等新增）
SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollment_order_item' AND COLUMN_NAME = 'amount_due';
SET @sql = IF(@done = 0, 'ALTER TABLE `enrollment_order_item` ADD COLUMN `amount_due` decimal(12,2) DEFAULT NULL COMMENT ''Amount due for this course item'' AFTER `price_amount`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollment_order_item' AND COLUMN_NAME = 'discount_amount';
SET @sql = IF(@done = 0, 'ALTER TABLE `enrollment_order_item` ADD COLUMN `discount_amount` decimal(12,2) DEFAULT NULL COMMENT ''Discount allocated to this course item'' AFTER `price_amount`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollment_order_item' AND COLUMN_NAME = 'paid_amount';
SET @sql = IF(@done = 0, 'ALTER TABLE `enrollment_order_item` ADD COLUMN `paid_amount` decimal(12,2) NOT NULL DEFAULT ''0.00'' COMMENT ''Cumulative allocated payments for this course item'' AFTER `amount_due`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollment_order_item' AND COLUMN_NAME = 'balance_amount';
SET @sql = IF(@done = 0, 'ALTER TABLE `enrollment_order_item` ADD COLUMN `balance_amount` decimal(12,2) DEFAULT NULL COMMENT ''Outstanding balance for this course item'' AFTER `paid_amount`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 历史折扣按课程价格权重回填，舍入余差归最后一门课。
WITH base AS (
    SELECT oi.id,
           oi.enrollment_order_id,
           COALESCE(o.discount_amount, 0) AS order_discount,
           COALESCE(oi.price_amount, 0) AS item_price,
           COALESCE(o.tuition_subtotal_amount, 0) AS tuition_subtotal,
           ROW_NUMBER() OVER (PARTITION BY oi.enrollment_order_id ORDER BY oi.line_no, oi.id) AS row_no,
           COUNT(*) OVER (PARTITION BY oi.enrollment_order_id) AS row_count
    FROM enrollment_order_item oi
    JOIN enrollment_order o ON o.id = oi.enrollment_order_id
    WHERE oi.is_deleted = 0 AND o.is_deleted = 0
), rounded AS (
    SELECT base.*,
           CASE WHEN tuition_subtotal > 0
                THEN TRUNCATE(order_discount * item_price / tuition_subtotal, 2)
                ELSE TRUNCATE(order_discount / row_count, 2)
           END AS rounded_share
    FROM base
), shares AS (
    SELECT id,
           CASE WHEN row_no = row_count
                THEN order_discount - COALESCE(SUM(rounded_share) OVER (
                    PARTITION BY enrollment_order_id ORDER BY row_no
                    ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING), 0)
                ELSE rounded_share
           END AS discount_share
    FROM rounded
)
UPDATE enrollment_order_item oi
JOIN shares s ON s.id = oi.id
SET oi.discount_amount = s.discount_share
WHERE oi.discount_amount IS NULL;

UPDATE enrollment_order_item
SET discount_amount = 0.00
WHERE discount_amount IS NULL;

ALTER TABLE `enrollment_order_item`
  MODIFY COLUMN `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00'
  COMMENT 'Discount allocated to this course item';

-- 4) 应收按课程价、服务费权重和课程优惠计算。此前已有应收快照不会被覆盖。
UPDATE enrollment_order_item oi
JOIN enrollment_order o ON o.id = oi.enrollment_order_id
SET oi.amount_due = COALESCE(ROUND(
        COALESCE(oi.price_amount, 0)
        + (COALESCE(o.pickup_fee_amount, 0) + COALESCE(o.dropoff_fee_amount, 0) + COALESCE(o.extra_fee_amount, 0))
          * COALESCE(oi.price_amount, 0) / NULLIF(o.tuition_subtotal_amount, 0)
        - COALESCE(oi.discount_amount, 0)
    , 2), 0)
WHERE oi.is_deleted = 0
  AND o.is_deleted = 0
  AND oi.amount_due IS NULL;

-- 5) 余差归最后一项，保证课程应收合计等于订单应收。
UPDATE enrollment_order_item oi
JOIN (
    SELECT o.id AS order_id,
           COALESCE(o.amount_due, 0) - COALESCE(SUM(COALESCE(oi2.amount_due, 0)), 0) AS diff,
           (SELECT oi3.id FROM enrollment_order_item oi3
            WHERE oi3.enrollment_order_id = o.id AND oi3.is_deleted = 0
            ORDER BY oi3.line_no DESC, oi3.id DESC LIMIT 1) AS last_item_id
    FROM enrollment_order o
    LEFT JOIN enrollment_order_item oi2
           ON oi2.enrollment_order_id = o.id AND oi2.is_deleted = 0
    WHERE o.is_deleted = 0
    GROUP BY o.id, o.amount_due
) t ON t.last_item_id = oi.id
SET oi.amount_due = oi.amount_due + t.diff
WHERE t.diff <> 0;

-- 6) 历史流水不追溯课程归属。只初始化缺失余额，重复运行不会清空之后写入的课程已付快照。
UPDATE enrollment_order_item
SET balance_amount = amount_due - COALESCE(paid_amount, 0)
WHERE is_deleted = 0 AND amount_due IS NOT NULL AND balance_amount IS NULL;

-- 7) 验证：有课程明细的订单应满足 SUM(item.amount_due) = order.amount_due。
SELECT o.id, o.amount_due, COALESCE(SUM(oi.amount_due), 0) AS items_total,
       o.amount_due - COALESCE(SUM(oi.amount_due), 0) AS diff
FROM enrollment_order o
JOIN enrollment_order_item oi ON oi.enrollment_order_id = o.id AND oi.is_deleted = 0
WHERE o.is_deleted = 0
GROUP BY o.id, o.amount_due
HAVING ABS(diff) > 0.01
LIMIT 5;
