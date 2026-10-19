package com.classdeduction.system.modules.schedule.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 排课时段课程名称变更前后的历史快照。 */
@Getter
@Setter
public class RecurringSlotSubjectHistoryVO {

    private Long scheduleSlotId;

    private LocalDateTime adjustedAt;

    private String beforeSubjectName;

    private String afterSubjectName;
}
