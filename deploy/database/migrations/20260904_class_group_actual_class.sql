ALTER TABLE class_group
  ADD COLUMN actual_class varchar(64) DEFAULT NULL
  COMMENT 'Actual class name under a billing grade, e.g. G1/G2'
  AFTER grade_band;
