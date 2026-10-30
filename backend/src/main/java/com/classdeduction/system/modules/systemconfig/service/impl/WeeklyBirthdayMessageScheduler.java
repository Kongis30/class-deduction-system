package com.classdeduction.system.modules.systemconfig.service.impl;

import com.classdeduction.system.common.time.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每天按美国东部时间刷新本周生日消息；唯一键保证一周只保留一条。 */
@Component
@RequiredArgsConstructor
public class WeeklyBirthdayMessageScheduler {

    private final BirthdayRefreshCoordinator coordinator;

    @Scheduled(cron = "0 0 9 * * *", zone = AppTime.APP_TIME_ZONE)
    public void refreshWeeklyBirthdayMessages() {
        coordinator.requestRefresh();
    }
}
