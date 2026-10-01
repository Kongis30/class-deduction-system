package com.classdeduction.system.modules.enrollment.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroupRegistrationCoverageTest {

    @Test
    void shouldCalculateRollingMonthEndFromTheSelectedStartDate() {
        assertThat(GroupRegistrationCoverage.rollingMonthEnd(LocalDate.of(2026, 10, 15), 2))
                .isEqualTo(LocalDate.of(2026, 12, 14));
    }

    @Test
    void shouldHandleMonthEndStartDatesConsistently() {
        assertThat(GroupRegistrationCoverage.rollingMonthEnd(LocalDate.of(2027, 1, 31), 1))
                .isEqualTo(LocalDate.of(2027, 2, 27));
    }

    @Test
    void shouldRejectInvalidMonthCoverageInput() {
        assertThatThrownBy(() -> GroupRegistrationCoverage.rollingMonthEnd(null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GroupRegistrationCoverage.rollingMonthEnd(LocalDate.of(2026, 1, 1), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
