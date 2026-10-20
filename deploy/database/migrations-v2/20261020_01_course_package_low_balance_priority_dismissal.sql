-- Persist manual dismissal of the low-balance priority for each individual package.
DELIMITER $$

DROP PROCEDURE IF EXISTS add_course_package_low_balance_priority_dismissal_20261020$$
CREATE PROCEDURE add_course_package_low_balance_priority_dismissal_20261020()
BEGIN
    DECLARE column_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO column_exists
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'course_package'
       AND column_name = 'low_balance_priority_dismissed';

    IF column_exists = 0 THEN
        ALTER TABLE course_package
            ADD COLUMN low_balance_priority_dismissed TINYINT(1) NOT NULL DEFAULT 0
                COMMENT 'Whether the low-balance priority was manually dismissed'
                AFTER remaining_units;
    END IF;
END$$

CALL add_course_package_low_balance_priority_dismissal_20261020()$$
DROP PROCEDURE add_course_package_low_balance_priority_dismissal_20261020$$

DELIMITER ;
