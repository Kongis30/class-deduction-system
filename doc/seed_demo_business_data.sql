-- Demo business data for local review.
-- This script is additive and idempotent: it only upserts DEMO-* records.

USE `class_deduction_system`;
SET NAMES utf8mb4;

SET @demo_password_hash = '{bcrypt}$2b$12$zXiRuqYhdEukoa5UEO5sOuMXLD/g1KKQMKwWKAFdjK3QnnDVgFBei';

-- Campus and rooms
INSERT INTO campus (
  id, campus_name, display_order, status, default_pickup_fee_amount, default_dropoff_fee_amount, notes
) VALUES
  (9100001, 'DEMO South Campus', 1, 'ACTIVE', 35.00, 35.00, 'Demo campus with service fee defaults.'),
  (9100002, 'DEMO North Campus', 2, 'ACTIVE', 0.00, 0.00, 'Demo campus for Saturday classes.')
ON DUPLICATE KEY UPDATE
  status = VALUES(status),
  default_pickup_fee_amount = VALUES(default_pickup_fee_amount),
  default_dropoff_fee_amount = VALUES(default_dropoff_fee_amount),
  notes = VALUES(notes),
  is_deleted = 0;

INSERT INTO classroom (
  id, room_code, room_name, campus, campus_id, capacity, floor, status, notes
) VALUES
  (9100101, 'DEMO-S-101', 'DEMO Room 101', 'DEMO South Campus', 9100001, 12, '1F', 'ACTIVE', 'Group classroom.'),
  (9100102, 'DEMO-S-102', 'DEMO Room 102', 'DEMO South Campus', 9100001, 4, '1F', 'ACTIVE', '1-on-1 room.'),
  (9100103, 'DEMO-N-201', 'DEMO Room 201', 'DEMO North Campus', 9100002, 16, '2F', 'ACTIVE', 'Saturday classroom.'),
  (9100104, 'DEMO-S-103', 'DEMO Conflict Room', 'DEMO South Campus', 9100001, 4, '1F', 'ACTIVE', 'Room used to review overlapping calendar events.')
ON DUPLICATE KEY UPDATE
  room_name = VALUES(room_name),
  campus = VALUES(campus),
  campus_id = VALUES(campus_id),
  capacity = VALUES(capacity),
  status = VALUES(status),
  notes = VALUES(notes),
  is_deleted = 0;

-- Teachers and students
INSERT INTO teacher (
  id, teacher_no, teacher_name, email, mobile, subject_tags, status, campus_id, hire_date, notes
) VALUES
  (9100201, 'T-BIZ-DEMO-01', 'DEMO Ms. Lee', 'demo.lee@example.com', '917-555-9101', JSON_ARRAY('Math', '1-on-1'), 'ACTIVE', 9100001, '2025-09-01', 'Demo teacher with an account.'),
  (9100202, 'T-BIZ-DEMO-02', 'DEMO Mr. Chen', 'demo.chen@example.com', '917-555-9102', JSON_ARRAY('ELA', 'Group'), 'ACTIVE', 9100001, '2025-09-01', 'Demo group class teacher with an account.'),
  (9100203, 'T-BIZ-DEMO-03', 'DEMO Ms. Smith', 'demo.smith@example.com', '917-555-9103', JSON_ARRAY('Science', 'Saturday'), 'ACTIVE', 9100002, '2025-09-01', 'Demo teacher without login access.'),
  (9100204, 'T-BIZ-DEMO-04', 'DEMO Mr. RoomConflict', 'demo.room.conflict@example.com', '917-555-9104', JSON_ARRAY('Math'), 'ACTIVE', 9100001, '2025-09-01', 'Different teacher used for room conflict.')
ON DUPLICATE KEY UPDATE
  teacher_no = VALUES(teacher_no),
  teacher_name = VALUES(teacher_name),
  email = VALUES(email),
  mobile = VALUES(mobile),
  subject_tags = VALUES(subject_tags),
  status = VALUES(status),
  campus_id = VALUES(campus_id),
  notes = VALUES(notes),
  is_deleted = 0;

INSERT INTO student (
  id, student_no, student_name, english_name, gender, birth_date, grade, school, source_channel,
  status, campus_id, notes, guardian_name, guardian_phone, guardian_phone_2, guardian_email,
  guardian_relation_type, pickup_required, pickup_address, dropoff_required, dropoff_address
) VALUES
  (9100301, 'STU-BIZ-DEMO-01', 'DEMO Amy Wang', 'Amy', 'Female', '2014-03-15', 'G4', 'PS 101', 'Walk-in', 'ACTIVE', 9100001, 'Demo student with pickup need and account.', 'Linda Wang', '917-555-9201', NULL, 'amy.demo.parent@example.com', 'MOTHER', 1, '136-20 Maple Ave', 0, NULL),
  (9100302, 'STU-BIZ-DEMO-02', 'DEMO Brian Chen', 'Brian', 'Male', '2013-07-22', 'G5', 'PS 120', 'Referral', 'ACTIVE', 9100001, 'Demo student with account and partial payment.', 'Grace Chen', '917-555-9202', NULL, 'brian.demo.parent@example.com', 'MOTHER', 0, NULL, 0, NULL),
  (9100303, 'STU-BIZ-DEMO-03', 'DEMO Cindy Liu', 'Cindy', 'Female', '2013-01-08', 'G5', 'PS 120', 'Referral', 'ACTIVE', 9100001, 'Demo paid group student without login.', 'Helen Liu', '917-555-9203', NULL, 'cindy.demo.parent@example.com', 'MOTHER', 0, NULL, 0, NULL),
  (9100304, 'STU-BIZ-DEMO-04', 'DEMO David Kim', 'David', 'Male', '2011-11-30', 'G7', 'JHS 189', 'Website', 'ACTIVE', 9100002, 'Demo unpaid Saturday student.', 'Sarah Kim', '917-555-9204', NULL, 'david.demo.parent@example.com', 'MOTHER', 0, NULL, 0, NULL),
  (9100305, 'STU-BIZ-DEMO-05', 'DEMO Eva RoomConflict', 'Eva', 'Female', '2014-06-18', 'G4', 'PS 101', 'Website', 'ACTIVE', 9100001, 'Student used to create room conflict.', 'Ivy Lin', '917-555-9205', NULL, 'eva.demo.parent@example.com', 'MOTHER', 0, NULL, 0, NULL)
ON DUPLICATE KEY UPDATE
  student_no = VALUES(student_no),
  student_name = VALUES(student_name),
  english_name = VALUES(english_name),
  grade = VALUES(grade),
  school = VALUES(school),
  status = VALUES(status),
  campus_id = VALUES(campus_id),
  notes = VALUES(notes),
  guardian_name = VALUES(guardian_name),
  guardian_phone = VALUES(guardian_phone),
  guardian_email = VALUES(guardian_email),
  pickup_required = VALUES(pickup_required),
  pickup_address = VALUES(pickup_address),
  dropoff_required = VALUES(dropoff_required),
  dropoff_address = VALUES(dropoff_address),
  is_deleted = 0;

-- Login accounts. Password is the same as the local admin seed password.
INSERT INTO sys_user (
  id, username, password_hash, user_type, display_name, mobile, email, teacher_id, student_id, status, remark
) VALUES
  (9100901, 'demo.staff@jin', @demo_password_hash, 'STAFF', 'DEMO Staff', NULL, 'demo.staff@example.com', NULL, NULL, 'ACTIVE', 'Demo staff login.'),
  (9100902, 'demo.lee@jin', @demo_password_hash, 'TEACHER', 'DEMO Ms. Lee', '917-555-9101', 'demo.lee@example.com', 9100201, NULL, 'ACTIVE', 'Demo teacher login.'),
  (9100903, 'demo.chen@jin', @demo_password_hash, 'TEACHER', 'DEMO Mr. Chen', '917-555-9102', 'demo.chen@example.com', 9100202, NULL, 'ACTIVE', 'Demo group teacher login.'),
  (9100904, 'demo.amy@jin', @demo_password_hash, 'STUDENT', 'DEMO Amy Wang', NULL, 'amy.demo.parent@example.com', NULL, 9100301, 'ACTIVE', 'Demo student login.'),
  (9100905, 'demo.brian@jin', @demo_password_hash, 'STUDENT', 'DEMO Brian Chen', NULL, 'brian.demo.parent@example.com', NULL, 9100302, 'ACTIVE', 'Demo student login.')
ON DUPLICATE KEY UPDATE
  password_hash = VALUES(password_hash),
  user_type = VALUES(user_type),
  display_name = VALUES(display_name),
  mobile = VALUES(mobile),
  email = VALUES(email),
  teacher_id = VALUES(teacher_id),
  student_id = VALUES(student_id),
  status = VALUES(status),
  remark = VALUES(remark),
  is_deleted = 0;

INSERT INTO sys_user_role (id, user_id, role_id)
VALUES
  (9100951, 9100901, 110),
  (9100952, 9100902, 120),
  (9100953, 9100903, 120),
  (9100954, 9100904, 130),
  (9100955, 9100905, 130)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- Course products, grade/class prices, and class schedules
INSERT INTO course_product (
  id, product_code, product_name, course_type, register_class, subject_name, teaching_mode,
  default_total_units, default_lesson_units, billing_type, start_date, total_sessions,
  start_month, end_month, default_classroom_id, default_teacher_id, list_price, status, notes
) VALUES
  (9100401, 'BIZ-DEMO-O2O-MATH-40', 'DEMO 1-on-1 Math 40h', 'ONE_ON_ONE', '1 on 1', 'Mathematics', 'ONE_ON_ONE', 80, 3, NULL, NULL, NULL, NULL, NULL, 9100102, 9100201, 2400.00, 'ACTIVE', 'Private package for recurring schedule demo.'),
  (9100402, 'BIZ-DEMO-AFTER-G3G5', 'DEMO After School Enrichment', 'AFTER_SCHOOL', 'After School', 'Mathematics', 'GROUP', NULL, NULL, 'MONTHLY', NULL, NULL, '2026-07-01', '2026-09-01', 9100101, 9100202, 580.00, 'ACTIVE', 'Monthly group product.'),
  (9100403, 'BIZ-DEMO-SAT-G6G8', 'DEMO Saturday Math Booster', 'SATURDAY', 'Saturday Class', 'Mathematics', 'GROUP', NULL, NULL, 'PER_SESSION', '2026-08-01', 8, NULL, NULL, 9100103, 9100203, 95.00, 'ACTIVE', 'Per-session group product.'),
  (9100404, 'BIZ-DEMO-CONFLICT-WORKSHOP', 'DEMO Conflict Workshop', 'AFTER_SCHOOL', 'After School', 'Mathematics', 'GROUP', NULL, NULL, 'PER_SESSION', '2026-07-28', 1, NULL, NULL, 9100102, 9100204, 60.00, 'ACTIVE', 'Single class intentionally overlapping with a private lesson.')
ON DUPLICATE KEY UPDATE
  product_name = VALUES(product_name),
  teaching_mode = VALUES(teaching_mode),
  default_classroom_id = VALUES(default_classroom_id),
  default_teacher_id = VALUES(default_teacher_id),
  list_price = VALUES(list_price),
  status = VALUES(status),
  notes = VALUES(notes),
  is_deleted = 0;

INSERT INTO class_group (
  id, class_name, description, teacher_id, classroom_id, list_price, schedule_desc, status,
  product_id, grade_band, billing_type, start_date, total_sessions, start_month, end_month
) VALUES
  (9100601, 'DEMO After School G3-G5', 'Demo monthly class with Monday/Wednesday lessons.', 9100202, 9100101, 580.00, 'Mon/Wed 15:30-17:30', 'ACTIVE', 9100402, 'G3-G5', 'MONTHLY', NULL, NULL, '2026-07-01', '2026-09-01'),
  (9100602, 'DEMO Saturday Math G6-G8', 'Demo Saturday per-session class.', 9100203, 9100103, 95.00, 'Sat 09:00-12:00', 'ACTIVE', 9100403, 'G6-G8', 'PER_SESSION', '2026-08-01', 8, NULL, NULL),
  (9100603, 'DEMO Conflict Workshop', 'Intentional student and room conflict with Amy private lesson.', 9100204, 9100102, 60.00, 'Tue 17:15-18:15', 'ACTIVE', 9100404, 'G4-G5', 'PER_SESSION', '2026-07-28', 1, NULL, NULL)
ON DUPLICATE KEY UPDATE
  class_name = VALUES(class_name),
  description = VALUES(description),
  teacher_id = VALUES(teacher_id),
  classroom_id = VALUES(classroom_id),
  list_price = VALUES(list_price),
  schedule_desc = VALUES(schedule_desc),
  status = VALUES(status),
  product_id = VALUES(product_id),
  grade_band = VALUES(grade_band),
  billing_type = VALUES(billing_type),
  is_deleted = 0;

INSERT INTO class_group_schedule_slot (id, class_group_id, weekday, start_time, end_time)
VALUES
  (9100701, 9100601, 1, '15:30:00', '17:30:00'),
  (9100702, 9100601, 3, '15:30:00', '17:30:00'),
  (9100703, 9100602, 6, '09:00:00', '12:00:00'),
  (9100704, 9100603, 2, '17:15:00', '18:15:00')
ON DUPLICATE KEY UPDATE
  weekday = VALUES(weekday),
  start_time = VALUES(start_time),
  end_time = VALUES(end_time),
  is_deleted = 0;

INSERT INTO course_price_option (
  id, option_code, course_type, register_class, grade_band, period_label, duration_label,
  time_slot_label, detail_text, one_on_one_hours, group_session_count, group_month_count,
  price_amount, currency_code, course_product_id, class_id, sort_order, status, notes
) VALUES
  (9100501, 'BIZ-DEMO-O2O-MATH-40', 'ONE_ON_ONE', '1 on 1', 'G3-G8', '40 hours', '40 hours', 'By weekly schedule', 'DEMO 1-on-1 Math 40h · G3-G8', 40.00, NULL, NULL, 2400.00, 'USD', 9100401, NULL, 1, 'ACTIVE', 'Demo private price.'),
  (9100502, 'BIZ-DEMO-AFTER-G3G5-MONTH', 'AFTER_SCHOOL', 'After School', 'G3-G5', 'Monthly', NULL, 'Mon/Wed 15:30-17:30', 'DEMO After School G3-G5 · Monthly', NULL, NULL, 2, 580.00, 'USD', 9100402, 9100601, 2, 'ACTIVE', 'Demo monthly class price.'),
  (9100503, 'BIZ-DEMO-SAT-G6G8-SESSION', 'SATURDAY', 'Saturday Class', 'G6-G8', '8 sessions', NULL, 'Sat 09:00-12:00', 'DEMO Saturday Math G6-G8 · Per session', NULL, 8, NULL, 95.00, 'USD', 9100403, 9100602, 3, 'ACTIVE', 'Demo per-session class price.'),
  (9100504, 'BIZ-DEMO-CONFLICT-WORKSHOP-SESSION', 'AFTER_SCHOOL', 'After School', 'G4-G5', '1 session', NULL, 'Tue 17:15-18:15', 'DEMO Conflict Workshop · Single session', NULL, 1, NULL, 60.00, 'USD', 9100404, 9100603, 4, 'ACTIVE', 'Intentional conflict price row.')
ON DUPLICATE KEY UPDATE
  detail_text = VALUES(detail_text),
  price_amount = VALUES(price_amount),
  course_product_id = VALUES(course_product_id),
  class_id = VALUES(class_id),
  status = VALUES(status),
  notes = VALUES(notes),
  is_deleted = 0;

-- Enrollment orders: partial, paid, unpaid, private, group, and conflict.
INSERT INTO enrollment_order (
  id, enrollment_no, student_id, class_id, campus_id, campus_name_snapshot, order_status,
  cashier_user_id, handled_by_name, signup_date, period_start_date, period_end_date,
  student_name_snapshot, grade_snapshot, school_snapshot, parent_phone_snapshot,
  parent_phone_2_snapshot, pickup_required_snapshot, pickup_address_snapshot,
  dropoff_required_snapshot, dropoff_address_snapshot, currency_code, tuition_subtotal_amount,
  pickup_fee_amount, dropoff_fee_amount, extra_fee_amount, original_amount, discount_amount,
  received_amount, amount_due, balance_amount, payment_status, remark
) VALUES
  (9101001, 'ENR-BIZ-DEMO-001', 9100301, NULL, 9100001, 'DEMO South Campus', 'CONFIRMED', 1, 'Admin', '2026-07-26', '2026-07-28', '2026-12-31', 'DEMO Amy Wang', 'G4', 'PS 101', '917-555-9201', NULL, 1, '136-20 Maple Ave', 0, NULL, 'USD', 2400.00, 35.00, 0.00, 0.00, 2435.00, 100.00, 1200.00, 2335.00, 1135.00, 'PARTIAL', 'Demo private enrollment with pickup charged once.'),
  (9101002, 'ENR-BIZ-DEMO-002', 9100302, 9100601, 9100001, 'DEMO South Campus', 'CONFIRMED', 1, 'Admin', '2026-07-26', '2026-07-27', '2026-09-30', 'DEMO Brian Chen', 'G5', 'PS 120', '917-555-9202', NULL, 0, NULL, 0, NULL, 'USD', 1160.00, 0.00, 0.00, 0.00, 1160.00, 0.00, 600.00, 1160.00, 560.00, 'PARTIAL', 'Demo monthly class partial payment.'),
  (9101003, 'ENR-BIZ-DEMO-003', 9100303, 9100601, 9100001, 'DEMO South Campus', 'CONFIRMED', 1, 'Admin', '2026-07-26', '2026-07-27', '2026-09-30', 'DEMO Cindy Liu', 'G5', 'PS 120', '917-555-9203', NULL, 0, NULL, 0, NULL, 'USD', 1160.00, 0.00, 0.00, 0.00, 1160.00, 0.00, 1160.00, 1160.00, 0.00, 'PAID', 'Demo monthly class paid order.'),
  (9101004, 'ENR-BIZ-DEMO-004', 9100304, 9100602, 9100002, 'DEMO North Campus', 'CONFIRMED', 1, 'Admin', '2026-07-26', '2026-08-01', '2026-09-19', 'DEMO David Kim', 'G7', 'JHS 189', '917-555-9204', NULL, 0, NULL, 0, NULL, 'USD', 760.00, 0.00, 0.00, 0.00, 760.00, 0.00, 0.00, 760.00, 760.00, 'UNPAID', 'Demo Saturday class unpaid order.'),
  (9101005, 'ENR-BIZ-DEMO-005', 9100301, 9100603, 9100001, 'DEMO South Campus', 'CONFIRMED', 1, 'Admin', '2026-07-26', '2026-07-28', '2026-07-28', 'DEMO Amy Wang', 'G4', 'PS 101', '917-555-9201', NULL, 1, '136-20 Maple Ave', 0, NULL, 'USD', 60.00, 0.00, 0.00, 0.00, 60.00, 0.00, 60.00, 60.00, 0.00, 'PAID', 'Intentional student conflict with private lesson.'),
  (9101006, 'ENR-BIZ-DEMO-006', 9100305, NULL, 9100001, 'DEMO South Campus', 'CONFIRMED', 1, 'Admin', '2026-07-26', '2026-07-28', '2026-12-31', 'DEMO Eva RoomConflict', 'G4', 'PS 101', '917-555-9205', NULL, 0, NULL, 0, NULL, 'USD', 1200.00, 0.00, 0.00, 0.00, 1200.00, 0.00, 1200.00, 1200.00, 0.00, 'PAID', 'Private enrollment used for room conflict.')
ON DUPLICATE KEY UPDATE
  order_status = VALUES(order_status),
  received_amount = VALUES(received_amount),
  amount_due = VALUES(amount_due),
  balance_amount = VALUES(balance_amount),
  payment_status = VALUES(payment_status),
  remark = VALUES(remark),
  is_deleted = 0;

INSERT INTO enrollment_order_item (
  id, enrollment_order_id, line_no, register_class, course_type, course_product_id,
  course_price_option_id, class_id, detail, grade_band, period_label, time_slot_label,
  price_amount, original_price_amount, manual_price_override, one_on_one_hours,
  one_on_one_units, initial_lesson_start_at, initial_lesson_end_at, teacher_id,
  item_status, remark
) VALUES
  (9101101, 9101001, 1, '1 on 1', 'ONE_ON_ONE', 9100401, 9100501, NULL, 'DEMO 1-on-1 Math 40h · G3-G8', 'G3-G8', '40 hours', 'Tue 17:00-18:30', 2400.00, 2400.00, 0, 40.00, 80, '2026-07-28 17:00:00.000', '2026-07-28 18:30:00.000', 9100201, 'ACTIVE', NULL),
  (9101102, 9101002, 1, 'After School', 'AFTER_SCHOOL', 9100402, 9100502, 9100601, 'DEMO After School G3-G5 · 2026-07 to 2026-08', 'G3-G5', 'Monthly', 'Mon/Wed 15:30-17:30', 1160.00, 1160.00, 0, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL),
  (9101103, 9101003, 1, 'After School', 'AFTER_SCHOOL', 9100402, 9100502, 9100601, 'DEMO After School G3-G5 · 2026-07 to 2026-08', 'G3-G5', 'Monthly', 'Mon/Wed 15:30-17:30', 1160.00, 1160.00, 0, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL),
  (9101104, 9101004, 1, 'Saturday Class', 'SATURDAY', 9100403, 9100503, 9100602, 'DEMO Saturday Math G6-G8 · 8 sessions', 'G6-G8', '8 sessions', 'Sat 09:00-12:00', 760.00, 760.00, 0, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL),
  (9101105, 9101005, 1, 'After School', 'AFTER_SCHOOL', 9100404, 9100504, 9100603, 'DEMO Conflict Workshop · Single session', 'G4-G5', '1 session', 'Tue 17:15-18:15', 60.00, 60.00, 0, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', 'Intentional student conflict.'),
  (9101106, 9101006, 1, '1 on 1', 'ONE_ON_ONE', 9100401, 9100501, NULL, 'DEMO 1-on-1 Math 20h · room conflict', 'G3-G8', '20 hours', 'Tue 17:30-19:00', 1200.00, 1200.00, 0, 20.00, 40, '2026-07-28 17:30:00.000', '2026-07-28 19:00:00.000', 9100204, 'ACTIVE', 'Intentional room conflict.')
ON DUPLICATE KEY UPDATE
  detail = VALUES(detail),
  price_amount = VALUES(price_amount),
  teacher_id = VALUES(teacher_id),
  item_status = VALUES(item_status),
  remark = VALUES(remark),
  is_deleted = 0;

INSERT INTO course_package (
  id, package_no, enrollment_order_id, enrollment_order_item_id, student_id, course_product_id,
  package_name, subject_name, teaching_mode, total_units, default_lesson_units, consumed_units,
  remaining_units, original_amount, discount_amount, received_amount, package_status,
  start_date, estimated_end_date, remark
) VALUES
  (9101201, 'PKG-BIZ-DEMO-001', 9101001, 9101101, 9100301, 9100401, 'DEMO 1-on-1 Math 40h', 'Mathematics', 'ONE_ON_ONE', 80, 3, 0, 80, 2400.00, 100.00, 1200.00, 'ACTIVE', '2026-07-28', '2026-12-31', 'Demo active 1-on-1 package.'),
  (9101202, 'PKG-BIZ-DEMO-002', 9101006, 9101106, 9100305, 9100401, 'DEMO 1-on-1 Math 20h Room Conflict', 'Mathematics', 'ONE_ON_ONE', 40, 3, 0, 40, 1200.00, 0.00, 1200.00, 'ACTIVE', '2026-07-28', '2026-12-31', 'Package used to create classroom conflict.')
ON DUPLICATE KEY UPDATE
  consumed_units = VALUES(consumed_units),
  remaining_units = VALUES(remaining_units),
  received_amount = VALUES(received_amount),
  package_status = VALUES(package_status),
  remark = VALUES(remark),
  is_deleted = 0;

INSERT INTO package_balance_snapshot (package_id, total_units, consumed_units, remaining_units, last_ledger_id)
VALUES
  (9101201, 80, 0, 80, NULL),
  (9101202, 40, 0, 40, NULL)
ON DUPLICATE KEY UPDATE
  total_units = VALUES(total_units),
  consumed_units = VALUES(consumed_units),
  remaining_units = VALUES(remaining_units);

INSERT INTO package_teacher_assignment (
  id, package_id, teacher_id, is_primary, effective_from, effective_to, assignment_status, change_reason
) VALUES
  (9101301, 9101201, 9100201, 1, '2026-07-28 00:00:00.000', NULL, 'ACTIVE', 'Demo primary teacher.'),
  (9101302, 9101202, 9100204, 1, '2026-07-28 00:00:00.000', NULL, 'ACTIVE', 'Demo room conflict teacher.')
ON DUPLICATE KEY UPDATE
  teacher_id = VALUES(teacher_id),
  assignment_status = VALUES(assignment_status),
  change_reason = VALUES(change_reason),
  is_deleted = 0;

INSERT INTO recurring_schedule_rule (
  id, package_id, primary_teacher_id, default_classroom_id, rule_status, start_date, end_date,
  generation_horizon_days, notes
) VALUES
  (9101401, 9101201, 9100201, 9100102, 'ACTIVE', '2026-07-28', '2026-12-31', 90, 'Demo weekly 1-on-1 schedule.'),
  (9101402, 9101202, 9100204, 9100102, 'ACTIVE', '2026-07-28', '2026-12-31', 90, 'Demo schedule intentionally sharing the same room.')
ON DUPLICATE KEY UPDATE
  primary_teacher_id = VALUES(primary_teacher_id),
  default_classroom_id = VALUES(default_classroom_id),
  rule_status = VALUES(rule_status),
  notes = VALUES(notes),
  is_deleted = 0;

INSERT INTO recurring_schedule_slot (id, rule_id, weekday, start_time, end_time, planned_units, slot_status)
VALUES
  (9101501, 9101401, 2, '17:00:00', '18:30:00', 3, 'ACTIVE'),
  (9101502, 9101402, 2, '17:30:00', '19:00:00', 3, 'ACTIVE')
ON DUPLICATE KEY UPDATE
  weekday = VALUES(weekday),
  start_time = VALUES(start_time),
  end_time = VALUES(end_time),
  planned_units = VALUES(planned_units),
  slot_status = VALUES(slot_status),
  is_deleted = 0;

INSERT INTO class_enrollment (
  id, class_id, student_id, enrollment_order_item_id, enrollment_date, expiry_date,
  billing_type, purchased_quantity, status
) VALUES
  (9101601, 9100601, 9100302, 9101102, '2026-07-26', '2026-08-31', 'MONTHLY', 2, 'ACTIVE'),
  (9101602, 9100601, 9100303, 9101103, '2026-07-26', '2026-08-31', 'MONTHLY', 2, 'ACTIVE'),
  (9101603, 9100602, 9100304, 9101104, '2026-07-26', '2026-09-19', 'PER_SESSION', 8, 'ACTIVE'),
  (9101604, 9100603, 9100301, 9101105, '2026-07-26', '2026-07-28', 'PER_SESSION', 1, 'ACTIVE')
ON DUPLICATE KEY UPDATE
  expiry_date = VALUES(expiry_date),
  billing_type = VALUES(billing_type),
  purchased_quantity = VALUES(purchased_quantity),
  status = VALUES(status),
  is_deleted = 0;

-- Calendar occurrences. 2026-07-28 contains the intentional conflicts.
INSERT INTO lesson_occurrence (
  id, lesson_no, package_id, class_group_id, student_id, schedule_rule_id, schedule_slot_id,
  occurrence_type, planned_teacher_id, planned_classroom_id, planned_start_at, planned_end_at,
  planned_units, planning_status, conflict_checked, notes
) VALUES
  (9102001, 'LS-BIZ-DEMO-O2O-001', 9101201, NULL, 9100301, 9101401, 9101501, 'REGULAR', 9100201, 9100102, '2026-07-28 17:00:00.000', '2026-07-28 18:30:00.000', 3, 'SCHEDULED', 1, 'DEMO base 1-on-1 lesson. Conflicts with workshop and room-conflict lesson.'),
  (9102002, 'LS-BIZ-DEMO-O2O-002', 9101201, NULL, 9100301, 9101401, 9101501, 'REGULAR', 9100201, 9100102, '2026-08-04 17:00:00.000', '2026-08-04 18:30:00.000', 3, 'SCHEDULED', 1, 'DEMO normal future 1-on-1 lesson.'),
  (9102003, 'LS-BIZ-DEMO-O2O-003', 9101201, NULL, 9100301, 9101401, 9101501, 'REGULAR', 9100201, 9100102, '2026-08-11 17:00:00.000', '2026-08-11 18:30:00.000', 3, 'SCHEDULED', 1, 'DEMO normal future 1-on-1 lesson.'),
  (9102004, 'LS-BIZ-DEMO-ROOM-CONFLICT', 9101202, NULL, 9100305, 9101402, 9101502, 'REGULAR', 9100204, 9100102, '2026-07-28 17:30:00.000', '2026-07-28 19:00:00.000', 3, 'SCHEDULED', 1, 'DEMO room conflict: same room as Amy lesson, overlapping time.'),
  (9102101, 'LS-BIZ-DEMO-AFTER-001', NULL, 9100601, NULL, NULL, NULL, 'REGULAR', 9100202, 9100101, '2026-07-27 15:30:00.000', '2026-07-27 17:30:00.000', 4, 'SCHEDULED', 1, 'DEMO group class lesson.'),
  (9102102, 'LS-BIZ-DEMO-AFTER-002', NULL, 9100601, NULL, NULL, NULL, 'REGULAR', 9100202, 9100101, '2026-07-29 15:30:00.000', '2026-07-29 17:30:00.000', 4, 'SCHEDULED', 1, 'DEMO group class lesson.'),
  (9102103, 'LS-BIZ-DEMO-AFTER-003', NULL, 9100601, NULL, NULL, NULL, 'REGULAR', 9100202, 9100101, '2026-08-03 15:30:00.000', '2026-08-03 17:30:00.000', 4, 'SCHEDULED', 1, 'DEMO group class lesson.'),
  (9102104, 'LS-BIZ-DEMO-AFTER-004', NULL, 9100601, NULL, NULL, NULL, 'REGULAR', 9100202, 9100101, '2026-08-05 15:30:00.000', '2026-08-05 17:30:00.000', 4, 'SCHEDULED', 1, 'DEMO group class lesson.'),
  (9102201, 'LS-BIZ-DEMO-SAT-001', NULL, 9100602, NULL, NULL, NULL, 'REGULAR', 9100203, 9100103, '2026-08-01 09:00:00.000', '2026-08-01 12:00:00.000', 6, 'SCHEDULED', 1, 'DEMO Saturday class lesson.'),
  (9102202, 'LS-BIZ-DEMO-SAT-002', NULL, 9100602, NULL, NULL, NULL, 'REGULAR', 9100203, 9100103, '2026-08-08 09:00:00.000', '2026-08-08 12:00:00.000', 6, 'SCHEDULED', 1, 'DEMO Saturday class lesson.'),
  (9102301, 'LS-BIZ-DEMO-STUDENT-CONFLICT', NULL, 9100603, NULL, NULL, NULL, 'REGULAR', 9100204, 9100102, '2026-07-28 17:15:00.000', '2026-07-28 18:15:00.000', 2, 'SCHEDULED', 1, 'DEMO student conflict: Amy is enrolled here and also has 1-on-1 at 17:00.')
ON DUPLICATE KEY UPDATE
  package_id = VALUES(package_id),
  class_group_id = VALUES(class_group_id),
  student_id = VALUES(student_id),
  planned_teacher_id = VALUES(planned_teacher_id),
  planned_classroom_id = VALUES(planned_classroom_id),
  planned_start_at = VALUES(planned_start_at),
  planned_end_at = VALUES(planned_end_at),
  planned_units = VALUES(planned_units),
  planning_status = VALUES(planning_status),
  conflict_checked = VALUES(conflict_checked),
  notes = VALUES(notes),
  is_deleted = 0;

INSERT INTO payment_record (
  id, enrollment_order_id, payment_no, payment_method, payment_type, payment_status,
  amount, paid_at, handled_by_user_id, handled_by_name, remark
) VALUES
  (9101701, 9101001, 'PAY-BIZ-DEMO-001', 'ZELLE', 'PAYMENT', 'PAID', 1200.00, '2026-07-26 10:00:00.000', 1, 'Admin', 'Demo partial payment.'),
  (9101702, 9101002, 'PAY-BIZ-DEMO-002', 'CASH', 'PAYMENT', 'PAID', 600.00, '2026-07-26 10:10:00.000', 1, 'Admin', 'Demo partial payment.'),
  (9101703, 9101003, 'PAY-BIZ-DEMO-003', 'CREDIT_CARD', 'PAYMENT', 'PAID', 1160.00, '2026-07-26 10:20:00.000', 1, 'Admin', 'Demo full payment.'),
  (9101704, 9101005, 'PAY-BIZ-DEMO-004', 'CASH', 'PAYMENT', 'PAID', 60.00, '2026-07-26 10:30:00.000', 1, 'Admin', 'Demo paid conflict workshop.'),
  (9101705, 9101006, 'PAY-BIZ-DEMO-005', 'CASH', 'PAYMENT', 'PAID', 1200.00, '2026-07-26 10:40:00.000', 1, 'Admin', 'Demo paid room conflict package.')
ON DUPLICATE KEY UPDATE
  amount = VALUES(amount),
  payment_status = VALUES(payment_status),
  remark = VALUES(remark),
  is_deleted = 0;

INSERT INTO receipt_record (
  id, receipt_no, enrollment_order_id, student_id, template_code, receipt_payload, issued_at, issued_by
) VALUES
  (9101801, 'RCT-BIZ-DEMO-001', 9101001, 9100301, 'DEFAULT_RECEIPT', JSON_OBJECT('studentName','DEMO Amy Wang','items',JSON_ARRAY(JSON_OBJECT('detail','DEMO 1-on-1 Math 40h','priceAmount',2400.00)),'extraFees',JSON_ARRAY(JSON_OBJECT('label','Pickup service','amount',35.00))), '2026-07-26 10:00:00.000', 1),
  (9101802, 'RCT-BIZ-DEMO-002', 9101002, 9100302, 'DEFAULT_RECEIPT', JSON_OBJECT('studentName','DEMO Brian Chen','items',JSON_ARRAY(JSON_OBJECT('detail','DEMO After School G3-G5','priceAmount',1160.00,'groupBillingType','MONTHLY'))), '2026-07-26 10:10:00.000', 1),
  (9101803, 'RCT-BIZ-DEMO-003', 9101003, 9100303, 'DEFAULT_RECEIPT', JSON_OBJECT('studentName','DEMO Cindy Liu','items',JSON_ARRAY(JSON_OBJECT('detail','DEMO After School G3-G5','priceAmount',1160.00,'groupBillingType','MONTHLY'))), '2026-07-26 10:20:00.000', 1),
  (9101804, 'RCT-BIZ-DEMO-004', 9101004, 9100304, 'DEFAULT_RECEIPT', JSON_OBJECT('studentName','DEMO David Kim','items',JSON_ARRAY(JSON_OBJECT('detail','DEMO Saturday Math G6-G8','priceAmount',760.00,'sessions',8))), '2026-07-26 10:30:00.000', 1)
ON DUPLICATE KEY UPDATE
  receipt_payload = VALUES(receipt_payload),
  issued_at = VALUES(issued_at),
  issued_by = VALUES(issued_by),
  is_deleted = 0;

INSERT INTO teacher_pay_rate (
  id, teacher_id, course_product_id, student_id, hourly_rate, notes
) VALUES
  (9101901, 9100201, 9100401, 9100301, 42.00, 'Demo private pay rate.'),
  (9101902, 9100202, 9100402, NULL, 38.00, 'Demo group pay rate.'),
  (9101903, 9100203, 9100403, NULL, 45.00, 'Demo Saturday pay rate.')
ON DUPLICATE KEY UPDATE
  hourly_rate = VALUES(hourly_rate),
  notes = VALUES(notes),
  is_deleted = 0;
