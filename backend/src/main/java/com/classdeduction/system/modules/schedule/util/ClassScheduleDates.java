package com.classdeduction.system.modules.schedule.util;

import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

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
        int count = 0;
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (hasClassOn(date, slots, endDate)) count++;
        }
        return count;
    }

    /** 返回各生效日期段内真实可上的日期，同一天多个时段只返回一次。 */
    public static List<LocalDate> scheduledDays(LocalDate startDate, LocalDate endDate,
                                                List<ClassGroupScheduleSlotEntity> slots,
                                                Predicate<LocalDate> isHoliday) {
        return scheduledDays(startDate, endDate, endDate, slots, isHoliday);
    }

    public static List<LocalDate> scheduledDays(LocalDate startDate, LocalDate endDate,
                                                LocalDate baseEndDate,
                                                List<ClassGroupScheduleSlotEntity> slots,
                                                Predicate<LocalDate> isHoliday) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate) || slots == null || slots.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<LocalDate> dates = new java.util.ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (hasClassOn(date, slots, baseEndDate) && !isHoliday.test(date)) dates.add(date);
        }
        return List.copyOf(dates);
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
        return nthScheduledDay(startDate, requiredDays, baseEndDate, slots, isHoliday);
    }

    /** 找到跳过假期后的第 N 个周课表上课日，用于顺延班级结束日期。 */
    public static LocalDate nthScheduledDay(LocalDate startDate, int quantity,
                                            List<ClassGroupScheduleSlotEntity> slots,
                                            Predicate<LocalDate> isHoliday) {
        return nthScheduledDay(startDate, quantity, null, slots, isHoliday);
    }

    public static LocalDate nthScheduledDay(LocalDate startDate, int quantity, LocalDate baseEndDate,
                                             List<ClassGroupScheduleSlotEntity> slots,
                                             Predicate<LocalDate> isHoliday) {
        if (startDate == null || quantity < 1) return null;
        int sessions = 0;
        for (LocalDate date = startDate; !date.isAfter(startDate.plusYears(10)); date = date.plusDays(1)) {
            if (hasClassOn(date, slots, baseEndDate) && !isHoliday.test(date)
                    && ++sessions >= quantity) return date;
        }
        return null;
    }

    /** 全部课次与报名日期共用此判断；段后假期顺延沿用最后一段的周课表。 */
    public static boolean slotAppliesOn(ClassGroupScheduleSlotEntity slot, LocalDate date,
                                        LocalDate baseEndDate, List<ClassGroupScheduleSlotEntity> slots) {
        if (slot == null || date == null || !Objects.equals(slot.getWeekday(), date.getDayOfWeek().getValue())) {
            return false;
        }
        LocalDate start = slot.getEffectiveStartDate();
        LocalDate end = slot.getEffectiveEndDate();
        if (start == null && end == null) return true;
        if (start == null || end == null || date.isBefore(start)) return false;
        if (!date.isAfter(end)) return true;
        LocalDate lastSegmentEnd = slots.stream().map(ClassGroupScheduleSlotEntity::getEffectiveEndDate)
                .filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);
        return baseEndDate != null && date.isAfter(baseEndDate) && end.equals(lastSegmentEnd);
    }

    public static boolean hasClassOn(LocalDate date, List<ClassGroupScheduleSlotEntity> slots,
                                     LocalDate baseEndDate) {
        return slots != null && slots.stream().anyMatch(slot -> slotAppliesOn(slot, date, baseEndDate, slots));
    }
}
