-- Remove the import-only placeholder from project descriptions in the local DBv2.
USE `class_deduction_system_v2`;

-- Refuse to run unless the selected database and connection are local DBv2.
SET @group_course_v2_guard_sql = IF(
  COALESCE(DATABASE(), '') = 'class_deduction_system_v2'
  AND SUBSTRING_INDEX(USER(), '@', -1) IN ('localhost','127.0.0.1','::1'),
  'SELECT 1',
  'THIS IS NOT A VALID GROUP COURSE V2 MIGRATION TARGET'
);
PREPARE group_course_v2_target_guard FROM @group_course_v2_guard_sql;
EXECUTE group_course_v2_target_guard;
DEALLOCATE PREPARE group_course_v2_target_guard;

UPDATE `group_course_project`
SET `description` = NULL
WHERE `is_deleted` = 0
  AND BINARY `description` = BINARY 'Migrated from the legacy group-course structure.';
