-- Retire legacy GROUP price options after all active enrollment data is linked to V2.
-- Run only against the isolated local class_deduction_system_v2 database.
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


CREATE TABLE IF NOT EXISTS `group_course_model_option_retirement_audit` (
  `option_id` bigint NOT NULL,
  `course_product_id` bigint DEFAULT NULL,
  `original_status` varchar(32) NOT NULL,
  `retirement_reason` varchar(128) NOT NULL,
  `retired_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`option_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Reversible audit for legacy group price options retired in DBv2';

DROP PROCEDURE IF EXISTS `assert_group_course_v2_legacy_options_ready`;
DELIMITER $$
CREATE PROCEDURE `assert_group_course_v2_legacy_options_ready`()
BEGIN
  DECLARE bad_count bigint DEFAULT 0;

  SELECT COUNT(*) INTO bad_count
  FROM enrollment_order_item item
  JOIN course_product product ON product.id=item.course_product_id
  WHERE product.teaching_mode='GROUP'
    AND item.is_deleted=0
    AND (item.group_project_id IS NULL OR item.group_registration_plan_id IS NULL);
  IF bad_count > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cannot retire legacy group options: a non-deleted group order item is missing its V2 project or plan.';
  END IF;

  SELECT COUNT(*) INTO bad_count
  FROM class_group actual_class
  JOIN course_product product ON product.id=actual_class.product_id
  WHERE product.teaching_mode='GROUP'
    AND actual_class.is_deleted=0
    AND actual_class.group_project_id IS NULL;
  IF bad_count > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cannot retire legacy group options: an active group class is missing its V2 project.';
  END IF;
END$$
DELIMITER ;

CALL `assert_group_course_v2_legacy_options_ready`();
DROP PROCEDURE `assert_group_course_v2_legacy_options_ready`;

START TRANSACTION;

INSERT IGNORE INTO `group_course_model_option_retirement_audit`
  (`option_id`,`course_product_id`,`original_status`,`retirement_reason`)
SELECT option_row.id,option_row.course_product_id,option_row.status,
       'SUPERSEDED_BY_GROUP_COURSE_MODEL_V2'
FROM course_price_option option_row
JOIN course_product product ON product.id=option_row.course_product_id
WHERE product.teaching_mode='GROUP'
  AND option_row.is_deleted=0
  AND option_row.status='ACTIVE';

UPDATE course_price_option option_row
JOIN course_product product ON product.id=option_row.course_product_id
SET option_row.status='INACTIVE'
WHERE product.teaching_mode='GROUP'
  AND option_row.is_deleted=0
  AND option_row.status='ACTIVE';

COMMIT;

SELECT COUNT(*) AS remaining_active_group_price_options
FROM course_price_option option_row
JOIN course_product product ON product.id=option_row.course_product_id
WHERE product.teaching_mode='GROUP'
  AND option_row.is_deleted=0
  AND option_row.status='ACTIVE';
