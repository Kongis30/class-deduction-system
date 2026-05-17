-- ============================================================
-- JIN BEST TUTORING — Test Data Seed
-- Run this AFTER mysql_schema.sql to populate the system with
-- realistic demo data for end-to-end testing.
-- ============================================================

USE `class_deduction_system`;

SET NAMES utf8mb4;

-- ── 1. Teachers ─────────────────────────────────────────────
INSERT INTO teacher (id, teacher_no, teacher_name, email, mobile, subject_tags, status, hire_date, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (7001, 'TH001', 'Sarah Johnson', 'sarah.johnson@jinbest.edu', '347-555-0101', '["Math","Science"]', 'ACTIVE', '2023-08-15', 'Experienced math and science tutor, MIT graduate', '2024-01-01 09:00:00.000', 1, '2024-01-01 09:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO teacher (id, teacher_no, teacher_name, email, mobile, subject_tags, status, hire_date, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (7002, 'TH002', 'Michael Chen', 'michael.chen@jinbest.edu', '347-555-0102', '["English","SAT"]', 'ACTIVE', '2022-09-01', 'English and SAT prep specialist', '2024-01-01 09:00:00.000', 1, '2024-01-01 09:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO teacher (id, teacher_no, teacher_name, email, mobile, subject_tags, status, hire_date, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (7003, 'TH003', 'Emily Rodriguez', 'emily.rodriguez@jinbest.edu', '347-555-0103', '["Math","SAT"]', 'ACTIVE', '2023-01-10', 'SAT math expert with 5 years experience', '2024-01-01 09:00:00.000', 1, '2024-01-01 09:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO teacher (id, teacher_no, teacher_name, email, mobile, subject_tags, status, hire_date, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (7004, 'TH004', 'David Kim', 'david.kim@jinbest.edu', '347-555-0104', '["Science","Biology"]', 'ACTIVE', '2023-06-01', 'Biology and life science tutor', '2024-01-01 09:00:00.000', 1, '2024-01-01 09:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;


-- ── 2. Students ─────────────────────────────────────────────
INSERT INTO student (id, student_no, student_name, english_name, gender, birth_date, source_channel, status, notes, guardian_name, guardian_phone, guardian_email, guardian_relation_type, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (8001, 'ST2024001', '张明轩', 'Ming Zhang', 'MALE', '2012-03-15', 'Website', 'ACTIVE', '', 'Zhang Wei', '917-555-0201', 'zhang.wei.guardian@gmail.com', 'FATHER', '2024-02-10 10:00:00.000', 1, '2024-02-10 10:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO student (id, student_no, student_name, english_name, gender, birth_date, source_channel, status, notes, guardian_name, guardian_phone, guardian_email, guardian_relation_type, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (8002, 'ST2024002', '李雨涵', 'Yuhan Li', 'FEMALE', '2011-07-22', 'Referral', 'ACTIVE', '', 'Li Xiaoming', '917-555-0202', 'li.xiaoming.guardian@gmail.com', 'MOTHER', '2024-03-01 11:00:00.000', 1, '2024-03-01 11:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO student (id, student_no, student_name, english_name, gender, birth_date, source_channel, status, notes, guardian_name, guardian_phone, guardian_email, guardian_relation_type, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (8003, 'ST2024003', '王浩然', 'Haoran Wang', 'MALE', '2013-01-08', 'Google Ads', 'ACTIVE', '', 'Wang Jiaming', '917-555-0203', 'wang.jiaming.guardian@gmail.com', 'FATHER', '2024-03-15 09:00:00.000', 1, '2024-03-15 09:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO student (id, student_no, student_name, english_name, gender, birth_date, source_channel, status, notes, guardian_name, guardian_phone, guardian_email, guardian_relation_type, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (8004, 'ST2024004', '陈思琪', 'Siqi Chen', 'FEMALE', '2010-11-30', 'School Partnership', 'ACTIVE', '', 'Chen Guohua', '917-555-0204', 'chen.guohua.guardian@gmail.com', 'MOTHER', '2024-04-01 10:00:00.000', 1, '2024-04-01 10:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO student (id, student_no, student_name, english_name, gender, birth_date, source_channel, status, notes, guardian_name, guardian_phone, guardian_email, guardian_relation_type, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (8005, 'ST2024005', '刘子涵', 'Zihan Liu', 'MALE', '2012-06-18', 'Website', 'ACTIVE', '', 'Liu Zhongming', '917-555-0205', 'liu.zhongming.guardian@gmail.com', 'FATHER', '2024-04-15 11:00:00.000', 1, '2024-04-15 11:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO student (id, student_no, student_name, english_name, gender, birth_date, source_channel, status, notes, guardian_name, guardian_phone, guardian_email, guardian_relation_type, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (8006, 'ST2024006', '周雅婷', 'Yating Zhou', 'FEMALE', '2011-09-05', 'Referral', 'ACTIVE', '', 'Zhou Huifang', '917-555-0206', 'zhou.huifang.guardian@gmail.com', 'MOTHER', '2024-05-01 09:00:00.000', 1, '2024-05-01 09:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;


-- ── 3. Guardian Contacts ─────────────────────────────────────
INSERT INTO guardian_contact (id, student_id, contact_name, relation_type, mobile, email, is_primary, status, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (9001, 8001, 'Zhang Li', 'MOTHER', '917-555-0211', 'zhangli.guardian@gmail.com', 1, 'ACTIVE', '', '2024-02-10 10:00:00.000', 1, '2024-02-10 10:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO guardian_contact (id, student_id, contact_name, relation_type, mobile, email, is_primary, status, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (9002, 8002, 'Li Ming', 'FATHER', '917-555-0212', 'liming.guardian@gmail.com', 1, 'ACTIVE', '', '2024-03-01 11:00:00.000', 1, '2024-03-01 11:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO guardian_contact (id, student_id, contact_name, relation_type, mobile, email, is_primary, status, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (9003, 8003, 'Wang Fang', 'MOTHER', '917-555-0213', 'wangfang.guardian@gmail.com', 1, 'ACTIVE', '', '2024-03-15 09:00:00.000', 1, '2024-03-15 09:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO guardian_contact (id, student_id, contact_name, relation_type, mobile, email, is_primary, status, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (9004, 8004, 'Chen Lihua', 'FATHER', '917-555-0214', 'chenlihua.guardian@gmail.com', 1, 'ACTIVE', '', '2024-04-01 10:00:00.000', 1, '2024-04-01 10:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

-- ── 4. Campuses ──────────────────────────────────────────────
INSERT INTO campus (id, campus_name, display_order, status, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (6001, 'Manhattan Campus', 1, 'ACTIVE', 'Main campus in Midtown Manhattan', '2023-01-01 00:00:00.000', 1, '2023-01-01 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO campus (id, campus_name, display_order, status, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (6002, 'Brooklyn Center', 2, 'ACTIVE', 'Satellite campus in Downtown Brooklyn', '2023-01-01 00:00:00.000', 1, '2023-01-01 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

-- ── 5. Classrooms ────────────────────────────────────────────
INSERT INTO classroom (id, room_code, room_name, campus, capacity, floor, status, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (6101, 'MH-101', 'Room 101', 'Manhattan Campus', 4, '1F', 'ACTIVE', 'Small classroom for 1-on-1 sessions', '2023-01-01 00:00:00.000', 1, '2023-01-01 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO classroom (id, room_code, room_name, campus, capacity, floor, status, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (6102, 'MH-102', 'Room 102', 'Manhattan Campus', 4, '1F', 'ACTIVE', 'Small classroom for 1-on-1 sessions', '2023-01-01 00:00:00.000', 1, '2023-01-01 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO classroom (id, room_code, room_name, campus, capacity, floor, status, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (6103, 'MH-201', 'Room 201', 'Manhattan Campus', 6, '2F', 'ACTIVE', 'Medium room for group sessions', '2023-01-01 00:00:00.000', 1, '2023-01-01 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO classroom (id, room_code, room_name, campus, capacity, floor, status, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (6104, 'BK-101', 'Room 101', 'Brooklyn Center', 4, '1F', 'ACTIVE', 'Brooklyn classroom', '2023-01-01 00:00:00.000', 1, '2023-01-01 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

-- ── 6. Subjects ─────────────────────────────────────────────
INSERT INTO subject (id, subject_name, display_order, status, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (6201, 'Mathematics', 1, 'ACTIVE', '', '2023-01-01 00:00:00.000', 1, '2023-01-01 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO subject (id, subject_name, display_order, status, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (6202, 'English', 2, 'ACTIVE', '', '2023-01-01 00:00:00.000', 1, '2023-01-01 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO subject (id, subject_name, display_order, status, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (6203, 'Science', 3, 'ACTIVE', '', '2023-01-01 00:00:00.000', 1, '2023-01-01 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO subject (id, subject_name, display_order, status, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (6204, 'SAT Prep', 4, 'ACTIVE', '', '2023-01-01 00:00:00.000', 1, '2023-01-01 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

-- ── 7. Course Products ───────────────────────────────────────
INSERT INTO course_product (id, product_code, product_name, subject_name, teaching_mode, default_total_units, default_lesson_units, list_price, status, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (6301, 'CP-001', 'SAT Math Intensive', 'Mathematics', 'ONE_ON_ONE', 40, 2, 3200.00, 'ACTIVE', 'SAT Math targeted training, 20 sessions of 1 hour each', '2024-01-01 00:00:00.000', 1, '2024-01-01 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO course_product (id, product_code, product_name, subject_name, teaching_mode, default_total_units, default_lesson_units, list_price, status, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (6302, 'CP-002', 'English Reading & Writing', 'English', 'ONE_ON_ONE', 60, 2, 4500.00, 'ACTIVE', 'English literacy and writing skills, 30 sessions', '2024-01-01 00:00:00.000', 1, '2024-01-01 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO course_product (id, product_code, product_name, subject_name, teaching_mode, default_total_units, default_lesson_units, list_price, status, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (6303, 'CP-003', 'Middle School Science', 'Science', 'ONE_ON_ONE', 40, 2, 3200.00, 'ACTIVE', 'Science fundamentals for grades 6-8', '2024-01-01 00:00:00.000', 1, '2024-01-01 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO course_product (id, product_code, product_name, subject_name, teaching_mode, default_total_units, default_lesson_units, list_price, status, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (6304, 'CP-004', 'SAT Full Prep', 'SAT Prep', 'ONE_ON_ONE', 80, 4, 6400.00, 'ACTIVE', 'Comprehensive SAT prep, 20 sessions of 2 hours each', '2024-01-01 00:00:00.000', 1, '2024-01-01 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

-- ── 8. Enrollment Orders ─────────────────────────────────────
INSERT INTO enrollment_order (id, enrollment_no, student_id, order_status, cashier_user_id, handled_by_name, currency_code, original_amount, discount_amount, received_amount, payment_status, remark, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (10001, 'ENR-2024-001', 8001, 'CONFIRMED', 1, 'System Admin', 'USD', 3200.00, 200.00, 3000.00, 'PAID', '', '2024-02-15 10:00:00.000', 1, '2024-02-15 10:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO enrollment_order (id, enrollment_no, student_id, order_status, cashier_user_id, handled_by_name, currency_code, original_amount, discount_amount, received_amount, payment_status, remark, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (10002, 'ENR-2024-002', 8002, 'CONFIRMED', 1, 'System Admin', 'USD', 4500.00, 0.00, 4500.00, 'PAID', '', '2024-03-05 11:00:00.000', 1, '2024-03-05 11:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO enrollment_order (id, enrollment_no, student_id, order_status, cashier_user_id, handled_by_name, currency_code, original_amount, discount_amount, received_amount, payment_status, remark, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (10003, 'ENR-2024-003', 8003, 'CONFIRMED', 1, 'System Admin', 'USD', 3200.00, 100.00, 3100.00, 'PAID', '', '2024-03-20 09:00:00.000', 1, '2024-03-20 09:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO enrollment_order (id, enrollment_no, student_id, order_status, cashier_user_id, handled_by_name, currency_code, original_amount, discount_amount, received_amount, payment_status, remark, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (10004, 'ENR-2024-004', 8004, 'CONFIRMED', 1, 'System Admin', 'USD', 6400.00, 400.00, 6000.00, 'PAID', '', '2024-04-05 10:00:00.000', 1, '2024-04-05 10:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO enrollment_order (id, enrollment_no, student_id, order_status, cashier_user_id, handled_by_name, currency_code, original_amount, discount_amount, received_amount, payment_status, remark, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (10005, 'ENR-2024-005', 8005, 'CONFIRMED', 1, 'System Admin', 'USD', 3200.00, 0.00, 3200.00, 'PAID', '', '2024-04-20 11:00:00.000', 1, '2024-04-20 11:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO enrollment_order (id, enrollment_no, student_id, order_status, cashier_user_id, handled_by_name, currency_code, original_amount, discount_amount, received_amount, payment_status, remark, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (10006, 'ENR-2024-006', 8006, 'CONFIRMED', 1, 'System Admin', 'USD', 4500.00, 300.00, 4200.00, 'PAID', '', '2024-05-10 09:00:00.000', 1, '2024-05-10 09:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

-- ── 9. Payment Records ───────────────────────────────────────
INSERT INTO payment_record (id, enrollment_order_id, payment_no, payment_method, payment_status, amount, paid_at, handled_by_user_id, handled_by_name, remark, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (11001, 10001, 'PAY-2024-001', 'CASH', 'PAID', 3000.00, '2024-02-15 10:30:00.000', 1, 'System Admin', '', '2024-02-15 10:30:00.000', 1, '2024-02-15 10:30:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO payment_record (id, enrollment_order_id, payment_no, payment_method, payment_status, amount, paid_at, handled_by_user_id, handled_by_name, remark, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (11002, 10002, 'PAY-2024-002', 'CASH', 'PAID', 4500.00, '2024-03-05 11:30:00.000', 1, 'System Admin', '', '2024-03-05 11:30:00.000', 1, '2024-03-05 11:30:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO payment_record (id, enrollment_order_id, payment_no, payment_method, payment_status, amount, paid_at, handled_by_user_id, handled_by_name, remark, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (11003, 10003, 'PAY-2024-003', 'CASH', 'PAID', 3100.00, '2024-03-20 09:30:00.000', 1, 'System Admin', '', '2024-03-20 09:30:00.000', 1, '2024-03-20 09:30:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO payment_record (id, enrollment_order_id, payment_no, payment_method, payment_status, amount, paid_at, handled_by_user_id, handled_by_name, remark, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (11004, 10004, 'PAY-2024-004', 'CASH', 'PAID', 6000.00, '2024-04-05 10:30:00.000', 1, 'System Admin', '', '2024-04-05 10:30:00.000', 1, '2024-04-05 10:30:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO payment_record (id, enrollment_order_id, payment_no, payment_method, payment_status, amount, paid_at, handled_by_user_id, handled_by_name, remark, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (11005, 10005, 'PAY-2024-005', 'CASH', 'PAID', 3200.00, '2024-04-20 11:30:00.000', 1, 'System Admin', '', '2024-04-20 11:30:00.000', 1, '2024-04-20 11:30:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO payment_record (id, enrollment_order_id, payment_no, payment_method, payment_status, amount, paid_at, handled_by_user_id, handled_by_name, remark, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (11006, 10006, 'PAY-2024-006', 'CASH', 'PAID', 4200.00, '2024-05-10 09:30:00.000', 1, 'System Admin', '', '2024-05-10 09:30:00.000', 1, '2024-05-10 09:30:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

-- ── 10. Course Packages ───────────────────────────────────────
INSERT INTO course_package (id, package_no, enrollment_order_id, student_id, course_product_id, package_name, subject_name, teaching_mode, total_units, default_lesson_units, consumed_units, remaining_units, original_amount, discount_amount, received_amount, package_status, start_date, estimated_end_date, completed_at, remark, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (12001, 'PKG-2024-001', 10001, 8001, 6301, 'SAT Math Intensive', 'Mathematics', 'ONE_ON_ONE', 40, 2, 20, 20, 3200.00, 200.00, 3000.00, 'ACTIVE', '2024-02-20', '2024-07-20', NULL, '', '2024-02-15 10:00:00.000', 1, '2024-02-15 10:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO course_package (id, package_no, enrollment_order_id, student_id, course_product_id, package_name, subject_name, teaching_mode, total_units, default_lesson_units, consumed_units, remaining_units, original_amount, discount_amount, received_amount, package_status, start_date, estimated_end_date, completed_at, remark, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (12002, 'PKG-2024-002', 10002, 8002, 6302, 'English Reading & Writing', 'English', 'ONE_ON_ONE', 60, 2, 10, 50, 4500.00, 0.00, 4500.00, 'ACTIVE', '2024-03-10', '2024-09-10', NULL, '', '2024-03-05 11:00:00.000', 1, '2024-03-05 11:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO course_package (id, package_no, enrollment_order_id, student_id, course_product_id, package_name, subject_name, teaching_mode, total_units, default_lesson_units, consumed_units, remaining_units, original_amount, discount_amount, received_amount, package_status, start_date, estimated_end_date, completed_at, remark, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (12003, 'PKG-2024-003', 10003, 8003, 6303, 'Middle School Science', 'Science', 'ONE_ON_ONE', 40, 2, 34, 6, 3200.00, 100.00, 3100.00, 'ACTIVE', '2024-03-25', '2024-06-25', NULL, '', '2024-03-20 09:00:00.000', 1, '2024-03-20 09:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO course_package (id, package_no, enrollment_order_id, student_id, course_product_id, package_name, subject_name, teaching_mode, total_units, default_lesson_units, consumed_units, remaining_units, original_amount, discount_amount, received_amount, package_status, start_date, estimated_end_date, completed_at, remark, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (12004, 'PKG-2024-004', 10004, 8004, 6304, 'SAT Full Prep', 'SAT Prep', 'ONE_ON_ONE', 80, 4, 16, 64, 6400.00, 400.00, 6000.00, 'ACTIVE', '2024-04-10', '2024-10-10', NULL, '', '2024-04-05 10:00:00.000', 1, '2024-04-05 10:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO course_package (id, package_no, enrollment_order_id, student_id, course_product_id, package_name, subject_name, teaching_mode, total_units, default_lesson_units, consumed_units, remaining_units, original_amount, discount_amount, received_amount, package_status, start_date, estimated_end_date, completed_at, remark, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (12005, 'PKG-2024-005', 10005, 8005, 6301, 'SAT Math Intensive', 'Mathematics', 'ONE_ON_ONE', 40, 2, 12, 28, 3200.00, 0.00, 3200.00, 'ACTIVE', '2024-04-25', '2024-08-25', NULL, '', '2024-04-20 11:00:00.000', 1, '2024-04-20 11:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO course_package (id, package_no, enrollment_order_id, student_id, course_product_id, package_name, subject_name, teaching_mode, total_units, default_lesson_units, consumed_units, remaining_units, original_amount, discount_amount, received_amount, package_status, start_date, estimated_end_date, completed_at, remark, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (12006, 'PKG-2024-006', 10006, 8006, 6302, 'English Reading & Writing', 'English', 'ONE_ON_ONE', 60, 2, 8, 52, 4500.00, 300.00, 4200.00, 'ACTIVE', '2024-05-15', '2024-11-15', NULL, '', '2024-05-10 09:00:00.000', 1, '2024-05-10 09:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

-- ── 11. Package Balance Snapshots ───────────────────────────
INSERT INTO package_balance_snapshot (package_id, total_units, consumed_units, remaining_units, last_ledger_id, snapshot_at)
VALUES (12001, 40, 20, 20, NULL, '2024-05-15 00:00:00.000')
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO package_balance_snapshot (package_id, total_units, consumed_units, remaining_units, last_ledger_id, snapshot_at)
VALUES (12002, 60, 10, 50, NULL, '2024-05-15 00:00:00.000')
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO package_balance_snapshot (package_id, total_units, consumed_units, remaining_units, last_ledger_id, snapshot_at)
VALUES (12003, 40, 34, 6, NULL, '2024-05-15 00:00:00.000')
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO package_balance_snapshot (package_id, total_units, consumed_units, remaining_units, last_ledger_id, snapshot_at)
VALUES (12004, 80, 16, 64, NULL, '2024-05-15 00:00:00.000')
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO package_balance_snapshot (package_id, total_units, consumed_units, remaining_units, last_ledger_id, snapshot_at)
VALUES (12005, 40, 12, 28, NULL, '2024-05-15 00:00:00.000')
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO package_balance_snapshot (package_id, total_units, consumed_units, remaining_units, last_ledger_id, snapshot_at)
VALUES (12006, 60, 8, 52, NULL, '2024-05-15 00:00:00.000')
ON DUPLICATE KEY UPDATE id=id;

-- ── 12. Package Teacher Assignments ──────────────────────────
INSERT INTO package_teacher_assignment (id, package_id, teacher_id, is_primary, effective_from, effective_to, assignment_status, change_reason, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (13001, 12001, 7001, 1, '2024-02-20 00:00:00.000', NULL, 'ACTIVE', '', '2024-02-15 10:00:00.000', 1, '2024-02-15 10:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO package_teacher_assignment (id, package_id, teacher_id, is_primary, effective_from, effective_to, assignment_status, change_reason, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (13002, 12002, 7002, 1, '2024-03-10 00:00:00.000', NULL, 'ACTIVE', '', '2024-03-05 11:00:00.000', 1, '2024-03-05 11:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO package_teacher_assignment (id, package_id, teacher_id, is_primary, effective_from, effective_to, assignment_status, change_reason, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (13003, 12003, 7004, 1, '2024-03-25 00:00:00.000', NULL, 'ACTIVE', '', '2024-03-20 09:00:00.000', 1, '2024-03-20 09:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO package_teacher_assignment (id, package_id, teacher_id, is_primary, effective_from, effective_to, assignment_status, change_reason, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (13004, 12004, 7003, 1, '2024-04-10 00:00:00.000', NULL, 'ACTIVE', '', '2024-04-05 10:00:00.000', 1, '2024-04-05 10:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO package_teacher_assignment (id, package_id, teacher_id, is_primary, effective_from, effective_to, assignment_status, change_reason, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (13005, 12005, 7001, 1, '2024-04-25 00:00:00.000', NULL, 'ACTIVE', '', '2024-04-20 11:00:00.000', 1, '2024-04-20 11:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO package_teacher_assignment (id, package_id, teacher_id, is_primary, effective_from, effective_to, assignment_status, change_reason, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (13006, 12006, 7002, 1, '2024-05-15 00:00:00.000', NULL, 'ACTIVE', '', '2024-05-10 09:00:00.000', 1, '2024-05-10 09:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

-- ── 13. Recurring Schedule Rules ───────────────────────────
INSERT INTO recurring_schedule_rule (id, package_id, primary_teacher_id, default_classroom_id, rule_status, start_date, end_date, generation_horizon_days, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (14001, 12001, 7001, 6101, 'ACTIVE', '2024-02-20', '2024-07-20', 90, 'SAT Math weekly sessions', '2024-02-15 10:00:00.000', 1, '2024-02-15 10:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO recurring_schedule_rule (id, package_id, primary_teacher_id, default_classroom_id, rule_status, start_date, end_date, generation_horizon_days, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (14002, 12002, 7002, 6102, 'ACTIVE', '2024-03-10', '2024-09-10', 90, 'English weekly sessions', '2024-03-05 11:00:00.000', 1, '2024-03-05 11:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO recurring_schedule_rule (id, package_id, primary_teacher_id, default_classroom_id, rule_status, start_date, end_date, generation_horizon_days, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (14003, 12003, 7004, 6103, 'ACTIVE', '2024-03-25', '2024-06-25', 90, 'Science weekly sessions', '2024-03-20 09:00:00.000', 1, '2024-03-20 09:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO recurring_schedule_rule (id, package_id, primary_teacher_id, default_classroom_id, rule_status, start_date, end_date, generation_horizon_days, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (14004, 12004, 7003, 6101, 'ACTIVE', '2024-04-10', '2024-10-10', 90, 'SAT Full Prep weekly sessions', '2024-04-05 10:00:00.000', 1, '2024-04-05 10:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO recurring_schedule_rule (id, package_id, primary_teacher_id, default_classroom_id, rule_status, start_date, end_date, generation_horizon_days, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (14005, 12005, 7001, 6102, 'ACTIVE', '2024-04-25', '2024-08-25', 90, 'SAT Math weekly sessions', '2024-04-20 11:00:00.000', 1, '2024-04-20 11:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

-- ── 14. Recurring Schedule Slots ────────────────────────────
INSERT INTO recurring_schedule_slot (id, rule_id, weekday, start_time, end_time, planned_units, slot_status, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (15001, 14001, 3, '16:00:00', '18:00:00', 4, 'ACTIVE', '2024-02-15 10:00:00.000', 1, '2024-02-15 10:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO recurring_schedule_slot (id, rule_id, weekday, start_time, end_time, planned_units, slot_status, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (15002, 14001, 5, '16:00:00', '18:00:00', 4, 'ACTIVE', '2024-02-15 10:00:00.000', 1, '2024-02-15 10:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO recurring_schedule_slot (id, rule_id, weekday, start_time, end_time, planned_units, slot_status, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (15003, 14002, 2, '17:00:00', '19:00:00', 4, 'ACTIVE', '2024-03-05 11:00:00.000', 1, '2024-03-05 11:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO recurring_schedule_slot (id, rule_id, weekday, start_time, end_time, planned_units, slot_status, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (15004, 14002, 6, '10:00:00', '12:00:00', 4, 'ACTIVE', '2024-03-05 11:00:00.000', 1, '2024-03-05 11:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO recurring_schedule_slot (id, rule_id, weekday, start_time, end_time, planned_units, slot_status, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (15005, 14003, 1, '16:00:00', '18:00:00', 4, 'ACTIVE', '2024-03-20 09:00:00.000', 1, '2024-03-20 09:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO recurring_schedule_slot (id, rule_id, weekday, start_time, end_time, planned_units, slot_status, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (15006, 14003, 4, '16:00:00', '18:00:00', 4, 'ACTIVE', '2024-03-20 09:00:00.000', 1, '2024-03-20 09:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO recurring_schedule_slot (id, rule_id, weekday, start_time, end_time, planned_units, slot_status, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (15007, 14004, 3, '18:00:00', '22:00:00', 8, 'ACTIVE', '2024-04-05 10:00:00.000', 1, '2024-04-05 10:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO recurring_schedule_slot (id, rule_id, weekday, start_time, end_time, planned_units, slot_status, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (15008, 14004, 6, '14:00:00', '18:00:00', 8, 'ACTIVE', '2024-04-05 10:00:00.000', 1, '2024-04-05 10:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO recurring_schedule_slot (id, rule_id, weekday, start_time, end_time, planned_units, slot_status, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (15009, 14005, 5, '15:00:00', '17:00:00', 4, 'ACTIVE', '2024-04-20 11:00:00.000', 1, '2024-04-20 11:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO recurring_schedule_slot (id, rule_id, weekday, start_time, end_time, planned_units, slot_status, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (15010, 14005, 6, '09:00:00', '11:00:00', 4, 'ACTIVE', '2024-04-20 11:00:00.000', 1, '2024-04-20 11:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

-- ── 15. Lesson Occurrences ────────────────────────────────────
INSERT INTO lesson_occurrence (id, lesson_no, package_id, student_id, schedule_rule_id, schedule_slot_id, occurrence_type, planned_teacher_id, planned_classroom_id, planned_start_at, planned_end_at, planned_units, planning_status, source_occurrence_id, conflict_checked, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (16001, 'L-2024-05-15-001', 12001, 8001, 14001, 15001, 'RECURRING', 7001, 6101, '2024-05-15 16:00:00.000', '2024-05-15 18:00:00.000', 4, 'CONFIRMED', NULL, 1, '', '2024-02-15 10:00:00.000', 1, '2024-05-15 18:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO lesson_occurrence (id, lesson_no, package_id, student_id, schedule_rule_id, schedule_slot_id, occurrence_type, planned_teacher_id, planned_classroom_id, planned_start_at, planned_end_at, planned_units, planning_status, source_occurrence_id, conflict_checked, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (16002, 'L-2024-05-16-001', 12002, 8002, 14002, 15003, 'RECURRING', 7002, 6102, '2024-05-21 17:00:00.000', '2024-05-21 19:00:00.000', 4, 'SCHEDULED', NULL, 1, '', '2024-03-05 11:00:00.000', 1, '2024-03-05 11:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

-- ── 16. Lesson Records & Hour Ledger ────────────────────────────
INSERT INTO lesson_record (id, occurrence_id, package_id, student_id, actual_teacher_id, actual_classroom_id, actual_start_at, actual_end_at, actual_units, result_status, confirmed_by_user_id, confirmed_at, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (17001, 16001, 12001, 8001, 7001, 6101, '2024-05-15 16:00:00.000', '2024-05-15 18:00:00.000', 4, 'COMPLETED', 1, '2024-05-15 18:05:00.000', '', '2024-05-15 18:05:00.000', 1, '2024-05-15 18:05:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO hour_ledger (id, package_id, lesson_record_id, occurrence_id, ledger_type, change_units, balance_after_units, business_date, reason_code, reason_desc, operated_by, operated_at, notes, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (18001, 12001, 17001, 16001, 'DEDUCT', -4, 16, '2024-05-15', 'COMPLETED', 'Lesson confirmed', 1, '2024-05-15 18:05:00.000', '', '2024-05-15 18:05:00.000', 1, '2024-05-15 18:05:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

-- ── 17. Notification Templates ──────────────────────────────────
INSERT INTO notification_template (id, template_code, template_name, channel_type, subject_template, body_template, status, remark, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (1, 'LESSON_CONFIRMED_DEFAULT', 'Lesson Confirmed - Default', 'EMAIL', '${brandName} - Lesson Confirmed: ${studentName}', 'Dear Parent,

The lesson for ${studentName} has been confirmed.

Package: ${packageName}
Status: ${lessonStatus}
Hours deducted: ${deductHours}
Remaining hours: ${remainingHours}
Estimated completion: ${estimatedEndDate}

Best regards,
${brandName}', 'ACTIVE', 'Default email sent when lesson is marked as COMPLETED', '2024-01-01 00:00:00.000', 1, '2024-01-01 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO notification_template (id, template_code, template_name, channel_type, subject_template, body_template, status, remark, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (2, 'LOW_BALANCE_DEFAULT', 'Low Balance Warning - Default', 'EMAIL', '${brandName} - Low Balance Warning: ${studentName}', 'Dear Parent,

This is to inform you that ${studentName}'s course package is running low.

Package: ${packageName}
Remaining hours: ${remainingHours}

Please consider purchasing additional hours to avoid interruption.

Best regards,
${brandName}', 'ACTIVE', 'Email sent when package balance drops below threshold', '2024-01-01 00:00:00.000', 1, '2024-01-01 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

-- ── 18. Notification Trigger Rules ────────────────────────────
INSERT INTO notification_trigger_rule (id, trigger_type, template_id, enabled, recipient_type, custom_recipients, threshold_value, remark, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (1, 'LESSON_CONFIRMED', 1, 1, 'GUARDIAN_EMAIL', NULL, NULL, 'Send email to guardian after lesson confirmation', '2024-05-15 00:00:00.000', 1, '2024-05-15 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO notification_trigger_rule (id, trigger_type, template_id, enabled, recipient_type, custom_recipients, threshold_value, remark, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (2, 'LOW_BALANCE', 2, 1, 'GUARDIAN_EMAIL', NULL, 4, 'Notify guardian when package balance drops below threshold', '2024-05-15 00:00:00.000', 1, '2024-05-15 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

-- ── 19. User Accounts ───────────────────────────────────────────
-- Password for all users: admin123 (bcrypt hash)
SET @PASS = '{bcrypt}$2b$12$Qv8n1eJPgIUwRToa3SOVL.3WSBG.x.747wvtFCBweU80udprGHq4K';

INSERT INTO sys_user (id, username, password, user_type, display_name, email, status, last_login_at, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (2001, 'sarah.johnson', @PASS, 'TEACHER', 'Sarah Johnson', 'sarah.johnson@jinbest.edu', 'ACTIVE', NULL, '2024-01-01 00:00:00.000', 1, '2024-01-01 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO sys_user (id, username, password, user_type, display_name, email, status, last_login_at, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (2002, 'michael.chen', @PASS, 'TEACHER', 'Michael Chen', 'michael.chen@jinbest.edu', 'ACTIVE', NULL, '2024-01-01 00:00:00.000', 1, '2024-01-01 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO sys_user (id, username, password, user_type, display_name, email, status, last_login_at, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (2003, 'emily.rodriguez', @PASS, 'TEACHER', 'Emily Rodriguez', 'emily.rodriguez@jinbest.edu', 'ACTIVE', NULL, '2024-01-01 00:00:00.000', 1, '2024-01-01 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO sys_user (id, username, password, user_type, display_name, email, status, last_login_at, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (2004, 'david.kim', @PASS, 'TEACHER', 'David Kim', 'david.kim@jinbest.edu', 'ACTIVE', NULL, '2024-01-01 00:00:00.000', 1, '2024-01-01 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO sys_user (id, username, password, user_type, display_name, email, status, last_login_at, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (8001, 'zhang.ming', @PASS, 'STUDENT', '张明轩', 'zhang.wei.guardian@gmail.com', 'ACTIVE', NULL, '2024-02-10 10:00:00.000', 1, '2024-02-10 10:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO sys_user (id, username, password, user_type, display_name, email, status, last_login_at, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (8002, 'li.yuhan', @PASS, 'STUDENT', '李雨涵', 'li.xiaoming.guardian@gmail.com', 'ACTIVE', NULL, '2024-03-01 11:00:00.000', 1, '2024-03-01 11:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO sys_user (id, username, password, user_type, display_name, email, status, last_login_at, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (8003, 'wang.haoran', @PASS, 'STUDENT', '王浩然', 'wang.jiaming.guardian@gmail.com', 'ACTIVE', NULL, '2024-03-15 09:00:00.000', 1, '2024-03-15 09:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO sys_user (id, username, password, user_type, display_name, email, status, last_login_at, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (8004, 'chen.siqi', @PASS, 'STUDENT', '陈思琪', 'chen.guohua.guardian@gmail.com', 'ACTIVE', NULL, '2024-04-01 10:00:00.000', 1, '2024-04-01 10:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO sys_user (id, username, password, user_type, display_name, email, status, last_login_at, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (8005, 'liu.zihan', @PASS, 'STUDENT', '刘子涵', 'liu.zhongming.guardian@gmail.com', 'ACTIVE', NULL, '2024-04-15 11:00:00.000', 1, '2024-04-15 11:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO sys_user (id, username, password, user_type, display_name, email, status, last_login_at, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (8006, 'zhou.yating', @PASS, 'STUDENT', '周雅婷', 'zhou.huifang.guardian@gmail.com', 'ACTIVE', NULL, '2024-05-01 09:00:00.000', 1, '2024-05-01 09:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

-- ── 20. Teacher Role Assignments ────────────────────────────────
INSERT INTO sys_user_role (id, user_id, role_code, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (1, 2001, 'TEACHER', '2024-01-01 00:00:00.000', 1, '2024-01-01 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO sys_user_role (id, user_id, role_code, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (2, 2002, 'TEACHER', '2024-01-01 00:00:00.000', 1, '2024-01-01 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO sys_user_role (id, user_id, role_code, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (3, 2003, 'TEACHER', '2024-01-01 00:00:00.000', 1, '2024-01-01 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO sys_user_role (id, user_id, role_code, created_at, created_by, updated_at, updated_by, is_deleted, version)
VALUES (4, 2004, 'TEACHER', '2024-01-01 00:00:00.000', 1, '2024-01-01 00:00:00.000', 1, 0, 0)
ON DUPLICATE KEY UPDATE id=id;

-- ── Done ─────────────────────────────────────────────────────
SELECT 'Test data seeded successfully. 6 students, 4 teachers, 6 packages, 2 lesson occurrences, notification templates & triggers enabled, 10 user accounts.' AS result;