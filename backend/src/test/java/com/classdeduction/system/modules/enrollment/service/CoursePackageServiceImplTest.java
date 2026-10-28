package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.common.model.PageResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.dto.CoursePackagePageQuery;
import com.classdeduction.system.modules.enrollment.dto.CoursePackageHoursAdjustmentRequest;
import com.classdeduction.system.modules.enrollment.dto.CoursePackageDeductedHoursRequest;
import com.classdeduction.system.modules.enrollment.dto.CoursePackageStartingHoursRequest;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupMemberEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMemberMapper;
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
import com.classdeduction.system.modules.schedule.service.RecurringScheduleRuleService;
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
    private PrivateLessonGroupMapper privateLessonGroupMapper;

    @Mock
    private PrivateLessonGroupMemberMapper privateLessonGroupMemberMapper;

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

    @Mock
    private RecurringScheduleRuleService recurringScheduleRuleService;

    private CoursePackageServiceImpl coursePackageService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        Configuration configuration = new Configuration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), LessonRecordEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), LessonOccurrenceEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RecurringScheduleRuleEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RecurringScheduleSlotEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ClassGroupEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), CourseProductEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), CoursePackageEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), PrivateLessonGroupMemberEntity.class);
    }

    @BeforeEach
    void setUp() {
        coursePackageService = new CoursePackageServiceImpl(
                coursePackageMapper,
                enrollmentOrderItemMapper,
                packageTeacherAssignmentMapper,
                privateLessonGroupMapper,
                privateLessonGroupMemberMapper,
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
                packageBalanceSnapshotMapper,
                recurringScheduleRuleService
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
    void shouldFilterActiveAndPausedPackagesForScheduleSelection() {
        CoursePackagePageQuery query = new CoursePackagePageQuery();
        query.setStudentId(201L);
        query.setTeachingMode("ONE_ON_ONE");
        query.setPackageStatuses("ACTIVE,PAUSED");
        when(coursePackageMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>(1, 10));

        coursePackageService.pagePackages(query);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<CoursePackageEntity>> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(coursePackageMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("packageStatus IN");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains("ACTIVE", "PAUSED");
    }

    @Test
    void shouldApplyRenewalDueFilterOnThePagedPackageQuery() {
        CoursePackagePageQuery query = new CoursePackagePageQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setTeachingMode("ONE_ON_ONE");
        query.setRenewalDueWithinDays(15);
        when(coursePackageMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>(1, 10));

        coursePackageService.pagePackages(query);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<CoursePackageEntity>> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(coursePackageMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("packageStatus =", "estimatedEndDate <=");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values()).contains("ACTIVE");
    }

    @Test
    void shouldPrioritizeLowBalancePackagesBeforeApplyingPagination() {
        CoursePackagePageQuery query = new CoursePackagePageQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setTeachingMode("ONE_ON_ONE");
        query.setPrioritizeLowBalance(true);
        when(coursePackageMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>(1, 10));

        coursePackageService.pagePackages(query);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<CoursePackageEntity>> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(coursePackageMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("ORDER BY CASE WHEN COALESCE(remaining_units, 0) <= 20 THEN 0 ELSE 1 END")
                .contains("CASE WHEN COALESCE(remaining_units, 0) <= 20 THEN COALESCE(remaining_units, 0) END ASC");
    }

    @Test
    void shouldIncludeScheduleSummaryInPackageListWithoutPerPackageDetailLookups() {
        CoursePackagePageQuery query = new CoursePackagePageQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setTeachingMode("ONE_ON_ONE");
        query.setIncludeScheduleSummary(true);

        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(101L);
        coursePackage.setStudentId(201L);
        coursePackage.setCourseProductId(301L);
        coursePackage.setTeachingMode("ONE_ON_ONE");
        coursePackage.setPackageStatus("ACTIVE");
        Page<CoursePackageEntity> page = new Page<>(1, 10);
        page.setRecords(List.of(coursePackage));
        page.setTotal(1);
        when(coursePackageMapper.selectPage(any(Page.class), any())).thenReturn(page);

        RecurringScheduleRuleEntity rule = new RecurringScheduleRuleEntity();
        rule.setId(501L);
        rule.setPackageId(101L);
        rule.setRuleStatus("ACTIVE");
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(rule));

        RecurringScheduleSlotEntity slot = new RecurringScheduleSlotEntity();
        slot.setRuleId(501L);
        slot.setTeacherId(601L);
        slot.setSubjectName("Math");
        slot.setAllocatedUnits(20);
        when(recurringScheduleSlotMapper.selectList(any())).thenReturn(List.of(slot));

        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setScheduleRuleId(501L);
        occurrence.setPlannedStartAt(LocalDateTime.of(2099, 1, 1, 12, 0));
        occurrence.setPlanningStatus("SCHEDULED");
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(occurrence));

        PageResponse<CoursePackageListItemVO> result = coursePackageService.pagePackages(query);

        assertThat(result.records()).hasSize(1);
        assertThat(result.records().get(0).scheduleStatus()).isEqualTo("Scheduled");
        assertThat(result.records().get(0).nextLessonStartAt()).isEqualTo(occurrence.getPlannedStartAt());
        verify(recurringScheduleRuleService, never()).getRule(any());
    }

    @Test
    void shouldRequireNewScheduleAfterPreviousRuleEnded() {
        CoursePackagePageQuery query = new CoursePackagePageQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setTeachingMode("ONE_ON_ONE");
        query.setIncludeScheduleSummary(true);

        CoursePackageEntity coursePackage = buildOneOnOnePackage("ACTIVE", 20);
        Page<CoursePackageEntity> page = new Page<>(1, 10);
        page.setRecords(List.of(coursePackage));
        page.setTotal(1);
        when(coursePackageMapper.selectPage(any(Page.class), any())).thenReturn(page);
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of());

        PageResponse<CoursePackageListItemVO> result = coursePackageService.pagePackages(query);

        assertThat(result.records().get(0).scheduleStatus()).isEqualTo("Needs setup");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<RecurringScheduleRuleEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(recurringScheduleRuleMapper).selectList(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getSqlSegment()).contains("ruleStatus");
        assertThat(queryCaptor.getValue().getParamNameValuePairs().values())
                .contains("ACTIVE", "PAUSED");
    }

    @Test
    void shouldIncludePendingDeductionHoursInPackageList() {
        CoursePackagePageQuery query = new CoursePackagePageQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setTeachingMode("ONE_ON_ONE");

        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(101L);
        coursePackage.setStudentId(201L);
        coursePackage.setCourseProductId(301L);
        coursePackage.setEnrollmentOrderItemId(401L);
        coursePackage.setTotalUnits(80);
        coursePackage.setConsumedUnits(20);
        coursePackage.setOpeningUsedUnits(8);
        coursePackage.setRemainingUnits(60);
        Page<CoursePackageEntity> page = new Page<>(1, 10);
        page.setRecords(List.of(coursePackage));
        page.setTotal(1);
        when(coursePackageMapper.selectPage(any(Page.class), any())).thenReturn(page);
        EnrollmentOrderItemEntity orderItem = new EnrollmentOrderItemEntity();
        orderItem.setId(401L);
        orderItem.setGradeBand("G6-G8");
        when(enrollmentOrderItemMapper.selectBatchIds(any())).thenReturn(List.of(orderItem));

        LessonRecordEntity pending = new LessonRecordEntity();
        pending.setPackageId(101L);
        pending.setDeductedUnits(6);
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(pending));

        PageResponse<CoursePackageListItemVO> result = coursePackageService.pagePackages(query);

        assertThat(result.records()).hasSize(1);
        assertThat(result.records().get(0).pendingDeductionUnits()).isEqualTo(3.0);
        assertThat(result.records().get(0).gradeBand()).isEqualTo("G6-G8");
    }

    @Test
    void shouldAddBonusHoursWithoutChangingConsumedHours() {
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(101L);
        coursePackage.setTeachingMode("ONE_ON_ONE");
        coursePackage.setTotalUnits(120);
        coursePackage.setBonusUnits(10);
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
        assertThat(coursePackage.getBonusUnits()).isEqualTo(14);
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
    void shouldRecalculatePackageBalanceAndRefreshScheduleWhenDeductedHoursChange() {
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(101L);
        coursePackage.setTeachingMode("ONE_ON_ONE");
        coursePackage.setTotalUnits(80);
        coursePackage.setOpeningUsedUnits(32);
        coursePackage.setConsumedUnits(40);
        coursePackage.setRemainingUnits(40);
        coursePackage.setPackageStatus("ACTIVE");
        when(coursePackageMapper.selectById(101L)).thenReturn(coursePackage);
        when(coursePackageMapper.updateById(any(CoursePackageEntity.class))).thenReturn(1);
        when(packageBalanceSnapshotMapper.selectById(101L)).thenReturn(null);
        when(hourLedgerMapper.insert(any(com.classdeduction.system.modules.lesson.entity.HourLedgerEntity.class))).thenAnswer(invocation -> {
            ((com.classdeduction.system.modules.lesson.entity.HourLedgerEntity) invocation.getArgument(0)).setId(501L);
            return 1;
        });

        CoursePackageDeductedHoursRequest request = new CoursePackageDeductedHoursRequest();
        request.setDeductedHours(BigDecimal.valueOf(14));

        coursePackageService.updateDeductedHours(101L, request, 9L);

        assertThat(coursePackage.getOpeningUsedUnits()).isEqualTo(20);
        assertThat(coursePackage.getConsumedUnits()).isEqualTo(28);
        assertThat(coursePackage.getRemainingUnits()).isEqualTo(52);
        ArgumentCaptor<com.classdeduction.system.modules.lesson.entity.HourLedgerEntity> ledgerCaptor =
                ArgumentCaptor.forClass(com.classdeduction.system.modules.lesson.entity.HourLedgerEntity.class);
        verify(hourLedgerMapper).insert(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().getChangeUnits()).isEqualTo(12);
        assertThat(ledgerCaptor.getValue().getReasonCode()).isEqualTo("OPENING_USED_HOURS");
        verify(recurringScheduleRuleService).refreshPackageSchedule(101L);
    }

    @Test
    void zeroRemainingHoursCompletesPackageAndRefreshesItsSchedule() {
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(101L);
        coursePackage.setTeachingMode("ONE_ON_ONE");
        coursePackage.setTotalUnits(240);
        coursePackage.setOpeningUsedUnits(201);
        coursePackage.setConsumedUnits(235);
        coursePackage.setRemainingUnits(5);
        coursePackage.setPackageStatus("ACTIVE");
        when(coursePackageMapper.selectById(101L)).thenReturn(coursePackage);
        when(coursePackageMapper.updateById(any(CoursePackageEntity.class))).thenReturn(1);
        when(packageBalanceSnapshotMapper.selectById(101L)).thenReturn(null);
        when(hourLedgerMapper.insert(any(com.classdeduction.system.modules.lesson.entity.HourLedgerEntity.class)))
                .thenAnswer(invocation -> {
                    ((com.classdeduction.system.modules.lesson.entity.HourLedgerEntity) invocation.getArgument(0)).setId(501L);
                    return 1;
                });
        CoursePackageDeductedHoursRequest request = new CoursePackageDeductedHoursRequest();
        request.setDeductedHours(BigDecimal.valueOf(120));

        coursePackageService.updateDeductedHours(101L, request, 9L);

        assertThat(coursePackage.getOpeningUsedUnits()).isEqualTo(206);
        assertThat(coursePackage.getConsumedUnits()).isEqualTo(240);
        assertThat(coursePackage.getRemainingUnits()).isZero();
        assertThat(coursePackage.getPackageStatus()).isEqualTo("COMPLETED");
        ArgumentCaptor<com.classdeduction.system.modules.lesson.entity.HourLedgerEntity> ledgerCaptor =
                ArgumentCaptor.forClass(com.classdeduction.system.modules.lesson.entity.HourLedgerEntity.class);
        verify(hourLedgerMapper).insert(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().getChangeUnits()).isEqualTo(-5);
        assertThat(ledgerCaptor.getValue().getBalanceAfterUnits()).isZero();
        verify(recurringScheduleRuleService).setPackageStartingHours(101L, 34);
        verify(recurringScheduleRuleService).refreshPackageSchedule(101L);
    }

    @Test
    void startingHoursSavesTheRequestedValueUsingLockedCurrentBalance() {
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(101L);
        coursePackage.setVersion(7);
        coursePackage.setTeachingMode("ONE_ON_ONE");
        coursePackage.setTotalUnits(240);
        coursePackage.setOpeningUsedUnits(201);
        coursePackage.setConsumedUnits(235);
        coursePackage.setRemainingUnits(5);
        coursePackage.setPackageStatus("ACTIVE");
        when(coursePackageMapper.selectByIdForUpdate(101L)).thenReturn(coursePackage);
        when(coursePackageMapper.selectById(101L)).thenReturn(coursePackage);
        when(coursePackageMapper.updateById(any(CoursePackageEntity.class))).thenReturn(1);
        when(packageBalanceSnapshotMapper.selectById(101L)).thenReturn(null);
        when(hourLedgerMapper.insert(any(com.classdeduction.system.modules.lesson.entity.HourLedgerEntity.class)))
                .thenAnswer(invocation -> {
                    ((com.classdeduction.system.modules.lesson.entity.HourLedgerEntity) invocation.getArgument(0)).setId(501L);
                    return 1;
                });
        CoursePackageStartingHoursRequest request = new CoursePackageStartingHoursRequest();
        request.setStartingHours(BigDecimal.valueOf(19));
        request.setExpectedVersion(7);

        coursePackageService.updateStartingHours(101L, request, 9L);

        assertThat(coursePackage.getOpeningUsedUnits()).isEqualTo(202);
        assertThat(coursePackage.getConsumedUnits()).isEqualTo(236);
        assertThat(coursePackage.getRemainingUnits()).isEqualTo(4);
        verify(recurringScheduleRuleService).setPackageStartingHours(101L, 38);
        verify(recurringScheduleRuleService).refreshPackageSchedule(101L);
    }

    @Test
    void startingHoursRejectsAPageOpenedBeforeAnotherLessonWasDeducted() {
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(101L);
        coursePackage.setVersion(8);
        coursePackage.setTeachingMode("ONE_ON_ONE");
        coursePackage.setTotalUnits(240);
        coursePackage.setOpeningUsedUnits(201);
        coursePackage.setConsumedUnits(237);
        coursePackage.setRemainingUnits(3);
        when(coursePackageMapper.selectByIdForUpdate(101L)).thenReturn(coursePackage);
        CoursePackageStartingHoursRequest request = new CoursePackageStartingHoursRequest();
        request.setStartingHours(BigDecimal.valueOf(19));
        request.setExpectedVersion(7);

        assertThatThrownBy(() -> coursePackageService.updateStartingHours(101L, request, 9L))
                .isInstanceOfSatisfying(com.classdeduction.system.common.exception.BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo("PACKAGE_CONCURRENT_UPDATE"));
        verify(coursePackageMapper, never()).updateById(any(CoursePackageEntity.class));
        verify(hourLedgerMapper, never()).insert(any(com.classdeduction.system.modules.lesson.entity.HourLedgerEntity.class));
        verify(recurringScheduleRuleService, never()).refreshPackageSchedule(101L);
    }

    @Test
    void shouldRejectDeductedHoursThatLeaveNoRoomForPendingDeductions() {
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(101L);
        coursePackage.setTeachingMode("ONE_ON_ONE");
        coursePackage.setTotalUnits(160);
        coursePackage.setOpeningUsedUnits(40);
        coursePackage.setConsumedUnits(120);
        coursePackage.setRemainingUnits(40);
        coursePackage.setPackageStatus("ACTIVE");
        when(coursePackageMapper.selectById(101L)).thenReturn(coursePackage);

        LessonRecordEntity pending = new LessonRecordEntity();
        pending.setPackageId(101L);
        pending.setDeductedUnits(12);
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(pending));

        CoursePackageDeductedHoursRequest request = new CoursePackageDeductedHoursRequest();
        request.setDeductedHours(BigDecimal.valueOf(78));

        assertThatThrownBy(() -> coursePackageService.updateDeductedHours(101L, request, 9L))
                .hasMessageContaining("pending deduction hours cannot exceed");
        verify(coursePackageMapper, never()).updateById(any(CoursePackageEntity.class));
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
        product.setStatus("ACTIVE");
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
        when(courseProductMapper.selectList(any())).thenReturn(List.of(product));
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
    void shouldHideClassWhenItsProductWasDeletedOrIsNoLongerActive() {
        CoursePackagePageQuery query = new CoursePackagePageQuery();
        query.setPageNum(1);
        query.setPageSize(10);

        when(courseProductMapper.selectList(any())).thenReturn(Collections.emptyList());

        PageResponse<GroupClassPackageProgressVO> result = coursePackageService.pageGroupClassPackages(query);

        assertThat(result.records()).isEmpty();
        assertThat(result.total()).isZero();
        verify(classGroupMapper, never()).selectPage(any(), any());
    }

    @Test
    void shouldReleaseFutureTeacherSlotsEvenWhenLessonHasARecord() {
        CoursePackageEntity coursePackage = buildOneOnOnePackage("ACTIVE", 20);
        LessonOccurrenceEntity unrecorded = buildFutureOccurrence(501L, 601L, 4, "SCHEDULED");
        LessonOccurrenceEntity pending = buildFutureOccurrence(502L, 601L, 4, "SCHEDULED");
        LessonOccurrenceEntity confirmed = buildFutureOccurrence(503L, 601L, 4, "ADJUSTED");

        when(coursePackageMapper.selectById(101L)).thenReturn(coursePackage);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(unrecorded, pending, confirmed));

        coursePackageService.pausePackage(101L);

        assertThat(coursePackage.getPackageStatus()).isEqualTo("PAUSED");
        verify(coursePackageMapper).updateById(coursePackage);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<RecurringScheduleRuleEntity>> ruleCaptor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(recurringScheduleRuleMapper).update(any(), ruleCaptor.capture());
        assertThat(ruleCaptor.getValue().getSqlSegment()).contains("ruleStatus");
        assertThat(ruleCaptor.getValue().getParamNameValuePairs().values())
                .contains(101L, "ACTIVE", "PAUSED", "ENDED");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<LessonOccurrenceEntity>> updateCaptor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(lessonOccurrenceMapper).update(any(), updateCaptor.capture());
        assertThat(updateCaptor.getValue().getSqlSegment()).contains("id IN");
        assertThat(updateCaptor.getValue().getParamNameValuePairs().values())
                .contains(501L, 502L, 503L, "CANCELLED");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<LessonRecordEntity>> recordCaptor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(lessonRecordMapper).update(any(), recordCaptor.capture());
        assertThat(recordCaptor.getValue().getSqlSegment()).contains("confirmStage");
        assertThat(recordCaptor.getValue().getParamNameValuePairs().values())
                .contains("PENDING_ADMIN", "REJECTED")
                .doesNotContain("CONFIRMED");
    }

    @Test
    void shouldResumePackageWithoutRestoringItsPreviousSchedule() {
        CoursePackageEntity coursePackage = buildOneOnOnePackage("PAUSED", 424);
        coursePackage.setEstimatedEndDate(LocalDate.now().plusDays(30));
        when(coursePackageMapper.selectById(101L)).thenReturn(coursePackage);

        coursePackageService.resumePackage(101L);

        assertThat(coursePackage.getPackageStatus()).isEqualTo("ACTIVE");
        assertThat(coursePackage.getEstimatedEndDate()).isNull();
        verify(coursePackageMapper).updateById(coursePackage);
        verify(recurringScheduleRuleMapper, never()).updateById(any(RecurringScheduleRuleEntity.class));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<RecurringScheduleRuleEntity>> ruleCaptor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(recurringScheduleRuleMapper).update(any(), ruleCaptor.capture());
        assertThat(ruleCaptor.getValue().getSqlSegment()).contains("ruleStatus");
        assertThat(ruleCaptor.getValue().getParamNameValuePairs().values())
                .contains(101L, "ACTIVE", "PAUSED", "ENDED");
        verify(lessonOccurrenceMapper, never()).insert(any(LessonOccurrenceEntity.class));
    }

    @Test
    void shouldRejectResumeWhenNoUnitsRemain() {
        CoursePackageEntity coursePackage = buildOneOnOnePackage("PAUSED", 0);
        when(coursePackageMapper.selectById(101L)).thenReturn(coursePackage);

        assertThatThrownBy(() -> coursePackageService.resumePackage(101L))
                .hasMessageContaining("no remaining hours");
        verify(coursePackageMapper, never()).updateById(any(CoursePackageEntity.class));
    }

    @Test
    void shouldPauseEveryMemberOfSharedPrivateLesson() {
        CoursePackageEntity primary = buildOneOnOnePackage("ACTIVE", 20);
        CoursePackageEntity partner = buildOneOnOnePackage("ACTIVE", 20);
        partner.setId(102L);
        when(coursePackageMapper.selectById(101L)).thenReturn(primary);
        when(coursePackageMapper.selectById(102L)).thenReturn(partner);
        when(privateLessonGroupMemberMapper.selectOne(any())).thenReturn(groupMember(101L, 1));
        when(privateLessonGroupMemberMapper.selectList(any())).thenReturn(List.of(groupMember(101L, 1), groupMember(102L, 2)));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of());

        coursePackageService.pausePackage(101L);

        assertThat(primary.getPackageStatus()).isEqualTo("PAUSED");
        assertThat(partner.getPackageStatus()).isEqualTo("PAUSED");
        verify(coursePackageMapper).updateById(primary);
        verify(coursePackageMapper).updateById(partner);
        verify(recurringScheduleRuleMapper, never()).updateById(any(RecurringScheduleRuleEntity.class));
        verify(recurringScheduleRuleMapper, times(2)).update(any(), any());
    }

    @Test
    void shouldResumeEverySharedMemberOnlyWhenAllHaveHours() {
        CoursePackageEntity primary = buildOneOnOnePackage("PAUSED", 20);
        CoursePackageEntity partner = buildOneOnOnePackage("PAUSED", 0);
        partner.setId(102L);
        when(coursePackageMapper.selectById(101L)).thenReturn(primary);
        when(coursePackageMapper.selectById(102L)).thenReturn(partner);
        when(privateLessonGroupMemberMapper.selectOne(any())).thenReturn(groupMember(101L, 1));
        when(privateLessonGroupMemberMapper.selectList(any())).thenReturn(List.of(groupMember(101L, 1), groupMember(102L, 2)));

        assertThatThrownBy(() -> coursePackageService.resumePackage(101L)).hasMessageContaining("no remaining hours");
        verify(coursePackageMapper, never()).updateById(any(CoursePackageEntity.class));

        partner.setRemainingUnits(10);
        coursePackageService.resumePackage(101L);
        assertThat(primary.getPackageStatus()).isEqualTo("ACTIVE");
        assertThat(partner.getPackageStatus()).isEqualTo("ACTIVE");
        verify(coursePackageMapper).updateById(primary);
        verify(coursePackageMapper).updateById(partner);
        verify(recurringScheduleRuleMapper, never()).updateById(any(RecurringScheduleRuleEntity.class));
    }

    private PrivateLessonGroupMemberEntity groupMember(Long packageId, int index) {
        PrivateLessonGroupMemberEntity member = new PrivateLessonGroupMemberEntity();
        member.setPrivateLessonGroupId(301L);
        member.setPackageId(packageId);
        member.setParticipantIndex(index);
        member.setMemberStatus("ACTIVE");
        return member;
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
