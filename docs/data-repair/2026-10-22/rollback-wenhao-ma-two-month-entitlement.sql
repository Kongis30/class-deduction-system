-- Rollback for wenhao-ma-two-month-entitlement.sql.
-- Run only if the corrected row is still exactly at version 3 and no later
-- business action has relied on the November entitlement.

USE class_deduction_system;

DROP PROCEDURE IF EXISTS rollback_wenhao_ma_two_month_entitlement_20261022;
DELIMITER //
CREATE PROCEDURE rollback_wenhao_ma_two_month_entitlement_20261022()
BEGIN
    DECLARE v_changed_rows INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    UPDATE class_enrollment
    SET expiry_date = '2026-10-31',
        purchased_quantity = 1,
        version = version + 1
    WHERE id = 2102540260594016257
      AND enrollment_order_item_id = 2102540260577239041
      AND enrollment_date = '2026-10-01'
      AND expiry_date = '2026-11-30'
      AND billing_type = 'MONTH'
      AND purchased_quantity = 2
      AND status = 'ACTIVE'
      AND is_deleted = 0
      AND version = 3;

    SET v_changed_rows = ROW_COUNT();
    IF v_changed_rows <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Rollback stopped: corrected entitlement changed after the repair.';
    END IF;
    COMMIT;
END//
DELIMITER ;

CALL rollback_wenhao_ma_two_month_entitlement_20261022();
DROP PROCEDURE rollback_wenhao_ma_two_month_entitlement_20261022;
