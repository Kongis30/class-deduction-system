-- Preload only the course products explicitly described in the customer PPT.
--
-- Price rows are intentionally not seeded. Staff should add grade/class price
-- rows in Course Pricing Matrix after confirming the real price, billing
-- period, teacher, room, and schedule.
--
-- Safe to rerun: product codes are stable. Legacy PPT_% price rows generated
-- by older versions of this script are deactivated so they no longer appear as
-- selectable templates.

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
         'PPT class type. Add grade class price rows manually in Price Matrix.' AS notes
  UNION ALL
  SELECT 'PPT-SATURDAY', 'Saturday Class', 'SATURDAY', 'Saturday Class',
         'GROUP', NULL, NULL,
         'PPT class type. Add grade class price rows manually in Price Matrix.'
  UNION ALL
  SELECT 'PPT-SUMMER', 'Summer Class', 'SUMMER', 'Summer Class',
         'GROUP', NULL, NULL,
         'PPT class type. Add grade class price rows manually in Price Matrix.'
  UNION ALL
  SELECT 'PPT-SPRINT', 'Sprint Class', 'SPRINT', 'Sprint Class',
         'GROUP', NULL, NULL,
         'The PPT names Sprint Class but does not define its grade, period, or time matrix.'
  UNION ALL
  SELECT 'PPT-ONE-ON-ONE', '1-on-1', 'ONE_ON_ONE', '1-on-1',
         'ONE_ON_ONE', 80, 4,
         'PPT private course type. Add package-hour price rows manually in Price Matrix.'
) AS seed
WHERE NOT EXISTS (
  SELECT 1
  FROM `course_product` existing
  WHERE existing.`product_code` = seed.product_code
);

UPDATE `course_price_option`
SET `status` = 'INACTIVE',
    `notes` = 'Legacy PPT template row deactivated. Add real price rows manually.'
WHERE `option_code` LIKE 'PPT_%'
  AND `is_deleted` = 0;

-- Verification query:
-- SELECT product_code, product_name, course_type, teaching_mode, status
-- FROM course_product
-- WHERE product_code LIKE 'PPT-%'
-- ORDER BY product_code;
