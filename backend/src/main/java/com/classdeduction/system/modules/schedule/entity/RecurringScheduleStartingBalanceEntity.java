package com.classdeduction.system.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 每个排课周期在各成员课包中的 Starting Hours 快照。 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("recurring_schedule_starting_balance")
public class RecurringScheduleStartingBalanceEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long scheduleRuleId;

    private Long packageId;

    /** 以半小时为单位；仅在新周期或人工修正课包余额时更新。 */
    private Integer startingUnits;
}
