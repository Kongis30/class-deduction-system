-- Align active monthly rolling entitlements with the configured end date of
-- their class program. Updates only class_enrollment expiry_date/version.
USE class_deduction_system_v2;

DROP PROCEDURE IF EXISTS repair_program_end_cap_20261023;
DELIMITER //
CREATE PROCEDURE repair_program_end_cap_20261023()
BEGIN
    DECLARE v_match_count INT DEFAULT 0;
    DECLARE v_changed_rows INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    SELECT COUNT(*) INTO v_match_count
    FROM class_enrollment e
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
      AND g.end_date = '2027-06-28'
      AND g.end_date < LAST_DAY(DATE_ADD(e.enrollment_date,
          INTERVAL CAST(p.package_quantity AS UNSIGNED) - 1 MONTH))
      AND e.expiry_date = LAST_DAY(DATE_ADD(e.enrollment_date,
          INTERVAL CAST(p.package_quantity AS UNSIGNED) - 1 MONTH));

    IF v_match_count <> 62 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: expected exactly 62 reviewed program-end mismatches.';
    END IF;

    UPDATE class_enrollment e
    JOIN class_group g ON g.id = e.class_id
    JOIN group_registration_plan p ON p.id = e.group_registration_plan_id
    JOIN enrollment_order_item i ON i.id = e.enrollment_order_item_id
    JOIN enrollment_order o ON o.id = i.enrollment_order_id
    SET e.expiry_date = g.end_date,
        e.version = e.version + 1
    WHERE e.is_deleted = 0 AND e.status = 'ACTIVE'
      AND p.coverage_type = 'ROLLING_MONTHS' AND p.package_unit = 'MONTH'
      AND p.package_quantity = CAST(p.package_quantity AS UNSIGNED)
      AND i.package_unit = p.package_unit AND i.package_quantity = p.package_quantity
      AND e.purchased_quantity = p.package_quantity
      AND i.start_date = e.enrollment_date
      AND o.order_status = 'CONFIRMED' AND o.is_deleted = 0
      AND g.end_date = '2027-06-28'
      AND g.end_date < LAST_DAY(DATE_ADD(e.enrollment_date,
          INTERVAL CAST(p.package_quantity AS UNSIGNED) - 1 MONTH))
      AND e.expiry_date = LAST_DAY(DATE_ADD(e.enrollment_date,
          INTERVAL CAST(p.package_quantity AS UNSIGNED) - 1 MONTH));

    SET v_changed_rows = ROW_COUNT();
    IF v_changed_rows <> 62 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: expected exactly 62 expiry updates.';
    END IF;

    COMMIT;
END//
DELIMITER ;

CALL repair_program_end_cap_20261023();
DROP PROCEDURE repair_program_end_cap_20261023;

-- All repaired rows must now match the program end date.
SELECT COUNT(*) AS remaining_mismatches
FROM class_enrollment e
JOIN class_group g ON g.id = e.class_id
JOIN group_registration_plan p ON p.id = e.group_registration_plan_id
JOIN enrollment_order_item i ON i.id = e.enrollment_order_item_id
JOIN enrollment_order o ON o.id = i.enrollment_order_id
WHERE e.is_deleted = 0 AND e.status = 'ACTIVE'
  AND p.coverage_type = 'ROLLING_MONTHS' AND p.package_unit = 'MONTH'
  AND p.package_quantity = 10
  AND i.package_unit = 'MONTH' AND i.package_quantity = 10
  AND e.purchased_quantity = 10 AND i.start_date = e.enrollment_date
  AND o.order_status = 'CONFIRMED' AND o.is_deleted = 0
  AND g.end_date = '2027-06-28' AND e.expiry_date = '2027-06-30';
