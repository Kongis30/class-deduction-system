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

import java.time.LocalDate;
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

    /** 分配额度归属快照；单课临时代课只能改变 plannedTeacherId，不能改此值。 */
    private Long allocationOwnerTeacherId;

    /** 周期课位对应的原计划日期；单课改期后仍用于续排识别该周已占用的课位。 */
    private LocalDate allocationAnchorDate;

    private String occurrenceType;

    /** 旧周期请假带入当前分配额的半小时数；仅自动生成的补课课次写入。 */
    private Integer leaveCarryoverUnits;

    private Long plannedTeacherId;

    /** 课次生成时的科目快照，避免排课模板后续修改影响历史课次展示。 */
    private String subjectName;

    private String deliveryMode;

    private Long plannedClassroomId;

    /** 是否由单次调课明确指定教室；为 false 时教室跟随周期排课槽。 */
    @TableField("classroom_overridden")
    private Integer classroomOverridden;

    private LocalDateTime plannedStartAt;

    private LocalDateTime plannedEndAt;

    private Integer plannedUnits;

    private String planningStatus;

    /** 命中的 Course + Grade 班课假期；非空时此课次因放假取消。 */
    private Long courseHolidayId;

    /** 放假取消前的状态，撤销假期时用于恢复原课次。 */
    private String holidayPreviousStatus;

    private Long sourceOccurrenceId;

    private Integer conflictChecked;

    private String notes;
}
