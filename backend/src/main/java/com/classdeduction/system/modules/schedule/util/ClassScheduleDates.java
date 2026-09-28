package com.classdeduction.system.modules.schedule.util;

import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** 班课排期共用的日期计算：同一天多时段只计一个上课日，假期日不计入课次。 */
public final class ClassScheduleDates {

    private ClassScheduleDates() {
    }

    public static LocalDate resolveBaseEndDate(LocalDate configuredBaseEndDate, LocalDate effectiveEndDate) {
        return configuredBaseEndDate == null ? effectiveEndDate : configuredBaseEndDate;
    }

    /** 统计原周期内按周课表应出现的上课日，计算时暂不扣除假期。 */
    public static int countScheduledDays(LocalDate startDate, LocalDate endDate,
                                         List<ClassGroupScheduleSlotEntity> slots) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) return 0;
        Set<Integer> weekdays = weekdays(slots);
        int count = 0;
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (weekdays.contains(date.getDayOfWeek().getValue())) count++;
        }
        return count;
    }

    /** 从用户配置的原始结束日重算假期后的终点，避免反复顺延时把上次结果当成新基准。 */
    public static LocalDate endDateAfterHolidays(LocalDate startDate, LocalDate baseEndDate,
                                                  String billingType, Integer totalSessions,
                                                  List<ClassGroupScheduleSlotEntity> slots,
                                                  Predicate<LocalDate> isHoliday) {
        boolean sessionBilling = "SESSION".equalsIgnoreCase(billingType)
                || "PER_SESSION".equalsIgnoreCase(billingType);
        int requiredDays = sessionBilling
                ? (totalSessions == null ? 0 : totalSessions)
                : countScheduledDays(startDate, baseEndDate, slots);
        return nthScheduledDay(startDate, requiredDays, slots, isHoliday);
    }

    /** 找到跳过假期后的第 N 个周课表上课日，用于顺延班级结束日期。 */
    public static LocalDate nthScheduledDay(LocalDate startDate, int quantity,
                                            List<ClassGroupScheduleSlotEntity> slots,
                                            Predicate<LocalDate> isHoliday) {
        if (startDate == null || quantity < 1) return null;
        Set<Integer> weekdays = weekdays(slots);
        if (weekdays.isEmpty()) return null;
        int sessions = 0;
        for (LocalDate date = startDate; !date.isAfter(startDate.plusYears(10)); date = date.plusDays(1)) {
            if (weekdays.contains(date.getDayOfWeek().getValue()) && !isHoliday.test(date)
                    && ++sessions >= quantity) return date;
        }
        return null;
    }

    private static Set<Integer> weekdays(List<ClassGroupScheduleSlotEntity> slots) {
        return slots.stream().map(ClassGroupScheduleSlotEntity::getWeekday)
                .filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
