-- Preload the course structure explicitly specified in the customer PPT.
--
-- The PPT does not contain approved prices. Price rows are ACTIVE so the
-- registration desk can select the customer-approved matrix immediately, but
-- they remain $0.00. The registration form already blocks a $0 line unless the
-- staff enters a manual price and reason, preventing accidental free orders.
--
-- Safe to rerun: product and option codes are stable and existing rows are
-- never overwritten. Existing PPT rows are normalized at the end of the script.

SET @drop_course_price_amount_check = (
  SELECT IF(
    EXISTS (
      SELECT 1
      FROM information_schema.check_constraints
      WHERE constraint_schema = DATABASE()
        AND constraint_name = 'chk_course_price_option_amount'
    ),
    'ALTER TABLE `course_price_option` DROP CHECK `chk_course_price_option_amount`',
    'SELECT 1'
  )
);
PREPARE stmt FROM @drop_course_price_amount_check;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_course_price_amount_check = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.check_constraints
      WHERE constraint_schema = DATABASE()
        AND constraint_name = 'chk_course_price_option_amount'
    ),
    'ALTER TABLE `course_price_option` ADD CONSTRAINT `chk_course_price_option_amount` CHECK (`price_amount` >= 0)',
    'SELECT 1'
  )
);
PREPARE stmt FROM @add_course_price_amount_check;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO `course_product` (
  `id`, `product_code`, `product_name`, `course_type`, `register_class`,
  `teaching_mode`, `default_total_units`, `default_lesson_units`,
  `list_price`, `status`, `notes`
)
SELECT UUID_SHORT(), seed.product_code, seed.product_name, seed.course_type, seed.register_class,
       seed.teaching_mode, seed.default_total_units, seed.default_lesson_units,
       0.00, 'ACTIVE', seed.notes
FROM (
  SELECT 'PPT-AFTER-SCHOOL' AS product_code, 'After School' AS product_name,
         'AFTER_SCHOOL' AS course_type, 'After School' AS register_class,
         'GROUP' AS teaching_mode, NULL AS default_total_units, NULL AS default_lesson_units,
         'PPT template: enter approved price when available; $0 rows require manual price during registration.' AS notes
  UNION ALL
  SELECT 'PPT-SATURDAY', 'Saturday Class', 'SATURDAY', 'Saturday Class',
         'GROUP', NULL, NULL,
         'PPT template: enter approved price when available; $0 rows require manual price during registration.'
  UNION ALL
  SELECT 'PPT-SUMMER', 'Summer Class', 'SUMMER', 'Summer Class',
         'GROUP', NULL, NULL,
         'PPT template: enter approved price when available; $0 rows require manual price during registration.'
  UNION ALL
  SELECT 'PPT-SPRINT', 'Sprint Class', 'SPRINT', 'Sprint Class',
         'GROUP', NULL, NULL,
         'The PPT names Sprint Class but does not define its grade, period, or time matrix.'
  UNION ALL
  SELECT 'PPT-ONE-ON-ONE', '1-on-1', 'ONE_ON_ONE', '1-on-1',
         'ONE_ON_ONE', 80, 4,
         'PPT template: 40/60/120-hour price rows. Default lesson length is 2 hours.'
) AS seed
WHERE NOT EXISTS (
  SELECT 1
  FROM `course_product` existing
  WHERE existing.`product_code` = seed.product_code
);

-- After School: G1-G5, G6-G8, ESL; 2/6/10 months; Monday-Friday, 3pm-6pm.
INSERT INTO `course_price_option` (
  `id`, `option_code`, `course_type`, `register_class`, `grade_band`,
  `period_label`, `duration_label`, `time_slot_label`, `detail_text`,
  `one_on_one_hours`, `price_amount`, `currency_code`, `course_product_id`,
  `class_id`, `sort_order`, `status`, `notes`
)
SELECT UUID_SHORT(),
       CONCAT('PPT_AS_', REPLACE(grades.grade_band, '-', '_'), '_', durations.month_count, 'M'),
       'AFTER_SCHOOL', 'After School', grades.grade_band,
       CONCAT(durations.month_count, ' months'),
       CONCAT(durations.month_count, ' months'),
       'Mon-Fri 3pm-6pm',
       CONCAT(grades.grade_band, ' | ', durations.month_count, ' months | Mon-Fri 3pm-6pm'),
       NULL, 0.00, 'USD', product.id, NULL,
       grades.sort_order + durations.sort_order, 'ACTIVE',
       'PPT template. $0 rows require manual price and reason during registration.'
FROM `course_product` product
JOIN (
  SELECT 'G1-G5' AS grade_band, 100 AS sort_order
  UNION ALL SELECT 'G6-G8', 200
  UNION ALL SELECT 'ESL', 300
) AS grades
JOIN (
  SELECT 2 AS month_count, 1 AS sort_order
  UNION ALL SELECT 6, 2
  UNION ALL SELECT 10, 3
) AS durations
WHERE product.`product_code` = 'PPT-AFTER-SCHOOL'
  AND NOT EXISTS (
    SELECT 1 FROM `course_price_option` existing
    WHERE existing.`option_code` = CONCAT('PPT_AS_', REPLACE(grades.grade_band, '-', '_'), '_', durations.month_count, 'M')
  );

-- Saturday Class: five levels; Fall 13 weeks, Spring 20 weeks, Full year 33 weeks;
-- morning half day, afternoon half day, and full day.
INSERT INTO `course_price_option` (
  `id`, `option_code`, `course_type`, `register_class`, `grade_band`,
  `period_label`, `duration_label`, `time_slot_label`, `detail_text`,
  `one_on_one_hours`, `price_amount`, `currency_code`, `course_product_id`,
  `class_id`, `sort_order`, `status`, `notes`
)
SELECT UUID_SHORT(),
       CONCAT('PPT_SAT_', REPLACE(grades.grade_band, '-', '_'), '_', periods.period_code, '_', slots.slot_code),
       'SATURDAY', 'Saturday Class', grades.grade_band,
       periods.period_label, periods.duration_label, slots.time_slot_label,
       CONCAT(grades.grade_band, ' | ', periods.period_label, ' ', periods.duration_label, ' | ', slots.time_slot_label),
       NULL, 0.00, 'USD', product.id, NULL,
       grades.sort_order + periods.sort_order + slots.sort_order, 'ACTIVE',
       'PPT template. $0 rows require manual price and reason during registration.'
FROM `course_product` product
JOIN (
  SELECT 'G1-2' AS grade_band, 100 AS sort_order
  UNION ALL SELECT 'G3-G6', 200
  UNION ALL SELECT 'SHSAT', 300
  UNION ALL SELECT 'PSAT', 400
  UNION ALL SELECT 'SAT', 500
) AS grades
JOIN (
  SELECT 'FALL' AS period_code, 'Fall' AS period_label, '13 weeks' AS duration_label, 10 AS sort_order
  UNION ALL SELECT 'SPRING', 'Spring', '20 weeks', 20
  UNION ALL SELECT 'FULL_YEAR', 'Full year', '33 weeks', 30
) AS periods
JOIN (
  SELECT 'AM' AS slot_code, 'Sat 9am-12pm' AS time_slot_label, 1 AS sort_order
  UNION ALL SELECT 'PM', 'Sat 1pm-4pm', 2
  UNION ALL SELECT 'FULL_DAY', 'Sat 9am-4pm', 3
) AS slots
WHERE product.`product_code` = 'PPT-SATURDAY'
  AND NOT EXISTS (
    SELECT 1 FROM `course_price_option` existing
    WHERE existing.`option_code` = CONCAT('PPT_SAT_', REPLACE(grades.grade_band, '-', '_'), '_', periods.period_code, '_', slots.slot_code)
  );

-- Summer Class: five levels; 7/6-8/21, seven weeks; morning half day,
-- afternoon half day, and full day.
INSERT INTO `course_price_option` (
  `id`, `option_code`, `course_type`, `register_class`, `grade_band`,
  `period_label`, `duration_label`, `time_slot_label`, `detail_text`,
  `one_on_one_hours`, `price_amount`, `currency_code`, `course_product_id`,
  `class_id`, `sort_order`, `status`, `notes`
)
SELECT UUID_SHORT(),
       CONCAT('PPT_SUMMER_', REPLACE(grades.grade_band, '-', '_'), '_', slots.slot_code),
       'SUMMER', 'Summer Class', grades.grade_band,
       '7/6-8/21', '7 weeks', slots.time_slot_label,
       CONCAT(grades.grade_band, ' | 7/6-8/21 (7 weeks) | ', slots.time_slot_label),
       NULL, 0.00, 'USD', product.id, NULL,
       grades.sort_order + slots.sort_order, 'ACTIVE',
       'PPT template. $0 rows require manual price and reason during registration.'
FROM `course_product` product
JOIN (
  SELECT 'G1-G6' AS grade_band, 100 AS sort_order
  UNION ALL SELECT 'SHSAT', 200
  UNION ALL SELECT 'PSAT', 300
  UNION ALL SELECT 'SAT', 400
  UNION ALL SELECT 'ESL', 500
) AS grades
JOIN (
  SELECT 'AM' AS slot_code, '9am-12pm' AS time_slot_label, 1 AS sort_order
  UNION ALL SELECT 'PM', '1pm-4pm', 2
  UNION ALL SELECT 'FULL_DAY', '9am-4pm', 3
) AS slots
WHERE product.`product_code` = 'PPT-SUMMER'
  AND NOT EXISTS (
    SELECT 1 FROM `course_price_option` existing
    WHERE existing.`option_code` = CONCAT('PPT_SUMMER_', REPLACE(grades.grade_band, '-', '_'), '_', slots.slot_code)
  );

-- One-on-one: four levels and the three package sizes explicitly listed in the PPT.
INSERT INTO `course_price_option` (
  `id`, `option_code`, `course_type`, `register_class`, `grade_band`,
  `period_label`, `duration_label`, `time_slot_label`, `detail_text`,
  `one_on_one_hours`, `price_amount`, `currency_code`, `course_product_id`,
  `class_id`, `sort_order`, `status`, `notes`
)
SELECT UUID_SHORT(),
       CONCAT('PPT_O2O_', REPLACE(grades.grade_band, '-', '_'), '_', packages.hours, 'H'),
       'ONE_ON_ONE', '1-on-1', grades.grade_band,
       CONCAT(packages.hours, ' hours'), CONCAT(packages.hours, ' hours'), NULL,
       CONCAT(grades.grade_band, ' | ', packages.hours, ' hours'),
       packages.hours, 0.00, 'USD', product.id, NULL,
       grades.sort_order + packages.sort_order, 'ACTIVE',
       'PPT template. $0 rows require manual price and reason during registration.'
FROM `course_product` product
JOIN (
  SELECT 'G1-G5' AS grade_band, 100 AS sort_order
  UNION ALL SELECT 'G6-G8', 200
  UNION ALL SELECT 'G9-G12', 300
  UNION ALL SELECT 'AP', 400
) AS grades
JOIN (
  SELECT 40 AS hours, 1 AS sort_order
  UNION ALL SELECT 60, 2
  UNION ALL SELECT 120, 3
) AS packages
WHERE product.`product_code` = 'PPT-ONE-ON-ONE'
  AND NOT EXISTS (
    SELECT 1 FROM `course_price_option` existing
    WHERE existing.`option_code` = CONCAT('PPT_O2O_', REPLACE(grades.grade_band, '-', '_'), '_', packages.hours, 'H')
  );

-- Make the PPT group-course rows usable from Registration by binding each row
-- to a generated Grade Class. Teacher and classroom stay empty because the
-- customer explicitly does not want classroom assignment during registration.
INSERT INTO `class_group` (
  `id`, `class_name`, `description`, `teacher_id`, `classroom_id`, `list_price`,
  `schedule_desc`, `status`, `product_id`, `grade_band`, `billing_type`,
  `start_date`, `total_sessions`, `start_month`, `end_month`
)
SELECT UUID_SHORT(),
       CONCAT(product.`product_name`, ' · ', option_row.`detail_text`),
       CONCAT('PPT_MATRIX:', option_row.`option_code`),
       NULL,
       NULL,
       0.00,
       option_row.`time_slot_label`,
       'ACTIVE',
       option_row.`course_product_id`,
       option_row.`grade_band`,
       CASE WHEN option_row.`course_type` = 'AFTER_SCHOOL' THEN 'MONTHLY' ELSE 'PER_SESSION' END,
       CASE
         WHEN option_row.`course_type` = 'SATURDAY' THEN '2026-09-01'
         WHEN option_row.`course_type` = 'SUMMER' THEN '2026-07-06'
         ELSE NULL
       END,
       CASE
         WHEN option_row.`duration_label` LIKE '13%' THEN 13
         WHEN option_row.`duration_label` LIKE '20%' THEN 20
         WHEN option_row.`duration_label` LIKE '33%' THEN 33
         WHEN option_row.`course_type` = 'SUMMER' THEN 35
         ELSE NULL
       END,
       CASE WHEN option_row.`course_type` = 'AFTER_SCHOOL' THEN '2026-01-01' ELSE NULL END,
       CASE WHEN option_row.`course_type` = 'AFTER_SCHOOL' THEN '2035-12-01' ELSE NULL END
FROM `course_price_option` option_row
JOIN `course_product` product ON product.`id` = option_row.`course_product_id`
WHERE option_row.`option_code` LIKE 'PPT_%'
  AND option_row.`course_type` <> 'ONE_ON_ONE'
  AND option_row.`class_id` IS NULL
  AND option_row.`is_deleted` = 0
  AND NOT EXISTS (
    SELECT 1
    FROM `class_group` existing
    WHERE existing.`description` = CONCAT('PPT_MATRIX:', option_row.`option_code`)
      AND existing.`is_deleted` = 0
  );

UPDATE `course_price_option` option_row
JOIN `class_group` class_group
  ON class_group.`description` = CONCAT('PPT_MATRIX:', option_row.`option_code`)
SET option_row.`class_id` = class_group.`id`
WHERE option_row.`option_code` LIKE 'PPT_%'
  AND option_row.`course_type` <> 'ONE_ON_ONE'
  AND option_row.`class_id` IS NULL
  AND option_row.`is_deleted` = 0
  AND class_group.`is_deleted` = 0;

-- Seed the timetable implied by the PPT. These slots make grade classes
-- schedulable without assigning a room during registration.
INSERT INTO `class_group_schedule_slot` (`id`, `class_group_id`, `weekday`, `start_time`, `end_time`)
SELECT UUID_SHORT(), class_group.`id`, weekdays.`weekday`, '15:00:00', '18:00:00'
FROM `course_price_option` option_row
JOIN `class_group` class_group ON class_group.`id` = option_row.`class_id`
JOIN (
  SELECT 1 AS `weekday` UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
) weekdays
WHERE option_row.`option_code` LIKE 'PPT_AS_%'
  AND NOT EXISTS (
    SELECT 1 FROM `class_group_schedule_slot` existing
    WHERE existing.`class_group_id` = class_group.`id`
      AND existing.`is_deleted` = 0
  );

INSERT INTO `class_group_schedule_slot` (`id`, `class_group_id`, `weekday`, `start_time`, `end_time`)
SELECT UUID_SHORT(),
       class_group.`id`,
       6,
       CASE
         WHEN option_row.`time_slot_label` LIKE '%1pm-4pm%' THEN '13:00:00'
         ELSE '09:00:00'
       END,
       CASE
         WHEN option_row.`time_slot_label` LIKE '%9am-4pm%' THEN '16:00:00'
         WHEN option_row.`time_slot_label` LIKE '%1pm-4pm%' THEN '16:00:00'
         ELSE '12:00:00'
       END
FROM `course_price_option` option_row
JOIN `class_group` class_group ON class_group.`id` = option_row.`class_id`
WHERE option_row.`option_code` LIKE 'PPT_SAT_%'
  AND NOT EXISTS (
    SELECT 1 FROM `class_group_schedule_slot` existing
    WHERE existing.`class_group_id` = class_group.`id`
      AND existing.`is_deleted` = 0
  );

INSERT INTO `class_group_schedule_slot` (`id`, `class_group_id`, `weekday`, `start_time`, `end_time`)
SELECT UUID_SHORT(),
       class_group.`id`,
       weekdays.`weekday`,
       CASE
         WHEN option_row.`time_slot_label` LIKE '%1pm-4pm%' THEN '13:00:00'
         ELSE '09:00:00'
       END,
       CASE
         WHEN option_row.`time_slot_label` LIKE '%9am-4pm%' THEN '16:00:00'
         WHEN option_row.`time_slot_label` LIKE '%1pm-4pm%' THEN '16:00:00'
         ELSE '12:00:00'
       END
FROM `course_price_option` option_row
JOIN `class_group` class_group ON class_group.`id` = option_row.`class_id`
JOIN (
  SELECT 1 AS `weekday` UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
) weekdays
WHERE option_row.`option_code` LIKE 'PPT_SUMMER_%'
  AND NOT EXISTS (
    SELECT 1 FROM `class_group_schedule_slot` existing
    WHERE existing.`class_group_id` = class_group.`id`
      AND existing.`is_deleted` = 0
  );

UPDATE `course_price_option`
SET `status` = 'ACTIVE',
    `notes` = 'PPT template. $0 rows require manual price and reason during registration.'
WHERE `option_code` LIKE 'PPT_%'
  AND `is_deleted` = 0;

-- Verification query:
-- SELECT course_type, status, COUNT(*)
-- FROM course_price_option
-- WHERE option_code LIKE 'PPT_%' AND is_deleted = 0
-- GROUP BY course_type, status
-- ORDER BY course_type, status;
