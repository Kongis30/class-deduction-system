package com.classdeduction.system.modules.finance.service;

import com.classdeduction.system.modules.finance.vo.StudentCreditBalanceVO;

import java.math.BigDecimal;
import java.util.List;

public interface StudentCreditService {

    String PAYMENT_METHOD_CODE = "STUDENT_CREDIT";

    List<StudentCreditBalanceVO> listBalances(String keyword);

    BigDecimal getBalance(Long studentId);

    void adjustBalance(Long studentId, BigDecimal targetBalance,
                       Long handledByUserId, String handledByName, String remark);

    void creditFromRefund(Long studentId, BigDecimal amount, Long orderId, Long paymentRecordId,
                          Long handledByUserId, String handledByName, String remark);

    /** 冲回未消费的退款 Credit；expectedBalance 用于阻止覆盖后续 Credit 操作。 */
    void reverseRefundCredit(Long studentId, BigDecimal amount, BigDecimal expectedBalance,
                             Long orderId, Long paymentRecordId,
                             Long handledByUserId, String handledByName, String remark);

    void creditFromTopUp(Long studentId, BigDecimal amount, Long orderId, Long paymentRecordId,
                         Long handledByUserId, String handledByName, String remark);

    /** 锁定学生并按当前可用余额冲回充值 Credit；可选核对确认框余额，余额不足时最多扣至零。 */
    BigDecimal reverseTopUpUpToAvailable(Long studentId, Long orderId, BigDecimal amount,
                                         BigDecimal expectedBalance, Long handledByUserId,
                                         String handledByName, String remark);

    void consumeForPayment(Long studentId, BigDecimal amount, Long orderId, Long paymentRecordId,
                           Long handledByUserId, String handledByName, String remark);
}
