-- Roll back only if none of the repaired rows has been changed since this repair.
USE class_deduction_system_v2;

DROP PROCEDURE IF EXISTS rollback_rolling_month_expiry_20261023;
DELIMITER //
CREATE PROCEDURE rollback_rolling_month_expiry_20261023()
BEGIN
    DECLARE v_changed_rows INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    UPDATE class_enrollment
    SET expiry_date = CASE id
            WHEN 2110453050981814274 THEN '2026-11-09'
            WHEN 2108689674270269441 THEN '2026-12-08'
            WHEN 2113019019742765057 THEN '2027-01-01'
        END,
        version = version + 1
    WHERE (id, expiry_date, version) IN
        ((2110453050981814274, '2026-10-31', 1),
         (2108689674270269441, '2026-11-30', 1),
         (2113019019742765057, '2026-12-31', 1))
      AND status = 'ACTIVE' AND is_deleted = 0;

    SET v_changed_rows = ROW_COUNT();
    IF v_changed_rows <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Rollback stopped: repaired rows no longer match the expected post-state.';
    END IF;
    COMMIT;
END//
DELIMITER ;

CALL rollback_rolling_month_expiry_20261023();
DROP PROCEDURE rollback_rolling_month_expiry_20261023;
