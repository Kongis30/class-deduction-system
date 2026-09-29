package com.classdeduction.system.modules.finance.service.impl;

import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.finance.entity.StudentCreditLedgerEntity;
import com.classdeduction.system.modules.finance.mapper.StudentCreditLedgerMapper;
import com.classdeduction.system.modules.finance.service.StudentCreditService;
import com.classdeduction.system.modules.finance.vo.StudentCreditBalanceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Credit 余额服务。所有余额变化都写流水，报名与退款事务由调用方统一控制回滚。
 */
@Service
@RequiredArgsConstructor
public class StudentCreditServiceImpl implements StudentCreditService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final String CREDIT_USED = "CREDIT_USED";

    private final StudentCreditLedgerMapper ledgerMapper;

    @Override
    public List<StudentCreditBalanceVO> listBalances(String keyword) {
        return ledgerMapper.selectBalances(StringUtils.hasText(keyword) ? keyword.trim() : null);
    }

    @Override
    public BigDecimal getBalance(Long studentId) {
        if (studentId == null) {
            return ZERO;
        }
        return defaultAmount(ledgerMapper.selectBalance(studentId));
    }

    @Override
    @Transactional
    public void adjustBalance(Long studentId, BigDecimal targetBalance,
                              Long handledByUserId, String handledByName, String remark) {
        if (targetBalance != null && targetBalance.stripTrailingZeros().scale() > 0) {
            throw new BusinessException("WHOLE_DOLLAR_REQUIRED", "Credit balance must be a whole dollar amount.");
        }
        if (studentId == null || ledgerMapper.lockStudent(studentId) == null) {
            throw new BusinessException("CREDIT_STUDENT_REQUIRED", "The Credit student does not exist.");
        }
        BigDecimal target = defaultAmount(targetBalance);
        if (target.compareTo(ZERO) < 0) {
            throw new BusinessException("INVALID_CREDIT_AMOUNT", "Credit balance cannot be negative.");
        }
        BigDecimal delta = target.subtract(getBalance(studentId));
        if (delta.compareTo(ZERO) == 0) {
            return;
        }
        appendManualAdjustment(studentId, delta, handledByUserId, handledByName, remark);
    }

    @Override
    public void creditFromRefund(Long studentId, BigDecimal amount, Long orderId, Long paymentRecordId,
                                 Long handledByUserId, String handledByName, String remark) {
        append(studentId, amount, "REFUND_TO_CREDIT", orderId, paymentRecordId,
                handledByUserId, handledByName, remark);
    }

    @Override
    public void creditFromTopUp(Long studentId, BigDecimal amount, Long orderId, Long paymentRecordId,
                                Long handledByUserId, String handledByName, String remark) {
        append(studentId, amount, "TOP_UP_CREDIT", orderId, paymentRecordId,
                handledByUserId, handledByName, remark);
    }

    @Override
    public void consumeForPayment(Long studentId, BigDecimal amount, Long orderId, Long paymentRecordId,
                                  Long handledByUserId, String handledByName, String remark) {
        BigDecimal normalized = defaultAmount(amount);
        if (normalized.compareTo(ZERO) <= 0) {
            throw new BusinessException("INVALID_CREDIT_AMOUNT", "Credit payment amount must be greater than zero.");
        }
        if (ledgerMapper.lockStudent(studentId) == null) {
            throw new BusinessException("CREDIT_STUDENT_REQUIRED", "The Credit student does not exist.");
        }
        BigDecimal balance = getBalance(studentId);
        if (balance.compareTo(normalized) < 0) {
            throw new BusinessException("INSUFFICIENT_STUDENT_CREDIT",
                    "Student Credit balance is not enough for this payment.");
        }
        append(studentId, normalized.negate(), CREDIT_USED, orderId, paymentRecordId,
                handledByUserId, handledByName, remark);
    }

    private void append(Long studentId, BigDecimal amount, String entryType, Long orderId, Long paymentRecordId,
                        Long handledByUserId, String handledByName, String remark) {
        if (studentId == null) {
            throw new BusinessException("CREDIT_STUDENT_REQUIRED", "A student is required for Credit transactions.");
        }
        BigDecimal normalized = defaultAmount(amount);
        // append 接收已经带方向的账本变动：使用抵扣为负数，退款和充值入账为正数。
        boolean isCreditUsed = CREDIT_USED.equals(entryType);
        if ((isCreditUsed && normalized.compareTo(ZERO) >= 0)
                || (!isCreditUsed && normalized.compareTo(ZERO) <= 0)) {
            String message = isCreditUsed
                    ? "Credit usage amount must be less than zero."
                    : "Credit amount must be greater than zero.";
            throw new BusinessException("INVALID_CREDIT_AMOUNT", message);
        }
        StudentCreditLedgerEntity entry = new StudentCreditLedgerEntity();
        entry.setStudentId(studentId);
        entry.setEntryType(entryType);
        entry.setAmount(normalized);
        entry.setSourceEnrollmentOrderId(orderId);
        entry.setSourcePaymentRecordId(paymentRecordId);
        entry.setReferenceNo(orderId == null ? null : String.valueOf(orderId));
        entry.setHandledByUserId(handledByUserId);
        entry.setHandledByName(handledByName);
        entry.setOccurredAt(AppTime.nowLocalDateTime());
        entry.setRemark(remark);
        ledgerMapper.insert(entry);
    }

    private void appendManualAdjustment(Long studentId, BigDecimal amount,
                                        Long handledByUserId, String handledByName, String remark) {
        StudentCreditLedgerEntity entry = new StudentCreditLedgerEntity();
        entry.setStudentId(studentId);
        entry.setEntryType("MANUAL_ADJUSTMENT");
        entry.setAmount(amount);
        entry.setHandledByUserId(handledByUserId);
        entry.setHandledByName(handledByName);
        entry.setOccurredAt(AppTime.nowLocalDateTime());
        entry.setRemark(StringUtils.hasText(remark) ? remark.trim() : "Manual Credit balance adjustment");
        ledgerMapper.insert(entry);
    }

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount == null ? ZERO : amount.setScale(2, RoundingMode.HALF_UP);
    }
}
