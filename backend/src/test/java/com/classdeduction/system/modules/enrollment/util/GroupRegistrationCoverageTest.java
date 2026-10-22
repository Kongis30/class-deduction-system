package com.classdeduction.system.modules.enrollment.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroupRegistrationCoverageTest {

    @Test
    void shouldCountStartMonthAsTheFirstPurchasedMonth() {
        assertThat(GroupRegistrationCoverage.monthPackageEndDate(LocalDate.of(2026, 9, 15), 2))
                .isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(GroupRegistrationCoverage.monthPackageEndDate(LocalDate.of(2026, 10, 22), 2))
                .isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(GroupRegistrationCoverage.monthPackageEndDate(LocalDate.of(2026, 11, 2), 2))
                .isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void shouldReturnStartMonthEndForOneMonthAndHandleLeapYears() {
        assertThat(GroupRegistrationCoverage.monthPackageEndDate(LocalDate.of(2026, 10, 22), 1))
                .isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(GroupRegistrationCoverage.monthPackageEndDate(LocalDate.of(2028, 1, 31), 2))
                .isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void shouldRejectInvalidMonthCoverageInput() {
        assertThatThrownBy(() -> GroupRegistrationCoverage.monthPackageEndDate(null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GroupRegistrationCoverage.monthPackageEndDate(LocalDate.of(2026, 1, 1), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
