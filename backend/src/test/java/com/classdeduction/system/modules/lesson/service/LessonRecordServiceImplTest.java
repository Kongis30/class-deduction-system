package com.classdeduction.system.modules.lesson.service;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.lesson.dto.LessonRecordConfirmRequest;
import com.classdeduction.system.modules.lesson.entity.HourLedgerEntity;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.entity.LessonStatusRuleEntity;
import com.classdeduction.system.modules.lesson.entity.PackageBalanceSnapshotEntity;
import com.classdeduction.system.modules.lesson.mapper.HourLedgerMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonStatusRuleMapper;
import com.classdeduction.system.modules.lesson.mapper.PackageBalanceSnapshotMapper;
import com.classdeduction.system.modules.lesson.service.impl.LessonRecordServiceImpl;
import com.classdeduction.system.modules.lesson.vo.LessonRecordConfirmResultVO;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.systemconfig.service.NotificationOutboxService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 课次确认服务测试：覆盖正常扣课和余额不足阻断这两个最关键的消课场景。
 */
@ExtendWith(MockitoExtension.class)
class LessonRecordServiceImplTest {

    @Mock
    private LessonRecordMapper lessonRecordMapper;

    @Mock
    private HourLedgerMapper hourLedgerMapper;

    @Mock
    private PackageBalanceSnapshotMapper packageBalanceSnapshotMapper;

    @Mock
    private LessonStatusRuleMapper lessonStatusRuleMapper;

    @Mock
    private LessonOccurrenceMapper lessonOccurrenceMapper;

    @Mock
    private CoursePackageMapper coursePackageMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private TeacherMapper teacherMapper;

    @Mock
    private ClassroomMapper classroomMapper;

    @Mock
    private NotificationOutboxService notificationOutboxService;

    @Mock
    private com.classdeduction.system.modules.systemconfig.mapper.NotificationTriggerRuleMapper notificationTriggerRuleMapper;

    @Captor
    private ArgumentCaptor<LessonRecordEntity> recordCaptor;

    @Captor
    private ArgumentCaptor<HourLedgerEntity> ledgerCaptor;

    @Captor
    private ArgumentCaptor<CoursePackageEntity> packageCaptor;

    @Captor
    private ArgumentCaptor<PackageBalanceSnapshotEntity> snapshotCaptor;

    private LessonRecordServiceImpl lessonRecordService;

    @BeforeEach
    void setUp() {
        lessonRecordService = new LessonRecordServiceImpl(
                lessonRecordMapper,
                hourLedgerMapper,
                packageBalanceSnapshotMapper,
                lessonStatusRuleMapper,
                lessonOccurrenceMapper,
                coursePackageMapper,
                studentMapper,
                teacherMapper,
                classroomMapper,
                notificationOutboxService,
                notificationTriggerRuleMapper
        );
    }

    @Test
    void shouldConfirmCompletedLessonAndWriteLedger() {
        LessonRecordConfirmRequest request = new LessonRecordConfirmRequest();
        request.setOccurrenceId(1001L);
        request.setResultStatus("COMPLETED");
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(buildOccurrence());
        when(lessonRecordMapper.selectOne(any())).thenReturn(null);
        when(coursePackageMapper.selectById(501L)).thenReturn(buildPackage(10, 2));
        when(studentMapper.selectById(601L)).thenReturn(buildStudent());
        when(lessonStatusRuleMapper.selectOne(any())).thenReturn(buildCompletedRule());
        when(teacherMapper.selectById(701L)).thenReturn(buildTeacher());
        when(classroomMapper.selectById(801L)).thenReturn(buildClassroom());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(buildFutureOccurrence(2001L, LocalDate.of(2026, 4, 20), 4), buildFutureOccurrence(2002L, LocalDate.of(2026, 4, 27), 4)));
        when(coursePackageMapper.updateById(any(CoursePackageEntity.class))).thenReturn(1);
        when(packageBalanceSnapshotMapper.selectById(501L)).thenReturn(null);
        stubInsertedIds();

        LessonRecordConfirmResultVO result = lessonRecordService.confirmLesson(request, currentStaffUser());

        verify(lessonRecordMapper).insert(recordCaptor.capture());
        verify(hourLedgerMapper).insert(ledgerCaptor.capture());
        verify(coursePackageMapper).updateById(packageCaptor.capture());
        verify(packageBalanceSnapshotMapper).insert(snapshotCaptor.capture());
        verify(notificationOutboxService).queueLessonBalanceEmail(any(LessonRecordEntity.class), any(CoursePackageEntity.class), any(StudentEntity.class), eq("Completed"));

        assertThat(recordCaptor.getValue().getDeductedUnits()).isEqualTo(4);
        assertThat(ledgerCaptor.getValue().getChangeUnits()).isEqualTo(-4);
        assertThat(packageCaptor.getValue().getRemainingUnits()).isEqualTo(6);
        assertThat(snapshotCaptor.getValue().getRemainingUnits()).isEqualTo(6);
        assertThat(result.ledgerId()).isEqualTo(3001L);
        assertThat(result.remainingUnits()).isEqualTo(3);
        assertThat(result.packageCompleted()).isFalse();
    }

    @Test
    void shouldRejectWhenDeductionWouldMakeBalanceNegative() {
        LessonRecordConfirmRequest request = new LessonRecordConfirmRequest();
        request.setOccurrenceId(1001L);
        request.setResultStatus("NO_SHOW");
        when(lessonOccurrenceMapper.selectById(1001L)).thenReturn(buildOccurrence());
        when(lessonRecordMapper.selectOne(any())).thenReturn(null);
        when(coursePackageMapper.selectById(501L)).thenReturn(buildPackage(2, 10));
        when(studentMapper.selectById(601L)).thenReturn(buildStudent());
        when(lessonStatusRuleMapper.selectOne(any())).thenReturn(buildNoShowRule());

        assertThatThrownBy(() -> lessonRecordService.confirmLesson(request, currentStaffUser()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("The package does not have enough remaining units for this confirmation.");
    }

    private void stubInsertedIds() {
        doAnswer(invocation -> {
            LessonRecordEntity entity = invocation.getArgument(0);
            entity.setId(2001L);
            return 1;
        }).when(lessonRecordMapper).insert(any(LessonRecordEntity.class));
        doAnswer(invocation -> {
            HourLedgerEntity entity = invocation.getArgument(0);
            entity.setId(3001L);
            return 1;
        }).when(hourLedgerMapper).insert(any(HourLedgerEntity.class));
    }

    private CurrentUserResponse currentStaffUser() {
        return new CurrentUserResponse(999L, "staff", "Front Desk", "STAFF", null, null, List.of("STAFF"));
    }

    private LessonOccurrenceEntity buildOccurrence() {
        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setId(1001L);
        occurrence.setLessonNo("LS-1001");
        occurrence.setPackageId(501L);
        occurrence.setStudentId(601L);
        occurrence.setPlannedTeacherId(701L);
        occurrence.setPlannedClassroomId(801L);
        occurrence.setPlannedStartAt(LocalDateTime.of(2026, 4, 12, 18, 0));
        occurrence.setPlannedEndAt(LocalDateTime.of(2026, 4, 12, 20, 0));
        occurrence.setPlannedUnits(4);
        occurrence.setPlanningStatus("SCHEDULED");
        return occurrence;
    }

    private LessonOccurrenceEntity buildFutureOccurrence(Long id, LocalDate date, int units) {
        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setId(id);
        occurrence.setPackageId(501L);
        occurrence.setPlannedStartAt(date.atTime(18, 0));
        occurrence.setPlannedEndAt(date.atTime(20, 0));
        occurrence.setPlannedUnits(units);
        occurrence.setPlanningStatus("SCHEDULED");
        return occurrence;
    }

    private CoursePackageEntity buildPackage(int remainingUnits, int consumedUnits) {
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(501L);
        coursePackage.setPackageNo("PK-501");
        coursePackage.setPackageStatus("ACTIVE");
        coursePackage.setTotalUnits(12);
        coursePackage.setConsumedUnits(consumedUnits);
        coursePackage.setRemainingUnits(remainingUnits);
        return coursePackage;
    }

    private StudentEntity buildStudent() {
        StudentEntity student = new StudentEntity();
        student.setId(601L);
        student.setStudentName("Olivia Lin");
        return student;
    }

    private TeacherEntity buildTeacher() {
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(701L);
        teacher.setStatus("ACTIVE");
        return teacher;
    }

    private ClassroomEntity buildClassroom() {
        ClassroomEntity classroom = new ClassroomEntity();
        classroom.setId(801L);
        classroom.setStatus("ACTIVE");
        return classroom;
    }

    private LessonStatusRuleEntity buildCompletedRule() {
        LessonStatusRuleEntity rule = new LessonStatusRuleEntity();
        rule.setId(1L);
        rule.setStatusCode("COMPLETED");
        rule.setStatusName("Completed");
        rule.setDefaultShouldDeduct(1);
        rule.setDeductMode("ACTUAL_UNITS");
        rule.setDefaultShouldExtend(0);
        rule.setAllowManualOverride(1);
        rule.setStatus("ACTIVE");
        return rule;
    }

    private LessonStatusRuleEntity buildNoShowRule() {
        LessonStatusRuleEntity rule = new LessonStatusRuleEntity();
        rule.setId(2L);
        rule.setStatusCode("NO_SHOW");
        rule.setStatusName("No-show");
        rule.setDefaultShouldDeduct(1);
        rule.setDeductMode("PLANNED_UNITS");
        rule.setDefaultShouldExtend(0);
        rule.setAllowManualOverride(1);
        rule.setStatus("ACTIVE");
        return rule;
    }
}
