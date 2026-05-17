package com.classdeduction.system.modules.lesson.dto;

import com.classdeduction.system.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 课次状态规则分页查询参数。
 */
@Getter
@Setter
@Schema(description = "Lesson status rule page query")
public class LessonStatusRulePageQuery extends PageQuery {

    @Schema(description = "Keyword matching status code or status name", example = "COMPLETED")
    private String keyword;

    @Schema(description = "Rule status", example = "ACTIVE")
    private String status;
}
