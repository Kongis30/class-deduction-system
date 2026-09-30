package com.classdeduction.system.modules.schedule.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 周排课时段时间规则：数据库用次日 00:00 表示当天 24:00。 */
public final class ScheduleSlotTime {
    private static final LocalTime MIDNIGHT = LocalTime.MIDNIGHT;

    private ScheduleSlotTime() {
    }

    /** 将 24:00 转成 LocalTime 可存储的 00:00，其余值按标准时间解析。 */
    public static LocalTime parseEndTime(String value) {
        String time = value == null ? "" : value.trim();
        if ("24:00".equals(time) || "24:00:00".equals(time)) {
            return MIDNIGHT;
        }
        return LocalTime.parse(time.length() > 5 ? time.substring(0, 5) : time);
    }

    /** 00:00 作为结束时间时，只有开始时间晚于午夜才表示当天 24:00。 */
    public static boolean isEndOfDay(LocalTime start, LocalTime end) {
        return start != null && end != null && !start.equals(MIDNIGHT) && end.equals(MIDNIGHT);
    }

    public static boolean isValidRange(LocalTime start, LocalTime end) {
        return start != null && end != null && (end.isAfter(start) || isEndOfDay(start, end));
    }

    public static int durationMinutes(LocalTime start, LocalTime end) {
        return endMinutes(start, end) - start.toSecondOfDay() / 60;
    }

    public static int endMinutes(LocalTime start, LocalTime end) {
        return isEndOfDay(start, end) ? 24 * 60 : end.toSecondOfDay() / 60;
    }

    /** 生成课次的结束时刻；跨到次日 00:00 时，课次仍归属开始日期。 */
    public static LocalDateTime endAt(LocalDate date, LocalTime start, LocalTime end) {
        return LocalDateTime.of(isEndOfDay(start, end) ? date.plusDays(1) : date, end);
    }

    public static String formatEndTime(LocalTime start, LocalTime end) {
        return isEndOfDay(start, end) ? "24:00" : end.toString().substring(0, 5);
    }
}
