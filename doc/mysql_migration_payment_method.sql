-- ============================================================
-- Migration: Payment method configuration table
-- Creates payment_method table for user-defined payment methods
-- and seeds default values.
-- ============================================================

CREATE TABLE IF NOT EXISTS `payment_method` (
  `id`           BIGINT        NOT NULL COMMENT 'Primary key ID',
  `method_code`  VARCHAR(32)   NOT NULL COMMENT 'Unique method code (e.g. CASH, CREDIT_CARD)',
  `method_name`  VARCHAR(64)   NOT NULL COMMENT 'Display name (e.g. Cash, Credit Card)',
  `sort_order`   INT           NOT NULL DEFAULT 0 COMMENT 'Sort order (ascending)',
  `status`       VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / INACTIVE',
  `created_at`   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by`   BIGINT        DEFAULT NULL,
  `updated_at`   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by`   BIGINT        DEFAULT NULL,
  `is_deleted`   TINYINT(1)    NOT NULL DEFAULT 0,
  `deleted_at`   DATETIME(3)   DEFAULT NULL,
  `deleted_by`   BIGINT        DEFAULT NULL,
  `version`      INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_method_code` (`method_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='User-configurable payment methods';

-- Seed default payment methods
INSERT INTO `payment_method` (`id`, `method_code`, `method_name`, `sort_order`, `status`) VALUES
  (1001, 'CASH',         'Cash',         1, 'ACTIVE'),
  (1002, 'CREDIT_CARD',  'Credit Card',  2, 'ACTIVE'),
  (1003, 'DEBIT_CARD',   'Debit Card',   3, 'ACTIVE'),
  (1004, 'BANK_TRANSFER','Bank Transfer',4, 'ACTIVE'),
  (1005, 'CHECK',        'Check',        5, 'ACTIVE'),
  (1006, 'OTHER',        'Other',        6, 'ACTIVE')
ON DUPLICATE KEY UPDATE `method_name` = VALUES(`method_name`), `sort_order` = VALUES(`sort_order`);
