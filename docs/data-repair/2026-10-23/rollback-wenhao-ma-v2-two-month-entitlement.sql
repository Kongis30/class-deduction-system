-- Roll back only if no later business action has changed Wenhao's V2 entitlement.
USE class_deduction_system_v2;

DROP PROCEDURE IF EXISTS rollback_wenhao_ma_v2_two_month_entitlement_20261023;
DELIMITER //
CREATE PROCEDURE rollback_wenhao_ma_v2_two_month_entitlement_20261023()
BEGIN
    DECLARE v_changed_rows INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    UPDATE class_enrollment
    SET expiry_date = '2026-10-31', purchased_quantity = 1, version = version + 1
    WHERE id = 2102540260594016257
      AND enrollment_order_item_id = 2102540260577239041
      AND enrollment_date = '2026-10-01' AND expiry_date = '2026-11-30'
      AND billing_type = 'MONTH' AND purchased_quantity = 2
      AND status = 'ACTIVE' AND is_deleted = 0 AND version = 3;

    SET v_changed_rows = ROW_COUNT();
    IF v_changed_rows <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Rollback stopped: Wenhao Ma V2 entitlement changed after repair.';
    END IF;
    COMMIT;
END//
DELIMITER ;

CALL rollback_wenhao_ma_v2_two_month_entitlement_20261023();
DROP PROCEDURE rollback_wenhao_ma_v2_two_month_entitlement_20261023;
