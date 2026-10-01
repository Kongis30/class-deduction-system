-- Local DBv2-only crosswalk for traceable legacy-data conversion and rollback.
USE `class_deduction_system_v2`;
-- Read-only preflight: invalid targets deliberately fail PREPARE before any DDL or data change.
SET @group_course_v2_guard_sql = IF(
  COALESCE(DATABASE(), '') = 'class_deduction_system_v2'
  AND SUBSTRING_INDEX(USER(), '@', -1) IN ('localhost','127.0.0.1','::1'),
  'SELECT 1',
  'THIS IS NOT A VALID GROUP COURSE V2 MIGRATION TARGET'
);
PREPARE group_course_v2_target_guard FROM @group_course_v2_guard_sql;
EXECUTE group_course_v2_target_guard;
DEALLOCATE PREPARE group_course_v2_target_guard;


CREATE TABLE IF NOT EXISTS `group_course_model_migration_map` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `mapping_type` varchar(32) NOT NULL,
  `source_id` bigint NOT NULL,
  `scope_key` varchar(160) NOT NULL DEFAULT '',
  `target_id` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_course_migration_source` (`mapping_type`,`source_id`,`scope_key`),
  KEY `idx_group_course_migration_target` (`mapping_type`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Audit crosswalk from legacy group-class rows to DBv2 project model';
