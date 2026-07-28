package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.enrollment.dto.RenewalReminderSendRequest;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.service.impl.RenewalReminderServiceImpl;
import com.classdeduction.system.modules.enrollment.vo.RenewalReminderOverviewVO;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.systemconfig.mapper.NotificationTriggerRuleMapper;
import com.classdeduction.system.modules.systemconfig.service.SystemNotificationService;
import com.classdeduction.system.modules.systemconfig.vo.BrandConfigVO;
import com.classdeduction.system.modules.systemconfig.vo.NotificationConfigVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenewalReminderServiceImplTest {

    @Mock
    private CoursePackageMapper coursePackageMapper;
    @Mock
    private ClassEnrollmentMapper classEnrollmentMapper;
    @Mock
    private ClassGroupMapper classGroupMapper;
    @Mock
    private StudentMapper studentMapper;
    @Mock
    private NotificationTriggerRuleMapper notificationTriggerRuleMapper;
    @Mock
    private SystemNotificationService systemNotificationService;

    private RenewalReminderServiceImpl renewalReminderService;

    @BeforeEach
    void setUp() {
        renewalReminderService = new RenewalReminderServiceImpl(
                coursePackageMapper,
                classEnrollmentMapper,
                classGroupMapper,
                studentMapper,
                notificationTriggerRuleMapper,
                systemNotificationService
        );
        lenient().when(systemNotificationService.getCurrentBrandConfig()).thenReturn(
                BrandConfigVO.builder().brandName("JBT").build());
        lenient().when(systemNotificationService.getNotificationConfig()).thenReturn(
                NotificationConfigVO.builder().enabled(true).fromAddress("office@example.com").build());
        lenient().when(notificationTriggerRuleMapper.selectOne(any())).thenReturn(null);
    }

    @Test
    void shouldListLowLessonAndExpiringStudentsWithPreparedEmails() {
        CoursePackageEntity packageEntity = new CoursePackageEntity();
        packageEntity.setId(2001L);
        packageEntity.setStudentId(101L);
        packageEntity.setTeachingMode("ONE_ON_ONE");
        packageEntity.setPackageStatus("ACTIVE");
        packageEntity.setPackageName("Math 1-on-1");
        packageEntity.setDefaultLessonUnits(2);
        packageEntity.setRemainingUnits(4); // Two one-hour lessons remain.
        packageEntity.setEstimatedEndDate(LocalDate.now().plusDays(90));

        ClassEnrollmentEntity classEnrollment = new ClassEnrollmentEntity();
        classEnrollment.setId(3001L);
        classEnrollment.setStudentId(102L);
        classEnrollment.setClassId(401L);
        classEnrollment.setStatus("ACTIVE");
        classEnrollment.setExpiryDate(LocalDate.now().plusDays(10));

        StudentEntity firstStudent = student(101L, "Ava", "ava.parent@example.com");
        StudentEntity secondStudent = student(102L, "Ben", "ben.parent@example.com");
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(401L);
        classGroup.setClassName("SAT Group");

        when(coursePackageMapper.selectList(any())).thenReturn(List.of(packageEntity));
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(classEnrollment));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(firstStudent, secondStudent));
        when(classGroupMapper.selectBatchIds(any())).thenReturn(List.of(classGroup));

        RenewalReminderOverviewVO overview = renewalReminderService.getOverview();

        assertThat(overview.emailReady()).isTrue();
        assertThat(overview.reminders()).hasSize(2);
        assertThat(overview.reminders())
                .extracting(RenewalReminderOverviewVO.ReminderVO::reminderId)
                .containsExactlyInAnyOrder("PACKAGE:2001", "CLASS_ENROLLMENT:3001");
        assertThat(overview.reminders())
                .allSatisfy(item -> {
                    assertThat(item.emailAvailable()).isTrue();
                    assertThat(item.emailSubject()).contains("Renewal reminder");
                    assertThat(item.emailBody()).contains(item.studentName());
                });
    }

    @Test
    void shouldRejectReminderSendingUntilPreviewIsConfirmed() {
        RenewalReminderSendRequest request = new RenewalReminderSendRequest();
        request.setReminderIds(List.of("PACKAGE:2001"));
        request.setPreviewConfirmed(false);

        assertThatThrownBy(() -> renewalReminderService.sendConfirmedReminders(request, 9001L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("EMAIL_PREVIEW_CONFIRMATION_REQUIRED");

        verify(systemNotificationService, never()).sendApprovedEmail(any(), any());
    }

    private StudentEntity student(Long id, String name, String email) {
        StudentEntity student = new StudentEntity();
        student.setId(id);
        student.setStudentName(name);
        student.setGuardianName(name + " Parent");
        student.setGuardianEmail(email);
        return student;
    }
}
