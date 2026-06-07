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

export async function queryOne<T extends Record<string, unknown>>(connection: Connection, sql: string, params: unknown[] = []): Promise<T | null> {
  const [rows] = await connection.execute(sql, params);
  const list = rows as T[];
  return list[0] ?? null;
}

export async function queryAll<T extends Record<string, unknown>>(connection: Connection, sql: string, params: unknown[] = []): Promise<T[]> {
  const [rows] = await connection.execute(sql, params);
  return rows as T[];
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
  await connection.execute('DELETE FROM teacher_payment WHERE notes LIKE ?', [like]);
  await deleteIn(connection, 'teacher_pay_rate', 'teacher_id', teacherIds);
  await deleteIn(connection, 'teacher_pay_rate', 'course_product_id', productIds);
  await connection.execute('DELETE FROM notification_log WHERE request_payload LIKE ? OR response_payload LIKE ? OR error_message LIKE ?', [like, like, like]);
  await deleteIn(connection, 'lesson_adjustment_log', 'occurrence_id', 'SELECT id FROM lesson_occurrence WHERE package_id IN (SELECT id FROM course_package WHERE package_name LIKE ? OR remark LIKE ?)', [like, like]);
  await deleteIn(connection, 'lesson_adjustment_log', 'occurrence_id', 'SELECT id FROM lesson_occurrence WHERE class_group_id IN (SELECT id FROM class_group WHERE class_name LIKE ?)', [like]);
  await deleteIn(connection, 'hour_ledger', 'package_id', packageIds);
  await deleteIn(connection, 'package_balance_snapshot', 'package_id', packageIds);
  await deleteIn(connection, 'lesson_record', 'package_id', packageIds);
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
  await deleteIn(connection, 'course_product', 'id', productIds);
  await connection.execute('DELETE FROM payment_method WHERE method_name LIKE ? OR method_code LIKE ?', [like, like]);
  await connection.execute('DELETE FROM discount_preset WHERE preset_name LIKE ?', [like]);
  await connection.execute('DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE ?)', [`${marker}%`]);
  await connection.execute('DELETE FROM auth_session WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE ?)', [`${marker}%`]);
  await connection.execute('DELETE FROM sys_user WHERE username LIKE ? OR display_name LIKE ?', [`${marker}%`, like]);
  await deleteIn(connection, 'student', 'id', studentIds);
  await deleteIn(connection, 'teacher', 'id', teacherIds);
  await deleteIn(connection, 'classroom', 'id', roomIds);
  await connection.execute('SET FOREIGN_KEY_CHECKS=1');
}

async function deleteIn(connection: Connection, table: string, column: string, valuesOrSubquery: string[] | string, params: unknown[] = []): Promise<void> {
  if (Array.isArray(valuesOrSubquery)) {
    if (valuesOrSubquery.length === 0) return;
    const placeholders = valuesOrSubquery.map(() => '?').join(',');
    await connection.execute(`DELETE FROM ${table} WHERE ${column} IN (${placeholders})`, valuesOrSubquery);
    return;
  }
  await connection.execute(`DELETE FROM ${table} WHERE ${column} IN (${valuesOrSubquery})`, params);
}
