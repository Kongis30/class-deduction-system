-- Before-state for the specifically confirmed two-month Wenhao Ma purchase.
USE class_deduction_system_v2;

SELECT s.student_name, o.enrollment_no, i.id AS order_item_id,
       i.start_date, i.package_unit, i.package_quantity,
       p.coverage_type, p.package_quantity AS plan_quantity,
       e.id AS enrollment_id, e.enrollment_date, e.expiry_date,
       e.billing_type, e.purchased_quantity, e.status, e.version
FROM class_enrollment e
JOIN student s ON s.id = e.student_id
JOIN enrollment_order_item i ON i.id = e.enrollment_order_item_id
JOIN enrollment_order o ON o.id = i.enrollment_order_id
JOIN group_registration_plan p ON p.id = e.group_registration_plan_id
WHERE e.id = 2102540260594016257;
