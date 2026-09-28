package com.classdeduction.system.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@TableName("class_group_schedule_slot")
public class ClassGroupScheduleSlotEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long classGroupId;

    private Long teacherId;

    private String deliveryMode;

    private Long classroomId;

    private String subjectName;

    private Integer weekday;

    private LocalTime startTime;

    private LocalTime endTime;

    private LocalTime morningEndTime;

    private LocalTime afternoonStartTime;

    private String attendanceOptions;
}
