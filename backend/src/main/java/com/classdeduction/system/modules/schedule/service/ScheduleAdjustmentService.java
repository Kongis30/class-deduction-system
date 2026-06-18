package com.classdeduction.system.modules.schedule.service;

import com.classdeduction.system.modules.schedule.dto.ScheduleAdjustmentRequest;
import com.classdeduction.system.modules.schedule.dto.WeeklyScheduleAdjustmentRequest;
import com.classdeduction.system.modules.schedule.vo.ScheduleAdjustmentResultVO;
import com.classdeduction.system.modules.schedule.vo.WeeklyScheduleAdjustmentResultVO;

public interface ScheduleAdjustmentService {

    ScheduleAdjustmentResultVO adjust(ScheduleAdjustmentRequest request);

    WeeklyScheduleAdjustmentResultVO adjustWeekly(WeeklyScheduleAdjustmentRequest request);
}
