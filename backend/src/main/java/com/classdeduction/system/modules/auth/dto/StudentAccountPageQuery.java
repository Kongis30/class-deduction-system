package com.classdeduction.system.modules.auth.dto;

import com.classdeduction.system.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 学生账号分页查询参数。
 */
@Getter
@Setter
@Schema(description = "Student account page query")
public class StudentAccountPageQuery extends PageQuery {

    @Schema(description = "Keyword matching username, student number, or student name", example = "sophia")
    private String keyword;

    @Schema(description = "Student ID", example = "1880000000000000001")
    private Long studentId;

    @Schema(description = "Account status", example = "ACTIVE")
    private String status;
}
