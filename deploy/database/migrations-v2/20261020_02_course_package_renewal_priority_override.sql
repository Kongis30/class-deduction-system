-- Store manual pin/unpin decisions while NULL continues to follow the balance threshold.
DELIMITER $$

DROP PROCEDURE IF EXISTS add_course_package_renewal_priority_override_20261020$$
CREATE PROCEDURE add_course_package_renewal_priority_override_20261020()
BEGIN
    DECLARE column_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO column_exists
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'course_package'
       AND column_name = 'renewal_priority_override';

    IF column_exists = 0 THEN
        ALTER TABLE course_package
            ADD COLUMN renewal_priority_override TINYINT(1) NULL DEFAULT NULL
                COMMENT 'Manual renewal priority override; NULL follows the balance threshold'
                AFTER low_balance_priority_dismissed;
    END IF;
END$$

CALL add_course_package_renewal_priority_override_20261020()$$
DROP PROCEDURE add_course_package_renewal_priority_override_20261020$$

-- Preserve packages that administrators had already unpinned before this tri-state setting existed.
UPDATE course_package
   SET renewal_priority_override = 0
 WHERE COALESCE(low_balance_priority_dismissed, 0) = 1
   AND renewal_priority_override IS NULL$$

DELIMITER ;
