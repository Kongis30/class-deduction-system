package com.classdeduction.system.modules.schedule.dto;

import com.classdeduction.system.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 周期排课规则分页查询参数。
 */
@Getter
@Setter
@Schema(description = "Recurring schedule rule page query")
public class RecurringScheduleRulePageQuery extends PageQuery {

    @Schema(description = "Keyword matching package number, rule notes, or student name", example = "PK2026")
    private String keyword;

    @Schema(description = "Rule status", example = "ACTIVE")
    private String ruleStatus;

    @Schema(description = "Teacher ID", example = "1880000000000000002")
    private Long teacherId;

    @Schema(description = "Classroom ID", example = "1880000000000000003")
    private Long classroomId;

    @Schema(description = "Course package ID", example = "1880000000000000004")
    private Long packageId;
}
