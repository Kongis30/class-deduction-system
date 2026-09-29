package com.classdeduction.system.modules.enrollment.util;

import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WholeDollarBillingTest {

    @Test
    void automaticQuoteRoundsUpButDoesNotChangeExactPrice() {
        assertThat(WholeDollarBilling.ceil(new BigDecimal("1291.38"))).isEqualByComparingTo("1292.00");
        assertThat(WholeDollarBilling.ceil(new BigDecimal("1291.00"))).isEqualByComparingTo("1291.00");
        assertThat(WholeDollarBilling.ceil(new BigDecimal("0.01"))).isEqualByComparingTo("1.00");
    }

    @Test
    void rejectsFractionalManualAmountsWithoutChangingThem() {
        assertThat(WholeDollarBilling.requireInteger(new BigDecimal("10.00"), "Discount"))
                .isEqualByComparingTo("10.00");
        assertThatThrownBy(() -> WholeDollarBilling.requireInteger(new BigDecimal("10.01"), "Discount"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Discount must be a whole dollar");
    }

    @Test
    void onlyMarkedOrdersUseTheNewPolicy() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        assertThat(WholeDollarBilling.appliesTo(order)).isFalse();
        order.setBillingPolicy(WholeDollarBilling.POLICY);
        assertThat(WholeDollarBilling.appliesTo(order)).isTrue();
    }
}
