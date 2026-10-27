package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 排课分配超额时返回给 Edit Schedule 的额度核算明细。 */
@Schema(description = "Schedule allocation validation details")
public record ScheduleAllocationErrorVO(
        double packageTotalHours,
        double packageRemainingHours,
        double requestedAllocationHours,
        double availableAllocationHours,
        double shortfallHours,
        double pendingReviewHours,
        double otherScheduleReservedHours,
        double preservedLessonHours,
        double transferredFutureLessonHours,
        double currentRuleRetainedHours
) {
}
