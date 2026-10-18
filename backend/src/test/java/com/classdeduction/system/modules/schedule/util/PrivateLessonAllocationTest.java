package com.classdeduction.system.modules.schedule.util;

import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleSlotEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 排课占用与消课状态的边界测试，防止已扣课时再次出现在 Allocated 中。 */
class PrivateLessonAllocationTest {

    @Test
    void shouldCountOnlyUndeductedScheduledLessonsOnce() {
        LessonOccurrenceEntity completed = occurrence(1L, 4, "MERGED");
        LessonOccurrenceEntity scheduled = occurrence(2L, 4, "SCHEDULED");
        LessonOccurrenceEntity awaitingAdmin = occurrence(3L, 3, "SCHEDULED");
        LessonOccurrenceEntity cancelled = occurrence(4L, 4, "CANCELLED");
        Map<Long, LessonRecordEntity> records = Map.of(
                1L, record("CONFIRMED"),
                3L, record("PENDING_ADMIN"));

        int reserved = PrivateLessonAllocation.reservedUnits(
                List.of(completed, scheduled, scheduled, awaitingAdmin, cancelled), records);

        assertThat(reserved).isEqualTo(7);
    }

    @Test
    void shouldReleaseConfirmedLeaveWithoutDeduction() {
        LessonOccurrenceEntity leave = occurrence(1L, 4, "SCHEDULED");
        assertThat(PrivateLessonAllocation.reservedUnits(leave, record("CONFIRMED"))).isZero();
    }

    @Test
    void teacherProposedFormalLeaveStillReservesPlannedHours() {
        LessonOccurrenceEntity leave = occurrence(1L, 4, "SCHEDULED");
        LessonRecordEntity teacherProposal = record("PENDING_ADMIN");
        teacherProposal.setResultStatus("FORMAL_LEAVE");
        teacherProposal.setShouldDeduct(0);
        teacherProposal.setDeductedUnits(0);

        assertThat(PrivateLessonAllocation.reservedUnits(leave, teacherProposal)).isEqualTo(4);
    }

    @Test
    void rebuiltSlotWithSameAllocationDoesNotAddHistoricalLessonsAgain() {
        RecurringScheduleSlotEntity oldSlot = recurringSlot(1L, 7, 32);
        oldSlot.setSubjectName("ELA");
        RecurringScheduleSlotEntity currentSlot = recurringSlot(1L, 7, 32);
        currentSlot.setSubjectName("English");
        currentSlot.setClassroomId(4001L);
        currentSlot.setDeliveryMode("IN_PERSON");

        assertThat(PrivateLessonAllocation.hasEquivalentCurrentSlot(oldSlot, List.of(currentSlot), 1L))
                .isTrue();
        currentSlot.setAllocatedUnits(28);
        assertThat(PrivateLessonAllocation.hasEquivalentCurrentSlot(oldSlot, List.of(currentSlot), 1L))
                .isFalse();
    }

    private RecurringScheduleSlotEntity recurringSlot(Long ruleId, int weekday, int allocatedUnits) {
        RecurringScheduleSlotEntity slot = new RecurringScheduleSlotEntity();
        slot.setRuleId(ruleId);
        slot.setTeacherId(3001L);
        slot.setWeekday(weekday);
        slot.setStartTime(LocalTime.of(12, 0));
        slot.setEndTime(LocalTime.of(14, 0));
        slot.setPlannedUnits(4);
        slot.setAllocatedUnits(allocatedUnits);
        return slot;
    }

    private LessonOccurrenceEntity occurrence(Long id, int plannedUnits, String planningStatus) {
        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setId(id);
        occurrence.setPlannedUnits(plannedUnits);
        occurrence.setPlanningStatus(planningStatus);
        return occurrence;
    }

    private LessonRecordEntity record(String confirmStage) {
        LessonRecordEntity record = new LessonRecordEntity();
        record.setConfirmStage(confirmStage);
        return record;
    }
}
