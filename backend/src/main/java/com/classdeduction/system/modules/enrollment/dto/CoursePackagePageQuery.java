package com.classdeduction.system.modules.enrollment.dto;

import com.classdeduction.system.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 课时包分页查询参数。
 */
@Getter
@Setter
@Schema(description = "课时包分页查询参数")
public class CoursePackagePageQuery extends PageQuery {

    @Schema(description = "关键字，支持课时包编号、学生姓名、课时包名称", example = "PK2026")
    private String keyword;

    @Schema(description = "学生ID", example = "1880000000000000001")
    private Long studentId;

    @Schema(description = "课程产品ID", example = "1880000000000000003")
    private Long courseProductId;

    @Schema(description = "课时包状态", example = "ACTIVE")
    private String packageStatus;

    @Schema(description = "Comma-separated package statuses, e.g. ACTIVE,PAUSED")
    private String packageStatuses;

    @Schema(description = "主老师ID", example = "1880000000000000002")
    private Long teacherId;

    @Schema(description = "教学模式：ONE_ON_ONE / GROUP", example = "ONE_ON_ONE")
    private String teachingMode;

    @Schema(description = "排序字段：subjectName | progress", example = "subjectName")
    private String sortBy;

    @Schema(description = "排序方向：asc | desc", example = "asc")
    private String sortOrder;

    @Schema(description = "Only include active packages ending within this many days", example = "15")
    private Integer renewalDueWithinDays;

    @Schema(description = "Include batched recurring schedule status and next lesson details")
    private Boolean includeScheduleSummary;

    @Schema(description = "Prioritize packages with low remaining balance before pagination")
    private Boolean prioritizeLowBalance;
}
