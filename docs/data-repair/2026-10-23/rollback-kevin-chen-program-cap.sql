-- Roll back only if Kevin's row has not changed since this repair.
USE class_deduction_system_v2;

DROP PROCEDURE IF EXISTS rollback_kevin_chen_program_cap_20261023;
DELIMITER //
CREATE PROCEDURE rollback_kevin_chen_program_cap_20261023()
BEGIN
    DECLARE v_changed_rows INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    UPDATE class_enrollment
    SET expiry_date = '2027-06-30', version = version + 1
    WHERE id = 2107210630940864513
      AND enrollment_order_item_id = 2107210630622097410
      AND enrollment_date = '2026-10-05' AND expiry_date = '2027-06-28'
      AND purchased_quantity = 9 AND status = 'ACTIVE' AND is_deleted = 0 AND version = 1;

    SET v_changed_rows = ROW_COUNT();
    IF v_changed_rows <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Rollback stopped: Kevin Chen entitlement changed after repair.';
    END IF;
    COMMIT;
END//
DELIMITER ;

CALL rollback_kevin_chen_program_cap_20261023();
DROP PROCEDURE rollback_kevin_chen_program_cap_20261023;
