package com.classdeduction.system.modules.schedule.service;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.modules.schedule.dto.ScheduleAdjustmentRequest;
import com.classdeduction.system.modules.schedule.dto.WeeklyScheduleAdjustmentRequest;
import com.classdeduction.system.modules.schedule.vo.ScheduleAdjustmentResultVO;
import com.classdeduction.system.modules.schedule.vo.WeeklyScheduleAdjustmentResultVO;

public interface ScheduleAdjustmentService {

    ScheduleAdjustmentResultVO adjust(ScheduleAdjustmentRequest request);

    ScheduleAdjustmentResultVO adjustByTeacher(ScheduleAdjustmentRequest request, CurrentUserResponse currentUser);

    WeeklyScheduleAdjustmentResultVO adjustWeekly(WeeklyScheduleAdjustmentRequest request, CurrentUserResponse currentUser);
}
