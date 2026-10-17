-- 单学生报名的参与人已付快照应等于整单有效净收款。
-- 仅修复整单金额与有效流水一致、且所有流水均未标学生的旧记录；多人订单需单独核对。
USE `class_deduction_system_v2`;

START TRANSACTION;

CREATE TEMPORARY TABLE `single_participant_payment_repair` AS
SELECT p.id AS participant_id,
       o.received_amount AS paid_amount,
       p.amount_due - o.received_amount AS balance_amount
FROM `enrollment_order` o
JOIN `enrollment_order_participant` p
  ON p.enrollment_order_id = o.id AND p.is_deleted = 0
JOIN (
    SELECT enrollment_order_id,
           COUNT(*) AS participant_count
    FROM `enrollment_order_participant`
    WHERE is_deleted = 0
    GROUP BY enrollment_order_id
) pc ON pc.enrollment_order_id = o.id AND pc.participant_count = 1
JOIN (
    SELECT enrollment_order_id,
           SUM(CASE WHEN payment_status = 'VOIDED' THEN 0 ELSE amount END) AS net_amount,
           SUM(CASE WHEN payment_status <> 'VOIDED' AND participant_index IS NULL THEN amount ELSE 0 END)
             AS unscoped_amount
    FROM `payment_record`
    WHERE is_deleted = 0
    GROUP BY enrollment_order_id
) ledger ON ledger.enrollment_order_id = o.id
  AND ledger.net_amount = o.received_amount
  AND ledger.unscoped_amount = ledger.net_amount
WHERE o.is_deleted = 0
  AND p.paid_amount <> o.received_amount;

UPDATE `enrollment_order_participant` p
JOIN `single_participant_payment_repair` repair ON repair.participant_id = p.id
SET p.paid_amount = repair.paid_amount,
    p.balance_amount = repair.balance_amount;

DROP TEMPORARY TABLE `single_participant_payment_repair`;
COMMIT;
