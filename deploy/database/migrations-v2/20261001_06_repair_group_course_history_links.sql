-- Repair group-class holidays and historical lesson entitlement links after the initial V2 conversion.
-- Idempotent; only operates against the isolated local DBv2 clone.
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

CREATE TABLE IF NOT EXISTS `group_course_model_holiday_occurrence_audit` (
  `occurrence_id` bigint NOT NULL,
  `source_holiday_id` bigint NOT NULL,
  `target_holiday_id` bigint NOT NULL,
  `captured_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`occurrence_id`),
  KEY `idx_group_holiday_occurrence_source` (`source_holiday_id`),
  KEY `idx_group_holiday_occurrence_target` (`target_holiday_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Original holiday links for safely reversing the local V2 schedule migration';

DROP TEMPORARY TABLE IF EXISTS `tmp_v2_lesson_candidate`;
DROP TEMPORARY TABLE IF EXISTS `tmp_v2_lesson_match`;
DROP TEMPORARY TABLE IF EXISTS `tmp_v2_holiday_source`;
DROP TEMPORARY TABLE IF EXISTS `tmp_v2_holiday_days`;
DROP TEMPORARY TABLE IF EXISTS `tmp_v2_holiday_ranges`;

CREATE TEMPORARY TABLE `tmp_v2_lesson_candidate` AS
SELECT DISTINCT record_row.id lesson_record_id,enrollment.id class_enrollment_id
FROM lesson_record record_row
JOIN lesson_occurrence occurrence ON occurrence.id=record_row.occurrence_id AND occurrence.is_deleted=0
JOIN class_group actual_class ON actual_class.id=occurrence.class_group_id
JOIN course_product product ON product.id=actual_class.product_id AND product.teaching_mode='GROUP'
JOIN class_enrollment enrollment ON enrollment.class_id=actual_class.id
  AND enrollment.student_id=record_row.student_id AND enrollment.is_deleted=0
WHERE record_row.is_deleted=0
  AND actual_class.group_project_id IS NOT NULL
  AND DATE(COALESCE(record_row.planned_start_at,occurrence.planned_start_at))
      BETWEEN enrollment.enrollment_date AND enrollment.expiry_date;

CREATE TEMPORARY TABLE `tmp_v2_lesson_match` AS
SELECT lesson_record_id,MIN(class_enrollment_id) class_enrollment_id,
       COUNT(DISTINCT class_enrollment_id) candidate_count
FROM tmp_v2_lesson_candidate
GROUP BY lesson_record_id;

CREATE TEMPORARY TABLE `tmp_v2_holiday_source` AS
SELECT DISTINCT holiday.id source_holiday_id,actual_class.group_project_id,
       holiday.holiday_name,holiday.start_date,holiday.end_date
FROM course_holiday holiday
JOIN lesson_occurrence occurrence ON occurrence.course_holiday_id=holiday.id AND occurrence.is_deleted=0
JOIN class_group actual_class ON actual_class.id=occurrence.class_group_id
JOIN course_product product ON product.id=actual_class.product_id AND product.teaching_mode='GROUP'
WHERE holiday.is_deleted=0 AND holiday.group_project_id IS NULL
  AND actual_class.group_project_id IS NOT NULL;

DROP PROCEDURE IF EXISTS `assert_group_course_v2_history_links_ready`;
DELIMITER $$
CREATE PROCEDURE `assert_group_course_v2_history_links_ready`()
BEGIN
  DECLARE bad_count bigint DEFAULT 0;

  IF COALESCE(DATABASE(), '') <> 'class_deduction_system_v2'
     OR SUBSTRING_INDEX(USER(), '@', -1) NOT IN ('localhost','127.0.0.1','::1') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Refusing to repair history outside the isolated local DBv2.';
  END IF;

  SELECT COUNT(*) INTO bad_count
  FROM lesson_record record_row
  JOIN lesson_occurrence occurrence ON occurrence.id=record_row.occurrence_id
  JOIN class_group actual_class ON actual_class.id=occurrence.class_group_id
  JOIN course_product product ON product.id=actual_class.product_id AND product.teaching_mode='GROUP'
  LEFT JOIN tmp_v2_lesson_match matched ON matched.lesson_record_id=record_row.id
  WHERE record_row.is_deleted=0 AND actual_class.group_project_id IS NOT NULL
    AND ((record_row.class_enrollment_id IS NULL AND COALESCE(matched.candidate_count,0)<>1)
      OR (record_row.class_enrollment_id IS NOT NULL
          AND (COALESCE(matched.candidate_count,0)<>1
            OR matched.class_enrollment_id<>record_row.class_enrollment_id)));
  IF bad_count > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A historical group lesson does not map to exactly one student entitlement; no changes were made.';
  END IF;

  SELECT COUNT(*) INTO bad_count
  FROM course_holiday holiday
  JOIN lesson_occurrence occurrence ON occurrence.course_holiday_id=holiday.id AND occurrence.is_deleted=0
  JOIN class_group actual_class ON actual_class.id=occurrence.class_group_id
  JOIN course_product product ON product.id=actual_class.product_id AND product.teaching_mode='GROUP'
  WHERE holiday.is_deleted=0 AND holiday.group_project_id IS NULL
    AND actual_class.group_project_id IS NULL;
  IF bad_count > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A legacy holiday is linked to a group class without a V2 project; no changes were made.';
  END IF;
END$$
DELIMITER ;

CALL `assert_group_course_v2_history_links_ready`();
DROP PROCEDURE `assert_group_course_v2_history_links_ready`;

START TRANSACTION;

UPDATE lesson_record record_row
JOIN tmp_v2_lesson_match matched ON matched.lesson_record_id=record_row.id
SET record_row.class_enrollment_id=matched.class_enrollment_id
WHERE record_row.is_deleted=0 AND record_row.class_enrollment_id IS NULL
  AND matched.candidate_count=1;

CREATE TEMPORARY TABLE `tmp_v2_holiday_days` AS
WITH RECURSIVE holiday_days AS (
  SELECT group_project_id,source_holiday_id,holiday_name,start_date holiday_date,end_date
  FROM tmp_v2_holiday_source
  UNION ALL
  SELECT group_project_id,source_holiday_id,holiday_name,
         DATE_ADD(holiday_date,INTERVAL 1 DAY),end_date
  FROM holiday_days WHERE holiday_date<end_date
)
SELECT DISTINCT group_project_id,source_holiday_id,holiday_name,holiday_date FROM holiday_days;

CREATE TEMPORARY TABLE `tmp_v2_holiday_ranges` AS
WITH daily AS (
  SELECT group_project_id,holiday_date,MIN(holiday_name) holiday_name
  FROM tmp_v2_holiday_days GROUP BY group_project_id,holiday_date
), numbered AS (
  SELECT group_project_id,holiday_date,holiday_name,
         DATE_SUB(holiday_date,INTERVAL ROW_NUMBER() OVER (PARTITION BY group_project_id ORDER BY holiday_date) DAY) island
  FROM daily
)
SELECT group_project_id,MIN(holiday_date) start_date,MAX(holiday_date) end_date,MIN(holiday_name) holiday_name
FROM numbered GROUP BY group_project_id,island;

INSERT INTO course_holiday (id,group_project_id,holiday_name,start_date,end_date,is_deleted)
SELECT UUID_SHORT(),ranges.group_project_id,ranges.holiday_name,ranges.start_date,ranges.end_date,0
FROM tmp_v2_holiday_ranges ranges
WHERE NOT EXISTS (
  SELECT 1 FROM course_holiday existing
  WHERE existing.group_project_id=ranges.group_project_id
    AND existing.start_date=ranges.start_date AND existing.end_date=ranges.end_date
    AND existing.is_deleted=0
);

INSERT IGNORE INTO group_course_model_migration_map (mapping_type,source_id,scope_key,target_id)
SELECT 'HOLIDAY',source.source_holiday_id,
       CONCAT(source.group_project_id,':',target.id),target.id
FROM tmp_v2_holiday_source source
JOIN course_holiday target ON target.group_project_id=source.group_project_id
  AND target.start_date<=source.end_date AND target.end_date>=source.start_date
  AND target.is_deleted=0;

INSERT IGNORE INTO group_course_model_holiday_occurrence_audit
  (occurrence_id,source_holiday_id,target_holiday_id)
SELECT occurrence.id,legacy.id,target.id
FROM lesson_occurrence occurrence
JOIN class_group actual_class ON actual_class.id=occurrence.class_group_id
JOIN course_holiday legacy ON legacy.id=occurrence.course_holiday_id
  AND legacy.group_project_id IS NULL
JOIN course_holiday target ON target.group_project_id=actual_class.group_project_id
  AND target.start_date<=DATE(occurrence.planned_start_at)
  AND target.end_date>=DATE(occurrence.planned_start_at)
  AND target.is_deleted=0
JOIN tmp_v2_holiday_source source ON source.source_holiday_id=legacy.id
  AND source.group_project_id=actual_class.group_project_id
WHERE occurrence.is_deleted=0 AND actual_class.group_project_id IS NOT NULL;

UPDATE lesson_occurrence occurrence
JOIN class_group actual_class ON actual_class.id=occurrence.class_group_id
JOIN course_holiday legacy ON legacy.id=occurrence.course_holiday_id
  AND legacy.group_project_id IS NULL
JOIN course_holiday target ON target.group_project_id=actual_class.group_project_id
  AND target.start_date<=DATE(occurrence.planned_start_at)
  AND target.end_date>=DATE(occurrence.planned_start_at)
  AND target.is_deleted=0
SET occurrence.course_holiday_id=target.id
WHERE occurrence.is_deleted=0 AND actual_class.group_project_id IS NOT NULL;

UPDATE course_holiday legacy
JOIN (SELECT DISTINCT source_holiday_id FROM tmp_v2_holiday_source) mapped
  ON mapped.source_holiday_id=legacy.id
SET legacy.is_deleted=1,legacy.deleted_at=NOW(3)
WHERE legacy.is_deleted=0 AND legacy.group_project_id IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM lesson_occurrence occurrence
    WHERE occurrence.course_holiday_id=legacy.id AND occurrence.is_deleted=0
  );

COMMIT;

SELECT COUNT(*) AS remaining_unlinked_group_lesson_records
FROM lesson_record record_row
JOIN lesson_occurrence occurrence ON occurrence.id=record_row.occurrence_id
JOIN class_group actual_class ON actual_class.id=occurrence.class_group_id
JOIN course_product product ON product.id=actual_class.product_id AND product.teaching_mode='GROUP'
WHERE record_row.is_deleted=0 AND actual_class.group_project_id IS NOT NULL
  AND record_row.class_enrollment_id IS NULL;

SELECT COUNT(*) AS active_unmigrated_group_holiday_occurrences
FROM lesson_occurrence occurrence
JOIN class_group actual_class ON actual_class.id=occurrence.class_group_id
JOIN course_product product ON product.id=actual_class.product_id AND product.teaching_mode='GROUP'
JOIN course_holiday holiday ON holiday.id=occurrence.course_holiday_id
WHERE occurrence.is_deleted=0 AND actual_class.group_project_id IS NOT NULL
  AND holiday.is_deleted=0 AND holiday.group_project_id IS NULL;

DROP TEMPORARY TABLE IF EXISTS `tmp_v2_holiday_ranges`;
DROP TEMPORARY TABLE IF EXISTS `tmp_v2_holiday_days`;
DROP TEMPORARY TABLE IF EXISTS `tmp_v2_holiday_source`;
DROP TEMPORARY TABLE IF EXISTS `tmp_v2_lesson_match`;
DROP TEMPORARY TABLE IF EXISTS `tmp_v2_lesson_candidate`;
