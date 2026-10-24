-- Before-state for active monthly rolling enrollments whose saved end is later
-- than the current, authoritative class program end. Read-only inventory.
USE class_deduction_system_v2;

SELECT s.student_name, g.class_name, e.id AS enrollment_id,
       i.id AS order_item_id, o.enrollment_no, e.enrollment_date,
       e.expiry_date AS saved_expiry_date, g.end_date AS program_end_date,
       e.purchased_quantity, p.package_quantity AS plan_quantity,
       i.package_quantity AS item_quantity, e.status, e.version
FROM class_enrollment e
JOIN student s ON s.id = e.student_id
JOIN class_group g ON g.id = e.class_id
JOIN group_registration_plan p ON p.id = e.group_registration_plan_id
JOIN enrollment_order_item i ON i.id = e.enrollment_order_item_id
JOIN enrollment_order o ON o.id = i.enrollment_order_id
WHERE e.is_deleted = 0 AND e.status = 'ACTIVE'
  AND p.coverage_type = 'ROLLING_MONTHS' AND p.package_unit = 'MONTH'
  AND p.package_quantity = CAST(p.package_quantity AS UNSIGNED)
  AND i.package_unit = p.package_unit AND i.package_quantity = p.package_quantity
  AND e.purchased_quantity = p.package_quantity
  AND i.start_date = e.enrollment_date
  AND o.order_status = 'CONFIRMED' AND o.is_deleted = 0
  AND g.end_date IS NOT NULL
  AND g.end_date < LAST_DAY(DATE_ADD(e.enrollment_date,
      INTERVAL CAST(p.package_quantity AS UNSIGNED) - 1 MONTH))
  AND e.expiry_date = LAST_DAY(DATE_ADD(e.enrollment_date,
      INTERVAL CAST(p.package_quantity AS UNSIGNED) - 1 MONTH))
  AND e.expiry_date <> g.end_date
ORDER BY g.class_name, s.student_name;
