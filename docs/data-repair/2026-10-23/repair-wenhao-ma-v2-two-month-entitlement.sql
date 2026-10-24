-- The customer confirmed this order was for two rolling months. The previous
-- repair changed the legacy database; production V2 still had its old one-month snapshot.
-- Preserve the class schedule and lesson records; correct only this entitlement snapshot.
USE class_deduction_system_v2;

DROP PROCEDURE IF EXISTS repair_wenhao_ma_v2_two_month_entitlement_20261023;
DELIMITER //
CREATE PROCEDURE repair_wenhao_ma_v2_two_month_entitlement_20261023()
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
    WHERE e.id = 2102540260594016257
      AND e.enrollment_order_item_id = 2102540260577239041
      AND o.enrollment_no = 'EO202609220020'
      AND o.order_status = 'CONFIRMED' AND o.is_deleted = 0
      AND i.is_deleted = 0 AND i.start_date = '2026-10-01'
      AND i.package_unit = 'MONTH' AND i.package_quantity = 2.00
      AND p.coverage_type = 'ROLLING_MONTHS'
      AND p.package_unit = 'MONTH' AND p.package_quantity = 2.00 AND p.is_deleted = 0
      AND e.enrollment_date = '2026-10-01' AND e.expiry_date = '2026-10-31'
      AND e.billing_type = 'MONTH' AND e.purchased_quantity = 1
      AND e.status = 'ACTIVE' AND e.is_deleted = 0 AND e.version = 2;

    IF v_match_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: reviewed Wenhao Ma V2 order/enrollment state changed.';
    END IF;

    UPDATE class_enrollment
    SET expiry_date = '2026-11-30',
        purchased_quantity = 2,
        version = version + 1
    WHERE id = 2102540260594016257
      AND enrollment_order_item_id = 2102540260577239041
      AND enrollment_date = '2026-10-01' AND expiry_date = '2026-10-31'
      AND billing_type = 'MONTH' AND purchased_quantity = 1
      AND status = 'ACTIVE' AND is_deleted = 0 AND version = 2;

    SET v_changed_rows = ROW_COUNT();
    IF v_changed_rows <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: expected exactly one Wenhao Ma V2 enrollment update.';
    END IF;

    COMMIT;
END//
DELIMITER ;

CALL repair_wenhao_ma_v2_two_month_entitlement_20261023();
DROP PROCEDURE repair_wenhao_ma_v2_two_month_entitlement_20261023;

SELECT id, enrollment_order_item_id, enrollment_date, expiry_date,
       billing_type, purchased_quantity, status, version
FROM class_enrollment
WHERE id = 2102540260594016257;
