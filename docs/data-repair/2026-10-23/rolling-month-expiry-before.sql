-- Read-only before-state capture for the 2026-10-23 rolling-month period repair.
USE class_deduction_system_v2;

SELECT s.student_name, g.class_name, e.id AS enrollment_id,
       i.id AS order_item_id, o.enrollment_no, e.enrollment_date,
       e.expiry_date, e.purchased_quantity, e.billing_type, e.status,
       e.version, p.coverage_type, p.package_unit, p.package_quantity AS plan_quantity,
       i.package_unit AS item_unit, i.package_quantity AS item_quantity, i.start_date
FROM class_enrollment e
JOIN student s ON s.id = e.student_id
JOIN class_group g ON g.id = e.class_id
JOIN group_registration_plan p ON p.id = e.group_registration_plan_id
JOIN enrollment_order_item i ON i.id = e.enrollment_order_item_id
JOIN enrollment_order o ON o.id = i.enrollment_order_id
WHERE e.id IN (2110453050981814274, 2108689674270269441, 2113019019742765057)
ORDER BY e.id;
