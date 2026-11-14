package com.classdeduction.system.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMemberMapper;
import com.classdeduction.system.modules.finance.dto.TeacherPaymentCreateRequest;
import com.classdeduction.system.modules.finance.dto.UnpaidLessonQuery;
import com.classdeduction.system.modules.finance.entity.TeacherPayRateEntity;
import com.classdeduction.system.modules.finance.entity.TeacherPaymentEntity;
import com.classdeduction.system.modules.finance.entity.TeacherPaymentItemEntity;
import com.classdeduction.system.modules.finance.mapper.TeacherPayRateMapper;
import com.classdeduction.system.modules.finance.mapper.TeacherPaymentItemMapper;
import com.classdeduction.system.modules.finance.mapper.TeacherPaymentMapper;
import com.classdeduction.system.modules.finance.service.impl.TeacherPaymentServiceImpl;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 老师工资结算测试：确保未结算课时只读取管理员终确认后的记录。
 */
@ExtendWith(MockitoExtension.class)
class TeacherPaymentServiceImplTest {

    @Mock
    private LessonRecordMapper lessonRecordMapper;

    @Mock
    private CoursePackageMapper coursePackageMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private TeacherMapper teacherMapper;

    @Mock
    private CourseProductMapper courseProductMapper;

    @Mock
    private ClassroomMapper classroomMapper;

    @Mock
    private EnrollmentOrderMapper enrollmentOrderMapper;

    @Mock
    private EnrollmentOrderItemMapper enrollmentOrderItemMapper;

    @Mock
    private TeacherPayRateMapper payRateMapper;

    @Mock
    private TeacherPaymentMapper paymentMapper;

    @Mock
    private TeacherPaymentItemMapper paymentItemMapper;

    @Mock
    private LessonOccurrenceMapper lessonOccurrenceMapper;

    @Mock
    private ClassGroupMapper classGroupMapper;

    @Mock
    private PrivateLessonGroupMemberMapper privateLessonGroupMemberMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<LessonRecordEntity>> recordQueryCaptor;

    @Captor
    private ArgumentCaptor<TeacherPaymentItemEntity> paymentItemCaptor;

    private TeacherPaymentServiceImpl teacherPaymentService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        // 单元测试不启动 MyBatis 上下文，手动初始化 LambdaQueryWrapper 字段缓存。
        Configuration configuration = new Configuration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), TeacherPaymentItemEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), TeacherPayRateEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), LessonRecordEntity.class);
    }

    @BeforeEach
    void setUp() {
        teacherPaymentService = new TeacherPaymentServiceImpl(
                lessonRecordMapper,
                coursePackageMapper,
                studentMapper,
                teacherMapper,
                courseProductMapper,
                classroomMapper,
                enrollmentOrderMapper,
                enrollmentOrderItemMapper,
                payRateMapper,
                paymentMapper,
                paymentItemMapper,
                lessonOccurrenceMapper,
                classGroupMapper,
                privateLessonGroupMemberMapper
        );
    }

    @Test
    void shouldExposeTeacherPayrollAsHistoryOnlyAfterItIsMarkedPaid() {
        TeacherPaymentEntity payment = new TeacherPaymentEntity();
        payment.setId(901L);
        payment.setStatus("PENDING");
        when(paymentMapper.selectById(901L)).thenReturn(payment);
        when(paymentMapper.updateById(any(TeacherPaymentEntity.class))).thenReturn(1);

        teacherPaymentService.confirmPayment(901L, 88L);

        ArgumentCaptor<TeacherPaymentEntity> paymentCaptor = ArgumentCaptor.forClass(TeacherPaymentEntity.class);
        verify(paymentMapper).updateById(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo("PAID");
        assertThat(paymentCaptor.getValue().getPaidBy()).isEqualTo(88L);
        assertThat(paymentCaptor.getValue().getPaidAt()).isNotNull();
    }

    @Test
    void shouldQueryOnlyConfirmedRecordsForUnpaidLessons() {
        UnpaidLessonQuery query = new UnpaidLessonQuery();
        query.setTeacherId(701L);
        when(paymentItemMapper.selectList(any())).thenReturn(Collections.<TeacherPaymentItemEntity>emptyList());
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of());

        assertThat(teacherPaymentService.queryUnpaidLessons(query)).isEmpty();

        verify(lessonRecordMapper).selectList(recordQueryCaptor.capture());
        LambdaQueryWrapper<LessonRecordEntity> wrapper = recordQueryCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("confirmStage");
        assertThat(wrapper.getParamNameValuePairs()).containsValue("CONFIRMED");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains("COMPLETED", "MAKEUP_COMPLETED", "SUBSTITUTED")
                .doesNotContain("FORMAL_LEAVE", "NO_SHOW");
    }

    @Test
    void unpaidPeriodFiltersLessonTimeWithInclusiveEndDate() {
        UnpaidLessonQuery query = new UnpaidLessonQuery();
        query.setTeacherId(701L);
        query.setStartDate(LocalDate.of(2026, 10, 30));
        query.setEndDate(LocalDate.of(2026, 11, 12));
        when(paymentItemMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(lessonRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        teacherPaymentService.queryUnpaidLessons(query);

        verify(lessonRecordMapper).selectList(recordQueryCaptor.capture());
        var wrapper = recordQueryCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("actual_start_at", "planned_start_at", "lesson_occurrence")
                .doesNotContain("confirmed_at >=", "confirmed_at <");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(LocalDateTime.of(2026, 10, 30, 0, 0), LocalDateTime.of(2026, 11, 13, 0, 0));
    }

    @Test
    void unpaidLessonsShowClassTimeInsteadOfConfirmationTime() {
        LessonRecordEntity actualLesson = payableRecord();
        actualLesson.setActualUnits(4);
        actualLesson.setPlannedStartAt(LocalDateTime.of(2026, 9, 23, 17, 0));
        actualLesson.setPlannedEndAt(LocalDateTime.of(2026, 9, 23, 19, 0));
        actualLesson.setActualStartAt(LocalDateTime.of(2026, 9, 23, 18, 0));
        actualLesson.setActualEndAt(LocalDateTime.of(2026, 9, 23, 20, 0));

        LessonRecordEntity plannedLesson = payableRecord();
        plannedLesson.setId(1002L);
        plannedLesson.setActualUnits(4);
        plannedLesson.setPlannedStartAt(LocalDateTime.of(2026, 9, 24, 14, 0));
        plannedLesson.setPlannedEndAt(LocalDateTime.of(2026, 9, 24, 16, 0));

        LessonRecordEntity legacyLesson = payableRecord();
        legacyLesson.setId(1003L);
        legacyLesson.setOccurrenceId(4001L);
        legacyLesson.setActualUnits(4);
        legacyLesson.setPlannedStartAt(null);
        legacyLesson.setPlannedEndAt(null);
        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setId(4001L);
        occurrence.setPlannedStartAt(LocalDateTime.of(2026, 9, 25, 9, 0));
        occurrence.setPlannedEndAt(LocalDateTime.of(2026, 9, 25, 11, 0));

        UnpaidLessonQuery query = new UnpaidLessonQuery();
        query.setTeacherId(701L);
        when(paymentItemMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(actualLesson, plannedLesson, legacyLesson));
        when(lessonOccurrenceMapper.selectBatchIds(any())).thenReturn(List.of(occurrence));
        when(payRateMapper.selectList(any())).thenReturn(Collections.emptyList());

        var lessons = teacherPaymentService.queryUnpaidLessons(query);

        assertThat(lessons).hasSize(3);
        assertThat(lessons.get(0).lessonStartAt()).isEqualTo(LocalDateTime.of(2026, 9, 23, 18, 0));
        assertThat(lessons.get(0).lessonEndAt()).isEqualTo(LocalDateTime.of(2026, 9, 23, 20, 0));
        assertThat(lessons.get(1).lessonStartAt()).isEqualTo(plannedLesson.getPlannedStartAt());
        assertThat(lessons.get(1).lessonEndAt()).isEqualTo(plannedLesson.getPlannedEndAt());
        assertThat(lessons.get(2).lessonStartAt()).isEqualTo(occurrence.getPlannedStartAt());
        assertThat(lessons.get(2).lessonEndAt()).isEqualTo(occurrence.getPlannedEndAt());
    }

    @Test
    void paymentPeriodUsesLessonDateInsteadOfConfirmationDate() {
        LessonRecordEntity record = payableRecord();
        TeacherPaymentCreateRequest request = paymentRequest(record);
        record.setConfirmedAt(LocalDateTime.of(2026, 8, 10, 12, 0));
        ReflectionTestUtils.invokeMethod(teacherPaymentService, "validatePaymentPeriod", request, List.of(record), Map.of());

        record.setActualStartAt(LocalDateTime.of(2026, 8, 1, 10, 0));
        record.setActualEndAt(LocalDateTime.of(2026, 8, 1, 12, 0));
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(teacherPaymentService,
                "validatePaymentPeriod", request, List.of(record), Map.of()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("LESSON_OUTSIDE_PAYMENT_PERIOD");
    }

    @Test
    void paymentPeriodUsesLegacyOccurrenceWhenRecordHasNoLessonTime() {
        LessonRecordEntity record = payableRecord();
        record.setPlannedStartAt(null);
        record.setPlannedEndAt(null);
        record.setOccurrenceId(4001L);
        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setPlannedStartAt(LocalDateTime.of(2026, 7, 31, 23, 0));
        ReflectionTestUtils.invokeMethod(teacherPaymentService, "validatePaymentPeriod",
                paymentRequest(record), List.of(record), Map.of(4001L, occurrence));
    }

    @Test
    void paymentPeriodIncludesBothDatesAndRejectsMissingOrOutsideLessonTime() {
        LessonRecordEntity record = payableRecord();
        TeacherPaymentCreateRequest request = paymentRequest(record);
        record.setPlannedStartAt(LocalDateTime.of(2026, 7, 1, 0, 0));
        ReflectionTestUtils.invokeMethod(teacherPaymentService, "validatePaymentPeriod", request, List.of(record), Map.of());
        record.setPlannedStartAt(LocalDateTime.of(2026, 7, 31, 23, 59));
        ReflectionTestUtils.invokeMethod(teacherPaymentService, "validatePaymentPeriod", request, List.of(record), Map.of());
        record.setPlannedStartAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(teacherPaymentService, "validatePaymentPeriod",
                request, List.of(record), Map.of())).isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("LESSON_OUTSIDE_PAYMENT_PERIOD");
        record.setPlannedStartAt(null);
        record.setOccurrenceId(4001L);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(teacherPaymentService, "validatePaymentPeriod",
                request, List.of(record), Map.of())).isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("LESSON_OUTSIDE_PAYMENT_PERIOD");
    }

    @Test
    void shouldRejectNoShowLessonWhenCreatingPayment() {
        LessonRecordEntity record = payableRecord();
        record.setResultStatus("NO_SHOW");

        assertPaymentCreationRejected(record, "LESSON_RECORD_NOT_PAYABLE");
    }

    @Test
    void shouldRejectFormalLeaveLessonWhenCreatingPayment() {
        LessonRecordEntity record = payableRecord();
        record.setResultStatus("FORMAL_LEAVE");

        assertPaymentCreationRejected(record, "LESSON_RECORD_NOT_PAYABLE");
    }

    @Test
    void shouldRejectRejectedLessonWhenCreatingPayment() {
        LessonRecordEntity record = payableRecord();
        record.setConfirmStage("REJECTED");

        assertPaymentCreationRejected(record, "LESSON_RECORD_NOT_CONFIRMED");
    }

    @Test
    void shouldRejectAnotherTeachersLessonWhenCreatingPayment() {
        LessonRecordEntity record = payableRecord();
        record.setActualTeacherId(702L);

        assertPaymentCreationRejected(record, "LESSON_RECORD_TEACHER_MISMATCH");
    }

    @Test
    void shouldRejectPaymentWhenTeacherPayRateIsMissing() {
        LessonRecordEntity record = payableRecord();
        record.setPackageId(3001L);
        record.setOccurrenceId(4001L);
        record.setActualUnits(4);
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(701L);
        teacher.setTeacherNo("T701");
        teacher.setStatus("ACTIVE");
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(3001L);
        coursePackage.setCourseProductId(202L);
        TeacherPaymentCreateRequest request = new TeacherPaymentCreateRequest();
        request.setTeacherId(701L);
        request.setLessonRecordIds(List.of(record.getId()));
        request.setPeriodStart(LocalDate.of(2026, 7, 1));
        request.setPeriodEnd(LocalDate.of(2026, 7, 31));

        when(teacherMapper.selectById(701L)).thenReturn(teacher);
        when(lessonRecordMapper.selectByIdsForUpdate(request.getLessonRecordIds())).thenReturn(List.of(record));
        when(paymentItemMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(lessonOccurrenceMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());
        when(coursePackageMapper.selectBatchIds(any())).thenReturn(List.of(coursePackage));
        when(payRateMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> teacherPaymentService.createPayment(request, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("TEACHER_PAY_RATE_REQUIRED");
        verify(paymentMapper, never()).insert(any(TeacherPaymentEntity.class));
    }

    @Test
    void shouldUseStudentSpecificRateBeforeDefaultProductRate() {
        LessonRecordEntity record = payableRecord();
        record.setPackageId(3001L);
        record.setOccurrenceId(4001L);
        record.setStudentId(801L);
        record.setActualUnits(4);

        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(701L);
        teacher.setTeacherNo("T701");
        teacher.setStatus("ACTIVE");
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(3001L);
        coursePackage.setCourseProductId(202L);

        TeacherPayRateEntity defaultRate = new TeacherPayRateEntity();
        defaultRate.setTeacherId(701L);
        defaultRate.setCourseProductId(202L);
        defaultRate.setHourlyRate(new BigDecimal("40.00"));
        TeacherPayRateEntity studentOverride = new TeacherPayRateEntity();
        studentOverride.setTeacherId(701L);
        studentOverride.setCourseProductId(202L);
        studentOverride.setStudentId(801L);
        studentOverride.setHourlyRate(new BigDecimal("55.00"));

        TeacherPaymentCreateRequest request = new TeacherPaymentCreateRequest();
        request.setTeacherId(701L);
        request.setLessonRecordIds(List.of(record.getId()));
        request.setPeriodStart(LocalDate.of(2026, 7, 1));
        request.setPeriodEnd(LocalDate.of(2026, 7, 31));

        when(teacherMapper.selectById(701L)).thenReturn(teacher);
        when(lessonRecordMapper.selectByIdsForUpdate(request.getLessonRecordIds())).thenReturn(List.of(record));
        when(paymentItemMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(lessonOccurrenceMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());
        when(coursePackageMapper.selectBatchIds(any())).thenReturn(List.of(coursePackage));
        when(payRateMapper.selectList(any())).thenReturn(List.of(defaultRate, studentOverride));

        teacherPaymentService.createPayment(request, 1L);

        verify(paymentItemMapper).insert(paymentItemCaptor.capture());
        assertThat(paymentItemCaptor.getValue().getHourlyRate()).isEqualByComparingTo(new BigDecimal("55.00"));
        assertThat(paymentItemCaptor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("110.00"));
    }

    @Test
    void shouldUseGradeAndDeliveryScopedRateBeforeDefaultProductRate() {
        LessonRecordEntity record = payableRecord();
        record.setPackageId(3001L);
        record.setOccurrenceId(4001L);
        record.setStudentId(801L);
        record.setActualUnits(4);

        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(701L);
        teacher.setTeacherNo("T701");
        teacher.setStatus("ACTIVE");

        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(3001L);
        coursePackage.setCourseProductId(202L);
        coursePackage.setEnrollmentOrderId(9001L);
        coursePackage.setEnrollmentOrderItemId(9101L);

        EnrollmentOrderItemEntity orderItem = new EnrollmentOrderItemEntity();
        orderItem.setId(9101L);
        orderItem.setGradeBand("High School");

        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(9001L);
        order.setCampusNameSnapshot("Online Class");

        TeacherPayRateEntity defaultRate = new TeacherPayRateEntity();
        defaultRate.setTeacherId(701L);
        defaultRate.setCourseProductId(202L);
        defaultRate.setHourlyRate(new BigDecimal("40.00"));

        TeacherPayRateEntity scopedRate = new TeacherPayRateEntity();
        scopedRate.setTeacherId(701L);
        scopedRate.setCourseProductId(202L);
        scopedRate.setGradeBand("High School");
        scopedRate.setDeliveryMode("ONLINE");
        scopedRate.setHourlyRate(new BigDecimal("70.00"));

        TeacherPaymentCreateRequest request = new TeacherPaymentCreateRequest();
        request.setTeacherId(701L);
        request.setLessonRecordIds(List.of(record.getId()));
        request.setPeriodStart(LocalDate.of(2026, 7, 1));
        request.setPeriodEnd(LocalDate.of(2026, 7, 31));

        when(teacherMapper.selectById(701L)).thenReturn(teacher);
        when(lessonRecordMapper.selectByIdsForUpdate(request.getLessonRecordIds())).thenReturn(List.of(record));
        when(paymentItemMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(lessonOccurrenceMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());
        when(coursePackageMapper.selectBatchIds(any())).thenReturn(List.of(coursePackage));
        when(enrollmentOrderItemMapper.selectBatchIds(any())).thenReturn(List.of(orderItem));
        when(enrollmentOrderMapper.selectBatchIds(any())).thenReturn(List.of(order));
        when(payRateMapper.selectList(any())).thenReturn(List.of(defaultRate, scopedRate));

        teacherPaymentService.createPayment(request, 1L);

        verify(paymentItemMapper).insert(paymentItemCaptor.capture());
        assertThat(paymentItemCaptor.getValue().getHourlyRate()).isEqualByComparingTo(new BigDecimal("70.00"));
        assertThat(paymentItemCaptor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("140.00"));
    }

    @Test
    void shouldUseCourseSpecificRateBeforeTeacherWideDefaultRate() {
        LessonRecordEntity record = payableRecord();
        record.setPackageId(3001L);
        record.setOccurrenceId(4001L);
        record.setActualUnits(4);

        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(701L);
        teacher.setTeacherNo("T701");
        teacher.setStatus("ACTIVE");

        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(3001L);
        coursePackage.setCourseProductId(202L);

        TeacherPayRateEntity teacherWideDefault = new TeacherPayRateEntity();
        teacherWideDefault.setTeacherId(701L);
        teacherWideDefault.setHourlyRate(new BigDecimal("40.00"));

        TeacherPayRateEntity courseException = new TeacherPayRateEntity();
        courseException.setTeacherId(701L);
        courseException.setCourseProductId(202L);
        courseException.setHourlyRate(new BigDecimal("55.00"));

        TeacherPaymentCreateRequest request = new TeacherPaymentCreateRequest();
        request.setTeacherId(701L);
        request.setLessonRecordIds(List.of(record.getId()));
        request.setPeriodStart(LocalDate.of(2026, 7, 1));
        request.setPeriodEnd(LocalDate.of(2026, 7, 31));

        when(teacherMapper.selectById(701L)).thenReturn(teacher);
        when(lessonRecordMapper.selectByIdsForUpdate(request.getLessonRecordIds())).thenReturn(List.of(record));
        when(paymentItemMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(lessonOccurrenceMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());
        when(coursePackageMapper.selectBatchIds(any())).thenReturn(List.of(coursePackage));
        when(payRateMapper.selectList(any())).thenReturn(List.of(teacherWideDefault, courseException));

        teacherPaymentService.createPayment(request, 1L);

        verify(paymentItemMapper).insert(paymentItemCaptor.capture());
        assertThat(paymentItemCaptor.getValue().getHourlyRate()).isEqualByComparingTo(new BigDecimal("55.00"));
    }

    @Test
    void shouldUseTeacherWideK5OnlineRateBeforeTeacherWideStandardRate() {
        LessonRecordEntity record = payableRecord();
        record.setPackageId(3001L);
        record.setOccurrenceId(4001L);
        record.setStudentId(801L);
        record.setActualUnits(4);

        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(3001L);
        coursePackage.setCourseProductId(202L);
        coursePackage.setEnrollmentOrderId(9001L);
        coursePackage.setEnrollmentOrderItemId(9101L);
        EnrollmentOrderItemEntity orderItem = new EnrollmentOrderItemEntity();
        orderItem.setId(9101L);
        orderItem.setGradeBand("G4");
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(9001L);
        order.setCampusNameSnapshot("Online Class");

        TeacherPayRateEntity standardRate = new TeacherPayRateEntity();
        standardRate.setTeacherId(701L);
        standardRate.setHourlyRate(new BigDecimal("40.00"));
        TeacherPayRateEntity k5OnlineRate = new TeacherPayRateEntity();
        k5OnlineRate.setTeacherId(701L);
        k5OnlineRate.setGradeBand("K-5");
        k5OnlineRate.setDeliveryMode("ONLINE");
        k5OnlineRate.setHourlyRate(new BigDecimal("52.00"));

        TeacherPaymentCreateRequest request = paymentRequest(record);
        stubTeacherAndRecords(request, record);
        when(lessonOccurrenceMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());
        when(coursePackageMapper.selectBatchIds(any())).thenReturn(List.of(coursePackage));
        when(enrollmentOrderItemMapper.selectBatchIds(any())).thenReturn(List.of(orderItem));
        when(enrollmentOrderMapper.selectBatchIds(any())).thenReturn(List.of(order));
        when(payRateMapper.selectList(any())).thenReturn(List.of(standardRate, k5OnlineRate));

        teacherPaymentService.createPayment(request, 1L);

        verify(paymentItemMapper).insert(paymentItemCaptor.capture());
        assertThat(paymentItemCaptor.getValue().getHourlyRate()).isEqualByComparingTo(new BigDecimal("52.00"));
        assertThat(paymentItemCaptor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("104.00"));
    }

    @Test
    void shouldIgnoreGroupClassSlotRateSnapshotAndUseConfiguredTeacherRate() {
        LessonRecordEntity record = payableRecord();
        record.setPackageId(null);
        record.setOccurrenceId(4001L);
        record.setActualUnits(4);

        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setId(4001L);
        occurrence.setClassGroupId(5001L);
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(5001L);
        classGroup.setProductId(202L);
        TeacherPayRateEntity configuredRate = new TeacherPayRateEntity();
        configuredRate.setTeacherId(701L);
        configuredRate.setHourlyRate(new BigDecimal("40.00"));

        TeacherPaymentCreateRequest request = paymentRequest(record);
        stubTeacherAndRecords(request, record);
        when(lessonOccurrenceMapper.selectBatchIds(any())).thenReturn(List.of(occurrence));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(classGroupMapper.selectBatchIds(any())).thenReturn(List.of(classGroup));
        when(payRateMapper.selectList(any())).thenReturn(List.of(configuredRate));

        teacherPaymentService.createPayment(request, 1L);

        verify(paymentItemMapper).insert(paymentItemCaptor.capture());
        assertThat(paymentItemCaptor.getValue().getHourlyRate()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(paymentItemCaptor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("80.00"));
        verifyNoInteractions(coursePackageMapper);
    }

    @Test
    void shouldIgnoreOneOnOnePackageRateSnapshotAndUseConfiguredTeacherRate() {
        LessonRecordEntity record = payableRecord();
        record.setPackageId(3001L);
        record.setOccurrenceId(4001L);
        record.setActualUnits(4);

        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(3001L);
        coursePackage.setCourseProductId(202L);
        TeacherPayRateEntity configuredRate = new TeacherPayRateEntity();
        configuredRate.setTeacherId(701L);
        configuredRate.setHourlyRate(new BigDecimal("40.00"));

        TeacherPaymentCreateRequest request = paymentRequest(record);
        stubTeacherAndRecords(request, record);
        when(lessonOccurrenceMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());
        when(coursePackageMapper.selectBatchIds(any())).thenReturn(List.of(coursePackage));
        when(payRateMapper.selectList(any())).thenReturn(List.of(configuredRate));

        teacherPaymentService.createPayment(request, 1L);

        verify(paymentItemMapper).insert(paymentItemCaptor.capture());
        assertThat(paymentItemCaptor.getValue().getHourlyRate()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(paymentItemCaptor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("80.00"));
    }

    @Test
    void shouldRejectMultipleStudentRecordsFromTheSameClassOccurrence() {
        LessonRecordEntity first = payableRecord();
        first.setId(1001L);
        first.setPackageId(null);
        first.setOccurrenceId(4001L);
        LessonRecordEntity second = payableRecord();
        second.setId(1002L);
        second.setPackageId(null);
        second.setOccurrenceId(4001L);

        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(701L);
        teacher.setTeacherNo("T701");
        teacher.setStatus("ACTIVE");
        TeacherPaymentCreateRequest request = new TeacherPaymentCreateRequest();
        request.setTeacherId(701L);
        request.setLessonRecordIds(List.of(first.getId(), second.getId()));
        request.setPeriodStart(LocalDate.of(2026, 7, 1));
        request.setPeriodEnd(LocalDate.of(2026, 7, 31));

        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setId(4001L);
        occurrence.setClassGroupId(5001L);

        when(teacherMapper.selectById(701L)).thenReturn(teacher);
        when(lessonRecordMapper.selectByIdsForUpdate(request.getLessonRecordIds())).thenReturn(List.of(first, second));
        when(paymentItemMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(lessonOccurrenceMapper.selectBatchIds(any())).thenReturn(List.of(occurrence));
        when(classGroupMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> teacherPaymentService.createPayment(request, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("DUPLICATE_CLASS_LESSON_PAYMENT");
        verify(paymentMapper, never()).insert(any(TeacherPaymentEntity.class));
    }

    private void assertPaymentCreationRejected(LessonRecordEntity record, String expectedCode) {
        TeacherPaymentCreateRequest request = new TeacherPaymentCreateRequest();
        request.setTeacherId(701L);
        request.setLessonRecordIds(List.of(record.getId()));
        request.setPeriodStart(LocalDate.of(2026, 7, 1));
        request.setPeriodEnd(LocalDate.of(2026, 7, 31));

        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(701L);
        teacher.setStatus("ACTIVE");
        when(teacherMapper.selectById(701L)).thenReturn(teacher);
        when(lessonRecordMapper.selectByIdsForUpdate(request.getLessonRecordIds())).thenReturn(List.of(record));

        assertThatThrownBy(() -> teacherPaymentService.createPayment(request, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(expectedCode);

        verify(paymentMapper, never()).insert(any(TeacherPaymentEntity.class));
        verifyNoInteractions(paymentItemMapper);
    }

    private LessonRecordEntity payableRecord() {
        LessonRecordEntity record = new LessonRecordEntity();
        record.setId(1001L);
        record.setActualTeacherId(701L);
        record.setConfirmStage("CONFIRMED");
        record.setResultStatus("COMPLETED");
        record.setConfirmedAt(LocalDateTime.of(2026, 7, 15, 12, 0));
        record.setPlannedStartAt(LocalDateTime.of(2026, 7, 15, 10, 0));
        record.setPlannedEndAt(LocalDateTime.of(2026, 7, 15, 12, 0));
        return record;
    }

    private TeacherPaymentCreateRequest paymentRequest(LessonRecordEntity record) {
        TeacherPaymentCreateRequest request = new TeacherPaymentCreateRequest();
        request.setTeacherId(701L);
        request.setLessonRecordIds(List.of(record.getId()));
        request.setPeriodStart(LocalDate.of(2026, 7, 1));
        request.setPeriodEnd(LocalDate.of(2026, 7, 31));
        return request;
    }

    private void stubTeacherAndRecords(TeacherPaymentCreateRequest request, LessonRecordEntity record) {
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(701L);
        teacher.setTeacherNo("T701");
        teacher.setStatus("ACTIVE");
        when(teacherMapper.selectById(701L)).thenReturn(teacher);
        when(lessonRecordMapper.selectByIdsForUpdate(request.getLessonRecordIds())).thenReturn(List.of(record));
        when(paymentItemMapper.selectList(any())).thenReturn(Collections.emptyList());
    }
}
