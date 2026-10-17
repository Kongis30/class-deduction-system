package com.classdeduction.system.modules.schedule.util;

import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleSlotEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** 私教排课周期边界：消课统计和请假顺延使用同一口径。 */
public final class PrivateSchedulePeriod {

    private PrivateSchedulePeriod() {
    }

    /** 旧规则复用 ID 时，以最后创建的有效课位作为最后一次改排课的起点。 */
    public static LocalDateTime startAt(RecurringScheduleRuleEntity rule,
                                        List<RecurringScheduleSlotEntity> currentSlots) {
        if (rule.getAllocationPeriodStartedAt() != null) {
            return rule.getAllocationPeriodStartedAt();
        }
        LocalDateTime start = rule.getCreatedAt();
        if (rule.getSupersedesRuleId() == null) {
            for (RecurringScheduleSlotEntity slot : currentSlots) {
                if (Objects.equals(rule.getId(), slot.getRuleId()) && slot.getCreatedAt() != null
                        && (start == null || slot.getCreatedAt().isAfter(start))) {
                    start = slot.getCreatedAt();
                }
            }
        }
        return start;
    }

    /** 保留的旧规则课次按实际课次日期归属，历史确认不能混入本周期。 */
    public static boolean contains(LessonOccurrenceEntity occurrence, LessonRecordEntity record,
                                   RecurringScheduleRuleEntity currentRule, LocalDateTime periodStart) {
        if (periodStart != null && (occurrence.getPlannedStartAt() == null
                || occurrence.getPlannedStartAt().isBefore(periodStart)
                || record != null && record.getConfirmedAt() != null
                && record.getConfirmedAt().isBefore(periodStart))) {
            return false;
        }
        if (Objects.equals(currentRule.getId(), occurrence.getScheduleRuleId())) {
            return true;
        }
        if (periodStart != null && occurrence.getScheduleRuleId() == null
                && "ADHOC".equals(occurrence.getOccurrenceType())
                && occurrence.getCreatedAt() != null
                && !occurrence.getCreatedAt().isBefore(periodStart)) {
            return true;
        }
        return currentRule.getSupersedesRuleId() != null && periodStart != null
                && (record == null || record.getConfirmedAt() == null
                || !record.getConfirmedAt().isBefore(periodStart));
    }
}
