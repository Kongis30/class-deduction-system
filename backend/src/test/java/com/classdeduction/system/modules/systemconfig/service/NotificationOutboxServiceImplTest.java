package com.classdeduction.system.modules.systemconfig.service;

import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.systemconfig.entity.BrandConfigEntity;
import com.classdeduction.system.modules.systemconfig.entity.NotificationLogEntity;
import com.classdeduction.system.modules.systemconfig.entity.NotificationTemplateEntity;
import com.classdeduction.system.modules.systemconfig.entity.NotificationTriggerRuleEntity;
import com.classdeduction.system.modules.systemconfig.mapper.BrandConfigMapper;
import com.classdeduction.system.modules.systemconfig.mapper.NotificationLogMapper;
import com.classdeduction.system.modules.systemconfig.mapper.NotificationTemplateMapper;
import com.classdeduction.system.modules.systemconfig.service.impl.NotificationOutboxServiceImpl;
import com.classdeduction.system.common.util.JsonHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知出站服务测试：确保课后通知会写入 pending 邮件日志，并正确使用监护人邮箱。
 */
@ExtendWith(MockitoExtension.class)
class NotificationOutboxServiceImplTest {

    @Mock
    private NotificationTemplateMapper notificationTemplateMapper;

    @Mock
    private NotificationLogMapper notificationLogMapper;

    @Mock
    private BrandConfigMapper brandConfigMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private com.classdeduction.system.modules.systemconfig.mapper.NotificationTriggerRuleMapper notificationTriggerRuleMapper;

    @Captor
    private ArgumentCaptor<NotificationLogEntity> logCaptor;

    private NotificationOutboxServiceImpl notificationOutboxService;

    @BeforeEach
    void setUp() {
        notificationOutboxService = new NotificationOutboxServiceImpl(
                notificationTemplateMapper,
                notificationLogMapper,
                brandConfigMapper,
                studentMapper,
                notificationTriggerRuleMapper,
                new JsonHelper(new ObjectMapper())
        );
    }

    @Test
    void shouldQueuePendingEmailForPrimaryGuardian() {
        when(notificationTriggerRuleMapper.selectOne(any())).thenReturn(buildEnabledLessonConfirmedRule());
        when(notificationTemplateMapper.selectOne(any())).thenReturn(buildTemplate());
        when(brandConfigMapper.selectOne(any())).thenReturn(buildBrandConfig());
        when(studentMapper.selectById(601L)).thenReturn(buildStudentWithGuardianEmail());

        notificationOutboxService.queueLessonBalanceEmail(buildLessonRecord(), buildCoursePackage(), buildStudent(), "Completed");

        verify(notificationLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getRecipient()).isEqualTo("primary@example.com");
        assertThat(logCaptor.getValue().getSendStatus()).isEqualTo("PENDING");
        assertThat(logCaptor.getValue().getRequestPayload()).contains("Olivia Lin");
        assertThat(logCaptor.getValue().getRequestPayload()).contains("JIN BEST TUTORING");
    }

    @Test
    void shouldQueueLowBalanceHourEmailWhenEnabled() {
        when(notificationTriggerRuleMapper.selectOne(any())).thenReturn(buildLowBalanceRule("LOW_BALANCE_HOUR"));
        when(notificationTemplateMapper.selectOne(any())).thenReturn(buildTemplate());
        when(brandConfigMapper.selectOne(any())).thenReturn(buildBrandConfig());
        when(studentMapper.selectById(601L)).thenReturn(buildStudentWithGuardianEmail());

        notificationOutboxService.queueLowBalanceWarning(buildCoursePackage(), buildStudent());

        verify(notificationLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getSendStatus()).isEqualTo("PENDING");
        assertThat(logCaptor.getValue().getPackageId()).isEqualTo(501L);
        assertThat(logCaptor.getValue().getStudentId()).isEqualTo(601L);
    }

    @Test
    void shouldQueueLowBalanceSessionEmailWhenEnabled() {
        when(notificationTriggerRuleMapper.selectOne(any())).thenReturn(buildLowBalanceRule("LOW_BALANCE_SESSION"));
        when(notificationTemplateMapper.selectOne(any())).thenReturn(buildTemplate());
        when(brandConfigMapper.selectOne(any())).thenReturn(buildBrandConfig());
        when(studentMapper.selectById(601L)).thenReturn(buildStudentWithGuardianEmail());

        notificationOutboxService.queueLowBalanceSessionWarning(601L, 800L, "SHSAT Group A", 1);

        verify(notificationLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getSendStatus()).isEqualTo("PENDING");
        assertThat(logCaptor.getValue().getStudentId()).isEqualTo(601L);
        assertThat(logCaptor.getValue().getPackageId()).isNull();
        assertThat(logCaptor.getValue().getRequestPayload()).contains("remainingSessions");
    }

    @Test
    void shouldQueueLowBalanceMonthEmailWhenEnabled() {
        when(notificationTriggerRuleMapper.selectOne(any())).thenReturn(buildLowBalanceRule("LOW_BALANCE_MONTH"));
        when(notificationTemplateMapper.selectOne(any())).thenReturn(buildTemplate());
        when(brandConfigMapper.selectOne(any())).thenReturn(buildBrandConfig());
        when(studentMapper.selectById(601L)).thenReturn(buildStudentWithGuardianEmail());

        notificationOutboxService.queueLowBalanceMonthWarning(601L, 800L, "SHSAT Group A", 1,
                LocalDate.of(2026, 7, 31));

        verify(notificationLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getSendStatus()).isEqualTo("PENDING");
        assertThat(logCaptor.getValue().getStudentId()).isEqualTo(601L);
        assertThat(logCaptor.getValue().getRequestPayload()).contains("remainingMonths");
        assertThat(logCaptor.getValue().getRequestPayload()).contains("2026-07-31");
    }

    @Test
    void shouldSkipLowBalanceWhenRecentlyNotified() {
        when(notificationTriggerRuleMapper.selectOne(any())).thenReturn(buildLowBalanceRule("LOW_BALANCE_HOUR"));
        when(notificationTemplateMapper.selectOne(any())).thenReturn(buildTemplate());
        when(brandConfigMapper.selectOne(any())).thenReturn(buildBrandConfig());
        when(studentMapper.selectById(601L)).thenReturn(buildStudentWithGuardianEmail());
        // 模拟去重命中：近期已存在同维度未失效日志
        when(notificationLogMapper.selectCount(any())).thenReturn(1L);

        notificationOutboxService.queueLowBalanceWarning(buildCoursePackage(), buildStudent());

        verify(notificationLogMapper, never()).insert(any(NotificationLogEntity.class));
    }

    private NotificationTriggerRuleEntity buildLowBalanceRule(String triggerType) {
        NotificationTriggerRuleEntity entity = new NotificationTriggerRuleEntity();
        entity.setId(70002L);
        entity.setTriggerType(triggerType);
        entity.setEnabled(1);
        entity.setRecipientType("GUARDIAN_EMAIL");
        entity.setThresholdValue(4);
        return entity;
    }

    private NotificationTriggerRuleEntity buildEnabledLessonConfirmedRule() {
        NotificationTriggerRuleEntity entity = new NotificationTriggerRuleEntity();
        entity.setId(70001L);
        entity.setTriggerType("LESSON_CONFIRMED");
        entity.setEnabled(1);
        entity.setRecipientType("GUARDIAN_EMAIL");
        return entity;
    }

    private NotificationTemplateEntity buildTemplate() {
        NotificationTemplateEntity entity = new NotificationTemplateEntity();
        entity.setId(50001L);
        entity.setTemplateCode("LESSON_BALANCE_EMAIL");
        entity.setChannelType("EMAIL");
        entity.setStatus("ACTIVE");
        entity.setSubjectTemplate("[${brandName}] ${studentName} lesson summary and remaining units");
        entity.setBodyTemplate("Student: ${studentName}\\nRemaining units: ${remainingHours}\\n${brandName}");
        return entity;
    }

    private BrandConfigEntity buildBrandConfig() {
        BrandConfigEntity entity = new BrandConfigEntity();
        entity.setId(40001L);
        entity.setBrandName("JIN BEST TUTORING");
        entity.setStatus("ACTIVE");
        return entity;
    }

    private StudentEntity buildStudentWithGuardianEmail() {
        StudentEntity entity = new StudentEntity();
        entity.setId(601L);
        entity.setStudentName("Olivia Lin");
        entity.setGuardianEmail("primary@example.com");
        return entity;
    }

    private LessonRecordEntity buildLessonRecord() {
        LessonRecordEntity entity = new LessonRecordEntity();
        entity.setId(9001L);
        entity.setConfirmedByUserId(3001L);
        entity.setDeductedUnits(4);
        entity.setResultStatus("COMPLETED");
        entity.setConfirmedAt(LocalDateTime.of(2026, 4, 7, 20, 0));
        return entity;
    }

    private CoursePackageEntity buildCoursePackage() {
        CoursePackageEntity entity = new CoursePackageEntity();
        entity.setId(501L);
        entity.setPackageName("SHSAT Math 40h");
        entity.setRemainingUnits(20);
        entity.setEstimatedEndDate(LocalDate.of(2026, 5, 30));
        return entity;
    }

    private StudentEntity buildStudent() {
        StudentEntity entity = new StudentEntity();
        entity.setId(601L);
        entity.setStudentName("Olivia Lin");
        return entity;
    }
}
