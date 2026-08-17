package com.classdeduction.system.common.util;

import com.classdeduction.system.common.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 半课时单位（1单位=0.5h）与小时之间的转换工具。
 * 内部存储统一使用半课时单位（int），对外暴露（API/前端）统一使用小时。
 */
public final class UnitConverter {

    private UnitConverter() {}

    /**
     * 将半课时单位转换为小时（整除，丢失0.5h精度）。
     */
    public static int toHours(int halfHourUnits) {
        return halfHourUnits / 2;
    }

    /**
     * 将半课时单位转换为小时（保留0.5h精度）。
     */
    public static double toHoursDecimal(int halfHourUnits) {
        return halfHourUnits / 2.0;
    }

    /**
     * 将可空半课时单位转换为小时（保留0.5h精度）。
     */
    public static double toHoursDecimal(Integer halfHourUnits) {
        return halfHourUnits == null ? 0.0 : halfHourUnits / 2.0;
    }

    /**
     * 将半课时单位转换为小时（保留0.5h精度，返回字符串如 "1.5"）。
     */
    public static String toHoursString(int halfHourUnits) {
        int hours = halfHourUnits / 2;
        int remainder = halfHourUnits % 2;
        if (remainder == 0) {
            return String.valueOf(hours);
        }
        return hours + ".5";
    }

    /**
     * 将小时数转换为半课时单位。
     */
    public static int toHalfHourUnits(int hours) {
        return hours * 2;
    }

    /**
     * Convert externally supplied hours to internal half-hour units.
     */
    public static int toHalfHourUnits(BigDecimal hours) {
        if (hours == null) {
            throw new BusinessException("HOURS_REQUIRED", "Hours are required.");
        }
        BigDecimal units = hours.multiply(BigDecimal.valueOf(2));
        try {
            return units.setScale(0, RoundingMode.UNNECESSARY).intValueExact();
        } catch (ArithmeticException ex) {
            throw new BusinessException("INVALID_HOUR_INCREMENT", "Hours must use 0.5-hour increments.");
        }
    }
}
