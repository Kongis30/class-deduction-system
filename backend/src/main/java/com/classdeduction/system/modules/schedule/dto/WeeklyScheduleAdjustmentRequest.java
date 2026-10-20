package com.classdeduction.system.modules.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 临时调整请求：以所选自然周定位原课次；编辑已有课次可改到任意日期，新建课次仍须在所选周内。
 */
@Data
@Schema(description = "Weekly temporary schedule adjustment request")
public class WeeklyScheduleAdjustmentRequest {

    @NotNull(message = "Package ID is required")
    @Schema(description = "Target package ID", example = "1880000000000000001")
    private Long packageId;

    @NotNull(message = "Week start date is required")
    @Schema(description = "Natural week start date, inclusive", example = "2026-04-13")
    private LocalDate weekStartDate;

    @Valid
    @NotEmpty(message = "Adjustment items are required")
    @Schema(description = "Occurrences to adjust in this week")
    private List<Item> items;

    @Schema(description = "Adjustment reason")
    private String reason;

    @Schema(description = "Future occurrence IDs explicitly selected for reducing reserved hours")
    private List<Long> releaseFutureOccurrenceIds;

    @Data
    @Schema(description = "Weekly temporary adjustment item")
    public static class Item {

        // 动作类型：UPDATE | CREATE | DELETE，缺省按 UPDATE 处理以兼容旧调用。
        // 因不同动作字段必填要求不同，此处不再使用静态 @NotNull，改由 service 层按 action 校验。
        @Schema(description = "Action type: UPDATE | CREATE | DELETE", example = "UPDATE")
        private String action;

        @Schema(description = "Target occurrence ID (required for UPDATE/DELETE, must be empty for CREATE)", example = "1880000000000000101")
        private Long occurrenceId;

        @Schema(description = "Adjusted planned teacher ID", example = "1880000000000000002")
        private Long plannedTeacherId;

        @Schema(description = "Adjusted planned classroom ID", example = "1880000000000000003")
        private Long plannedClassroomId;

        @Schema(description = "Lesson delivery mode: IN_PERSON or ONLINE", example = "ONLINE")
        private String deliveryMode;

        @Size(max = 128, message = "Subject name must be 128 characters or fewer")
        @Schema(description = "Course/subject name; required for CREATE when the package has multiple courses")
        private String subjectName;

        @Schema(description = "Adjusted planned start time", example = "2026-04-13T18:00:00")
        private LocalDateTime plannedStartAt;

        @Schema(description = "Adjusted planned end time", example = "2026-04-13T20:00:00")
        private LocalDateTime plannedEndAt;

        // @Min 对 null 不校验，因此 CREATE/DELETE 缺省时仍安全，只在有值时约束下限。
        @Min(value = 1, message = "Planned units must be at least 1")
        @Schema(description = "Adjusted planned units", example = "4")
        private Integer plannedUnits;

        // 每条调整的原因，CREATE/DELETE 必填，UPDATE 可选。
        @Schema(description = "Reason for this item (required for CREATE/DELETE)")
        private String itemReason;
    }
}
