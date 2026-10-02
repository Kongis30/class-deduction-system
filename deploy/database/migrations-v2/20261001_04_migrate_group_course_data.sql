-- Convert existing local GROUP registrations to project + shared actual-class data.
-- The matching rollback snapshot must exist before running this file.
-- This file is intentionally restricted to the isolated local DBv2 clone.
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


DROP PROCEDURE IF EXISTS `assert_group_course_v2_migration_ready`;

CREATE TEMPORARY TABLE `tmp_group_product` (
  `product_id` bigint NOT NULL PRIMARY KEY,
  `product_name` varchar(128) NOT NULL,
  `course_type` varchar(32) DEFAULT NULL
) ENGINE=InnoDB;

INSERT INTO `tmp_group_product` (`product_id`,`product_name`,`course_type`)
SELECT p.id,p.product_name,p.course_type
FROM course_product p
WHERE p.teaching_mode='GROUP'
  AND (
    EXISTS (SELECT 1 FROM class_group c WHERE c.product_id=p.id AND c.is_deleted=0)
    OR EXISTS (SELECT 1 FROM enrollment_order_item i WHERE i.course_product_id=p.id AND i.is_deleted=0)
    OR EXISTS (
      SELECT 1 FROM class_group c
      JOIN lesson_occurrence occurrence ON occurrence.class_group_id=c.id AND occurrence.is_deleted=0
      JOIN lesson_record record_row ON record_row.occurrence_id=occurrence.id AND record_row.is_deleted=0
      WHERE c.product_id=p.id
    )
  );

CREATE TEMPORARY TABLE `tmp_project_seed` (
  `product_id` bigint NOT NULL,
  `project_code` varchar(64) NOT NULL,
  `project_name` varchar(128) NOT NULL,
  `display_order` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`product_id`,`project_code`)
) ENGINE=InnoDB;

-- If a prior successful run already created projects, use those stable keys on rerun.
INSERT INTO `tmp_project_seed` (`product_id`,`project_code`,`project_name`,`display_order`)
SELECT p.id,p.project_code,p.project_name,p.display_order
FROM group_course_project p
JOIN tmp_group_product gp ON gp.product_id=p.course_product_id
WHERE p.is_deleted=0;

-- Saturday seasonal rows encode registration choices, while period_label encodes curriculum.
INSERT IGNORE INTO `tmp_project_seed` (`product_id`,`project_code`,`project_name`,`display_order`)
SELECT DISTINCT gp.product_id,'STATE_ESL','G3-6 State Tests / ESL Newcomers',10
FROM tmp_group_product gp
JOIN class_group c ON c.product_id=gp.product_id AND c.is_deleted=0
WHERE c.grade_band IN ('全年班','秋季班','春季班');

INSERT IGNORE INTO `tmp_project_seed` (`product_id`,`project_code`,`project_name`,`display_order`)
SELECT DISTINCT gp.product_id,'STANDARDIZED_TESTS','SHSAT / PSAT / SAT',20
FROM tmp_group_product gp
JOIN class_group c ON c.product_id=gp.product_id AND c.is_deleted=0
WHERE c.grade_band IN ('全年班','秋季班','春季班');

-- After-school grade bands define the projects; class names retain the actual grade.
INSERT IGNORE INTO `tmp_project_seed` (`product_id`,`project_code`,`project_name`,`display_order`)
SELECT DISTINCT gp.product_id,c.grade_band,c.grade_band,
       CASE c.grade_band WHEN 'ESL' THEN 10 WHEN 'G1-G5' THEN 20 WHEN 'G6-G8' THEN 30 ELSE 50 END
FROM tmp_group_product gp
JOIN class_group c ON c.product_id=gp.product_id AND c.is_deleted=0
WHERE c.start_month IS NOT NULL
  AND c.actual_class IN ('ESL','G1','G2','G3','G4','G5','G6','G7','G8')
  AND c.grade_band IN ('ESL','G1-G5','G6-G8');

-- Crash and short-term test products have one teaching project per product.
INSERT IGNORE INTO `tmp_project_seed` (`product_id`,`project_code`,`project_name`,`display_order`)
SELECT gp.product_id,'MAIN',gp.product_name,10
FROM tmp_group_product gp
WHERE NOT EXISTS (
  SELECT 1 FROM class_group c
  WHERE c.product_id=gp.product_id AND c.is_deleted=0
    AND (
      c.grade_band IN ('全年班','秋季班','春季班')
      OR (c.start_month IS NOT NULL
          AND c.actual_class IN ('ESL','G1','G2','G3','G4','G5','G6','G7','G8')
          AND c.grade_band IN ('ESL','G1-G5','G6-G8'))
    )
);

CREATE TEMPORARY TABLE `tmp_class_map` (
  `source_class_id` bigint NOT NULL PRIMARY KEY,
  `target_class_id` bigint DEFAULT NULL,
  `product_id` bigint NOT NULL,
  `project_code` varchar(64) NOT NULL,
  `archive_source` tinyint(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB;

INSERT INTO `tmp_class_map` (`source_class_id`,`target_class_id`,`product_id`,`project_code`,`archive_source`)
SELECT source.id,
       CASE WHEN source.grade_band IN ('秋季班','春季班') THEN canonical.id ELSE source.id END,
       source.product_id,
       CASE
         WHEN EXISTS (SELECT 1 FROM class_group seasonal
                      WHERE seasonal.product_id=source.product_id AND seasonal.is_deleted=0
                        AND seasonal.grade_band IN ('全年班','秋季班','春季班'))
           THEN CASE WHEN UPPER(COALESCE(source.actual_class,source.class_name)) IN ('G3','G4','G5','G6') THEN 'STATE_ESL' ELSE 'STANDARDIZED_TESTS' END
         WHEN source.start_month IS NOT NULL AND source.actual_class IN ('ESL','G1','G2','G3','G4','G5','G6','G7','G8')
              AND source.grade_band IN ('ESL','G1-G5','G6-G8')
           THEN source.grade_band
         ELSE 'MAIN'
       END,
       source.grade_band IN ('秋季班','春季班')
FROM class_group source
JOIN tmp_group_product gp ON gp.product_id=source.product_id
LEFT JOIN class_group canonical
       ON source.grade_band IN ('秋季班','春季班')
      AND canonical.product_id=source.product_id
      AND canonical.grade_band='全年班'
      AND canonical.actual_class=source.actual_class
      AND canonical.campus_id <=> source.campus_id
      AND canonical.is_deleted=0
WHERE source.is_deleted=0;

-- Keep a second mapping copy for MySQL's temporary-table self-join restriction.
CREATE TEMPORARY TABLE `tmp_class_map_compare` AS
SELECT * FROM tmp_class_map;

-- 旧确认课次没有权益外键。按唯一实际班、学生和已排课日期恢复权益归属，重复/缺失匹配会在预检阶段阻断。
CREATE TEMPORARY TABLE `tmp_lesson_entitlement_candidate` AS
SELECT DISTINCT lr.id lesson_record_id,e.id class_enrollment_id
FROM lesson_record lr
JOIN lesson_occurrence occurrence ON occurrence.id=lr.occurrence_id AND occurrence.is_deleted=0
JOIN class_group lesson_class ON lesson_class.id=occurrence.class_group_id
JOIN tmp_group_product product ON product.product_id=lesson_class.product_id
JOIN tmp_class_map lesson_class_map ON lesson_class_map.source_class_id=lesson_class.id
JOIN class_enrollment e ON e.student_id=lr.student_id AND e.is_deleted=0
JOIN tmp_class_map_compare enrollment_class_map ON enrollment_class_map.source_class_id=e.class_id
  AND enrollment_class_map.target_class_id=lesson_class_map.target_class_id
WHERE lr.is_deleted=0
  AND DATE(COALESCE(lr.planned_start_at,occurrence.planned_start_at)) BETWEEN e.enrollment_date AND e.expiry_date;

CREATE TEMPORARY TABLE `tmp_lesson_entitlement_match` AS
SELECT lesson_record_id,MIN(class_enrollment_id) class_enrollment_id,
       COUNT(DISTINCT class_enrollment_id) candidate_count
FROM tmp_lesson_entitlement_candidate
GROUP BY lesson_record_id;

-- 假期按教学项目迁移。产品范围假期映射到对应项目；无产品旧假期从其关联班课课次推导项目。
CREATE TEMPORARY TABLE `tmp_holiday_project_scope` (
  `source_holiday_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `project_code` varchar(64) NOT NULL,
  `holiday_name` varchar(128) NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  PRIMARY KEY (`source_holiday_id`,`product_id`,`project_code`)
) ENGINE=InnoDB;

INSERT IGNORE INTO `tmp_holiday_project_scope`
  (`source_holiday_id`,`product_id`,`project_code`,`holiday_name`,`start_date`,`end_date`)
SELECT h.id,seed.product_id,seed.project_code,h.holiday_name,h.start_date,h.end_date
FROM course_holiday h
JOIN tmp_group_product gp ON gp.product_id=h.course_product_id
JOIN tmp_project_seed seed ON seed.product_id=gp.product_id
WHERE h.is_deleted=0 AND h.group_project_id IS NULL
  AND (
    seed.project_code IN ('STATE_ESL','STANDARDIZED_TESTS')
    OR seed.project_code='MAIN'
    OR h.grade_band IS NULL
    OR h.grade_band=seed.project_code
    OR (seed.project_code='G1-G5' AND h.grade_band IN ('G1','G2','G3','G4','G5'))
    OR (seed.project_code='G6-G8' AND h.grade_band IN ('G6','G7','G8'))
  );

INSERT IGNORE INTO `tmp_holiday_project_scope`
  (`source_holiday_id`,`product_id`,`project_code`,`holiday_name`,`start_date`,`end_date`)
SELECT DISTINCT h.id,class_map.product_id,class_map.project_code,h.holiday_name,h.start_date,h.end_date
FROM course_holiday h
JOIN lesson_occurrence occurrence ON occurrence.course_holiday_id=h.id AND occurrence.is_deleted=0
JOIN class_group lesson_class ON lesson_class.id=occurrence.class_group_id
JOIN tmp_group_product gp ON gp.product_id=lesson_class.product_id
JOIN tmp_class_map class_map ON class_map.source_class_id=lesson_class.id
JOIN tmp_project_seed seed ON seed.product_id=class_map.product_id AND seed.project_code=class_map.project_code
WHERE h.is_deleted=0 AND h.group_project_id IS NULL AND h.course_product_id IS NULL;

CREATE TEMPORARY TABLE `tmp_option_project` (
  `option_id` bigint NOT NULL PRIMARY KEY,
  `product_id` bigint NOT NULL,
  `project_code` varchar(64) NOT NULL,
  `plan_code` varchar(64) NOT NULL,
  `plan_name` varchar(128) NOT NULL,
  `coverage_type` varchar(32) NOT NULL,
  `term_code` varchar(32) DEFAULT NULL,
  `coverage_start_date` date DEFAULT NULL,
  `coverage_end_date` date DEFAULT NULL,
  `package_unit` varchar(16) NOT NULL,
  `package_quantity` decimal(8,2) NOT NULL,
  `full_day_price` decimal(12,2) NOT NULL,
  `half_day_price` decimal(12,2) DEFAULT NULL,
  `status` varchar(24) NOT NULL,
  `display_order` int NOT NULL DEFAULT 0
) ENGINE=InnoDB;

INSERT INTO `tmp_option_project`
SELECT o.id,
       p.id,
       CASE
         WHEN EXISTS (SELECT 1 FROM class_group c WHERE c.product_id=p.id AND c.grade_band IN ('全年班','秋季班','春季班') AND c.is_deleted=0)
           THEN CASE WHEN UPPER(COALESCE(o.period_label,'')) REGEXP 'SHSAT|PSAT|SAT' THEN 'STANDARDIZED_TESTS' ELSE 'STATE_ESL' END
         WHEN EXISTS (
           SELECT 1 FROM class_group actual
           WHERE actual.product_id=p.id AND actual.is_deleted=0
             AND actual.start_month IS NOT NULL
             AND actual.actual_class IN ('ESL','G1','G2','G3','G4','G5','G6','G7','G8')
             AND actual.grade_band=o.grade_band
             AND actual.grade_band IN ('ESL','G1-G5','G6-G8')
         )
           THEN o.grade_band
         ELSE 'MAIN'
       END AS project_code,
       CASE
         WHEN EXISTS (SELECT 1 FROM class_group c WHERE c.product_id=p.id AND c.grade_band IN ('全年班','秋季班','春季班') AND c.is_deleted=0)
           THEN CASE o.grade_band WHEN '秋季班' THEN 'FALL' WHEN '春季班' THEN 'SPRING' ELSE 'FULL_YEAR' END
         WHEN UPPER(o.package_unit)='MONTH' THEN CONCAT('MONTHS_',CAST(o.package_quantity AS UNSIGNED))
         ELSE CONCAT('SESSIONS_',CAST(o.package_quantity AS UNSIGNED))
       END AS plan_code,
       CASE
         WHEN EXISTS (SELECT 1 FROM class_group c WHERE c.product_id=p.id AND c.grade_band IN ('全年班','秋季班','春季班') AND c.is_deleted=0)
           THEN CONCAT(CASE o.grade_band WHEN '秋季班' THEN 'Fall' WHEN '春季班' THEN 'Spring' ELSE 'Full year' END,' · ',CAST(o.package_quantity AS UNSIGNED),' sessions')
         WHEN UPPER(o.package_unit)='MONTH' THEN CONCAT(CAST(o.package_quantity AS UNSIGNED),' months')
         ELSE CONCAT(CAST(o.package_quantity AS UNSIGNED),' sessions')
       END AS plan_name,
       CASE
         WHEN EXISTS (SELECT 1 FROM class_group c WHERE c.product_id=p.id AND c.grade_band IN ('全年班','秋季班','春季班') AND c.is_deleted=0) THEN 'FIXED_DATES'
         WHEN UPPER(o.package_unit)='MONTH' THEN 'ROLLING_MONTHS'
         ELSE 'SESSION_COUNT'
       END AS coverage_type,
       CASE
         WHEN EXISTS (SELECT 1 FROM class_group c WHERE c.product_id=p.id AND c.grade_band IN ('全年班','秋季班','春季班') AND c.is_deleted=0)
           THEN CASE o.grade_band WHEN '秋季班' THEN 'FALL' WHEN '春季班' THEN 'SPRING' ELSE 'FULL_YEAR' END
         ELSE NULL
       END AS term_code,
       CASE WHEN o.grade_band IN ('全年班','秋季班','春季班') THEN term_dates.start_date ELSE NULL END,
       CASE WHEN o.grade_band IN ('全年班','秋季班','春季班') THEN term_dates.end_date ELSE NULL END,
       UPPER(o.package_unit),o.package_quantity,
       COALESCE(o.full_day_price,o.total_price,o.group_total_price,o.price_amount),
       o.half_day_price,
       CASE WHEN o.status='INACTIVE' THEN 'INACTIVE' ELSE 'ACTIVE' END,
       o.sort_order
FROM course_price_option o
JOIN course_product p ON p.id=o.course_product_id
JOIN tmp_group_product gp ON gp.product_id=p.id
LEFT JOIN (
  SELECT product_id,grade_band,MIN(start_date) start_date,MAX(end_date) end_date
  FROM class_group
  WHERE is_deleted=0 AND grade_band IN ('全年班','秋季班','春季班')
  GROUP BY product_id,grade_band
) term_dates ON term_dates.product_id=p.id AND term_dates.grade_band=o.grade_band
WHERE o.is_deleted=0
  AND o.package_quantity IS NOT NULL AND o.package_quantity > 0
  AND UPPER(o.package_unit) IN ('SESSION','MONTH')
  AND COALESCE(o.full_day_price,o.total_price,o.group_total_price,o.price_amount) IS NOT NULL
  AND COALESCE(o.full_day_price,o.total_price,o.group_total_price,o.price_amount) >= 0;

CREATE TEMPORARY TABLE `tmp_plan_seed` AS
SELECT p.product_id,p.project_code,p.plan_code,
       MIN(p.plan_name) plan_name,MIN(p.coverage_type) coverage_type,MIN(p.term_code) term_code,
       MIN(p.coverage_start_date) coverage_start_date,MAX(p.coverage_end_date) coverage_end_date,
       MIN(p.package_unit) package_unit,MAX(p.package_quantity) package_quantity,
       MAX(p.full_day_price) full_day_price,MAX(p.half_day_price) half_day_price,
       MIN(p.status) status,MIN(p.display_order) display_order
FROM tmp_option_project p
GROUP BY p.product_id,p.project_code,p.plan_code;

-- MySQL cannot reopen a temporary table twice within one statement.
CREATE TEMPORARY TABLE `tmp_option_project_compare` AS
SELECT * FROM tmp_option_project;

DROP PROCEDURE IF EXISTS `assert_group_course_v2_migration_ready`;
DELIMITER $$
CREATE PROCEDURE `assert_group_course_v2_migration_ready`()
BEGIN
  DECLARE bad_count bigint DEFAULT 0;
  IF DATABASE() <> 'class_deduction_system_v2' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Refusing to migrate outside class_deduction_system_v2.';
  END IF;
  IF (SELECT COUNT(*) FROM group_course_model_migration_map) > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A group-course data migration map already exists; use the rollback snapshot before retrying.';
  END IF;
  IF (SELECT COUNT(*) FROM group_course_project) > 0 OR (SELECT COUNT(*) FROM group_registration_plan) > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V2 project or plan data already exists; do not mix manual setup with the legacy migration.';
  END IF;
  SELECT COUNT(*) INTO bad_count FROM tmp_class_map WHERE target_class_id IS NULL;
  IF bad_count > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A seasonal Saturday class has no unique full-year counterpart.'; END IF;
  SELECT COUNT(*) INTO bad_count
  FROM course_holiday holiday
  JOIN tmp_group_product product ON product.product_id=holiday.course_product_id
  LEFT JOIN tmp_holiday_project_scope scope ON scope.source_holiday_id=holiday.id
  WHERE holiday.is_deleted=0 AND holiday.group_project_id IS NULL AND scope.source_holiday_id IS NULL;
  IF bad_count > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A group-course holiday has an unknown grade scope and cannot be mapped to a teaching project.'; END IF;
  SELECT COUNT(*) INTO bad_count
  FROM course_holiday holiday
  JOIN lesson_occurrence occurrence ON occurrence.course_holiday_id=holiday.id AND occurrence.is_deleted=0
  JOIN class_group lesson_class ON lesson_class.id=occurrence.class_group_id
  JOIN tmp_group_product product ON product.product_id=lesson_class.product_id
  LEFT JOIN tmp_class_map class_map ON class_map.source_class_id=lesson_class.id
  LEFT JOIN tmp_holiday_project_scope scope ON scope.source_holiday_id=holiday.id
    AND scope.product_id=lesson_class.product_id
    AND scope.project_code=class_map.project_code
  WHERE holiday.is_deleted=0 AND holiday.group_project_id IS NULL
    AND holiday.course_product_id IS NULL
    AND (class_map.source_class_id IS NULL OR scope.source_holiday_id IS NULL);
  IF bad_count > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A group-class holiday occurrence has no unique V2 teaching-project mapping.'; END IF;
  SELECT COUNT(*) INTO bad_count
  FROM lesson_record record_row
  JOIN lesson_occurrence occurrence ON occurrence.id=record_row.occurrence_id
  JOIN class_group lesson_class ON lesson_class.id=occurrence.class_group_id
  JOIN tmp_group_product product ON product.product_id=lesson_class.product_id
  LEFT JOIN tmp_lesson_entitlement_match matched ON matched.lesson_record_id=record_row.id
  WHERE record_row.is_deleted=0
    AND ((record_row.class_enrollment_id IS NULL AND COALESCE(matched.candidate_count,0)<>1)
      OR (record_row.class_enrollment_id IS NOT NULL
          AND (COALESCE(matched.candidate_count,0)<>1
            OR matched.class_enrollment_id<>record_row.class_enrollment_id)));
  IF bad_count > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A historical group lesson does not map to exactly one student entitlement; resolve the mapping before migration.'; END IF;
  SELECT COUNT(*) INTO bad_count
  FROM (
    SELECT source.id
    FROM class_group source
    JOIN tmp_class_map source_map ON source_map.source_class_id=source.id AND source_map.archive_source=1
    LEFT JOIN class_group canonical ON canonical.product_id=source.product_id
      AND canonical.grade_band='全年班'
      AND canonical.actual_class=source.actual_class
      AND canonical.campus_id <=> source.campus_id
      AND canonical.is_deleted=0
    GROUP BY source.id
    HAVING COUNT(canonical.id)<>1
  ) ambiguous_class;
  IF bad_count > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A seasonal Saturday class does not have exactly one full-year counterpart.'; END IF;
  SELECT COUNT(*) INTO bad_count
  FROM tmp_group_product gp
  JOIN enrollment_order_item i ON i.course_product_id=gp.product_id AND i.is_deleted=0
  LEFT JOIN tmp_option_project o ON o.option_id=i.course_price_option_id
  WHERE o.option_id IS NULL;
  IF bad_count > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='An active group order item cannot be matched to an active legacy registration option.'; END IF;
  SELECT COUNT(*) INTO bad_count
  FROM tmp_group_product gp
  JOIN enrollment_order_item i ON i.course_product_id=gp.product_id AND i.is_deleted=0
  LEFT JOIN tmp_class_map c ON c.source_class_id=i.class_id
  WHERE c.source_class_id IS NULL;
  IF bad_count > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='An active group order item cannot be matched to an active actual class.'; END IF;
  SELECT COUNT(*) INTO bad_count
  FROM tmp_group_product gp
  JOIN enrollment_order_item i ON i.course_product_id=gp.product_id AND i.is_deleted=0
  WHERE i.course_price_option_id IS NULL OR i.class_id IS NULL;
  IF bad_count > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A group order item is missing its legacy plan or actual class.'; END IF;
  SELECT COUNT(*) INTO bad_count
  FROM tmp_group_product gp
  JOIN class_enrollment e ON e.is_deleted=0
  JOIN class_group c ON c.id=e.class_id AND c.product_id=gp.product_id
  LEFT JOIN enrollment_order_item i ON i.id=e.enrollment_order_item_id
  LEFT JOIN tmp_option_project o ON o.option_id=i.course_price_option_id
  LEFT JOIN tmp_class_map class_map ON class_map.source_class_id=e.class_id
  WHERE e.status<>'CANCELLED'
    AND (i.id IS NULL OR o.option_id IS NULL OR class_map.source_class_id IS NULL);
  IF bad_count > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A group entitlement is missing a mappable source order item, option, or class.'; END IF;
  SELECT COUNT(*) INTO bad_count
  FROM tmp_option_project p
  WHERE p.package_unit NOT IN ('SESSION','MONTH') OR p.package_quantity <> FLOOR(p.package_quantity);
  IF bad_count > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='An in-scope legacy plan has an unsupported unit or fractional quantity.'; END IF;
  SELECT COUNT(*) INTO bad_count
  FROM tmp_option_project a JOIN tmp_option_project_compare b
    ON a.product_id=b.product_id AND a.project_code=b.project_code AND a.plan_code=b.plan_code AND a.option_id<b.option_id
  WHERE a.package_unit<>b.package_unit OR a.package_quantity<>b.package_quantity
     OR a.full_day_price<>b.full_day_price OR NOT (a.half_day_price <=> b.half_day_price)
     OR a.coverage_type<>b.coverage_type
     OR NOT (a.coverage_start_date <=> b.coverage_start_date)
     OR NOT (a.coverage_end_date <=> b.coverage_end_date);
  IF bad_count > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy rows disagree on the price or coverage of one V2 plan.'; END IF;
  SELECT COUNT(*) INTO bad_count
  FROM tmp_group_product gp
  JOIN enrollment_order_item i ON i.course_product_id=gp.product_id AND i.is_deleted=0
  JOIN tmp_option_project o ON o.option_id=i.course_price_option_id
  JOIN tmp_class_map c ON c.source_class_id=i.class_id
  WHERE c.project_code<>o.project_code;
  IF bad_count > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='An order class and its registration plan belong to different projects.'; END IF;
  SELECT COUNT(*) INTO bad_count
  FROM tmp_group_product gp
  JOIN enrollment_order_item i ON i.course_product_id=gp.product_id AND i.is_deleted=0
  JOIN tmp_option_project o ON o.option_id=i.course_price_option_id
  WHERE o.product_id<>i.course_product_id;
  IF bad_count > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='An order item product differs from the product of its registration option.'; END IF;
  SELECT COUNT(*) INTO bad_count
  FROM tmp_group_product gp
  JOIN class_enrollment e ON e.is_deleted=0
  JOIN class_group ec ON ec.id=e.class_id AND ec.product_id=gp.product_id
  JOIN enrollment_order_item i ON i.id=e.enrollment_order_item_id
  JOIN tmp_option_project o ON o.option_id=i.course_price_option_id
  JOIN tmp_class_map c ON c.source_class_id=ec.id
  WHERE e.status<>'CANCELLED' AND c.project_code<>o.project_code;
  IF bad_count > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='An entitlement class and its registration plan belong to different projects.'; END IF;
  SELECT COUNT(*) INTO bad_count
  FROM class_group source
  JOIN tmp_class_map m ON m.source_class_id=source.id AND m.archive_source=1
  WHERE EXISTS (
    SELECT 1 FROM lesson_occurrence lo
    JOIN lesson_record lr ON lr.occurrence_id=lo.id AND lr.is_deleted=0
    WHERE lo.class_group_id=source.id AND lo.is_deleted=0
  );
  IF bad_count > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A duplicate Saturday class has lesson records; it cannot be archived automatically.'; END IF;
  -- Fall/spring rows are legacy registration windows. Their generated lessons are
  -- soft-deleted below; the full-year class is the approved V2 teaching schedule.
  -- Earlier preflight blocks this archival if a seasonal class has confirmed lesson records.
  SELECT COUNT(*) INTO bad_count
  FROM class_enrollment left_entitlement
  JOIN tmp_class_map left_map ON left_map.source_class_id=left_entitlement.class_id
  JOIN class_enrollment right_entitlement
    ON right_entitlement.student_id=left_entitlement.student_id
   AND right_entitlement.id>left_entitlement.id
   AND right_entitlement.is_deleted=0 AND right_entitlement.status='ACTIVE'
  JOIN tmp_class_map_compare right_map ON right_map.source_class_id=right_entitlement.class_id
  WHERE left_entitlement.is_deleted=0 AND left_entitlement.status='ACTIVE'
    AND left_map.target_class_id=right_map.target_class_id
    AND (left_map.archive_source=1 OR right_map.archive_source=1)
    AND left_entitlement.enrollment_date<=right_entitlement.expiry_date
    AND right_entitlement.enrollment_date<=left_entitlement.expiry_date;
  IF bad_count > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A student has overlapping active entitlements that would share one V2 actual class.'; END IF;
  -- Seasonal teacher, room, and weekly-slot configurations are retained in the
  -- rollback snapshot, while the full-year class supplies the single active V2 schedule.
END$$
DELIMITER ;

CALL `assert_group_course_v2_migration_ready`();
DROP PROCEDURE `assert_group_course_v2_migration_ready`;

START TRANSACTION;

INSERT INTO group_course_project
  (id,course_product_id,project_code,project_name,description,display_order,status,is_deleted)
SELECT UUID_SHORT(),s.product_id,s.project_code,s.project_name,
       'Migrated from the legacy group-course structure.',s.display_order,
       CASE WHEN p.status='INACTIVE' THEN 'INACTIVE' ELSE 'ACTIVE' END,0
FROM tmp_project_seed s
JOIN course_product p ON p.id=s.product_id
WHERE NOT EXISTS (
  SELECT 1 FROM group_course_project existing
  WHERE existing.course_product_id=s.product_id AND existing.project_code=s.project_code AND existing.is_deleted=0
);

INSERT IGNORE INTO group_course_model_migration_map (mapping_type,source_id,scope_key,target_id)
SELECT 'PROJECT',p.course_product_id,p.project_code,p.id
FROM group_course_project p
JOIN tmp_project_seed s ON s.product_id=p.course_product_id AND s.project_code=p.project_code
WHERE p.is_deleted=0;

INSERT INTO group_registration_plan
  (id,project_id,plan_code,plan_name,coverage_type,term_code,coverage_start_date,coverage_end_date,
   package_unit,package_quantity,full_day_price,half_day_price,currency_code,display_order,status,is_deleted)
SELECT UUID_SHORT(),project_map.target_id,s.plan_code,s.plan_name,s.coverage_type,s.term_code,
       s.coverage_start_date,s.coverage_end_date,s.package_unit,s.package_quantity,
       s.full_day_price,s.half_day_price,'USD',s.display_order,s.status,0
FROM tmp_plan_seed s
JOIN group_course_model_migration_map project_map
  ON project_map.mapping_type='PROJECT' AND project_map.source_id=s.product_id AND project_map.scope_key=s.project_code
WHERE NOT EXISTS (
  SELECT 1 FROM group_registration_plan existing
  WHERE existing.project_id=project_map.target_id AND existing.plan_code=s.plan_code AND existing.is_deleted=0
);

INSERT IGNORE INTO group_course_model_migration_map (mapping_type,source_id,scope_key,target_id)
SELECT 'PLAN_OPTION',o.option_id,'PLAN',plan.id
FROM tmp_option_project o
JOIN group_course_model_migration_map project_map
  ON project_map.mapping_type='PROJECT' AND project_map.source_id=o.product_id AND project_map.scope_key=o.project_code
JOIN group_registration_plan plan ON plan.project_id=project_map.target_id AND plan.plan_code=o.plan_code AND plan.is_deleted=0;

INSERT IGNORE INTO group_course_model_migration_map (mapping_type,source_id,scope_key,target_id)
SELECT 'CLASS',source_class_id,'CLASS',target_class_id FROM tmp_class_map;

UPDATE class_group source
JOIN tmp_class_map m ON m.source_class_id=source.id
JOIN group_course_model_migration_map project_map
  ON project_map.mapping_type='PROJECT' AND project_map.source_id=m.product_id AND project_map.scope_key=m.project_code
SET source.group_project_id=project_map.target_id,
    source.grade_band=COALESCE(source.actual_class,source.class_name);

UPDATE course_price_option option_row
JOIN tmp_option_project option_map ON option_map.option_id=option_row.id
JOIN group_course_model_migration_map project_map
  ON project_map.mapping_type='PROJECT' AND project_map.source_id=option_map.product_id AND project_map.scope_key=option_map.project_code
JOIN group_course_model_migration_map plan_map
  ON plan_map.mapping_type='PLAN_OPTION' AND plan_map.source_id=option_row.id AND plan_map.scope_key='PLAN'
-- Keep the old class pointer as audit history; V2 plans no longer select or own a class.
SET option_row.group_project_id=project_map.target_id,
    option_row.group_registration_plan_id=plan_map.target_id,
    option_row.status='INACTIVE';

UPDATE enrollment_order_item item
JOIN tmp_option_project option_map ON option_map.option_id=item.course_price_option_id
JOIN group_course_model_migration_map project_map
  ON project_map.mapping_type='PROJECT' AND project_map.source_id=option_map.product_id AND project_map.scope_key=option_map.project_code
JOIN group_course_model_migration_map plan_map
  ON plan_map.mapping_type='PLAN_OPTION' AND plan_map.source_id=item.course_price_option_id AND plan_map.scope_key='PLAN'
JOIN tmp_class_map class_map ON class_map.source_class_id=item.class_id
SET item.group_project_id=project_map.target_id,
    item.group_registration_plan_id=plan_map.target_id,
    item.class_id=class_map.target_class_id;

UPDATE class_enrollment enrollment
JOIN enrollment_order_item item ON item.id=enrollment.enrollment_order_item_id
JOIN tmp_class_map class_map ON class_map.source_class_id=enrollment.class_id
SET enrollment.class_id=class_map.target_class_id,
    enrollment.group_project_id=item.group_project_id,
    enrollment.group_registration_plan_id=item.group_registration_plan_id
WHERE enrollment.is_deleted=0;

UPDATE lesson_record record_row
JOIN tmp_lesson_entitlement_match matched ON matched.lesson_record_id=record_row.id
SET record_row.class_enrollment_id=matched.class_enrollment_id
WHERE record_row.is_deleted=0 AND record_row.class_enrollment_id IS NULL
  AND matched.candidate_count=1;

CREATE TEMPORARY TABLE `tmp_holiday_source` AS
SELECT scope.source_holiday_id,project_map.target_id group_project_id,
       scope.holiday_name,scope.start_date,scope.end_date
FROM tmp_holiday_project_scope scope
JOIN group_course_model_migration_map project_map
  ON project_map.mapping_type='PROJECT' AND project_map.source_id=scope.product_id
 AND project_map.scope_key=scope.project_code;

CREATE TEMPORARY TABLE `tmp_holiday_days` AS
WITH RECURSIVE holiday_days AS (
  SELECT group_project_id,source_holiday_id,holiday_name,start_date holiday_date,end_date
  FROM tmp_holiday_source
  UNION ALL
  SELECT group_project_id,source_holiday_id,holiday_name,DATE_ADD(holiday_date,INTERVAL 1 DAY),end_date
  FROM holiday_days WHERE holiday_date<end_date
)
SELECT DISTINCT group_project_id,source_holiday_id,holiday_name,holiday_date FROM holiday_days;

CREATE TEMPORARY TABLE `tmp_holiday_ranges` AS
WITH daily AS (
  SELECT group_project_id,holiday_date,MIN(holiday_name) holiday_name
  FROM tmp_holiday_days GROUP BY group_project_id,holiday_date
), numbered AS (
  SELECT group_project_id,holiday_date,holiday_name,
         DATE_SUB(holiday_date,INTERVAL ROW_NUMBER() OVER (PARTITION BY group_project_id ORDER BY holiday_date) DAY) island
  FROM daily
)
SELECT group_project_id,MIN(holiday_date) start_date,MAX(holiday_date) end_date,MIN(holiday_name) holiday_name
FROM numbered GROUP BY group_project_id,island;

INSERT INTO course_holiday (id,group_project_id,holiday_name,start_date,end_date,is_deleted)
SELECT UUID_SHORT(),ranges.group_project_id,ranges.holiday_name,ranges.start_date,ranges.end_date,0
FROM tmp_holiday_ranges ranges
WHERE NOT EXISTS (
  SELECT 1 FROM course_holiday existing
  WHERE existing.group_project_id=ranges.group_project_id AND existing.start_date=ranges.start_date
    AND existing.end_date=ranges.end_date AND existing.is_deleted=0
);

INSERT IGNORE INTO group_course_model_migration_map (mapping_type,source_id,scope_key,target_id)
SELECT 'HOLIDAY',source.source_holiday_id,CONCAT(source.group_project_id,':',target.id),target.id
FROM tmp_holiday_source source
JOIN course_holiday target ON target.group_project_id=source.group_project_id
  AND target.start_date<=source.end_date AND target.end_date>=source.start_date AND target.is_deleted=0;

INSERT IGNORE INTO group_course_model_holiday_occurrence_audit
  (occurrence_id,source_holiday_id,target_holiday_id)
SELECT occurrence.id,legacy.id,target.id
FROM lesson_occurrence occurrence
JOIN course_holiday legacy ON legacy.id=occurrence.course_holiday_id AND legacy.group_project_id IS NULL
JOIN class_group class_row ON class_row.id=occurrence.class_group_id
JOIN course_holiday target ON target.group_project_id=class_row.group_project_id
  AND target.start_date<=DATE(occurrence.planned_start_at)
  AND target.end_date>=DATE(occurrence.planned_start_at) AND target.is_deleted=0
JOIN tmp_holiday_source source ON source.source_holiday_id=legacy.id
  AND source.group_project_id=class_row.group_project_id
WHERE occurrence.is_deleted=0 AND class_row.group_project_id IS NOT NULL;

UPDATE lesson_occurrence occurrence
JOIN class_group class_row ON class_row.id=occurrence.class_group_id
JOIN course_holiday legacy ON legacy.id=occurrence.course_holiday_id AND legacy.group_project_id IS NULL
JOIN course_holiday target ON target.group_project_id=class_row.group_project_id
  AND target.start_date<=DATE(occurrence.planned_start_at) AND target.end_date>=DATE(occurrence.planned_start_at)
  AND target.is_deleted=0
SET occurrence.course_holiday_id=target.id
WHERE occurrence.is_deleted=0 AND class_row.group_project_id IS NOT NULL;

UPDATE course_holiday legacy
JOIN (SELECT DISTINCT source_holiday_id FROM tmp_holiday_source) mapped
  ON mapped.source_holiday_id=legacy.id
SET legacy.is_deleted=1,legacy.deleted_at=NOW(3)
WHERE legacy.is_deleted=0 AND legacy.group_project_id IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM lesson_occurrence occurrence
    WHERE occurrence.course_holiday_id=legacy.id AND occurrence.is_deleted=0
  );

UPDATE lesson_occurrence occurrence
JOIN tmp_class_map class_map ON class_map.source_class_id=occurrence.class_group_id AND class_map.archive_source=1
SET occurrence.is_deleted=1,occurrence.deleted_at=NOW(3)
WHERE occurrence.is_deleted=0;

UPDATE class_group_schedule_slot slot
JOIN tmp_class_map class_map ON class_map.source_class_id=slot.class_group_id AND class_map.archive_source=1
SET slot.is_deleted=1,slot.deleted_at=NOW(3)
WHERE slot.is_deleted=0;

UPDATE class_group source
JOIN tmp_class_map class_map ON class_map.source_class_id=source.id AND class_map.archive_source=1
SET source.is_deleted=1,source.deleted_at=NOW(3),source.status='INACTIVE'
WHERE source.is_deleted=0;

COMMIT;

SELECT mapping_type,COUNT(*) mapped_rows FROM group_course_model_migration_map GROUP BY mapping_type ORDER BY mapping_type;
SELECT p.product_name,COUNT(DISTINCT project.id) projects,COUNT(DISTINCT plan.id) plans,
       COUNT(DISTINCT CASE WHEN class_row.is_deleted=0 THEN class_row.id END) active_classes,
       COUNT(DISTINCT item.id) order_items,COUNT(DISTINCT enrollment.id) enrollments
FROM tmp_group_product gp
JOIN course_product p ON p.id=gp.product_id
LEFT JOIN group_course_project project ON project.course_product_id=p.id AND project.is_deleted=0
LEFT JOIN group_registration_plan plan ON plan.project_id=project.id AND plan.is_deleted=0
LEFT JOIN class_group class_row ON class_row.product_id=p.id
LEFT JOIN enrollment_order_item item ON item.course_product_id=p.id AND item.is_deleted=0
LEFT JOIN class_enrollment enrollment ON enrollment.class_id=class_row.id AND enrollment.is_deleted=0
GROUP BY p.product_name ORDER BY p.product_name;

DROP TEMPORARY TABLE IF EXISTS tmp_holiday_ranges;
DROP TEMPORARY TABLE IF EXISTS tmp_holiday_days;
DROP TEMPORARY TABLE IF EXISTS tmp_holiday_source;
DROP TEMPORARY TABLE IF EXISTS tmp_holiday_project_scope;
DROP TEMPORARY TABLE IF EXISTS tmp_lesson_entitlement_match;
DROP TEMPORARY TABLE IF EXISTS tmp_lesson_entitlement_candidate;
DROP TEMPORARY TABLE IF EXISTS tmp_plan_seed;
DROP TEMPORARY TABLE IF EXISTS tmp_option_project_compare;
DROP TEMPORARY TABLE IF EXISTS tmp_option_project;
DROP TEMPORARY TABLE IF EXISTS tmp_class_map_compare;
DROP TEMPORARY TABLE IF EXISTS tmp_class_map;
DROP TEMPORARY TABLE IF EXISTS tmp_project_seed;
DROP TEMPORARY TABLE IF EXISTS tmp_group_product;
