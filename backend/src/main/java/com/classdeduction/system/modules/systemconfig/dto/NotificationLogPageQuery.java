package com.classdeduction.system.modules.systemconfig.dto;

import com.classdeduction.system.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 通知日志分页查询参数。
 */
@Getter
@Setter
@Schema(description = "Notification log page query")
public class NotificationLogPageQuery extends PageQuery {

    @Schema(description = "Recipient keyword", example = "parent@example.com")
    private String keyword;

    @Schema(description = "Channel type", example = "EMAIL")
    private String channelType;

    @Schema(description = "Send status", example = "PENDING")
    private String sendStatus;

    @Schema(description = "Student ID", example = "1880000000000000001")
    private Long studentId;

    @Schema(description = "Package ID", example = "1880000000000000002")
    private Long packageId;

    @Schema(description = "Lesson record ID", example = "1880000000000000003")
    private Long lessonRecordId;

    @Schema(description = "Scheduled time from", example = "2026-04-07T00:00:00")
    private LocalDateTime scheduledFrom;

    @Schema(description = "Scheduled time to", example = "2026-04-07T23:59:59")
    private LocalDateTime scheduledTo;
}
