package com.classdeduction.system.modules.systemconfig.service.impl;

import com.classdeduction.system.modules.systemconfig.config.NotificationEmailProperties;
import com.classdeduction.system.modules.systemconfig.dto.NotificationDispatchRequest;
import com.classdeduction.system.modules.systemconfig.entity.NotificationConfigEntity;
import com.classdeduction.system.modules.systemconfig.mapper.NotificationConfigMapper;
import com.classdeduction.system.modules.systemconfig.service.SystemNotificationService;
import com.classdeduction.system.modules.systemconfig.vo.NotificationDispatchResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 邮件分发定时任务：每分钟检查并发送待处理的邮件通知。
 * 检查数据库 notification_config 表的 enabled 状态，辅以 YAML 配置兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailDispatchScheduler {

    private final SystemNotificationService systemNotificationService;
    private final NotificationEmailProperties emailProperties;
    private final NotificationConfigMapper notificationConfigMapper;

    @Scheduled(fixedDelayString = "${app.notification.email.dispatch-interval-ms:60000}")
    public void dispatchPendingEmails() {
        if (!isEmailEnabled()) {
            log.debug("Email dispatch is disabled, skipping.");
            return;
        }
        try {
            NotificationDispatchRequest request = new NotificationDispatchRequest();
            NotificationDispatchResultVO result = systemNotificationService.dispatchPendingEmails(request, null);
            if (result.claimedCount() > 0) {
                log.info("Email dispatch round: claimed={}, success={}, failed={}",
                        result.claimedCount(), result.successCount(), result.failedCount());
            }
        } catch (Exception e) {
            log.error("Scheduled email dispatch failed, will retry next round.", e);
        }
    }

    private boolean isEmailEnabled() {
        NotificationConfigEntity config = notificationConfigMapper.selectById(1L);
        if (config != null && config.getEnabled() != null && config.getEnabled() == 1) {
            return true;
        }
        return emailProperties.isEnabled();
    }
}