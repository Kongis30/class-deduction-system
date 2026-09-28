-- 按 Course 配置的班课放假区间及受影响课次的可恢复标记。
CREATE TABLE `course_holiday` (
  `id` bigint NOT NULL,
  `course_product_id` bigint NOT NULL,
  `holiday_name` varchar(100) NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_course_holiday_dates` (`course_product_id`,`is_deleted`,`start_date`,`end_date`),
  CONSTRAINT `fk_course_holiday_product` FOREIGN KEY (`course_product_id`) REFERENCES `course_product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Group-class holidays scoped to a course product';

ALTER TABLE `lesson_occurrence`
  ADD COLUMN `course_holiday_id` bigint DEFAULT NULL COMMENT 'Course holiday that cancelled this occurrence' AFTER `planning_status`,
  ADD COLUMN `holiday_previous_status` varchar(32) DEFAULT NULL COMMENT 'Occurrence status before holiday cancellation' AFTER `course_holiday_id`,
  ADD KEY `idx_occurrence_course_holiday` (`course_holiday_id`),
  ADD CONSTRAINT `fk_occurrence_course_holiday` FOREIGN KEY (`course_holiday_id`) REFERENCES `course_holiday` (`id`);
