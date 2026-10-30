package com.classdeduction.system.modules.schedule.util;

import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PrivateSchedulePeriodTest {

    @Test
    void explicitPeriodBoundaryTakesPrecedenceOverRuleCreationTime() {
        RecurringScheduleRuleEntity rule = new RecurringScheduleRuleEntity();
        rule.setCreatedAt(LocalDateTime.of(2026, 9, 24, 10, 0));
        rule.setAllocationPeriodStartedAt(LocalDateTime.of(2026, 10, 11, 15, 16));

        assertThat(PrivateSchedulePeriod.startAt(rule)).isEqualTo(rule.getAllocationPeriodStartedAt());
    }

    @Test
    void oldLessonConfirmedAfterNewPeriodDoesNotCountInCurrentPeriod() {
        RecurringScheduleRuleEntity rule = rule();
        LessonOccurrenceEntity oldLesson = lesson(1L, LocalDateTime.of(2026, 10, 5, 18, 0));
        LessonRecordEntity record = new LessonRecordEntity();
        record.setConfirmedAt(LocalDateTime.of(2026, 10, 18, 12, 0));

        assertThat(PrivateSchedulePeriod.contains(oldLesson, record, rule, PrivateSchedulePeriod.startAt(rule)))
                .isFalse();
    }

    @Test
    void adjustedLessonRetainsItsOriginalPeriodThroughAllocationAnchor() {
        RecurringScheduleRuleEntity rule = rule();
        LessonOccurrenceEntity adjusted = lesson(1L, LocalDateTime.of(2026, 10, 20, 18, 0));
        adjusted.setAllocationAnchorDate(LocalDate.of(2026, 10, 5));

        assertThat(PrivateSchedulePeriod.contains(adjusted, null, rule, PrivateSchedulePeriod.startAt(rule)))
                .isFalse();
    }

    @Test
    void currentPeriodLessonAndPreservedFutureExceptionBelongToCurrentPeriod() {
        RecurringScheduleRuleEntity rule = rule();
        rule.setSupersedesRuleId(2L);
        LessonOccurrenceEntity current = lesson(1L, LocalDateTime.of(2026, 10, 20, 18, 0));
        LessonOccurrenceEntity preserved = lesson(2L, LocalDateTime.of(2026, 10, 21, 18, 0));

        assertThat(PrivateSchedulePeriod.contains(current, null, rule, PrivateSchedulePeriod.startAt(rule)))
                .isTrue();
        assertThat(PrivateSchedulePeriod.contains(preserved, null, rule, PrivateSchedulePeriod.startAt(rule)))
                .isTrue();
    }

    @Test
    void legacySameDayLessonDependsOnTheVerifiedPeriodBoundary() {
        RecurringScheduleRuleEntity rule = new RecurringScheduleRuleEntity();
        rule.setId(1L);
        rule.setCreatedAt(LocalDateTime.of(2026, 9, 30, 20, 11));
        LessonOccurrenceEntity lesson = lesson(1L, LocalDateTime.of(2026, 9, 30, 18, 0));
        lesson.setAllocationAnchorDate(LocalDate.of(2026, 9, 30));
        LessonRecordEntity record = new LessonRecordEntity();
        record.setConfirmedAt(LocalDateTime.of(2026, 10, 2, 9, 51));

        // 旧规则没有明确起点时按创建日读取；不能仅凭上课早于创建时刻判为旧周期。
        assertThat(PrivateSchedulePeriod.contains(lesson, record, rule, PrivateSchedulePeriod.startAt(rule)))
                .isTrue();

        // 只有经业务证据确认的新周期起点，才能将同一节课排除在新周期外。
        rule.setAllocationPeriodStartedAt(LocalDateTime.of(2026, 10, 1, 0, 0));
        assertThat(PrivateSchedulePeriod.contains(lesson, record, rule, PrivateSchedulePeriod.startAt(rule)))
                .isFalse();
    }

    private RecurringScheduleRuleEntity rule() {
        RecurringScheduleRuleEntity rule = new RecurringScheduleRuleEntity();
        rule.setId(1L);
        rule.setAllocationPeriodStartedAt(LocalDateTime.of(2026, 10, 11, 15, 16));
        return rule;
    }

    private LessonOccurrenceEntity lesson(Long ruleId, LocalDateTime startAt) {
        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setScheduleRuleId(ruleId);
        occurrence.setPlannedStartAt(startAt);
        return occurrence;
    }
}
