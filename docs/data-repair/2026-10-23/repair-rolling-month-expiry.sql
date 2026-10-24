-- Correct only the three verified active enrollments saved with the legacy
-- start-date + N months - 1 day formula. Lesson schedules and order data remain untouched.
USE class_deduction_system_v2;

DROP PROCEDURE IF EXISTS repair_rolling_month_expiry_20261023;
DELIMITER //
CREATE PROCEDURE repair_rolling_month_expiry_20261023()
BEGIN
    DECLARE v_match_count INT DEFAULT 0;
    DECLARE v_conflict_count INT DEFAULT 0;
    DECLARE v_changed_rows INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    -- Recheck identity, purchase, dates, prior value and version under the write transaction.
    SELECT COUNT(*) INTO v_match_count
    FROM class_enrollment e
    JOIN enrollment_order_item i ON i.id = e.enrollment_order_item_id
    JOIN enrollment_order o ON o.id = i.enrollment_order_id
    JOIN group_registration_plan p ON p.id = e.group_registration_plan_id
    JOIN class_group g ON g.id = e.class_id
    WHERE (e.id, e.enrollment_order_item_id, o.enrollment_no,
           e.enrollment_date, e.expiry_date, e.purchased_quantity, e.billing_type, e.version,
           i.start_date, i.package_unit, i.package_quantity,
           p.coverage_type, p.package_unit, p.package_quantity)
       IN ((2110453050981814274, 2100792728171925506, 'EO202609170122', '2026-09-10', '2026-11-09', 2, 'MONTHLY', 0, '2026-09-10', 'MONTH', 2.00, 'ROLLING_MONTHS', 'MONTH', 2.00),
           (2108689674270269441, 2108689674106691585, 'EO202610090001', '2026-10-09', '2026-12-08', 2, 'MONTHLY', 0, '2026-10-09', 'MONTH', 2.00, 'ROLLING_MONTHS', 'MONTH', 2.00),
           (2113019019742765057, 2113019019700822018, 'EO202610210002', '2026-11-02', '2027-01-01', 2, 'MONTHLY', 0, '2026-11-02', 'MONTH', 2.00, 'ROLLING_MONTHS', 'MONTH', 2.00))
      AND e.status = 'ACTIVE' AND e.is_deleted = 0
      AND i.is_deleted = 0 AND o.order_status = 'CONFIRMED' AND o.is_deleted = 0
      AND p.is_deleted = 0 AND g.is_deleted = 0
      AND g.end_date > CASE e.id
          WHEN 2110453050981814274 THEN '2026-10-31'
          WHEN 2108689674270269441 THEN '2026-11-30'
          WHEN 2113019019742765057 THEN '2026-12-31'
      END;

    IF v_match_count <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: one or more reviewed order/enrollment rows changed.';
    END IF;

    -- Do not shorten a period if another active entitlement for that same student/class overlaps it.
    SELECT COUNT(*) INTO v_conflict_count
    FROM class_enrollment target
    JOIN class_enrollment other_e
      ON other_e.student_id = target.student_id
     AND other_e.class_id = target.class_id
     AND other_e.id <> target.id
     AND other_e.status = 'ACTIVE' AND other_e.is_deleted = 0
     AND other_e.enrollment_date <= CASE target.id
         WHEN 2110453050981814274 THEN '2026-10-31'
         WHEN 2108689674270269441 THEN '2026-11-30'
         WHEN 2113019019742765057 THEN '2026-12-31'
     END
     AND other_e.expiry_date >= target.enrollment_date
    WHERE target.id IN (2110453050981814274, 2108689674270269441, 2113019019742765057)
      AND target.status = 'ACTIVE' AND target.is_deleted = 0;

    IF v_conflict_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: another active entitlement overlaps a corrected period.';
    END IF;

    UPDATE class_enrollment
    SET expiry_date = CASE id
            WHEN 2110453050981814274 THEN '2026-10-31'
            WHEN 2108689674270269441 THEN '2026-11-30'
            WHEN 2113019019742765057 THEN '2026-12-31'
        END,
        version = version + 1
    WHERE (id, expiry_date, version) IN
        ((2110453050981814274, '2026-11-09', 0),
         (2108689674270269441, '2026-12-08', 0),
         (2113019019742765057, '2027-01-01', 0))
      AND status = 'ACTIVE' AND is_deleted = 0;

    SET v_changed_rows = ROW_COUNT();
    IF v_changed_rows <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Repair stopped: expected exactly three enrollment updates.';
    END IF;

    COMMIT;
END//
DELIMITER ;

CALL repair_rolling_month_expiry_20261023();
DROP PROCEDURE repair_rolling_month_expiry_20261023;

-- Post-state verification. Expected end dates: 2026-10-31, 2026-11-30, 2026-12-31.
SELECT id, enrollment_order_item_id, enrollment_date, expiry_date,
       billing_type, purchased_quantity, status, version
FROM class_enrollment
WHERE id IN (2110453050981814274, 2108689674270269441, 2113019019742765057)
ORDER BY id;
