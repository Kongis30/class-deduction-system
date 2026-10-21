package com.classdeduction.system.modules.lesson.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 保存管理员批准正式请假时生成的补课课次及初始版本，避免撤销时碰到人工改过的安排。 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("lesson_record_makeup_effect")
public class LessonRecordMakeupEffectEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long sourceLessonRecordId;

    private Long makeupOccurrenceId;

    private Integer generatedVersion;

    private LocalDateTime plannedStartAt;

    private LocalDateTime plannedEndAt;

    private Integer plannedUnits;

    private Long scheduleRuleId;

    private Long scheduleSlotId;

    private Long allocationOwnerTeacherId;

    private LocalDate allocationAnchorDate;

    private Long plannedTeacherId;

    private String subjectName;

    private String deliveryMode;

    private Long plannedClassroomId;

    private Integer leaveCarryoverUnits;

    private String planningStatus;

    private Long sourceOccurrenceId;
}
