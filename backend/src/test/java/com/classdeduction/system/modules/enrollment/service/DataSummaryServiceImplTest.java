package com.classdeduction.system.modules.enrollment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.classdeduction.system.modules.base.mapper.CampusMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.dto.DataSummaryQuery;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderParticipantEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentRecordEntity;
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderParticipantMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentRecordMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMapper;
import com.classdeduction.system.modules.enrollment.service.impl.DataSummaryServiceImpl;
import com.classdeduction.system.modules.enrollment.vo.DataSummaryResponseVO;
import com.classdeduction.system.modules.lesson.entity.HourLedgerEntity;
import com.classdeduction.system.modules.lesson.mapper.HourLedgerMapper;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataSummaryServiceImplTest {

    @Mock
    private EnrollmentOrderMapper enrollmentOrderMapper;

    @Mock
    private EnrollmentOrderItemMapper enrollmentOrderItemMapper;

    @Mock
    private EnrollmentOrderParticipantMapper enrollmentOrderParticipantMapper;

    @Mock
    private PrivateLessonGroupMapper privateLessonGroupMapper;

    @Mock
    private CoursePackageMapper coursePackageMapper;

    @Mock
    private PackageTeacherAssignmentMapper packageTeacherAssignmentMapper;

    @Mock
    private PaymentRecordMapper paymentRecordMapper;

    @Mock
    private ClassEnrollmentMapper classEnrollmentMapper;

    @Mock
    private ClassGroupMapper classGroupMapper;

    @Mock
    private CourseProductMapper courseProductMapper;

    @Mock
    private TeacherMapper teacherMapper;

    @Mock
    private HourLedgerMapper hourLedgerMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private CampusMapper campusMapper;

    private DataSummaryServiceImpl dataSummaryService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        // 单元测试不启动 MyBatis 上下文，手动初始化 LambdaQueryWrapper 字段缓存。
        Configuration configuration = new Configuration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), EnrollmentOrderEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), PrivateLessonGroupEntity.class);
    }

    @BeforeEach
    void setUp() {
        dataSummaryService = new DataSummaryServiceImpl(
                enrollmentOrderMapper,
                enrollmentOrderItemMapper,
                enrollmentOrderParticipantMapper,
                privateLessonGroupMapper,
                coursePackageMapper,
                packageTeacherAssignmentMapper,
                paymentRecordMapper,
                classEnrollmentMapper,
                classGroupMapper,
                courseProductMapper,
                teacherMapper,
                hourLedgerMapper,
                studentMapper,
                campusMapper
        );
        lenient().when(enrollmentOrderItemMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(enrollmentOrderParticipantMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(privateLessonGroupMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(coursePackageMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(paymentRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(classEnrollmentMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(studentMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());
    }

    @Test
    void shouldExcludeCancelledAndRefundedOrdersBeforeBuildingSummary() {
        when(enrollmentOrderMapper.selectList(any())).thenReturn(Collections.emptyList());

        dataSummaryService.getSummary(new DataSummaryQuery());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<EnrollmentOrderEntity>> wrapperCaptor =
                ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
        verify(enrollmentOrderMapper).selectList(wrapperCaptor.capture());

        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertThat(sqlSegment).contains("orderStatus <>").contains("paymentStatus <>");
    }

    @Test
    void shouldReturnPaymentReviewCodesWhenPaymentNetDiffersFromOrderSnapshot() {
        EnrollmentOrderEntity order = order(1L, "100.00", "60.00", "40.00");
        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment(1L, "50.00")));

        DataSummaryResponseVO response = dataSummaryService.getSummary(new DataSummaryQuery());

        assertThat(response.rows()).singleElement()
                .extracting(DataSummaryResponseVO.RowVO::reviewIssueCodes)
                .asList()
                .containsExactlyInAnyOrder("PAYMENT_NET_MISMATCH", "BALANCE_MISMATCH");
    }

    @Test
    void shouldReturnUnpaidBalancesAsNegativeValuesInOperationalSummary() {
        EnrollmentOrderEntity order = order(1L, "100.00", "60.00", "40.00");
        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment(1L, "60.00")));

        DataSummaryResponseVO response = dataSummaryService.getSummary(new DataSummaryQuery());

        assertThat(response.totalOutstandingAmount()).isEqualByComparingTo("-40.00");
        assertThat(response.rows()).singleElement()
                .extracting(DataSummaryResponseVO.RowVO::balanceAmount)
                .isEqualTo(new BigDecimal("-40.00"));
        assertThat(response.campusSummaries()).singleElement()
                .extracting(DataSummaryResponseVO.SummaryGroupVO::outstandingAmount)
                .isEqualTo(new BigDecimal("-40.00"));
    }

    @Test
    void shouldFallbackToCurrentStudentProfileWhenOrderSnapshotNameIsMissing() {
        EnrollmentOrderEntity order = order(1L, "100.00", "100.00", "0.00");
        order.setStudentNameSnapshot(null);
        order.setGradeSnapshot(null);
        order.setSchoolSnapshot(null);
        StudentEntity student = new StudentEntity();
        student.setId(1L);
        student.setStudentName("Current Student");
        student.setStudentClass("G6");
        student.setGrade("6");
        student.setSchool("PS 1");

        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment(1L, "100.00")));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(student));

        DataSummaryResponseVO response = dataSummaryService.getSummary(new DataSummaryQuery());

        assertThat(response.rows()).singleElement()
                .satisfies(row -> {
                    assertThat(row.studentName()).isEqualTo("Current Student");
                    assertThat(row.studentClass()).isEqualTo("G6");
                    assertThat(row.grade()).isEqualTo("6");
                    assertThat(row.school()).isEqualTo("PS 1");
                });
    }

    @Test
    void shouldRenderSharedPrivateLessonAsSeparateRowsWithoutDuplicatingOrderTotals() {
        EnrollmentOrderEntity order = order(1L, "200.00", "200.00", "0.00");
        EnrollmentOrderParticipantEntity first = participant(1L, 1, "Student 1");
        EnrollmentOrderParticipantEntity second = participant(2L, 2, "Student 2");

        StudentEntity firstStudent = new StudentEntity();
        firstStudent.setId(1L);
        firstStudent.setStudentName("Student 1");
        StudentEntity secondStudent = new StudentEntity();
        secondStudent.setId(2L);
        secondStudent.setStudentName("Student 2");

        CoursePackageEntity firstPackage = packageForParticipant(11L, 1L, "100.00");
        CoursePackageEntity secondPackage = packageForParticipant(12L, 2L, "100.00");
        firstPackage.setTeachingMode("ONE_ON_ONE");
        secondPackage.setTeachingMode("ONE_ON_ONE");

        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(enrollmentOrderParticipantMapper.selectList(any())).thenReturn(List.of(first, second));
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(firstPackage, secondPackage));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment(1L, "200.00")));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(firstStudent, secondStudent));

        DataSummaryResponseVO response = dataSummaryService.getSummary(new DataSummaryQuery());

        assertThat(response.rows()).hasSize(2);
        assertThat(response.rows()).extracting(DataSummaryResponseVO.RowVO::studentName)
                .containsExactly("Student 1", "Student 2");
        assertThat(response.rows()).extracting(DataSummaryResponseVO.RowVO::linePriceAmount)
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("100.00"));
        assertThat(response.rows()).allSatisfy(row ->
                assertThat(row.privateLessonParticipantCount()).isEqualTo(2));
        assertThat(response.totalRegistrationCount()).isEqualTo(2L);
        assertThat(response.totalIncomeAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void shouldHideRowsWithoutAnyDisplayableStudentName() {
        EnrollmentOrderEntity order = order(1L, "100.00", "100.00", "0.00");
        order.setStudentNameSnapshot(null);
        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment(1L, "100.00")));
        when(studentMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());

        DataSummaryResponseVO response = dataSummaryService.getSummary(new DataSummaryQuery());

        assertThat(response.rows()).isEmpty();
        assertThat(response.totalRegistrationCount()).isZero();
    }

    @Test
    void shouldReturnPackageLedgerMismatchWhenPackageSnapshotsDoNotMatchLedgerChanges() {
        EnrollmentOrderEntity order = order(1L, "100.00", "100.00", "0.00");
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(11L);
        coursePackage.setEnrollmentOrderId(1L);
        coursePackage.setTotalUnits(20);
        coursePackage.setConsumedUnits(2);
        coursePackage.setRemainingUnits(18);
        HourLedgerEntity ledger = new HourLedgerEntity();
        ledger.setPackageId(11L);
        ledger.setChangeUnits(-4);

        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment(1L, "100.00")));
        when(hourLedgerMapper.selectList(any())).thenReturn(List.of(ledger));
        when(packageTeacherAssignmentMapper.selectList(any())).thenReturn(Collections.emptyList());

        DataSummaryResponseVO response = dataSummaryService.getSummary(new DataSummaryQuery());

        assertThat(response.rows()).singleElement()
                .extracting(DataSummaryResponseVO.RowVO::reviewIssueCodes)
                .asList()
                .containsExactly("PACKAGE_LEDGER_MISMATCH");
    }

    @Test
    void shouldDisplayOneOnOneCoursePackagePeriodBeforeOrderPeriod() {
        EnrollmentOrderEntity order = order(1L, "100.00", "100.00", "0.00");
        order.setPeriodStartDate(LocalDate.of(2026, 1, 1));
        order.setPeriodEndDate(LocalDate.of(2026, 1, 31));
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(11L);
        coursePackage.setEnrollmentOrderId(1L);
        coursePackage.setStartDate(LocalDate.of(2026, 9, 1));
        coursePackage.setEstimatedEndDate(LocalDate.of(2026, 12, 31));

        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment(1L, "100.00")));
        when(hourLedgerMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(packageTeacherAssignmentMapper.selectList(any())).thenReturn(Collections.emptyList());

        DataSummaryResponseVO response = dataSummaryService.getSummary(new DataSummaryQuery());

        assertThat(response.rows()).singleElement()
                .satisfies(row -> {
                    assertThat(row.periodStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
                    assertThat(row.periodEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
                });
    }

    @Test
    void shouldDisplayGroupClassEnrollmentPeriodBeforeOrderPeriod() {
        EnrollmentOrderEntity order = order(1L, "100.00", "100.00", "0.00");
        order.setPeriodStartDate(LocalDate.of(2026, 1, 1));
        order.setPeriodEndDate(LocalDate.of(2026, 1, 31));
        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(21L);
        item.setEnrollmentOrderId(1L);
        item.setLineNo(1);
        item.setClassId(31L);
        item.setCourseType("GROUP");
        ClassEnrollmentEntity enrollment = new ClassEnrollmentEntity();
        enrollment.setId(41L);
        enrollment.setEnrollmentOrderItemId(21L);
        enrollment.setClassId(31L);
        enrollment.setEnrollmentDate(LocalDate.of(2026, 9, 10));
        enrollment.setExpiryDate(LocalDate.of(2026, 11, 30));
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(31L);
        classGroup.setClassName("G1-G5");
        classGroup.setStartDate(LocalDate.of(2026, 9, 1));
        classGroup.setEndDate(LocalDate.of(2026, 12, 20));

        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));
        when(classGroupMapper.selectBatchIds(any())).thenReturn(List.of(classGroup));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment(1L, "100.00")));

        DataSummaryResponseVO response = dataSummaryService.getSummary(new DataSummaryQuery());

        assertThat(response.rows()).singleElement()
                .satisfies(row -> {
                    assertThat(row.periodStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
                    assertThat(row.periodEndDate()).isEqualTo(LocalDate.of(2026, 12, 20));
                });
    }

    @Test
    void shouldDisplayLegacyOneOnOnePackagePeriodWhenOrderItemLinkIsMissing() {
        EnrollmentOrderEntity order = order(1L, "100.00", "100.00", "0.00");
        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(21L);
        item.setEnrollmentOrderId(1L);
        item.setLineNo(1);
        item.setCourseProductId(31L);
        item.setCourseType("ONE_ON_ONE");
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(11L);
        coursePackage.setEnrollmentOrderId(1L);
        coursePackage.setCourseProductId(31L);
        coursePackage.setStartDate(LocalDate.of(2026, 9, 1));
        coursePackage.setEstimatedEndDate(LocalDate.of(2026, 12, 31));

        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment(1L, "100.00")));
        when(hourLedgerMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(packageTeacherAssignmentMapper.selectList(any())).thenReturn(Collections.emptyList());

        DataSummaryResponseVO response = dataSummaryService.getSummary(new DataSummaryQuery());

        assertThat(response.rows()).singleElement()
                .satisfies(row -> {
                    assertThat(row.periodStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
                    assertThat(row.periodEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
                });
    }

    @Test
    void shouldDisplayLegacyGroupClassEnrollmentPeriodWhenOrderItemLinkIsMissing() {
        EnrollmentOrderEntity order = order(1L, "100.00", "100.00", "0.00");
        order.setSignupDate(LocalDate.of(2026, 9, 15));
        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(21L);
        item.setEnrollmentOrderId(1L);
        item.setLineNo(1);
        item.setClassId(31L);
        item.setCourseType("GROUP");
        ClassEnrollmentEntity enrollment = new ClassEnrollmentEntity();
        enrollment.setId(41L);
        enrollment.setStudentId(1L);
        enrollment.setClassId(31L);
        enrollment.setEnrollmentDate(LocalDate.of(2026, 9, 10));
        enrollment.setExpiryDate(LocalDate.of(2026, 11, 30));
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(31L);
        classGroup.setClassName("G1-G5");
        classGroup.setStartDate(LocalDate.of(2026, 9, 1));
        classGroup.setEndDate(LocalDate.of(2026, 12, 20));

        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(classEnrollmentMapper.selectList(any())).thenReturn(Collections.emptyList(), List.of(enrollment));
        when(classGroupMapper.selectBatchIds(any())).thenReturn(List.of(classGroup));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment(1L, "100.00")));

        DataSummaryResponseVO response = dataSummaryService.getSummary(new DataSummaryQuery());

        assertThat(response.rows()).singleElement()
                .satisfies(row -> {
                    assertThat(row.periodStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
                    assertThat(row.periodEndDate()).isEqualTo(LocalDate.of(2026, 12, 20));
                });
    }

    @Test
    void shouldOnlyReturnRowsWithReviewIssuesWhenOnlyNeedsReviewIsEnabled() {
        EnrollmentOrderEntity consistentOrder = order(1L, "100.00", "100.00", "0.00");
        EnrollmentOrderEntity inconsistentOrder = order(2L, "100.00", "60.00", "40.00");
        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(consistentOrder, inconsistentOrder));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(
                payment(1L, "100.00"),
                payment(2L, "50.00")
        ));

        DataSummaryQuery query = new DataSummaryQuery();
        query.setOnlyNeedsReview(true);
        DataSummaryResponseVO response = dataSummaryService.getSummary(query);

        assertThat(response.rows()).singleElement()
                .satisfies(row -> {
                    assertThat(row.orderId()).isEqualTo(2L);
                    assertThat(row.reviewIssueCodes()).contains("PAYMENT_NET_MISMATCH", "BALANCE_MISMATCH");
                });
    }

    private EnrollmentOrderEntity order(Long id, String amountDue, String receivedAmount, String balanceAmount) {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(id);
        order.setEnrollmentNo("EO-" + id);
        order.setStudentId(id);
        order.setStudentNameSnapshot("Student " + id);
        order.setSignupDate(LocalDate.of(2026, 7, 21));
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PARTIAL");
        order.setAmountDue(new BigDecimal(amountDue));
        order.setReceivedAmount(new BigDecimal(receivedAmount));
        order.setBalanceAmount(new BigDecimal(balanceAmount));
        return order;
    }

    private PaymentRecordEntity payment(Long orderId, String amount) {
        PaymentRecordEntity payment = new PaymentRecordEntity();
        payment.setEnrollmentOrderId(orderId);
        payment.setAmount(new BigDecimal(amount));
        return payment;
    }

    private EnrollmentOrderParticipantEntity participant(Long studentId, int index, String name) {
        EnrollmentOrderParticipantEntity participant = new EnrollmentOrderParticipantEntity();
        participant.setEnrollmentOrderId(1L);
        participant.setStudentId(studentId);
        participant.setParticipantIndex(index);
        participant.setStudentNameSnapshot(name);
        return participant;
    }

    private CoursePackageEntity packageForParticipant(Long id, Long studentId, String originalAmount) {
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(id);
        coursePackage.setEnrollmentOrderId(1L);
        coursePackage.setStudentId(studentId);
        coursePackage.setOriginalAmount(new BigDecimal(originalAmount));
        coursePackage.setTotalUnits(20);
        coursePackage.setConsumedUnits(0);
        coursePackage.setRemainingUnits(20);
        return coursePackage;
    }
}
