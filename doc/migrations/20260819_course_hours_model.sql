-- New course-hours model:
-- product defines the product only; price options define purchased quantity;
-- one-on-one recurring schedule slots define lesson duration and allocated hours.
-- Keep the legacy package column nullable for historical records, but remove
-- product-level defaults and the old package-level check constraint.

DELIMITER $$

DROP PROCEDURE IF EXISTS add_course_hours_model_columns$$
CREATE PROCEDURE add_course_hours_model_columns()
BEGIN
    DECLARE constraint_exists INT DEFAULT 0;

    SELECT COUNT(*)
      INTO constraint_exists
      FROM information_schema.table_constraints
     WHERE table_schema = DATABASE()
       AND table_name = 'course_package'
       AND constraint_name = 'chk_course_package_units';

    IF constraint_exists > 0 THEN
        ALTER TABLE course_package DROP CHECK chk_course_package_units;
    END IF;

    ALTER TABLE course_package
        MODIFY COLUMN default_lesson_units INT NULL
            COMMENT 'Legacy default lesson units; new schedules define duration per slot';
END$$

CALL add_course_hours_model_columns()$$
DROP PROCEDURE add_course_hours_model_columns$$

DELIMITER ;
