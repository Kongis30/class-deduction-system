-- Repair Wenhao Ma's verified two-month rolling entitlement.
-- Order: EO202609220020 (item 2102540260577239041)
-- Evidence: item package_quantity=2 MONTH; current active entitlement was saved as
-- 1 month through 2026-10-31. Class schedule ends 2027-06-30, so it does not cap
-- the intended rolling period. No other active entitlement overlaps November.
-- This changes entitlement metadata only; no class schedule or lesson rows change.

USE class_deduction_system;

DROP PROCEDURE IF EXISTS repair_wenhao_ma_two_month_entitlement_20261022;
DELIMITER //
CREATE PROCEDURE repair_wenhao_ma_two_month_entitlement_20261022()
BEGIN
    DECLARE v_match_count INT DEFAULT 0;
    DECLARE v_overlap_count INT DEFAULT 0;
    DECLARE v_changed_rows INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    SELECT COUNT(*) INTO v_match_count
    FROM enrollment_order o
    JOIN enrollment_order_item i ON i.enrollment_order_id = o.id
    JOIN class_group g ON g.id = i.class_id
    JOIN class_enrollment e ON e.enrollment_order_item_id = i.id
    WHERE o.id = 2102540260568850433
      AND o.enrollment_no = 'EO202609220020'
      AND o.is_deleted = 0
      AND i.id = 2102540260577239041
      AND i.package_unit = 'MONTH'
      AND i.package_quantity = 2.00
      AND i.start_date = '2026-10-01'
      AND i.is_deleted = 0
      AND g.id = 2096221029250150401
      AND g.end_month = '2027-06-01'
      AND g.is_deleted = 0
      AND e.id = 2102540260594016257
      AND e.student_id = 2102540260556267522
      AND e.class_id = 2096221029250150401
      AND e.enrollment_date = '2026-10-01'
      AND e.expiry_date = '2026-10-31'
      AND e.billing_type = 'MONTH'
      AND e.purchased_quantity = 1
      AND e.status = 'ACTIVE'
      AND e.is_deleted = 0
      AND e.version = 2;

    IF v_match_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: reviewed Wenhao Ma order and entitlement state no longer matches.';
    END IF;

    SELECT COUNT(*) INTO v_overlap_count
    FROM class_enrollment other_e
    WHERE other_e.student_id = 2102540260556267522
      AND other_e.class_id = 2096221029250150401
      AND other_e.id <> 2102540260594016257
      AND other_e.status = 'ACTIVE'
      AND other_e.is_deleted = 0
      AND other_e.enrollment_date <= '2026-11-30'
      AND other_e.expiry_date >= '2026-11-01';

    IF v_overlap_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: another active enrollment overlaps the extended period.';
    END IF;

    UPDATE class_enrollment
    SET expiry_date = '2026-11-30',
        purchased_quantity = 2,
        version = version + 1
    WHERE id = 2102540260594016257
      AND enrollment_date = '2026-10-01'
      AND expiry_date = '2026-10-31'
      AND billing_type = 'MONTH'
      AND purchased_quantity = 1
      AND status = 'ACTIVE'
      AND is_deleted = 0
      AND version = 2;

    SET v_changed_rows = ROW_COUNT();
    IF v_changed_rows <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: expected exactly one class enrollment update.';
    END IF;

    COMMIT;
END//
DELIMITER ;

CALL repair_wenhao_ma_two_month_entitlement_20261022();
DROP PROCEDURE repair_wenhao_ma_two_month_entitlement_20261022;

-- Expected post-state: 2026-10-01 through 2026-11-30, MONTH, quantity 2, ACTIVE.
SELECT id, enrollment_order_item_id, enrollment_date, expiry_date,
       billing_type, purchased_quantity, status, version
FROM class_enrollment
WHERE id = 2102540260594016257;
