-- Product 归档只增加目录状态，不迁移或改写订单、课包、排课和财务历史。
DELIMITER $$

DROP PROCEDURE IF EXISTS add_course_product_archive_fields_20261023$$
CREATE PROCEDURE add_course_product_archive_fields_20261023()
BEGIN
    DECLARE archived_at_exists INT DEFAULT 0;
    DECLARE archived_by_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO archived_at_exists
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'course_product'
       AND column_name = 'archived_at';
    IF archived_at_exists = 0 THEN
        ALTER TABLE course_product
            ADD COLUMN archived_at datetime(3) DEFAULT NULL
                COMMENT 'Product archive time; historical business rows remain unchanged'
                AFTER status;
    END IF;

    SELECT COUNT(*) INTO archived_by_exists
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'course_product'
       AND column_name = 'archived_by';
    IF archived_by_exists = 0 THEN
        ALTER TABLE course_product
            ADD COLUMN archived_by bigint DEFAULT NULL
                COMMENT 'User who archived the product'
                AFTER archived_at;
    END IF;
END$$

CALL add_course_product_archive_fields_20261023()$$
DROP PROCEDURE add_course_product_archive_fields_20261023$$

DELIMITER ;
