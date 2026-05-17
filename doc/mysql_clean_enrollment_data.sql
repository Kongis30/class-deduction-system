-- 清理所有报名相关数据（按外键依赖顺序）
-- 先禁用外键检查以简化顺序

SET FOREIGN_KEY_CHECKS = 0;

-- 1. 课次相关（引用 package）
TRUNCATE TABLE `lesson_record`;
TRUNCATE TABLE `hour_ledger`;
TRUNCATE TABLE `package_balance_snapshot`;

-- 2. 排课相关（引用 package）
TRUNCATE TABLE `lesson_occurrence`;
TRUNCATE TABLE `recurring_schedule_slot`;
TRUNCATE TABLE `recurring_schedule_rule`;

-- 3. 课包关联
TRUNCATE TABLE `package_teacher_assignment`;

-- 4. 收据/支付记录（引用 enrollment_order）
TRUNCATE TABLE `receipt_record`;
TRUNCATE TABLE `payment_record`;

-- 5. 课包（引用 enrollment_order）
TRUNCATE TABLE `course_package`;

-- 6. 班课报名（独立表）
TRUNCATE TABLE `class_enrollment`;

-- 7. 报名订单（独立表，DML 清空保留自增）
TRUNCATE TABLE `enrollment_order`;

SET FOREIGN_KEY_CHECKS = 1;
