package com.classdeduction.system.modules.schedule.service.impl;

import com.classdeduction.system.modules.enrollment.service.EnrollmentOrderService;
import com.classdeduction.system.modules.schedule.service.RecurringScheduleRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 课次滚动续期定时任务：周期性为 ACTIVE 的一对一排课规则与班级补足未来课次，
 * 避免 horizon（一对一）或产品周期（班课）之后日历空白。生成逻辑本身幂等。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OccurrenceRenewalScheduler {

    private final RecurringScheduleRuleService recurringScheduleRuleService;
    private final EnrollmentOrderService enrollmentOrderService;

    /**
     * 默认每天运行一次（启动后延迟 1 分钟）。一对一与班课各自独立续期，互不影响。
     */
    @Scheduled(fixedDelayString = "${app.schedule.renewal-interval-ms:86400000}",
            initialDelayString = "${app.schedule.renewal-initial-delay-ms:60000}")
    public void renewOccurrences() {
        log.info("Occurrence renewal job started.");
        int oneOnOne = 0;
        int classGroup = 0;
        try {
            oneOnOne = recurringScheduleRuleService.renewActiveRuleOccurrences();
        } catch (Exception e) {
            log.error("One-on-one occurrence renewal failed: {}", e.getMessage(), e);
        }
        try {
            classGroup = enrollmentOrderService.renewActiveClassOccurrences();
        } catch (Exception e) {
            log.error("Class occurrence renewal failed: {}", e.getMessage(), e);
        }
        log.info("Occurrence renewal job finished. oneOnOneInserted={}, classInserted={}", oneOnOne, classGroup);
    }
}
