package com.classdeduction.system.modules.schedule.util;

import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClassScheduleDatesTest {

    @Test
    void listsUniqueWeeklyClassDatesAndSkipsProjectHolidays() {
        List<ClassGroupScheduleSlotEntity> saturdaySlots = List.of(saturdaySlot(), saturdaySlot());
        LocalDate holiday = LocalDate.of(2026, 10, 10);

        assertThat(ClassScheduleDates.scheduledDays(
                LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 17), saturdaySlots,
                date -> date.equals(holiday)))
                .containsExactly(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 17));
    }

    @Test
    void segmentedScheduleLeavesTheGapUnscheduled() {
        ClassGroupScheduleSlotEntity firstPeriod = mondaySlot();
        firstPeriod.setEffectiveStartDate(LocalDate.of(2026, 10, 5));
        firstPeriod.setEffectiveEndDate(LocalDate.of(2026, 10, 11));
        ClassGroupScheduleSlotEntity secondPeriod = mondaySlot();
        secondPeriod.setEffectiveStartDate(LocalDate.of(2026, 10, 26));
        secondPeriod.setEffectiveEndDate(LocalDate.of(2026, 11, 1));

        assertThat(ClassScheduleDates.scheduledDays(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 1), List.of(firstPeriod, secondPeriod), date -> false))
                .containsExactly(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 26));
    }

    @Test
    void laterPeriodUsesItsOwnWeeklyTimesInsteadOfRepeatingTheEarlierPeriod() {
        ClassGroupScheduleSlotEntity firstPeriod = mondaySlot();
        firstPeriod.setEffectiveStartDate(LocalDate.of(2026, 10, 5));
        firstPeriod.setEffectiveEndDate(LocalDate.of(2026, 10, 18));
        ClassGroupScheduleSlotEntity secondPeriod = new ClassGroupScheduleSlotEntity();
        secondPeriod.setWeekday(3);
        secondPeriod.setEffectiveStartDate(LocalDate.of(2026, 10, 19));
        secondPeriod.setEffectiveEndDate(LocalDate.of(2026, 11, 1));

        assertThat(ClassScheduleDates.scheduledDays(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 1), List.of(firstPeriod, secondPeriod), date -> false))
                .containsExactly(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12),
                        LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 28));
    }

    @Test
    void purchasedSessionCoverageCountsDatesAcrossDifferentPeriods() {
        ClassGroupScheduleSlotEntity firstPeriod = mondaySlot();
        firstPeriod.setEffectiveStartDate(LocalDate.of(2026, 10, 5));
        firstPeriod.setEffectiveEndDate(LocalDate.of(2026, 10, 12));
        ClassGroupScheduleSlotEntity secondPeriod = new ClassGroupScheduleSlotEntity();
        secondPeriod.setWeekday(3);
        secondPeriod.setEffectiveStartDate(LocalDate.of(2026, 10, 19));
        secondPeriod.setEffectiveEndDate(LocalDate.of(2026, 10, 28));

        assertThat(ClassScheduleDates.nthScheduledDay(LocalDate.of(2026, 10, 5), 4,
                LocalDate.of(2026, 10, 28), List.of(firstPeriod, secondPeriod), date -> false))
                .isEqualTo(LocalDate.of(2026, 10, 28));
    }

    @Test
    void purchasedSessionCoverageSkipsHolidayDates() {
        List<ClassGroupScheduleSlotEntity> mondaySchedule = List.of(mondaySlot());

        assertThat(ClassScheduleDates.nthScheduledDay(LocalDate.of(2026, 10, 5), 2,
                LocalDate.of(2026, 10, 31), mondaySchedule,
                date -> date.equals(LocalDate.of(2026, 10, 12))))
                .isEqualTo(LocalDate.of(2026, 10, 19));
    }

    private static ClassGroupScheduleSlotEntity mondaySlot() {
        ClassGroupScheduleSlotEntity slot = new ClassGroupScheduleSlotEntity();
        slot.setWeekday(1);
        return slot;
    }

    private static ClassGroupScheduleSlotEntity saturdaySlot() {
        ClassGroupScheduleSlotEntity slot = new ClassGroupScheduleSlotEntity();
        slot.setWeekday(6);
        return slot;
    }
}
