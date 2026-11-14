package com.classdeduction.system.modules.report.mapper;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 尚无课程级分摊的历史流水；报表侧按课程价格权重回退分摊。 */
@Getter
@Setter
public class UnallocatedPaymentRow {

    private Long paymentId;
    private Long enrollmentOrderId;
    private BigDecimal amount;
    private String monthKey;
    private Long campusId;
    private String campusNameSnapshot;
}
