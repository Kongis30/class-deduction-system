-- Associate each actual group class with a campus.
-- Unknown legacy classes remain NULL until an administrator assigns them in Prices.

SET @class_campus_column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'class_group' AND column_name = 'campus_id'
);
SET @class_campus_column_sql = IF(
    @class_campus_column_exists = 0,
    'ALTER TABLE class_group ADD COLUMN campus_id BIGINT NULL COMMENT ''Campus assigned to this actual class'' AFTER classroom_id',
    'SELECT 1'
);
PREPARE class_campus_column_statement FROM @class_campus_column_sql;
EXECUTE class_campus_column_statement;
DEALLOCATE PREPARE class_campus_column_statement;

SET @class_campus_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'class_group' AND index_name = 'idx_class_group_campus'
);
SET @class_campus_index_sql = IF(
    @class_campus_index_exists = 0,
    'ALTER TABLE class_group ADD INDEX idx_class_group_campus (campus_id)',
    'SELECT 1'
);
PREPARE class_campus_index_statement FROM @class_campus_index_sql;
EXECUTE class_campus_index_statement;
DEALLOCATE PREPARE class_campus_index_statement;

SET @class_campus_fk_exists = (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'class_group'
      AND constraint_name = 'fk_class_group_campus' AND constraint_type = 'FOREIGN KEY'
);
SET @class_campus_fk_sql = IF(
    @class_campus_fk_exists = 0,
    'ALTER TABLE class_group ADD CONSTRAINT fk_class_group_campus FOREIGN KEY (campus_id) REFERENCES campus(id)',
    'SELECT 1'
);
PREPARE class_campus_fk_statement FROM @class_campus_fk_sql;
EXECUTE class_campus_fk_statement;
DEALLOCATE PREPARE class_campus_fk_statement;

-- The two campus suffixes are explicit business labels, so these rows can be safely assigned.
UPDATE class_group cg
JOIN campus c ON c.campus_name = CASE
    WHEN RIGHT(cg.class_name, CHAR_LENGTH(' 北校区')) = ' 北校区' THEN 'North Campus'
    WHEN RIGHT(cg.class_name, CHAR_LENGTH(' 南校区')) = ' 南校区' THEN 'South Campus'
END
SET cg.campus_id = c.id
WHERE cg.is_deleted = 0
  AND cg.campus_id IS NULL
  AND (RIGHT(cg.class_name, CHAR_LENGTH(' 北校区')) = ' 北校区'
       OR RIGHT(cg.class_name, CHAR_LENGTH(' 南校区')) = ' 南校区');
