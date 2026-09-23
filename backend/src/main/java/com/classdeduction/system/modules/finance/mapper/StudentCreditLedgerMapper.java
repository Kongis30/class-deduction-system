package com.classdeduction.system.modules.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.finance.entity.StudentCreditLedgerEntity;
import com.classdeduction.system.modules.finance.vo.StudentCreditBalanceVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface StudentCreditLedgerMapper extends BaseMapper<StudentCreditLedgerEntity> {

    @Select("""
            SELECT s.id AS student_id, s.student_no, s.student_name, s.grade, s.student_class,
                   s.guardian_phone, COALESCE(SUM(l.amount), 0.00) AS credit_balance,
                   MAX(l.occurred_at) AS last_occurred_at
            FROM student s
            JOIN student_credit_ledger l ON l.student_id = s.id AND l.is_deleted = 0
            WHERE s.is_deleted = 0
              AND (#{keyword} IS NULL OR #{keyword} = ''
                   OR s.student_name LIKE CONCAT('%', #{keyword}, '%')
                   OR s.student_no LIKE CONCAT('%', #{keyword}, '%')
                   OR s.guardian_phone LIKE CONCAT('%', #{keyword}, '%'))
            GROUP BY s.id, s.student_no, s.student_name, s.grade, s.student_class, s.guardian_phone
            HAVING SUM(l.amount) > 0
            ORDER BY credit_balance DESC, s.student_name ASC
            """)
    List<StudentCreditBalanceVO> selectBalances(@Param("keyword") String keyword);

    @Select("SELECT COALESCE(SUM(amount), 0.00) FROM student_credit_ledger "
            + "WHERE student_id = #{studentId} AND is_deleted = 0")
    BigDecimal selectBalance(@Param("studentId") Long studentId);

    /**
     * 抵扣 Credit 时锁定学生主表行，避免并发付款同时读取同一余额而超额使用。
     * 调用方必须处于事务中；报名和后续收款服务都满足这一条件。
     */
    @Select("SELECT id FROM student WHERE id = #{studentId} AND is_deleted = 0 FOR UPDATE")
    Long lockStudent(@Param("studentId") Long studentId);
}
