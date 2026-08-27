-- Migration: create course_category configuration table.
-- The set of category codes is fixed by the system; this table only stores the
-- editable display name and display order for each top-level course group so
-- staff can rename and reorder them in the course-creation / registration screens.
-- Idempotent: safe to run multiple times.

CREATE TABLE IF NOT EXISTS `course_category` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `type_code` varchar(32) NOT NULL COMMENT 'Fixed category code: AFTER_SCHOOL/SATURDAY/SUMMER/SPRINT/ONE_ON_ONE',
  `display_name` varchar(64) NOT NULL COMMENT 'Editable display name',
  `display_order` int NOT NULL DEFAULT '99' COMMENT 'Display order for sorting',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/INACTIVE',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` bigint DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` bigint DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` bigint DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_category_type_code` (`type_code`),
  KEY `idx_course_category_display_order` (`display_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Top-level course categories (display name + order editable)';

-- Seed the fixed set of categories if missing (application also self-heals on read).
INSERT INTO `course_category` (`id`, `type_code`, `display_name`, `display_order`)
SELECT * FROM (
  SELECT 1 AS id, 'AFTER_SCHOOL' AS type_code, 'After School' AS display_name, 1 AS display_order UNION ALL
  SELECT 2, 'SATURDAY', 'Saturday Class', 2 UNION ALL
  SELECT 3, 'SUMMER', 'Summer Class', 3 UNION ALL
  SELECT 4, 'SPRINT', 'Sprint Class', 4 UNION ALL
  SELECT 5, 'ONE_ON_ONE', '1 on 1', 5
) AS seed
WHERE NOT EXISTS (
  SELECT 1 FROM `course_category` c WHERE c.`type_code` = seed.type_code
);
