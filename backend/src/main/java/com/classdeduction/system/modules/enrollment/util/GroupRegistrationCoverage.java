package com.classdeduction.system.modules.enrollment.util;

import java.time.LocalDate;
import java.time.YearMonth;

/** 班课报名有效期的共享规则，供新报名、收据更正和报表回退读取。 */
public final class GroupRegistrationCoverage {

    private GroupRegistrationCoverage() {
    }

    /** 月包将开始日所在自然月计为第一个月，返回所购月数对应月份的月末。 */
    public static LocalDate monthPackageEndDate(LocalDate startDate, int months) {
        if (startDate == null) {
            throw new IllegalArgumentException("A start date is required to calculate monthly package coverage.");
        }
        if (months < 1) {
            throw new IllegalArgumentException("Month quantity must be positive.");
        }
        return YearMonth.from(startDate).plusMonths(months - 1L).atEndOfMonth();
    }
}
