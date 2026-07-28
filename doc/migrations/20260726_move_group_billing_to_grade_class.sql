-- A Course Product is a catalog item. A Grade Class owns the actual class run:
-- teaching resources, weekly schedule, and its enrollment/billing window.
ALTER TABLE `class_group`
  ADD COLUMN `billing_type` varchar(32) NULL COMMENT 'PER_SESSION or MONTHLY' AFTER `grade_band`,
  ADD COLUMN `start_date` date NULL COMMENT 'First date for a per-session grade class' AFTER `billing_type`,
  ADD COLUMN `total_sessions` int NULL COMMENT 'Total sessions for a per-session grade class' AFTER `start_date`,
  ADD COLUMN `start_month` date NULL COMMENT 'First month for a monthly grade class' AFTER `total_sessions`,
  ADD COLUMN `end_month` date NULL COMMENT 'Last month for a monthly grade class' AFTER `start_month`;

-- Preserve existing active product configurations while moving future reads to
-- the grade class. Each existing class inherits the fields of its product once.
UPDATE `class_group` cg
JOIN `course_product` cp ON cp.id = cg.product_id
SET cg.billing_type = cp.billing_type,
    cg.start_date = cp.start_date,
    cg.total_sessions = cp.total_sessions,
    cg.start_month = cp.start_month,
    cg.end_month = cp.end_month
WHERE cg.billing_type IS NULL;

-- Link legacy price rows only when the product has exactly one existing grade
-- class. Ambiguous products remain untouched for an administrator to configure
-- explicitly in Price Matrix; migration must never guess a student's grade.
UPDATE `course_price_option` cpo
JOIN (
  SELECT product_id, MIN(id) AS class_id
  FROM `class_group`
  WHERE is_deleted = 0
  GROUP BY product_id
  HAVING COUNT(*) = 1
) cg ON cg.product_id = cpo.course_product_id
SET cpo.class_id = cg.class_id
WHERE cpo.course_type <> 'ONE_ON_ONE'
  AND cpo.class_id IS NULL;

-- A group price row only prices its linked Grade Class. Grade/run/schedule
-- labels are derived from class_group and class_group_schedule_slot after it
-- has a reliable class link. Unlinked legacy rows retain their data as a
-- migration aid and cannot be used for new registrations.
UPDATE `course_price_option`
SET grade_band = NULL,
    period_label = NULL,
    duration_label = NULL,
    time_slot_label = NULL
WHERE course_type <> 'ONE_ON_ONE'
  AND class_id IS NOT NULL;
