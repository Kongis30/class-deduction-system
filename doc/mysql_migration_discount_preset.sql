-- Migration: discount_preset table
-- Created: 2026-05-17

CREATE TABLE `discount_preset` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `preset_name` VARCHAR(100) NOT NULL COMMENT '折扣名称，如 Sibling discount, Early bird',
  `discount_type` VARCHAR(20) NOT NULL COMMENT 'PERCENTAGE / FIXED',
  `discount_value` DECIMAL(10,2) NOT NULL COMMENT '百分比填10(=10% off), 固定金额填100(=$100 off)',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号，小的排前面',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / INACTIVE',
  `is_deleted` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `created_by` BIGINT DEFAULT NULL,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by` BIGINT DEFAULT NULL,
  `deleted_at` DATETIME DEFAULT NULL,
  `deleted_by` BIGINT DEFAULT NULL,
  `version` INT DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Discount presets configuration';

-- Seed data
INSERT INTO `discount_preset` (`preset_name`, `discount_type`, `discount_value`, `sort_order`, `status`) VALUES
('Sibling 10%', 'PERCENTAGE', 10, 1, 'ACTIVE'),
('Sibling 20%', 'PERCENTAGE', 20, 2, 'ACTIVE'),
('Early bird $50', 'FIXED', 50, 3, 'ACTIVE'),
('Referral $30', 'FIXED', 30, 4, 'ACTIVE');
