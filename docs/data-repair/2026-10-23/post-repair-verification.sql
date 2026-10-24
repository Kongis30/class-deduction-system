USE class_deduction_system_v2;

-- Expected: old plus-N-months-minus-one-day anomalies = 0.
SELECT COUNT(*) AS remaining_legacy_formula_rows
FROM class_enrollment e
JOIN group_registration_plan p ON p.id = e.group_registration_plan_id
WHERE e.is_deleted = 0 AND e.status IN ('ACTIVE', 'EXPIRED')
  AND p.coverage_type = 'ROLLING_MONTHS' AND p.package_unit = 'MONTH'
  AND p.package_quantity = CAST(p.package_quantity AS UNSIGNED)
  AND e.expiry_date = DATE_SUB(DATE_ADD(e.enrollment_date,
      INTERVAL CAST(p.package_quantity AS UNSIGNED) MONTH), INTERVAL 1 DAY)
  AND e.expiry_date <> LAST_DAY(DATE_ADD(e.enrollment_date,
      INTERVAL CAST(p.package_quantity AS UNSIGNED) - 1 MONTH));

-- Expected: no matching package extends beyond a known program end.
SELECT COUNT(*) AS remaining_verified_program_cap_rows
FROM class_enrollment e
JOIN class_group g ON g.id = e.class_id
JOIN group_registration_plan p ON p.id = e.group_registration_plan_id
JOIN enrollment_order_item i ON i.id = e.enrollment_order_item_id
JOIN enrollment_order o ON o.id = i.enrollment_order_id
WHERE e.is_deleted = 0 AND e.status IN ('ACTIVE', 'EXPIRED')
  AND p.coverage_type = 'ROLLING_MONTHS' AND p.package_unit = 'MONTH'
  AND p.package_quantity = CAST(p.package_quantity AS UNSIGNED)
  AND i.package_unit = p.package_unit AND i.package_quantity = p.package_quantity
  AND e.purchased_quantity = p.package_quantity
  AND i.start_date = e.enrollment_date
  AND o.order_status = 'CONFIRMED' AND o.is_deleted = 0
  AND g.end_date IS NOT NULL
  AND e.expiry_date > g.end_date
  AND e.expiry_date = LAST_DAY(DATE_ADD(e.enrollment_date,
      INTERVAL CAST(p.package_quantity AS UNSIGNED) - 1 MONTH));

-- Confirm the individually repaired rows and the explicitly confirmed Wenhao purchase.
SELECT s.student_name, g.class_name, e.id AS enrollment_id,
       e.enrollment_date, e.expiry_date, e.purchased_quantity,
       e.status, e.version
FROM class_enrollment e
JOIN student s ON s.id = e.student_id
JOIN class_group g ON g.id = e.class_id
WHERE e.id IN (2110453050981814274, 2108689674270269441,
               2113019019742765057, 2107210630940864513,
               2102540260594016257)
ORDER BY s.student_name;

-- Known unresolved order/enrollment quantity discrepancy; do not change automatically.
SELECT s.student_name, o.enrollment_no, i.package_quantity AS order_item_quantity,
       p.package_quantity AS plan_quantity, e.purchased_quantity AS entitlement_quantity,
       e.expiry_date, g.end_date AS program_end_date, e.status, e.version
FROM class_enrollment e
JOIN student s ON s.id = e.student_id
JOIN enrollment_order_item i ON i.id = e.enrollment_order_item_id
JOIN enrollment_order o ON o.id = i.enrollment_order_id
JOIN group_registration_plan p ON p.id = e.group_registration_plan_id
JOIN class_group g ON g.id = e.class_id
WHERE e.id = 2107210630940864513;
