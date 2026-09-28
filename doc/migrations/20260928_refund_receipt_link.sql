-- Refund receipts are independent snapshots and point to the immutable refund ledger entry.
-- Safe to run repeatedly on environments that may already have the compatibility column.

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'receipt_record'
      AND COLUMN_NAME = 'payment_record_id'
);
SET @sql = IF(
    @column_exists = 0,
    'ALTER TABLE receipt_record ADD COLUMN payment_record_id BIGINT DEFAULT NULL COMMENT ''Linked refund payment record ID'' AFTER student_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Backfill refunds created before independent refund receipts were restored.
-- The payment_record_id guard also makes this section safe to rerun.
UPDATE receipt_record r
JOIN payment_record pr
  ON pr.payment_type = 'REFUND'
 AND pr.is_deleted = 0
 AND r.template_code = 'REFUND_RECEIPT'
 AND r.payment_record_id IS NULL
 AND (
     JSON_UNQUOTE(JSON_EXTRACT(r.receipt_payload, '$.payments[0].paymentId')) = CAST(pr.id AS CHAR)
     OR JSON_UNQUOTE(JSON_EXTRACT(r.receipt_payload, '$.payments[0].paymentNo')) = pr.payment_no
 )
SET r.payment_record_id = pr.id
WHERE r.is_deleted = 0;

INSERT INTO receipt_record (
    id, receipt_no, enrollment_order_id, student_id, payment_record_id, template_code, receipt_payload,
    issued_at, issued_by, created_at, updated_at, is_deleted, version
)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM receipt_record)
        + ROW_NUMBER() OVER (ORDER BY b.paid_at, b.payment_id) AS id,
    b.receipt_no,
    b.enrollment_order_id,
    b.student_id,
    b.payment_id,
    'REFUND_RECEIPT',
    JSON_OBJECT(
        'receiptType', 'REFUND',
        'receiptNo', b.receipt_no,
        'enrollmentOrderId', b.enrollment_order_id,
        'enrollmentNo', b.enrollment_no,
        'studentId', b.student_id,
        'studentName', b.student_name,
        'currencyCode', b.currency_code,
        'issuedAt', b.paid_at,
        'paymentAmount', b.amount,
        'amountDue', b.amount_due,
        'receivedAmount', b.received_amount,
        'paidAmount', b.amount,
        'balanceAmount', b.balance_amount,
        'paymentStatus', b.payment_status,
        'paymentType', 'REFUND',
        'refundDisposition', IF(b.payment_method = 'STUDENT_CREDIT', 'CREDIT', 'REFUND'),
        'participantIndex', b.participant_index,
        'payments', JSON_ARRAY(JSON_OBJECT(
            'paymentId', b.payment_id,
            'paymentNo', b.payment_no,
            'paymentMethod', b.payment_method,
            'paymentType', b.payment_type,
            'paymentStatus', b.payment_status,
            'amount', b.amount,
            'paidAt', b.paid_at,
            'handledByUserId', b.handled_by_user_id,
            'handledByName', b.handled_by_name,
            'remark', b.remark,
            'participantIndex', b.participant_index
        ))
    ),
    b.paid_at,
    b.handled_by_user_id,
    b.paid_at,
    b.paid_at,
    0,
    0
FROM (
    SELECT
        pr.id AS payment_id,
        pr.enrollment_order_id,
        COALESCE(p.student_id, eo.student_id) AS student_id,
        pr.participant_index,
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
        pr.payment_type,
        pr.payment_status,
        pr.amount,
        pr.handled_by_name,
        pr.remark,
        eo.enrollment_no,
        COALESCE(p.student_name_snapshot, eo.student_name_snapshot) AS student_name,
        eo.currency_code,
        eo.amount_due,
        eo.received_amount,
        eo.balance_amount
    FROM payment_record pr
    JOIN enrollment_order eo ON eo.id = pr.enrollment_order_id
    LEFT JOIN enrollment_order_participant p
        ON p.enrollment_order_id = pr.enrollment_order_id
       AND p.participant_index = pr.participant_index
       AND p.is_deleted = 0
    WHERE pr.is_deleted = 0
      AND pr.payment_type = 'REFUND'
      AND NOT EXISTS (
          SELECT 1
          FROM receipt_record r2
          WHERE r2.payment_record_id = pr.id
             OR (
                 r2.template_code = 'REFUND_RECEIPT'
                 AND (
                     JSON_UNQUOTE(JSON_EXTRACT(r2.receipt_payload, '$.payments[0].paymentId')) = CAST(pr.id AS CHAR)
                     OR JSON_UNQUOTE(JSON_EXTRACT(r2.receipt_payload, '$.payments[0].paymentNo')) = pr.payment_no
                 )
             )
      )
) b;

SET @index_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'receipt_record'
      AND INDEX_NAME = 'idx_receipt_payment_record'
);
SET @sql = IF(
    @index_exists = 0,
    'ALTER TABLE receipt_record ADD KEY idx_receipt_payment_record (payment_record_id)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
