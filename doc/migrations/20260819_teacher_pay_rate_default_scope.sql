-- Make the course-product scope optional. A NULL course_product_id is a
-- teacher-wide rate and is the normal configuration for most instructors.
ALTER TABLE `teacher_pay_rate`
  MODIFY COLUMN `course_product_id` bigint DEFAULT NULL
  COMMENT 'Optional course product scope; NULL applies to all products';
