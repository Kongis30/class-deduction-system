package com.classdeduction.system.modules.systemconfig.service.impl;

import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.systemconfig.service.SystemMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每天按美国东部时间刷新本周生日消息；唯一键保证一周只保留一条。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyBirthdayMessageScheduler {

    private final SystemMessageService systemMessageService;

    @Scheduled(cron = "0 0 9 * * *", zone = AppTime.APP_TIME_ZONE)
    public void refreshWeeklyBirthdayMessages() {
        int recipients = systemMessageService.refreshCurrentWeekBirthdayMessages();
        log.info("Weekly birthday messages refreshed for {} administrators.", recipients);
    }
}
