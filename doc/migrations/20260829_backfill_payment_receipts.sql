-- =============================================================================
-- 一次性回填：为历史 payment_record 补写 PAYMENT_RECEIPT 收据
-- 执行日期：2026-08-29
--
-- 背景：
--   PaymentRecordServiceImpl.createPaymentReceipt（commit a5a55e9）上线前录入的
--   历史付款从未生成收据。本地库 28 条 payment_record（2024-02 ~ 2026-05-24）
--   均无对应 PAYMENT_RECEIPT。
--
-- 作用：
--   为每条「未删除 + payment_type='PAYMENT' + 尚无 PAYMENT_RECEIPT 收据」的付款
--   补写一条 receipt_record（template_code='PAYMENT_RECEIPT'）。
--
-- 幂等性：
--   通过 NOT EXISTS（enrollment_order_id + template_code='PAYMENT_RECEIPT'）守卫，
--   可安全重复执行；已回填的付款不会被重复插入。
--
-- receipt_no 生成：
--   与 BusinessNoGenerator 一致：RCP + yyyyMMdd（取付款 paid_at 日期）+ 4 位序号。
--   序号 = 该日期现有 receipt_no 最大序号 + 行号，保证不与已有收据冲突。
--
-- 回滚：
--   若需撤销本次回填，执行：
--     DELETE FROM receipt_record WHERE template_code='PAYMENT_RECEIPT';
--   （该操作仅删除 PAYMENT_RECEIPT 类型，不影响已有 DEFAULT/REFUND 收据。）
-- =============================================================================

INSERT INTO receipt_record (
  id, receipt_no, enrollment_order_id, student_id, template_code, receipt_payload,
  issued_at, issued_by, created_at, updated_at, is_deleted, version
)
SELECT
  (SELECT COALESCE(MAX(id), 0) FROM receipt_record) + ROW_NUMBER() OVER (ORDER BY b.paid_at, b.payment_id) AS id,
  b.receipt_no,
  b.enrollment_order_id,
  b.student_id,
  'PAYMENT_RECEIPT' AS template_code,
  JSON_OBJECT(
    'receiptType', 'PAYMENT',
    'receiptNo', b.receipt_no,
    'enrollmentOrderId', b.enrollment_order_id,
    'enrollmentNo', b.enrollment_no,
    'studentId', b.student_id,
    'studentName', b.student_name_snapshot,
    'currencyCode', b.currency_code,
    'issuedAt', b.paid_at,
    'paymentAmount', b.amount,
    'amountDue', b.amount_due,
    'receivedAmount', b.received_amount,
    'balanceAmount', b.balance_amount,
    'paymentStatus', b.payment_status,
    'payments', JSON_ARRAY(JSON_OBJECT(
      'paymentId', b.payment_id,
      'paymentNo', b.payment_no,
      'paymentMethod', b.payment_method,
      'amount', b.amount,
      'paidAt', b.paid_at,
      'handledByName', b.handled_by_name,
      'remark', b.remark
    ))
  ) AS receipt_payload,
  b.paid_at AS issued_at,
  b.handled_by_user_id AS issued_by,
  b.paid_at AS created_at,
  b.paid_at AS updated_at,
  0 AS is_deleted,
  0 AS version
FROM (
  SELECT
    pr.id AS payment_id,
    pr.enrollment_order_id,
    eo.student_id,
    pr.paid_at,
    pr.handled_by_user_id,
    CONCAT('RCP', DATE_FORMAT(pr.paid_at, '%Y%m%d'), LPAD(
        ROW_NUMBER() OVER (PARTITION BY DATE(pr.paid_at) ORDER BY pr.paid_at, pr.id)
        + COALESCE(
            (SELECT MAX(CAST(SUBSTRING(r.receipt_no, 12) AS UNSIGNED))
             FROM receipt_record r
             WHERE r.receipt_no LIKE CONCAT('RCP', DATE_FORMAT(pr.paid_at, '%Y%m%d'), '%')),
            0),
        4, '0')) AS receipt_no,
    pr.payment_no,
    pr.payment_method,
    pr.amount,
    pr.handled_by_name,
    pr.remark,
    eo.enrollment_no,
    eo.student_name_snapshot,
    eo.currency_code,
    eo.amount_due,
    eo.received_amount,
    eo.balance_amount,
    eo.payment_status
  FROM payment_record pr
  JOIN enrollment_order eo ON eo.id = pr.enrollment_order_id
  WHERE pr.is_deleted = 0
    AND pr.payment_type = 'PAYMENT'
    AND NOT EXISTS (
      SELECT 1 FROM receipt_record r2
      WHERE r2.enrollment_order_id = pr.enrollment_order_id
        AND r2.template_code = 'PAYMENT_RECEIPT'
    )
) b;
