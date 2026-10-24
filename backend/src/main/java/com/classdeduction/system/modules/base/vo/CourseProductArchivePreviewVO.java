package com.classdeduction.system.modules.base.vo;

import java.math.BigDecimal;

/** 归档确认窗口使用的影响范围快照；fingerprint 用于防止预览后范围变化仍按旧内容确认。 */
public record CourseProductArchivePreviewVO(
        Long productId,
        String productName,
        long classCount,
        long activeEnrollmentCount,
        long activePackageCount,
        long recurringRuleCount,
        long unfinishedLessonCount,
        BigDecimal estimatedDeductedHours,
        String fingerprint
) {
}
