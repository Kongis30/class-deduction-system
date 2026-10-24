-- Correct the confirmed class-program end cap without changing the disputed
-- purchased quantity (the order item says 10, entitlement snapshot says 9).
USE class_deduction_system_v2;

DROP PROCEDURE IF EXISTS repair_kevin_chen_program_cap_20261023;
DELIMITER //
CREATE PROCEDURE repair_kevin_chen_program_cap_20261023()
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
    JOIN enrollment_order_item i ON i.id = e.enrollment_order_item_id
    JOIN enrollment_order o ON o.id = i.enrollment_order_id
    JOIN group_registration_plan p ON p.id = e.group_registration_plan_id
    JOIN class_group g ON g.id = e.class_id
    WHERE e.id = 2107210630940864513
      AND e.enrollment_order_item_id = 2107210630622097410
      AND o.enrollment_no = 'EO202610050003'
      AND o.order_status = 'CONFIRMED' AND o.is_deleted = 0
      AND i.start_date = '2026-10-05' AND i.package_unit = 'MONTH' AND i.package_quantity = 10.00
      AND p.coverage_type = 'ROLLING_MONTHS' AND p.package_unit = 'MONTH' AND p.package_quantity = 10.00
      AND e.enrollment_date = '2026-10-05' AND e.expiry_date = '2027-06-30'
      AND e.billing_type = 'MONTH' AND e.purchased_quantity = 9
      AND g.end_date = '2027-06-28'
      AND e.status = 'ACTIVE' AND e.is_deleted = 0 AND e.version = 0;

    IF v_match_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: reviewed Kevin Chen period data changed.';
    END IF;

    UPDATE class_enrollment
    SET expiry_date = '2027-06-28', version = version + 1
    WHERE id = 2107210630940864513
      AND enrollment_order_item_id = 2107210630622097410
      AND enrollment_date = '2026-10-05' AND expiry_date = '2027-06-30'
      AND purchased_quantity = 9 AND status = 'ACTIVE' AND is_deleted = 0 AND version = 0;

    SET v_changed_rows = ROW_COUNT();
    IF v_changed_rows <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: expected exactly one Kevin Chen expiry update.';
    END IF;
    COMMIT;
END//
DELIMITER ;

CALL repair_kevin_chen_program_cap_20261023();
DROP PROCEDURE repair_kevin_chen_program_cap_20261023;

SELECT id, enrollment_order_item_id, enrollment_date, expiry_date,
       billing_type, purchased_quantity, status, version
FROM class_enrollment
WHERE id = 2107210630940864513;
