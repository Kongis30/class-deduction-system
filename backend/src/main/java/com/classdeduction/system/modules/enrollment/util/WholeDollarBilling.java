package com.classdeduction.system.modules.enrollment.util;

import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 新报名的整美元计价契约；旧订单无标记，继续按原始分值处理。 */
public final class WholeDollarBilling {

    public static final String POLICY = "WHOLE_DOLLAR_V1";

    private WholeDollarBilling() {
    }

    /** 自动报价和比例优惠向上取整；入库仍保留 DECIMAL(?,2) 结构。 */
    public static BigDecimal ceil(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.CEILING).setScale(2);
    }

    public static boolean appliesTo(EnrollmentOrderEntity order) {
        return order != null && POLICY.equals(order.getBillingPolicy());
    }

    /** 人工输入不得静默取整，否则收款、抵扣和实际凭证可能不一致。 */
    public static BigDecimal requireInteger(BigDecimal amount, String field) {
        if (amount != null && amount.stripTrailingZeros().scale() > 0) {
            throw new BusinessException("WHOLE_DOLLAR_REQUIRED", field + " must be a whole dollar amount.");
        }
        return amount;
    }
}
