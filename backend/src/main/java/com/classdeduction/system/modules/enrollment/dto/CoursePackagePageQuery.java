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

    @Schema(description = "课时包状态", example = "ACTIVE")
    private String packageStatus;

    @Schema(description = "主老师ID", example = "1880000000000000002")
    private Long teacherId;
}
