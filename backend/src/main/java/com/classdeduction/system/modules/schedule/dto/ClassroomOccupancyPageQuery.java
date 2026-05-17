package com.classdeduction.system.modules.schedule.dto;

import com.classdeduction.system.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 教室占用分页查询参数。
 */
@Getter
@Setter
@Schema(description = "Classroom occupancy page query")
public class ClassroomOccupancyPageQuery extends PageQuery {

    @Schema(description = "Classroom ID", example = "1880000000000000003")
    private Long classroomId;

    @Schema(description = "Teacher ID", example = "1880000000000000002")
    private Long teacherId;

    @Schema(description = "Start time", example = "2026-04-07T00:00:00")
    private LocalDateTime startAt;

    @Schema(description = "End time", example = "2026-04-13T23:59:59")
    private LocalDateTime endAt;
}
