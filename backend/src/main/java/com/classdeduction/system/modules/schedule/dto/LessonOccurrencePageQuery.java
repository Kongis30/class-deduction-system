package com.classdeduction.system.modules.schedule.dto;

import com.classdeduction.system.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 计划课次分页查询参数。
 */
@Getter
@Setter
@Schema(description = "Lesson occurrence page query")
public class LessonOccurrencePageQuery extends PageQuery {

    @Schema(description = "Keyword matching lesson number, package number, or student name", example = "LS2026")
    private String keyword;

    @Schema(description = "Package ID", example = "1880000000000000001")
    private Long packageId;

    @Schema(description = "Student ID", example = "1880000000000000004")
    private Long studentId;

    @Schema(description = "Teacher ID", example = "1880000000000000002")
    private Long teacherId;

    @Schema(description = "Classroom ID", example = "1880000000000000003")
    private Long classroomId;

    @Schema(description = "Planning status: single value like 'SCHEDULED' or comma-separated like 'SCHEDULED,ADJUSTED'", example = "SCHEDULED,ADJUSTED")
    private String planningStatus;

    @Schema(description = "Window start time", example = "2026-04-07T00:00:00")
    private LocalDateTime startAt;

    @Schema(description = "Window end time", example = "2026-04-30T23:59:59")
    private LocalDateTime endAt;

    @Schema(description = "Class group ID", example = "1880000000000000005")
    private Long classGroupId;
}
