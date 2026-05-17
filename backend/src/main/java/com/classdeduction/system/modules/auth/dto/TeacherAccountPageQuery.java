package com.classdeduction.system.modules.auth.dto;

import com.classdeduction.system.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 教师账号分页查询参数。
 */
@Getter
@Setter
@Schema(description = "Teacher account page query")
public class TeacherAccountPageQuery extends PageQuery {

    @Schema(description = "Keyword matching username, teacher number, or teacher name", example = "emma")
    private String keyword;

    @Schema(description = "Teacher ID", example = "1880000000000000001")
    private Long teacherId;

    @Schema(description = "Account status", example = "ACTIVE")
    private String status;
}
