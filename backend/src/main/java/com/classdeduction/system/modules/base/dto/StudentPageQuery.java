package com.classdeduction.system.modules.base.dto;

import com.classdeduction.system.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Student list query parameters.
 */
@Getter
@Setter
@Schema(description = "Student list query")
public class StudentPageQuery extends PageQuery {

    @Schema(description = "Keyword for student ID or student name", example = "Ava")
    private String keyword;

    @Schema(description = "Student status", example = "ACTIVE")
    private String status;

    @Schema(description = "Guardian name or phone keyword", example = "Mei Lin")
    private String guardian;

    @Schema(description = "Campus ID", example = "1001")
    private Long campusId;

    @Schema(description = "Aggregated enrollment payment status", example = "PARTIAL")
    private String paymentStatus;

    @Schema(description = "Whether the student has at least one enrollment order", example = "true")
    private Boolean hasEnrollmentOrder;

    /** 收据页使用：同时纳入存在有效收据、但报名已取消或全额退款的学生。 */
    @Schema(description = "Include students with saved receipts even without an active enrollment", example = "true")
    private Boolean includeReceiptStudents;

    @Schema(description = "Sort students by name in ascending order", example = "true")
    private Boolean sortByName;
}
