package com.classdeduction.system.modules.schedule.service;

import com.classdeduction.system.modules.schedule.dto.ScheduleAdjustmentRequest;
import com.classdeduction.system.modules.schedule.vo.ScheduleAdjustmentResultVO;

public interface ScheduleAdjustmentService {

    ScheduleAdjustmentResultVO adjust(ScheduleAdjustmentRequest request);
}
