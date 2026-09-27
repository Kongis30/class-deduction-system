package com.classdeduction.system.modules.enrollment.vo;

import java.math.BigDecimal;

/** 单个学生在报名单中一门课程的金额，供收款窗口展示。 */
public record ParticipantCourseAmountVO(
        Long orderItemId,
        BigDecimal priceAmount,
        BigDecimal amountDue,
        BigDecimal paidAmount,
        BigDecimal balanceAmount
) {
}
