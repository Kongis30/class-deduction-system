-- Per-student/per-course refund state. A shared order item must not carry a
-- global STOPPED flag because one participant may stop while others continue.
CREATE TABLE IF NOT EXISTS `enrollment_order_course_refund_state` (
  `id` bigint NOT NULL,
  `enrollment_order_id` bigint NOT NULL,
  `enrollment_order_item_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `participant_index` int DEFAULT NULL,
  `item_status` varchar(24) NOT NULL DEFAULT 'ACTIVE',
  `stopped_consumed_amount` decimal(12,2) DEFAULT NULL,
  `stopped_session_count` int DEFAULT NULL,
  `stopped_period_start_date` date DEFAULT NULL,
  `stopped_period_end_date` date DEFAULT NULL,
  `stopped_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_item_student_refund_state` (`enrollment_order_item_id`,`student_id`,`is_deleted`),
  KEY `idx_refund_state_order` (`enrollment_order_id`),
  KEY `idx_refund_state_student` (`student_id`),
  CONSTRAINT `fk_refund_state_order` FOREIGN KEY (`enrollment_order_id`) REFERENCES `enrollment_order` (`id`),
  CONSTRAINT `fk_refund_state_order_item` FOREIGN KEY (`enrollment_order_item_id`) REFERENCES `enrollment_order_item` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
