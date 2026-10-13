-- Each recurring schedule revision starts from the member package's live balance.
-- Historical rules and their lesson occurrences remain available for audit.

DELIMITER $$

DROP PROCEDURE IF EXISTS add_schedule_period_starting_balance$$
CREATE PROCEDURE add_schedule_period_starting_balance()
BEGIN
    DECLARE column_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO column_exists
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'recurring_schedule_rule'
       AND column_name = 'supersedes_rule_id';
    IF column_exists = 0 THEN
        ALTER TABLE recurring_schedule_rule
            ADD COLUMN supersedes_rule_id bigint DEFAULT NULL
                COMMENT 'Previous schedule period replaced by this rule';
    END IF;

    SELECT COUNT(*) INTO column_exists
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'recurring_schedule_rule'
       AND column_name = 'superseded_by_rule_id';
    IF column_exists = 0 THEN
        ALTER TABLE recurring_schedule_rule
            ADD COLUMN superseded_by_rule_id bigint DEFAULT NULL
                COMMENT 'Replacement schedule period created from this rule';
    END IF;

    CREATE TABLE IF NOT EXISTS recurring_schedule_starting_balance (
        id bigint NOT NULL,
        schedule_rule_id bigint NOT NULL,
        package_id bigint NOT NULL,
        starting_units int NOT NULL DEFAULT 0 COMMENT 'Schedule-period opening balance, in half-hour units',
        created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        created_by bigint DEFAULT NULL,
        updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
        updated_by bigint DEFAULT NULL,
        is_deleted tinyint(1) NOT NULL DEFAULT 0,
        deleted_at datetime(3) DEFAULT NULL,
        deleted_by bigint DEFAULT NULL,
        version int NOT NULL DEFAULT 0,
        PRIMARY KEY (id),
        UNIQUE KEY uk_schedule_starting_balance_rule_package (schedule_rule_id, package_id),
        KEY idx_schedule_starting_balance_package (package_id, schedule_rule_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
END$$

CALL add_schedule_period_starting_balance()$$
DROP PROCEDURE add_schedule_period_starting_balance$$

DELIMITER ;
