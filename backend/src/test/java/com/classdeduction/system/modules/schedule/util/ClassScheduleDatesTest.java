package com.classdeduction.system.modules.schedule.util;

import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClassScheduleDatesTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 5);
    private static final LocalDate BASE_END = LocalDate.of(2026, 10, 31);
    private static final List<ClassGroupScheduleSlotEntity> MONDAY_SCHEDULE = List.of(mondaySlot());

    @Test
    void recalculatesFromOriginalEndWhenHolidaysAreAddedOrRemoved() {
        assertThat(recalculate(Set.of())).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(recalculate(Set.of(LocalDate.of(2026, 10, 12))))
                .isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(recalculate(Set.of(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 19))))
                .isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(recalculate(Set.of())).isEqualTo(LocalDate.of(2026, 10, 26));
    }

    @Test
    void sessionBillingKeepsItsPurchasedSessionCountAcrossHolidays() {
        LocalDate endDate = ClassScheduleDates.endDateAfterHolidays(START, BASE_END,
                "SESSION", 4, MONDAY_SCHEDULE,
                date -> date.equals(LocalDate.of(2026, 10, 12)));

        assertThat(endDate).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    void missingBaseEndDateIsInitializedOnceAndDoesNotDrift() {
        LocalDate baseEnd = ClassScheduleDates.resolveBaseEndDate(null, BASE_END);
        LocalDate firstExtension = recalculate(Set.of(LocalDate.of(2026, 10, 12)));
        LocalDate afterRemovingHoliday = ClassScheduleDates.resolveBaseEndDate(baseEnd, firstExtension);

        assertThat(firstExtension).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(afterRemovingHoliday).isEqualTo(BASE_END);
        assertThat(ClassScheduleDates.endDateAfterHolidays(START, afterRemovingHoliday,
                "MONTHLY", null, MONDAY_SCHEDULE, date -> false))
                .isEqualTo(LocalDate.of(2026, 10, 26));
    }

    private LocalDate recalculate(Set<LocalDate> holidays) {
        return ClassScheduleDates.endDateAfterHolidays(START, BASE_END,
                "MONTHLY", null, MONDAY_SCHEDULE, holidays::contains);
    }

    private static ClassGroupScheduleSlotEntity mondaySlot() {
        ClassGroupScheduleSlotEntity slot = new ClassGroupScheduleSlotEntity();
        slot.setWeekday(1);
        return slot;
    }
}
