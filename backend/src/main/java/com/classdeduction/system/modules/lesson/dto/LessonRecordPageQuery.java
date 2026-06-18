package com.classdeduction.system.modules.lesson.dto;

import com.classdeduction.system.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 课次确认记录分页查询参数。
 */
@Getter
@Setter
@Schema(description = "Lesson record page query")
public class LessonRecordPageQuery extends PageQuery {

    @Schema(description = "Keyword matching lesson number, package number, or student name", example = "LS2026")
    private String keyword;

    @Schema(description = "Package ID", example = "1880000000000000001")
    private Long packageId;

    @Schema(description = "Student ID", example = "1880000000000000004")
    private Long studentId;

    @Schema(description = "Actual teacher ID", example = "1880000000000000002")
    private Long teacherId;

    @Schema(description = "Result status code", example = "COMPLETED")
    private String resultStatus;

    @Schema(description = "Record source: STAFF/TEACHER/SYSTEM", example = "STAFF")
    private String recordSource;

    @Schema(description = "Confirm stage: PENDING_ADMIN/CONFIRMED/REJECTED", example = "CONFIRMED")
    private String confirmStage;

    @Schema(description = "Confirmation start time", example = "2026-04-01T00:00:00")
    private LocalDateTime confirmedFrom;

    @Schema(description = "Confirmation end time", example = "2026-04-30T23:59:59")
    private LocalDateTime confirmedTo;
}
