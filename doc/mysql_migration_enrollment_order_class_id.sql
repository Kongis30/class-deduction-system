-- Migration: Add class_id to enrollment_order for group class financial tracking
-- 班课报名也使用 enrollment_order 表记录财务信息（金额、支付、取消）
-- 1v1 订单：course_product_id 有值，class_id = NULL
-- 班课订单：class_id 有值，course_product_id = NULL

ALTER TABLE `enrollment_order`
  ADD COLUMN `class_id` BIGINT DEFAULT NULL COMMENT 'Class group ID (for group class enrollments)' AFTER `student_id`,
  ADD INDEX `idx_enrollment_order_class_id` (`class_id`);

-- Add list_price to class_group for price display in enrollment dialog
ALTER TABLE `class_group`
  ADD COLUMN `list_price` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Original price (sticker price for this class group)' AFTER `classroom_id`;
