package com.classdeduction.system.modules.systemconfig.service;

import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.systemconfig.config.NotificationEmailProperties;
import com.classdeduction.system.modules.systemconfig.dto.NotificationDispatchRequest;
import com.classdeduction.system.modules.systemconfig.entity.NotificationLogEntity;
import com.classdeduction.system.modules.systemconfig.mapper.BrandConfigMapper;
import com.classdeduction.system.modules.systemconfig.mapper.NotificationLogMapper;
import com.classdeduction.system.modules.systemconfig.mapper.NotificationTemplateMapper;
import com.classdeduction.system.modules.systemconfig.service.impl.SystemNotificationServiceImpl;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.lesson.mapper.HourLedgerMapper;
import com.classdeduction.system.modules.systemconfig.vo.NotificationDispatchResultVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知系统服务测试：验证安全分发不会绕过总开关和白名单保护。
 */
@ExtendWith(MockitoExtension.class)
class SystemNotificationServiceImplTest {

    @Mock
    private NotificationTemplateMapper notificationTemplateMapper;

    @Mock
    private NotificationLogMapper notificationLogMapper;

    @Mock
    private BrandConfigMapper brandConfigMapper;

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private CoursePackageMapper coursePackageMapper;

    @Mock
    private HourLedgerMapper hourLedgerMapper;

    @Mock
    private com.classdeduction.system.modules.systemconfig.mapper.NotificationConfigMapper notificationConfigMapper;

    @Mock
    private com.classdeduction.system.modules.systemconfig.mapper.NotificationTriggerRuleMapper notificationTriggerRuleMapper;

    @Mock
    private com.classdeduction.system.modules.base.mapper.StudentMapper studentMapper;

    @Mock
    private com.classdeduction.system.modules.auth.mapper.UserAccountMapper userAccountMapper;

    @Captor
    private ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<NotificationLogEntity>> updateCaptor;

    private NotificationEmailProperties notificationEmailProperties;
    private SystemNotificationServiceImpl systemNotificationService;

    @BeforeEach
    void setUp() {
        notificationEmailProperties = new NotificationEmailProperties();
        systemNotificationService = new SystemNotificationServiceImpl(
                notificationTemplateMapper,
                notificationLogMapper,
                brandConfigMapper,
                notificationConfigMapper,
                notificationTriggerRuleMapper,
                javaMailSender,
                new ObjectMapper(),
                notificationEmailProperties,
                coursePackageMapper,
                hourLedgerMapper,
                studentMapper,
                userAccountMapper
        );
    }

    @Test
    void shouldRejectDispatchWhenEmailIsDisabled() {
        NotificationDispatchRequest request = new NotificationDispatchRequest();
        request.setBatchSize(10);

        assertThatThrownBy(() -> systemNotificationService.dispatchPendingEmails(request, 3001L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Email dispatch is currently disabled.");
    }

    @Test
    void shouldDispatchAllowlistedPendingEmail() {
        notificationEmailProperties.setEnabled(true);
        notificationEmailProperties.setFromAddress("no-reply@example.com");
        notificationEmailProperties.setFromName("JIN BEST TUTORING");
        notificationEmailProperties.setStrictAllowlist(true);
        notificationEmailProperties.setAllowedRecipients(List.of("parent@example.com"));
        notificationEmailProperties.setMaxBatchSize(20);
        notificationEmailProperties.setMaxRetries(5);

        NotificationLogEntity pendingLog = new NotificationLogEntity();
        pendingLog.setId(9001L);
        pendingLog.setRecipient("parent@example.com");
        pendingLog.setChannelType("EMAIL");
        pendingLog.setRetryCount(0);
        pendingLog.setRequestPayload("{\"subject\":\"Lesson summary\",\"body\":\"Hello parent\"}");
        pendingLog.setScheduledAt(LocalDateTime.of(2026, 4, 7, 9, 0));

        when(notificationLogMapper.selectList(any())).thenReturn(List.of(pendingLog));
        when(notificationLogMapper.update(any(), any())).thenReturn(1);
        when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        NotificationDispatchRequest request = new NotificationDispatchRequest();
        request.setBatchSize(5);
        NotificationDispatchResultVO result = systemNotificationService.dispatchPendingEmails(request, 3001L);

        verify(javaMailSender).send(any(MimeMessage.class));
        assertThat(result.claimedCount()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(0);
        assertThat(result.successLogIds()).containsExactly(9001L);
    }
}
