-- Store the Class selected on the registration form independently from Grade
-- and from the course Program shown in Data Summary.
ALTER TABLE `enrollment_order`
  ADD COLUMN `student_class_snapshot` varchar(32) DEFAULT NULL
  COMMENT 'Registration class level snapshot'
  AFTER `student_name_snapshot`;
