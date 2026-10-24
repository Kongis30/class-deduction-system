-- Read-only capture. The period end is unambiguous for either 9 or 10 months:
-- both natural month-end dates exceed the program end (2027-06-28).
USE class_deduction_system_v2;

SELECT s.student_name, o.enrollment_no, i.id AS order_item_id,
       i.start_date, i.package_unit, i.package_quantity AS item_quantity,
       p.coverage_type, p.package_quantity AS plan_quantity,
       e.id AS enrollment_id, e.enrollment_date, e.expiry_date,
       e.billing_type, e.purchased_quantity, g.end_date AS program_end_date,
       e.status, e.version
FROM class_enrollment e
JOIN student s ON s.id = e.student_id
JOIN enrollment_order_item i ON i.id = e.enrollment_order_item_id
JOIN enrollment_order o ON o.id = i.enrollment_order_id
JOIN group_registration_plan p ON p.id = e.group_registration_plan_id
JOIN class_group g ON g.id = e.class_id
WHERE e.id = 2107210630940864513;
