-- 持久化接送司机列表，以及课程学生与司机的关联。
CREATE TABLE IF NOT EXISTS `transport_driver` (
  `id` bigint NOT NULL,
  `driver_name` varchar(128) NOT NULL,
  `display_order` int NOT NULL DEFAULT 99,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `version` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transport_driver_name` (`driver_name`, `is_deleted`),
  KEY `idx_transport_driver_order` (`display_order`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `transport_driver_assignment` (
  `id` bigint NOT NULL,
  `assignment_key` varchar(255) NOT NULL,
  `order_id` bigint NOT NULL,
  `order_item_id` bigint DEFAULT NULL,
  `student_id` bigint NOT NULL,
  `participant_index` int NOT NULL DEFAULT 1,
  `transport_type` varchar(16) NOT NULL DEFAULT 'BOTH',
  `driver_id` bigint DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `version` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transport_assignment_key` (`assignment_key`, `is_deleted`),
  KEY `idx_transport_assignment_driver` (`driver_id`, `is_deleted`),
  KEY `idx_transport_assignment_student` (`student_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
