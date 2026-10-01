package com.classdeduction.system.modules.enrollment.util;

import java.time.LocalDate;

/** 班课报名有效期的共享规则，供新报名、收据更正和报表回退读取。 */
public final class GroupRegistrationCoverage {

    private GroupRegistrationCoverage() {
    }

    /** 月包按报名日滚动计算，结束日为对应月数的周年日前一天。 */
    public static LocalDate rollingMonthEnd(LocalDate startDate, int months) {
        if (startDate == null) {
            throw new IllegalArgumentException("A start date is required to calculate rolling coverage.");
        }
        if (months < 1) {
            throw new IllegalArgumentException("Month quantity must be positive.");
        }
        return startDate.plusMonths(months).minusDays(1);
    }
}
