package com.classdeduction.system.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalTime;

/**
 * 周期排课时段实体：定义每周固定时间段。
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("recurring_schedule_slot")
@Schema(description = "Recurring schedule slot entity")
public class RecurringScheduleSlotEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long ruleId;

    private Long teacherId;

    private Integer weekday;

    private LocalTime startTime;

    private LocalTime endTime;

    private Integer plannedUnits;

    private String slotStatus;
}
