import { createConnection, type Connection } from 'mysql2/promise';

export async function createDbConnection(): Promise<Connection> {
  return createConnection({
    host: process.env.E2E_DB_HOST ?? '127.0.0.1',
    port: Number(process.env.E2E_DB_PORT ?? 3306),
    user: process.env.E2E_DB_USER ?? 'root',
    password: process.env.E2E_DB_PASSWORD ?? '123456whw',
    database: process.env.E2E_DB_NAME ?? 'class_deduction_system',
    timezone: 'Z',
    supportBigNumbers: true,
    bigNumberStrings: true,
  });
}

export async function queryOne<T extends Record<string, unknown>>(connection: Connection, sql: string, params: any[] = []): Promise<T | null> {
  const [rows] = await connection.execute(sql, params);
  const list = rows as T[];
  return list[0] ?? null;
}

export async function queryAll<T extends Record<string, unknown>>(connection: Connection, sql: string, params: any[] = []): Promise<T[]> {
  const [rows] = await connection.execute(sql, params);
  return rows as T[];
}

export async function ensureLessonRecordDualConfirmSchema(connection: Connection): Promise<void> {
  const columns = await queryAll<{ COLUMN_NAME: string }>(
    connection,
    `
      SELECT COLUMN_NAME
      FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'lesson_record'
        AND COLUMN_NAME IN (
          'confirm_stage',
          'teacher_confirmed_at',
          'teacher_confirmed_by_user_id',
          'admin_confirmed_at',
          'admin_confirmed_by_user_id',
          'reject_reason',
          'rejected_at',
          'rejected_by_user_id'
        )
    `,
  );
  const existing = new Set(columns.map((row) => row.COLUMN_NAME));
  const alters: string[] = [];

  // 当前后端 LessonRecordEntity 已包含二阶段确认字段；测试库未跑迁移时，任何 lesson_record 查询都会 500。
  if (!existing.has('confirm_stage')) {
    alters.push("ADD COLUMN `confirm_stage` varchar(32) NOT NULL DEFAULT 'PENDING_ADMIN' COMMENT 'Confirm stage: PENDING_ADMIN/CONFIRMED/REJECTED' AFTER `should_extend_end_date`");
  }
  if (!existing.has('teacher_confirmed_at')) {
    alters.push("ADD COLUMN `teacher_confirmed_at` datetime(3) DEFAULT NULL COMMENT 'Teacher initial confirmed at' AFTER `confirmed_at`");
  }
  if (!existing.has('teacher_confirmed_by_user_id')) {
    alters.push("ADD COLUMN `teacher_confirmed_by_user_id` bigint DEFAULT NULL COMMENT 'Teacher initial confirmed by user ID' AFTER `teacher_confirmed_at`");
  }
  if (!existing.has('admin_confirmed_at')) {
    alters.push("ADD COLUMN `admin_confirmed_at` datetime(3) DEFAULT NULL COMMENT 'Admin final confirmed at' AFTER `teacher_confirmed_by_user_id`");
  }
  if (!existing.has('admin_confirmed_by_user_id')) {
    alters.push("ADD COLUMN `admin_confirmed_by_user_id` bigint DEFAULT NULL COMMENT 'Admin final confirmed by user ID' AFTER `admin_confirmed_at`");
  }
  if (!existing.has('reject_reason')) {
    alters.push("ADD COLUMN `reject_reason` varchar(500) DEFAULT NULL COMMENT 'Admin reject reason' AFTER `admin_confirmed_by_user_id`");
  }
  if (!existing.has('rejected_at')) {
    alters.push("ADD COLUMN `rejected_at` datetime(3) DEFAULT NULL COMMENT 'Rejected at' AFTER `reject_reason`");
  }
  if (!existing.has('rejected_by_user_id')) {
    alters.push("ADD COLUMN `rejected_by_user_id` bigint DEFAULT NULL COMMENT 'Rejected by user ID' AFTER `rejected_at`");
  }

  if (alters.length === 0) return;

  await connection.execute(`ALTER TABLE lesson_record ${alters.join(', ')}`);
  await connection.execute(`
    UPDATE lesson_record
    SET confirm_stage = 'CONFIRMED',
        admin_confirmed_at = confirmed_at,
        admin_confirmed_by_user_id = confirmed_by_user_id
    WHERE confirmed_at IS NOT NULL
       OR confirmed_by_user_id IS NOT NULL
  `);
}

export async function cleanupE2EData(connection: Connection, marker: string): Promise<void> {
  await connection.execute('SET FOREIGN_KEY_CHECKS=0');
  const like = `%${marker}%`;
  const [studentRows] = await connection.execute('SELECT id FROM student WHERE student_name LIKE ?', [like]);
  const [teacherRows] = await connection.execute('SELECT id FROM teacher WHERE teacher_name LIKE ?', [like]);
  const [roomRows] = await connection.execute('SELECT id FROM classroom WHERE room_name LIKE ?', [like]);
  const [productRows] = await connection.execute('SELECT id FROM course_product WHERE product_name LIKE ?', [like]);
  const [orderRows] = await connection.execute('SELECT id FROM enrollment_order WHERE handled_by_name LIKE ? OR remark LIKE ?', [like, like]);
  const [packageRows] = await connection.execute('SELECT id FROM course_package WHERE package_name LIKE ? OR remark LIKE ?', [like, like]);
  const [classRows] = await connection.execute('SELECT id FROM class_group WHERE class_name LIKE ?', [like]);

  const studentIds = (studentRows as { id: string }[]).map((row) => row.id);
  const teacherIds = (teacherRows as { id: string }[]).map((row) => row.id);
  const roomIds = (roomRows as { id: string }[]).map((row) => row.id);
  const productIds = (productRows as { id: string }[]).map((row) => row.id);
  const orderIds = (orderRows as { id: string }[]).map((row) => row.id);
  const packageIds = (packageRows as { id: string }[]).map((row) => row.id);
  const classIds = (classRows as { id: string }[]).map((row) => row.id);

  await deleteIn(connection, 'teacher_payment_item', 'payment_id', 'SELECT id FROM teacher_payment WHERE notes LIKE ?', [like]);
  await deleteIn(connection, 'teacher_payment_item', 'lesson_record_id', 'SELECT lr.id FROM lesson_record lr INNER JOIN lesson_occurrence lo ON lo.id = lr.occurrence_id WHERE lo.class_group_id IN (SELECT id FROM class_group WHERE class_name LIKE ?)', [like]);
  await connection.execute('DELETE FROM teacher_payment WHERE notes LIKE ?', [like]);
  await deleteIn(connection, 'teacher_pay_rate', 'teacher_id', teacherIds);
  await deleteIn(connection, 'teacher_pay_rate', 'course_product_id', productIds);
  await connection.execute('DELETE FROM notification_log WHERE request_payload LIKE ? OR response_payload LIKE ? OR error_message LIKE ?', [like, like, like]);
  await deleteIn(connection, 'lesson_adjustment_log', 'occurrence_id', 'SELECT id FROM lesson_occurrence WHERE package_id IN (SELECT id FROM course_package WHERE package_name LIKE ? OR remark LIKE ?)', [like, like]);
  await deleteIn(connection, 'lesson_adjustment_log', 'occurrence_id', 'SELECT id FROM lesson_occurrence WHERE class_group_id IN (SELECT id FROM class_group WHERE class_name LIKE ?)', [like]);
  await deleteIn(connection, 'hour_ledger', 'package_id', packageIds);
  await deleteIn(connection, 'package_balance_snapshot', 'package_id', packageIds);
  await deleteIn(connection, 'lesson_record', 'package_id', packageIds);
  await deleteIn(connection, 'lesson_record', 'occurrence_id', 'SELECT id FROM lesson_occurrence WHERE class_group_id IN (SELECT id FROM class_group WHERE class_name LIKE ?)', [like]);
  await deleteIn(connection, 'lesson_occurrence', 'package_id', packageIds);
  await deleteIn(connection, 'lesson_occurrence', 'class_group_id', classIds);
  await deleteIn(connection, 'recurring_schedule_slot', 'rule_id', 'SELECT id FROM recurring_schedule_rule WHERE notes LIKE ?', [like]);
  await connection.execute('DELETE FROM recurring_schedule_rule WHERE notes LIKE ?', [like]);
  await deleteIn(connection, 'class_enrollment', 'class_id', classIds);
  await deleteIn(connection, 'class_enrollment', 'student_id', studentIds);
  await deleteIn(connection, 'payment_record', 'enrollment_order_id', orderIds);
  await deleteIn(connection, 'receipt_record', 'enrollment_order_id', orderIds);
  await deleteIn(connection, 'package_teacher_assignment', 'package_id', packageIds);
  await deleteIn(connection, 'course_package', 'id', packageIds);
  await deleteIn(connection, 'enrollment_order', 'id', orderIds);
  await deleteIn(connection, 'class_group', 'id', classIds);
  await deleteIn(connection, 'course_product_schedule_slot', 'product_id', productIds);
  await connection.execute('DELETE FROM course_price_option WHERE option_code LIKE ? OR detail_text LIKE ? OR notes LIKE ?', [like, like, like]);
  await deleteIn(connection, 'course_product', 'id', productIds);
  await connection.execute('DELETE FROM payment_method WHERE method_name LIKE ? OR method_code LIKE ?', [like, like]);
  await connection.execute('DELETE FROM discount_preset WHERE preset_name LIKE ?', [like]);
  // 科目（含被停用的）按名称标记物理清理，避免 API 仅能查到 ACTIVE 科目导致残留
  await connection.execute('DELETE FROM subject WHERE subject_name LIKE ?', [like]);
  await connection.execute('DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE ?)', [`${marker}%`]);
  await connection.execute('DELETE FROM auth_session WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE ?)', [`${marker}%`]);
  await connection.execute('DELETE FROM sys_user WHERE username LIKE ? OR display_name LIKE ?', [`${marker}%`, like]);
  await deleteIn(connection, 'student', 'id', studentIds);
  await deleteIn(connection, 'teacher', 'id', teacherIds);
  await deleteIn(connection, 'classroom', 'id', roomIds);
  await connection.execute('SET FOREIGN_KEY_CHECKS=1');
}

export async function cleanupBusinessFlowE2EData(connection: Connection, marker: string): Promise<void> {
  const like = `%${marker}%`;
  const accountPrefix = `${marker.toLowerCase()}%`;
  await connection.execute('SET FOREIGN_KEY_CHECKS=0');
  try {
    const [studentRows] = await connection.execute('SELECT id FROM student WHERE student_name LIKE ? OR notes LIKE ?', [like, like]);
    const [teacherRows] = await connection.execute('SELECT id FROM teacher WHERE teacher_name LIKE ? OR notes LIKE ?', [like, like]);
    const [roomRows] = await connection.execute('SELECT id FROM classroom WHERE room_name LIKE ? OR notes LIKE ?', [like, like]);
    const [productRows] = await connection.execute('SELECT id FROM course_product WHERE product_name LIKE ? OR notes LIKE ?', [like, like]);
    const [orderRows] = await connection.execute(`
      SELECT DISTINCT eo.id
      FROM enrollment_order eo
      LEFT JOIN course_package cp ON cp.enrollment_order_id = eo.id
      LEFT JOIN course_product p ON p.id = cp.course_product_id
      WHERE eo.handled_by_name LIKE ?
         OR eo.remark LIKE ?
         OR cp.package_name LIKE ?
         OR cp.remark LIKE ?
         OR p.product_name LIKE ?
         OR p.notes LIKE ?
    `, [like, like, like, like, like, like]);
    const [packageRows] = await connection.execute(`
      SELECT DISTINCT cp.id
      FROM course_package cp
      LEFT JOIN enrollment_order eo ON eo.id = cp.enrollment_order_id
      LEFT JOIN course_product p ON p.id = cp.course_product_id
      WHERE cp.package_name LIKE ?
         OR cp.remark LIKE ?
         OR eo.handled_by_name LIKE ?
         OR eo.remark LIKE ?
         OR p.product_name LIKE ?
         OR p.notes LIKE ?
    `, [like, like, like, like, like, like]);
    const [classRows] = await connection.execute(`
      SELECT DISTINCT cg.id
      FROM class_group cg
      LEFT JOIN course_product p ON p.id = cg.product_id
      WHERE cg.class_name LIKE ?
         OR p.product_name LIKE ?
         OR p.notes LIKE ?
    `, [like, like, like]);

    const studentIds = (studentRows as { id: string }[]).map((row) => row.id);
    const teacherIds = (teacherRows as { id: string }[]).map((row) => row.id);
    const roomIds = (roomRows as { id: string }[]).map((row) => row.id);
    const productIds = (productRows as { id: string }[]).map((row) => row.id);
    const orderIds = (orderRows as { id: string }[]).map((row) => row.id);
    const packageIds = (packageRows as { id: string }[]).map((row) => row.id);
    const classIds = (classRows as { id: string }[]).map((row) => row.id);

    // 先删跨页链路的派生数据，再删账号、主数据，避免 marker 残留污染后续用例。
    await deleteIn(connection, 'teacher_payment_item', 'payment_id', 'SELECT id FROM teacher_payment WHERE notes LIKE ?', [like]);
    await deleteIn(connection, 'teacher_payment_item', 'lesson_record_id', 'SELECT id FROM lesson_record WHERE package_id IN (SELECT id FROM course_package WHERE package_name LIKE ? OR remark LIKE ?)', [like, like]);
    await deleteIn(connection, 'teacher_payment_item', 'lesson_record_id', 'SELECT lr.id FROM lesson_record lr INNER JOIN lesson_occurrence lo ON lo.id = lr.occurrence_id WHERE lo.class_group_id IN (SELECT id FROM class_group WHERE class_name LIKE ?)', [like]);
    await connection.execute('DELETE FROM teacher_payment WHERE notes LIKE ?', [like]);
    await deleteIn(connection, 'teacher_pay_rate', 'teacher_id', teacherIds);
    await deleteIn(connection, 'teacher_pay_rate', 'course_product_id', productIds);
    await connection.execute('DELETE FROM teacher_pay_rate WHERE notes LIKE ?', [like]);
    await deleteIn(connection, 'notification_log', 'lesson_record_id', 'SELECT id FROM lesson_record WHERE package_id IN (SELECT id FROM course_package WHERE package_name LIKE ? OR remark LIKE ?)', [like, like]);
    await connection.execute('DELETE FROM notification_log WHERE request_payload LIKE ? OR response_payload LIKE ? OR error_message LIKE ?', [like, like, like]);
    await deleteIn(connection, 'lesson_adjustment_log', 'occurrence_id', 'SELECT id FROM lesson_occurrence WHERE package_id IN (SELECT id FROM course_package WHERE package_name LIKE ? OR remark LIKE ?)', [like, like]);
    await deleteIn(connection, 'lesson_adjustment_log', 'occurrence_id', 'SELECT id FROM lesson_occurrence WHERE class_group_id IN (SELECT id FROM class_group WHERE class_name LIKE ?)', [like]);
    await deleteIn(connection, 'hour_ledger', 'package_id', packageIds);
    await deleteIn(connection, 'package_balance_snapshot', 'package_id', packageIds);
    await deleteIn(connection, 'lesson_record', 'package_id', packageIds);
    await deleteIn(connection, 'lesson_record', 'occurrence_id', 'SELECT id FROM lesson_occurrence WHERE class_group_id IN (SELECT id FROM class_group WHERE class_name LIKE ?)', [like]);
    await deleteIn(connection, 'lesson_occurrence', 'package_id', packageIds);
    await deleteIn(connection, 'lesson_occurrence', 'class_group_id', classIds);
    await deleteIn(connection, 'recurring_schedule_slot', 'rule_id', 'SELECT id FROM recurring_schedule_rule WHERE notes LIKE ? OR package_id IN (SELECT id FROM course_package WHERE package_name LIKE ? OR remark LIKE ?)', [like, like, like]);
    await connection.execute('DELETE FROM recurring_schedule_rule WHERE notes LIKE ?', [like]);
    await deleteIn(connection, 'recurring_schedule_rule', 'package_id', packageIds);
    await deleteIn(connection, 'class_enrollment', 'class_id', classIds);
    await deleteIn(connection, 'class_enrollment', 'student_id', studentIds);
    await deleteIn(connection, 'payment_record', 'enrollment_order_id', orderIds);
    await deleteIn(connection, 'receipt_record', 'enrollment_order_id', orderIds);
    await deleteIn(connection, 'package_teacher_assignment', 'package_id', packageIds);
    await deleteIn(connection, 'course_package', 'id', packageIds);
    await deleteIn(connection, 'enrollment_order', 'id', orderIds);
    await deleteIn(connection, 'class_group', 'id', classIds);
    await deleteIn(connection, 'course_product_schedule_slot', 'product_id', productIds);
    await connection.execute('DELETE FROM course_price_option WHERE option_code LIKE ? OR detail_text LIKE ? OR notes LIKE ?', [like, like, like]);
    await deleteIn(connection, 'course_product', 'id', productIds);
    await connection.execute('DELETE FROM payment_method WHERE method_name LIKE ? OR method_code LIKE ?', [like, like]);
    await connection.execute('DELETE FROM discount_preset WHERE preset_name LIKE ?', [like]);
    await connection.execute('DELETE FROM subject WHERE subject_name LIKE ?', [like]);
    await connection.execute('DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE ? OR display_name LIKE ?)', [accountPrefix, like]);
    await connection.execute('DELETE FROM auth_session WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE ? OR display_name LIKE ?)', [accountPrefix, like]);
    await connection.execute('DELETE FROM sys_user WHERE username LIKE ? OR display_name LIKE ? OR email LIKE ?', [accountPrefix, like, like]);
    await deleteIn(connection, 'student', 'id', studentIds);
    await deleteIn(connection, 'teacher', 'id', teacherIds);
    await deleteIn(connection, 'classroom', 'id', roomIds);
  } finally {
    await connection.execute('SET FOREIGN_KEY_CHECKS=1');
  }
}

async function deleteIn(connection: Connection, table: string, column: string, valuesOrSubquery: string[] | string, params: any[] = []): Promise<void> {
  if (Array.isArray(valuesOrSubquery)) {
    if (valuesOrSubquery.length === 0) return;
    const placeholders = valuesOrSubquery.map(() => '?').join(',');
    await connection.execute(`DELETE FROM ${table} WHERE ${column} IN (${placeholders})`, valuesOrSubquery);
    return;
  }
  await connection.execute(`DELETE FROM ${table} WHERE ${column} IN (${valuesOrSubquery})`, params);
}
