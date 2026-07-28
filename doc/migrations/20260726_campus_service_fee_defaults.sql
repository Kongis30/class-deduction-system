-- Default registration service fees by campus.
-- Existing registrations keep their fee snapshots; these defaults apply only to new intake forms.
-- Safe to rerun on local/demo databases that may already have one or both columns.

SET @add_default_pickup_fee = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'campus'
        AND column_name = 'default_pickup_fee_amount'
    ),
    'ALTER TABLE `campus` ADD COLUMN `default_pickup_fee_amount` decimal(12,2) NOT NULL DEFAULT ''0.00'' COMMENT ''Default pickup service fee for new registrations'' AFTER `status`',
    'SELECT 1'
  )
);
PREPARE stmt FROM @add_default_pickup_fee;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_default_dropoff_fee = (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'campus'
        AND column_name = 'default_dropoff_fee_amount'
    ),
    'ALTER TABLE `campus` ADD COLUMN `default_dropoff_fee_amount` decimal(12,2) NOT NULL DEFAULT ''0.00'' COMMENT ''Default drop-off service fee for new registrations'' AFTER `default_pickup_fee_amount`',
    'SELECT 1'
  )
);
PREPARE stmt FROM @add_default_dropoff_fee;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
