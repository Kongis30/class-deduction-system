package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.common.model.PageResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.dto.CoursePackagePageQuery;
import com.classdeduction.system.modules.enrollment.dto.CoursePackageHoursAdjustmentRequest;
import com.classdeduction.system.modules.enrollment.dto.CoursePackageResumeRequest;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.service.impl.CoursePackageServiceImpl;
import com.classdeduction.system.modules.enrollment.vo.CoursePackageListItemVO;
import com.classdeduction.system.modules.enrollment.vo.GroupClassPackageProgressVO;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.lesson.mapper.HourLedgerMapper;
import com.classdeduction.system.modules.lesson.mapper.PackageBalanceSnapshotMapper;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleSlotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

/**
 * 课时包服务测试：覆盖主老师筛选为空时的短路逻辑，避免分页总数和列表记录不一致。
 */
@ExtendWith(MockitoExtension.class)
class CoursePackageServiceImplTest {

    @Mock
    private CoursePackageMapper coursePackageMapper;

    @Mock
    private EnrollmentOrderItemMapper enrollmentOrderItemMapper;

    @Mock
    private PackageTeacherAssignmentMapper packageTeacherAssignmentMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private TeacherMapper teacherMapper;

    @Mock
    private CourseProductMapper courseProductMapper;

    @Mock
    private ClassGroupMapper classGroupMapper;

    @Mock
    private ClassEnrollmentMapper classEnrollmentMapper;

    @Mock
    private LessonOccurrenceMapper lessonOccurrenceMapper;

    @Mock
    private RecurringScheduleRuleMapper recurringScheduleRuleMapper;

    @Mock
    private RecurringScheduleSlotMapper recurringScheduleSlotMapper;

    @Mock
    private LessonRecordMapper lessonRecordMapper;

    @Mock
    private HourLedgerMapper hourLedgerMapper;

    @Mock
    private PackageBalanceSnapshotMapper packageBalanceSnapshotMapper;

    private CoursePackageServiceImpl coursePackageService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        Configuration configuration = new Configuration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), LessonRecordEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), LessonOccurrenceEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RecurringScheduleRuleEntity.class);
    }

    @BeforeEach
    void setUp() {
        coursePackageService = new CoursePackageServiceImpl(
                coursePackageMapper,
                enrollmentOrderItemMapper,
                packageTeacherAssignmentMapper,
                studentMapper,
                teacherMapper,
                courseProductMapper,
                classGroupMapper,
                classEnrollmentMapper,
                lessonOccurrenceMapper,
                recurringScheduleRuleMapper,
                recurringScheduleSlotMapper,
                lessonRecordMapper,
                hourLedgerMapper,
                packageBalanceSnapshotMapper
        );
    }

    @Test
    void shouldReturnEmptyPageWhenTeacherFilterMatchesNoPrimaryPackage() {
        CoursePackagePageQuery query = new CoursePackagePageQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setTeacherId(301L);
        when(packageTeacherAssignmentMapper.selectList(any())).thenReturn(Collections.emptyList());

        PageResponse<CoursePackageListItemVO> result = coursePackageService.pagePackages(query);

        assertThat(result.records()).isEmpty();
        assertThat(result.total()).isZero();
        verify(coursePackageMapper, never()).selectPage(any(), any());
    }

    @Test
    void shouldAddBonusHoursWithoutChangingConsumedHours() {
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(101L);
        coursePackage.setTeachingMode("ONE_ON_ONE");
        coursePackage.setTotalUnits(120);
        coursePackage.setConsumedUnits(20);
        coursePackage.setRemainingUnits(100);
        coursePackage.setPackageStatus("ACTIVE");
        coursePackage.setVersion(0);
        when(coursePackageMapper.selectById(101L)).thenReturn(coursePackage);
        when(coursePackageMapper.updateById(any(CoursePackageEntity.class))).thenReturn(1);
        when(packageBalanceSnapshotMapper.selectById(101L)).thenReturn(null);

        CoursePackageHoursAdjustmentRequest request = new CoursePackageHoursAdjustmentRequest();
        request.setChangeHours(BigDecimal.valueOf(2));
        request.setAdjustmentType("BONUS_HOURS");
        request.setReason("Complimentary hours");

        coursePackageService.adjustPackageHours(101L, request, 9L);

        assertThat(coursePackage.getTotalUnits()).isEqualTo(124);
        assertThat(coursePackage.getConsumedUnits()).isEqualTo(20);
        assertThat(coursePackage.getRemainingUnits()).isEqualTo(104);
        ArgumentCaptor<com.classdeduction.system.modules.lesson.entity.HourLedgerEntity> ledgerCaptor =
                ArgumentCaptor.forClass(com.classdeduction.system.modules.lesson.entity.HourLedgerEntity.class);
        verify(hourLedgerMapper, times(1)).insert(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().getLedgerType()).isEqualTo("MANUAL_ADJUST");
        assertThat(ledgerCaptor.getValue().getReasonCode()).isEqualTo("BONUS_HOURS");
        assertThat(ledgerCaptor.getValue().getChangeUnits()).isEqualTo(4);
        assertThat(ledgerCaptor.getValue().getBalanceAfterUnits()).isEqualTo(104);
    }

    @Test
    void shouldRejectNegativeBonusHours() {
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(101L);
        coursePackage.setTeachingMode("ONE_ON_ONE");
        coursePackage.setTotalUnits(120);
        coursePackage.setConsumedUnits(20);
        coursePackage.setRemainingUnits(100);
        when(coursePackageMapper.selectById(101L)).thenReturn(coursePackage);

        CoursePackageHoursAdjustmentRequest request = new CoursePackageHoursAdjustmentRequest();
        request.setChangeHours(BigDecimal.valueOf(-2));
        request.setAdjustmentType("BONUS_HOURS");

        assertThatThrownBy(() -> coursePackageService.adjustPackageHours(101L, request, 9L))
                .hasMessageContaining("Bonus hours must increase");
        verify(coursePackageMapper, never()).updateById(any(CoursePackageEntity.class));
    }

    @Test
    void shouldCountOnlyFinallyConfirmedClassDaysInGroupProgress() {
        CoursePackagePageQuery query = new CoursePackagePageQuery();
        query.setPageNum(1);
        query.setPageSize(10);

        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(501L);
        classGroup.setProductId(201L);
        classGroup.setClassName("Saturday Math");
        classGroup.setBillingType("PER_SESSION");
        classGroup.setTotalSessions(8);
        classGroup.setStatus("ACTIVE");
        Page<ClassGroupEntity> page = new Page<>(1, 10);
        page.setRecords(List.of(classGroup));
        page.setTotal(1);

        CourseProductEntity product = new CourseProductEntity();
        product.setId(201L);
        product.setProductName("Saturday Math");
        product.setTeachingMode("GROUP");
        product.setBillingType("PER_SESSION");
        product.setTotalSessions(8);

        LessonOccurrenceEntity morning = new LessonOccurrenceEntity();
        morning.setId(601L);
        morning.setClassGroupId(501L);
        morning.setPlanningStatus("SCHEDULED");
        morning.setPlannedStartAt(LocalDateTime.of(2026, 9, 5, 9, 0));
        LessonOccurrenceEntity afternoon = new LessonOccurrenceEntity();
        afternoon.setId(602L);
        afternoon.setClassGroupId(501L);
        afternoon.setPlanningStatus("SCHEDULED");
        afternoon.setPlannedStartAt(LocalDateTime.of(2026, 9, 5, 13, 0));
        LessonRecordEntity morningRecord = new LessonRecordEntity();
        morningRecord.setOccurrenceId(601L);
        morningRecord.setConfirmStage("CONFIRMED");
        morningRecord.setIsDeleted(0);
        LessonRecordEntity afternoonRecord = new LessonRecordEntity();
        afternoonRecord.setOccurrenceId(602L);
        afternoonRecord.setConfirmStage("CONFIRMED");
        afternoonRecord.setIsDeleted(0);

        when(classGroupMapper.selectPage(any(), any())).thenReturn(page);
        when(courseProductMapper.selectBatchIds(any())).thenReturn(List.of(product));
        when(classEnrollmentMapper.countActiveByClassIds(anyList())).thenReturn(Collections.emptyList());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(morning, afternoon));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(morningRecord, afternoonRecord));

        PageResponse<GroupClassPackageProgressVO> result = coursePackageService.pageGroupClassPackages(query);

        assertThat(result.records()).singleElement().satisfies(progress -> {
            assertThat(progress.consumedUnits()).isEqualTo(1);
            assertThat(progress.remainingUnits()).isEqualTo(7);
        });
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<LambdaQueryWrapper<LessonRecordEntity>> queryCaptor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(lessonRecordMapper).selectList(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getSqlSegment()).contains("confirmStage", "is_deleted");
        assertThat(queryCaptor.getValue().getParamNameValuePairs()).containsValue("CONFIRMED");
    }

    @Test
    void shouldPauseOnlyUnrecordedFutureOneOnOneLessons() {
        CoursePackageEntity coursePackage = buildOneOnOnePackage("ACTIVE", 20);
        LessonOccurrenceEntity unrecorded = buildFutureOccurrence(501L, 601L, 4, "SCHEDULED");
        LessonOccurrenceEntity recorded = buildFutureOccurrence(502L, 601L, 4, "SCHEDULED");
        LessonRecordEntity activeRecord = new LessonRecordEntity();
        activeRecord.setOccurrenceId(502L);
        activeRecord.setConfirmStage("PENDING_ADMIN");
        activeRecord.setIsDeleted(0);

        when(coursePackageMapper.selectById(101L)).thenReturn(coursePackage);
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(buildRule("ACTIVE")));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(unrecorded, recorded));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(activeRecord));

        coursePackageService.pausePackage(101L);

        assertThat(coursePackage.getPackageStatus()).isEqualTo("PAUSED");
        verify(coursePackageMapper).updateById(coursePackage);
        verify(recurringScheduleRuleMapper).update(any(), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<LessonOccurrenceEntity>> updateCaptor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(lessonOccurrenceMapper).update(any(), updateCaptor.capture());
        assertThat(updateCaptor.getValue().getSqlSegment()).contains("id IN");
        assertThat(updateCaptor.getValue().getParamNameValuePairs()).containsValue("CANCELLED");
    }

    @Test
    void shouldResumeUsingRemainingPerSlotAllocationAndRestartDate() {
        CoursePackageEntity coursePackage = buildOneOnOnePackage("PAUSED", 8);
        RecurringScheduleRuleEntity rule = buildRule("PAUSED");
        RecurringScheduleSlotEntity slot = buildSlot(601L, 1, 4, 8);
        LocalDate restartDate = nextWeekday(DayOfWeek.MONDAY);
        LessonOccurrenceEntity preservedRecorded = buildFutureOccurrence(700L, 601L, 4, "SCHEDULED");
        preservedRecorded.setPlannedStartAt(LocalDateTime.of(restartDate.plusWeeks(1), LocalTime.of(18, 0)));
        preservedRecorded.setPlannedEndAt(preservedRecorded.getPlannedStartAt().plusHours(2));
        LessonRecordEntity activeRecord = new LessonRecordEntity();
        activeRecord.setOccurrenceId(700L);
        activeRecord.setConfirmStage("CONFIRMED");
        activeRecord.setIsDeleted(0);

        when(coursePackageMapper.selectById(101L)).thenReturn(coursePackage);
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(rule));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(slot));
        when(lessonOccurrenceMapper.selectList(any()))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(preservedRecorded))
                .thenReturn(Collections.emptyList())
                .thenReturn(Collections.emptyList())
                .thenReturn(Collections.emptyList());

        CoursePackageResumeRequest request = new CoursePackageResumeRequest();
        request.setStartDate(restartDate);
        coursePackageService.resumePackage(101L, request);

        assertThat(coursePackage.getPackageStatus()).isEqualTo("ACTIVE");
        assertThat(coursePackage.getStartDate()).isEqualTo(restartDate);
        assertThat(rule.getRuleStatus()).isEqualTo("ACTIVE");
        assertThat(rule.getStartDate()).isEqualTo(restartDate);
        verify(lessonOccurrenceMapper, times(1)).insert(any(LessonOccurrenceEntity.class));
        ArgumentCaptor<LessonOccurrenceEntity> occurrenceCaptor = ArgumentCaptor.forClass(LessonOccurrenceEntity.class);
        verify(lessonOccurrenceMapper).insert(occurrenceCaptor.capture());
        LessonOccurrenceEntity generated = occurrenceCaptor.getValue();
        assertThat(generated.getScheduleSlotId()).isEqualTo(601L);
        assertThat(generated.getPlannedUnits()).isEqualTo(4);
        assertThat(generated.getPlannedStartAt().toLocalDate()).isEqualTo(restartDate);
        assertThat(generated.getPlannedEndAt()).isEqualTo(generated.getPlannedStartAt().plusHours(2));
    }

    @Test
    void shouldRejectResumeWhenSlotAllocationIsIncomplete() {
        CoursePackageEntity coursePackage = buildOneOnOnePackage("PAUSED", 8);
        RecurringScheduleSlotEntity incompleteSlot = buildSlot(601L, 1, 4, null);
        when(coursePackageMapper.selectById(101L)).thenReturn(coursePackage);
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(buildRule("PAUSED")));
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(incompleteSlot));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(Collections.emptyList());

        CoursePackageResumeRequest request = new CoursePackageResumeRequest();
        request.setStartDate(nextWeekday(DayOfWeek.MONDAY));

        assertThatThrownBy(() -> coursePackageService.resumePackage(101L, request))
                .hasMessageContaining("Each schedule slot requires");
        verify(lessonOccurrenceMapper, never()).insert(any(LessonOccurrenceEntity.class));
    }

    @Test
    void shouldRejectResumeWhenNoUnitsRemain() {
        CoursePackageEntity coursePackage = buildOneOnOnePackage("PAUSED", 0);
        when(coursePackageMapper.selectById(101L)).thenReturn(coursePackage);

        CoursePackageResumeRequest request = new CoursePackageResumeRequest();
        request.setStartDate(nextWeekday(DayOfWeek.MONDAY));

        assertThatThrownBy(() -> coursePackageService.resumePackage(101L, request))
                .hasMessageContaining("no remaining hours");
        verify(recurringScheduleRuleMapper, never()).selectList(any());
    }

    @Test
    void shouldRejectResumeWhenNoScheduleExists() {
        CoursePackageEntity coursePackage = buildOneOnOnePackage("PAUSED", 8);
        when(coursePackageMapper.selectById(101L)).thenReturn(coursePackage);
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(Collections.emptyList());

        CoursePackageResumeRequest request = new CoursePackageResumeRequest();
        request.setStartDate(nextWeekday(DayOfWeek.MONDAY));

        assertThatThrownBy(() -> coursePackageService.resumePackage(101L, request))
                .hasMessageContaining("No recurring schedule");
        verify(recurringScheduleSlotMapper, never()).selectList(any());
    }

    private CoursePackageEntity buildOneOnOnePackage(String status, int remainingUnits) {
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(101L);
        coursePackage.setStudentId(201L);
        coursePackage.setTeachingMode("ONE_ON_ONE");
        coursePackage.setPackageStatus(status);
        coursePackage.setTotalUnits(20);
        coursePackage.setRemainingUnits(remainingUnits);
        return coursePackage;
    }

    private RecurringScheduleRuleEntity buildRule(String status) {
        RecurringScheduleRuleEntity rule = new RecurringScheduleRuleEntity();
        rule.setId(401L);
        rule.setPackageId(101L);
        rule.setRuleStatus(status);
        rule.setStartDate(nextWeekday(DayOfWeek.MONDAY));
        rule.setGenerationHorizonDays(30);
        return rule;
    }

    private RecurringScheduleSlotEntity buildSlot(Long id, int weekday, int plannedUnits, Integer allocatedUnits) {
        RecurringScheduleSlotEntity slot = new RecurringScheduleSlotEntity();
        slot.setId(id);
        slot.setRuleId(401L);
        slot.setTeacherId(301L);
        slot.setSubjectName("Math");
        slot.setWeekday(weekday);
        slot.setStartTime(LocalTime.of(18, 0));
        slot.setPlannedUnits(plannedUnits);
        slot.setAllocatedUnits(allocatedUnits);
        slot.setSlotStatus("ACTIVE");
        return slot;
    }

    private LessonOccurrenceEntity buildFutureOccurrence(Long id, Long slotId, int plannedUnits, String status) {
        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setId(id);
        occurrence.setPackageId(101L);
        occurrence.setScheduleSlotId(slotId);
        occurrence.setPlanningStatus(status);
        occurrence.setPlannedUnits(plannedUnits);
        LocalDateTime start = LocalDateTime.of(nextWeekday(DayOfWeek.MONDAY), LocalTime.of(18, 0));
        occurrence.setPlannedStartAt(start);
        occurrence.setPlannedEndAt(start.plusMinutes(plannedUnits * 30L));
        return occurrence;
    }

    private LocalDate nextWeekday(DayOfWeek dayOfWeek) {
        return LocalDate.now(com.classdeduction.system.common.time.AppTime.APP_ZONE_ID)
                .with(TemporalAdjusters.next(dayOfWeek));
    }
}
