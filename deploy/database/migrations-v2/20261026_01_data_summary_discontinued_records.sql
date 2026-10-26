-- Data Summary 的用户归类仅影响记录所在区域，可通过 Continue 恢复。
CREATE TABLE IF NOT EXISTS `data_summary_record_classification` (
  `record_key` varchar(128) NOT NULL,
  `order_id` bigint NOT NULL,
  `order_item_id` bigint DEFAULT NULL,
  `student_id` bigint NOT NULL,
  `participant_index` int DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`record_key`),
  KEY `idx_data_summary_classification_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
