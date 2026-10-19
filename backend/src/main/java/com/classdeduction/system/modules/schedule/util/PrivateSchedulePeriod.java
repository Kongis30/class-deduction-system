package com.classdeduction.system.modules.schedule.util;

import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;

import java.time.LocalDateTime;
import java.util.Objects;

/** 私教排课周期边界：消课统计和请假顺延使用同一口径。 */
public final class PrivateSchedulePeriod {

    private PrivateSchedulePeriod() {
    }

    /** 只读取明确保存的周期边界；课位写入时间可能来自刷新或元数据编辑，不能推断改排课。 */
    public static LocalDateTime startAt(RecurringScheduleRuleEntity rule) {
        if (rule.getAllocationPeriodStartedAt() != null) {
            return rule.getAllocationPeriodStartedAt();
        }
        return rule.getCreatedAt();
    }

    /** 保留的旧规则课次按实际课次日期归属，历史确认不能混入本周期。 */
    public static boolean contains(LessonOccurrenceEntity occurrence, LessonRecordEntity record,
                                   RecurringScheduleRuleEntity currentRule, LocalDateTime periodStart) {
        boolean occurrencePredatesPeriod = periodStart != null
                && (occurrence.getAllocationAnchorDate() != null
                ? occurrence.getAllocationAnchorDate().isBefore(periodStart.toLocalDate())
                : occurrence.getPlannedStartAt() == null || occurrence.getPlannedStartAt().isBefore(periodStart));
        if (periodStart != null && (occurrencePredatesPeriod
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
