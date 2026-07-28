-- Keep pickup and drop-off service charges independently queryable.
ALTER TABLE `enrollment_order`
  MODIFY COLUMN `pickup_fee_amount` decimal(12,2) NOT NULL DEFAULT '0.00'
  COMMENT 'Pickup service fee amount',
  ADD COLUMN `dropoff_fee_amount` decimal(12,2) NOT NULL DEFAULT '0.00'
  COMMENT 'Drop-off service fee amount'
  AFTER `pickup_fee_amount`;
