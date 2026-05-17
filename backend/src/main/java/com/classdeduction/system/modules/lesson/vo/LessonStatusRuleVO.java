package com.classdeduction.system.modules.lesson.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 课次状态规则响应。
 */
@Builder
@Schema(description = "Lesson status rule response")
public record LessonStatusRuleVO(
        Long id,
        String statusCode,
        String statusName,
        Boolean defaultShouldDeduct,
        String deductMode,
        Integer fixedUnits,
        Boolean defaultShouldExtend,
        Boolean allowManualOverride,
        String status,
        String remark
) {
}
