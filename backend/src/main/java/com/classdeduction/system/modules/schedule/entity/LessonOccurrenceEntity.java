package com.classdeduction.system.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 计划课次实体：排课后的具体一节课。
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("lesson_occurrence")
@Schema(description = "Lesson occurrence entity")
public class LessonOccurrenceEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String lessonNo;

    @TableField("package_id")
    private Long packageId;

    /** Shared private lesson group; one occurrence is generated for the whole group. */
    private Long privateLessonGroupId;

    @TableField("class_group_id")
    private Long classGroupId;

    private Long studentId;

    private Long scheduleRuleId;

    private Long scheduleSlotId;

    private String occurrenceType;

    private Long plannedTeacherId;

    private Long plannedClassroomId;

    private LocalDateTime plannedStartAt;

    private LocalDateTime plannedEndAt;

    private Integer plannedUnits;

    private String planningStatus;

    /** 命中的 Course 级班课假期；非空时此课次因放假取消。 */
    private Long courseHolidayId;

    /** 放假取消前的状态，撤销假期时用于恢复原课次。 */
    private String holidayPreviousStatus;

    private Long sourceOccurrenceId;

    private Integer conflictChecked;

    private String notes;
}
