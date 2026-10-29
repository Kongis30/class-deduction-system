-- 未关联课位的历史课次由管理员指定继承关系；不回填、不改变现有课次或账本。
DELIMITER $$
DROP PROCEDURE IF EXISTS add_unlinked_history_owner_20261029$$
CREATE PROCEDURE add_unlinked_history_owner_20261029()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'lesson_occurrence'
                     AND column_name = 'history_owner_lineage_id') THEN
        ALTER TABLE lesson_occurrence ADD COLUMN history_owner_lineage_id BIGINT DEFAULT NULL
            COMMENT 'Administrator-selected owner lineage for unlinked historical lessons';
    END IF;
END$$
CALL add_unlinked_history_owner_20261029()$$
DROP PROCEDURE add_unlinked_history_owner_20261029$$
DELIMITER ;
