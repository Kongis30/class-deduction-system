-- 将班级周期日期配置保存在课程 Grade 配置上；既有 class_group 日期继续保留，供排课和报名兼容读取。
ALTER TABLE `product_grade_config`
  ADD COLUMN `class_start_date` date DEFAULT NULL COMMENT 'Grade-wide actual class period start date' AFTER `display_order`,
  ADD COLUMN `class_end_date` date DEFAULT NULL COMMENT 'Grade-wide actual class period end date' AFTER `class_start_date`,
  ADD COLUMN `class_period_configured` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Whether the grade-wide class period is explicitly configured' AFTER `class_end_date`;
