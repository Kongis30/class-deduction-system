package com.classdeduction.system.modules.lesson.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/** 课次最终确认调整未来排课时保存的前后值，用于撤销时精确还原且验证期间未被改动。 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("lesson_record_schedule_effect")
public class LessonRecordScheduleEffectEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long sourceLessonRecordId;

    private Long affectedOccurrenceId;

    private String beforePlanningStatus;

    private LocalDateTime beforePlannedStartAt;

    private LocalDateTime beforePlannedEndAt;

    private Integer beforePlannedUnits;

    private String afterPlanningStatus;

    private LocalDateTime afterPlannedStartAt;

    private LocalDateTime afterPlannedEndAt;

    private Integer afterPlannedUnits;
}
