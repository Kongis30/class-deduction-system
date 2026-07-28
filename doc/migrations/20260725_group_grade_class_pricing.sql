-- A group-course price row now owns one grade-specific class and its fixed schedule.
ALTER TABLE `class_group`
  ADD COLUMN `grade_band` varchar(64) NULL COMMENT 'Grade band served by this class' AFTER `product_id`,
  ADD KEY `idx_class_group_product_grade` (`product_id`, `grade_band`);

ALTER TABLE `course_price_option`
  ADD COLUMN `class_id` bigint NULL COMMENT 'Generated grade class for group price option' AFTER `course_product_id`,
  ADD KEY `idx_course_price_option_class` (`class_id`);

CREATE TABLE `class_group_schedule_slot` (
  `id` bigint NOT NULL,
  `class_group_id` bigint NOT NULL,
  `weekday` tinyint NOT NULL,
  `start_time` time NOT NULL,
  `end_time` time NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_class_group_schedule_slot` (`class_group_id`, `weekday`),
  CONSTRAINT `fk_class_group_schedule_slot_group`
    FOREIGN KEY (`class_group_id`) REFERENCES `class_group` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
