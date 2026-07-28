package com.classdeduction.system.modules.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Schema(description = "One-on-one deduction query")
public class OneOnOneDeductionQuery {

    @Schema(description = "Keyword matching package number, package name, student name, or student number")
    private String keyword;

    @Schema(description = "Teacher ID")
    private Long teacherId;

    @Schema(description = "Student ID")
    private Long studentId;

    @Schema(description = "Package ID")
    private Long packageId;

    @Schema(description = "Occurrence window start time")
    private LocalDateTime startAt;

    @Schema(description = "Occurrence window end time")
    private LocalDateTime endAt;
}
