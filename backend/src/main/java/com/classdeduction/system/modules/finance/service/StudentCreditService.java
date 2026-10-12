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

    void creditFromTopUp(Long studentId, BigDecimal amount, Long orderId, Long paymentRecordId,
                         Long handledByUserId, String handledByName, String remark);

    /** 删除充值收据时追加可审计的负向流水；余额不足或重复冲回时拒绝操作。 */
    void reverseTopUp(Long studentId, Long orderId, BigDecimal amount,
                      Long handledByUserId, String handledByName, String remark);

    void consumeForPayment(Long studentId, BigDecimal amount, Long orderId, Long paymentRecordId,
                           Long handledByUserId, String handledByName, String remark);
}
