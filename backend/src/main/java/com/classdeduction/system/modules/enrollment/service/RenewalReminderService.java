package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.modules.enrollment.dto.RenewalReminderSendRequest;
import com.classdeduction.system.modules.enrollment.dto.RenewalReminderSettingsUpdateRequest;
import com.classdeduction.system.modules.enrollment.vo.RenewalReminderOverviewVO;
import com.classdeduction.system.modules.enrollment.vo.RenewalReminderSendResultVO;

/**
 * 面向管理员日常续费跟进的提醒工作台。
 */
public interface RenewalReminderService {

    RenewalReminderOverviewVO getOverview();

    RenewalReminderOverviewVO updateSettings(RenewalReminderSettingsUpdateRequest request, Long operatorId);

    RenewalReminderSendResultVO sendConfirmedReminders(RenewalReminderSendRequest request, Long operatorId);
}
