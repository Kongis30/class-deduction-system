package com.classdeduction.system.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@TableName("class_group_schedule_slot")
public class ClassGroupScheduleSlotEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long classGroupId;

    /** 同一班课时段跨多次编辑的稳定课位，用于保留单节调整的原位置。 */
    private Long slotLineageId;

    private Long teacherId;

    private String deliveryMode;

    private Long classroomId;

    private String alternateDeliveryMode;

    private Long alternateClassroomId;

    private String subjectName;

    private Long alternateTeacherId;

    private String alternateSubjectName;

    private LocalDate rotationAnchorDate;

    /** 本时段所属排课段的首末日期；旧时段为空时覆盖班级原周期。 */
    private LocalDate effectiveStartDate;

    private LocalDate effectiveEndDate;

    private Integer weekday;

    private LocalTime startTime;

    private LocalTime endTime;

    private LocalTime morningEndTime;

    private LocalTime afternoonStartTime;

    private String attendanceOptions;
}
