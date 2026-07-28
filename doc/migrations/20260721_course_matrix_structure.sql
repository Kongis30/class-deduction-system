-- Course matrix structure hardening.
-- Course Product is the parent course family; Course Price Option is the
-- grade/duration/time/price row selected during registration.

ALTER TABLE `course_product`
  ADD COLUMN `course_type` varchar(32) DEFAULT NULL COMMENT 'Parent course type: AFTER_SCHOOL/SATURDAY/SUMMER/SPRINT/ONE_ON_ONE' AFTER `product_name`,
  ADD COLUMN `register_class` varchar(64) DEFAULT NULL COMMENT 'Register Class label shown on registration' AFTER `course_type`,
  ADD KEY `idx_course_product_type` (`course_type`, `status`);

ALTER TABLE `course_product`
  MODIFY COLUMN `list_price` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Reference list price; sale price comes from course_price_option';

ALTER TABLE `course_price_option`
  MODIFY COLUMN `price_amount` decimal(12,2) NOT NULL COMMENT 'Configured sale price for this matrix row',
  MODIFY COLUMN `course_product_id` bigint DEFAULT NULL COMMENT 'Linked parent course product ID';

ALTER TABLE `course_price_option`
  ADD CONSTRAINT `chk_course_price_option_active_product`
    CHECK ((`status` <> 'ACTIVE') OR (`course_product_id` IS NOT NULL)),
  DROP CHECK `chk_course_price_option_amount`,
  ADD CONSTRAINT `chk_course_price_option_amount`
    CHECK (`price_amount` >= 0);
