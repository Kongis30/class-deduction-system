package com.classdeduction.system.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 调课日志实体：记录课次调整前后快照。
 */
@Getter
@Setter
@TableName("lesson_adjustment_log")
@Schema(description = "Lesson adjustment log entity")
public class LessonAdjustmentLogEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long occurrenceId;

    private String adjustmentType;

    private String beforeSnapshot;

    private String afterSnapshot;

    private String reason;

    private Long adjustedBy;

    private LocalDateTime adjustedAt;
}
