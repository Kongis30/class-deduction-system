package com.classdeduction.system.modules.enrollment.mapper;

import com.classdeduction.system.modules.enrollment.vo.DailyCashFlowRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DailyCashFlowMapper {

    /** 按发生时间读取收付款和已支付工资；包含软删流水，保留订单删除前的资金事实。 */
    @Select("""
            SELECT * FROM (
              SELECT CONCAT('PAYMENT-', p.id) AS transaction_id,
                     CASE WHEN p.payment_type = 'REFUND' THEN 'REFUND' ELSE 'PAYMENT' END AS transaction_type,
                     CASE WHEN p.payment_type = 'REFUND' THEN 'OUT' ELSE 'IN' END AS direction,
                     p.paid_at AS happened_at, p.handled_by_user_id, p.handled_by_name,
                     COALESCE((SELECT GROUP_CONCAT(op.student_name_snapshot ORDER BY op.participant_index SEPARATOR ' / ')
                               FROM enrollment_order_participant op
                               WHERE op.enrollment_order_id = p.enrollment_order_id
                                 AND (p.participant_index IS NULL OR op.participant_index = p.participant_index)),
                              o.student_name_snapshot, 'Student not recorded') AS counterparty_name,
                     p.payment_method, p.amount, p.payment_status AS status, p.payment_no AS reference_no,
                     p.remark AS note,
                     CASE WHEN p.payment_method = 'STUDENT_CREDIT' THEN 1 ELSE 0 END AS internal_transfer,
                     CASE WHEN p.payment_status = 'VOIDED' THEN 1 ELSE 0 END AS voided
              FROM payment_record p
              LEFT JOIN enrollment_order o ON o.id = p.enrollment_order_id
              WHERE p.paid_at >= #{fromTime} AND p.paid_at < #{toTime}
                AND (p.payment_type IS NULL OR p.payment_type IN ('PAYMENT', 'REFUND'))
                AND COALESCE(p.amount, 0) <> 0
                AND COALESCE(p.payment_status, '') NOT IN ('UNPAID', 'PENDING', 'FAILED', 'CANCELLED')
              UNION ALL
              SELECT CONCAT('TEACHER-', tp.id) AS transaction_id, 'TEACHER_PAYMENT' AS transaction_type,
                     'OUT' AS direction, tp.paid_at AS happened_at, tp.paid_by AS handled_by_user_id,
                     COALESCE(u.display_name, u.username, 'Operator not recorded') AS handled_by_name,
                     COALESCE(t.teacher_name, 'Teacher not recorded') AS counterparty_name,
                     NULL AS payment_method, tp.total_amount AS amount, tp.status,
                     tp.payment_no AS reference_no, tp.notes AS note, 0 AS internal_transfer,
                     0 AS voided
              FROM teacher_payment tp
              LEFT JOIN teacher t ON t.id = tp.teacher_id
              LEFT JOIN sys_user u ON u.id = tp.paid_by
              WHERE tp.status = 'PAID' AND tp.paid_at >= #{fromTime} AND tp.paid_at < #{toTime}
            ) cash_flow
            WHERE (#{operatorUserId} IS NULL OR handled_by_user_id = #{operatorUserId})
            ORDER BY happened_at DESC, transaction_id DESC
            """)
    @Results(id = "dailyCashFlowRow", value = {
            @Result(property = "transactionId", column = "transaction_id"),
            @Result(property = "transactionType", column = "transaction_type"),
            @Result(property = "direction", column = "direction"),
            @Result(property = "happenedAt", column = "happened_at"),
            @Result(property = "handledByUserId", column = "handled_by_user_id"),
            @Result(property = "handledByName", column = "handled_by_name"),
            @Result(property = "counterpartyName", column = "counterparty_name"),
            @Result(property = "paymentMethod", column = "payment_method"),
            @Result(property = "amount", column = "amount"),
            @Result(property = "status", column = "status"),
            @Result(property = "referenceNo", column = "reference_no"),
            @Result(property = "note", column = "note"),
            @Result(property = "internalTransfer", column = "internal_transfer"),
            @Result(property = "voided", column = "voided")
    })
    List<DailyCashFlowRow> selectByPeriod(@Param("fromTime") LocalDateTime fromTime,
                                          @Param("toTime") LocalDateTime toTime,
                                          @Param("operatorUserId") Long operatorUserId);
}
