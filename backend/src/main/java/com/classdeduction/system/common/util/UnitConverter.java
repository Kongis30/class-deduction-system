package com.classdeduction.system.common.util;

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
}
