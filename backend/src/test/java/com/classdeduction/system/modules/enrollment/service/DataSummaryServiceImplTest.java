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
import com.classdeduction.system.modules.enrollment.entity.PaymentAllocationEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentRecordEntity;
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderParticipantMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentAllocationMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentRecordMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMapper;
import com.classdeduction.system.modules.enrollment.service.impl.DataSummaryServiceImpl;
import com.classdeduction.system.modules.enrollment.service.ClassBillingService;
import com.classdeduction.system.modules.enrollment.vo.DataSummaryResponseVO;
import com.classdeduction.system.modules.lesson.entity.HourLedgerEntity;
import com.classdeduction.system.modules.lesson.mapper.HourLedgerMapper;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import com.classdeduction.system.common.util.JsonHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
    private PaymentAllocationMapper paymentAllocationMapper;

    @Mock
    private ClassGroupMapper classGroupMapper;

    @Mock
    private ClassBillingService classBillingService;

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
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), PaymentAllocationEntity.class);
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
                paymentAllocationMapper,
                classGroupMapper,
                classBillingService,
                courseProductMapper,
                teacherMapper,
                hourLedgerMapper,
                studentMapper,
                campusMapper,
                new JsonHelper(new ObjectMapper())
        );
        lenient().when(enrollmentOrderItemMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(enrollmentOrderParticipantMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(privateLessonGroupMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(coursePackageMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(paymentRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
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
    void shouldSkipSummaryJoinsWhenTransportRosterHasNoMatchingOrders() {
        DataSummaryQuery query = new DataSummaryQuery();
        query.setTransportOnly(true);
        when(enrollmentOrderMapper.selectTransportOrderIds()).thenReturn(Collections.emptyList());

        DataSummaryResponseVO response = dataSummaryService.getSummary(query);

        assertThat(response.rows()).isEmpty();
        verify(enrollmentOrderMapper).selectTransportOrderIds();
        verify(enrollmentOrderMapper, never()).selectList(any());
    }

    @Test
    void shouldLimitTransportRosterSummaryToOrdersWithTransportData() {
        DataSummaryQuery query = new DataSummaryQuery();
        query.setTransportOnly(true);
        when(enrollmentOrderMapper.selectTransportOrderIds()).thenReturn(List.of(42L));
        when(enrollmentOrderMapper.selectList(any())).thenReturn(Collections.emptyList());

        dataSummaryService.getSummary(query);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<EnrollmentOrderEntity>> wrapperCaptor =
                ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
        verify(enrollmentOrderMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("id IN");
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
    void shouldRemoveEveryMoneyAmountFromManagerDataSummaryProjection() {
        EnrollmentOrderEntity order = order(1L, "100.00", "60.00", "40.00");
        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment(1L, "60.00")));

        DataSummaryResponseVO managerView = dataSummaryService.getSummary(new DataSummaryQuery())
                .withoutFinancialAmounts();

        assertThat(managerView.totalIncomeAmount()).isNull();
        assertThat(managerView.totalOutstandingAmount()).isNull();
        assertThat(managerView.campusSummaries()).allSatisfy(summary -> {
            assertThat(summary.incomeAmount()).isNull();
            assertThat(summary.outstandingAmount()).isNull();
        });
        assertThat(managerView.rows()).singleElement().satisfies(row -> {
            assertThat(row.linePriceAmount()).isNull();
            assertThat(row.amountDue()).isNull();
            assertThat(row.paidAmount()).isNull();
            assertThat(row.balanceAmount()).isNull();
            assertThat(row.orderId()).isEqualTo(1L);
        });
        String json = new ObjectMapper().registerModule(new JavaTimeModule()).valueToTree(managerView).toString();
        assertThat(json).doesNotContain("totalIncomeAmount", "linePriceAmount", "amountDue", "paidAmount", "balanceAmount");
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
    void shouldDisplayStudentRegistrationPeriodBeforeGroupClassPeriod() {
        EnrollmentOrderEntity order = order(1L, "100.00", "100.00", "0.00");
        order.setPeriodStartDate(LocalDate.of(2026, 1, 1));
        order.setPeriodEndDate(LocalDate.of(2026, 1, 31));
        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(21L);
        item.setEnrollmentOrderId(1L);
        item.setLineNo(1);
        item.setClassId(31L);
        item.setCourseType("GROUP");
        item.setStartDate(LocalDate.of(2026, 9, 26));
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(31L);
        classGroup.setClassName("G1-G5");
        classGroup.setStartDate(LocalDate.of(2026, 9, 12));
        classGroup.setEndDate(LocalDate.of(2026, 11, 1));

        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(classGroupMapper.selectBatchIds(any())).thenReturn(List.of(classGroup));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment(1L, "100.00")));

        DataSummaryResponseVO response = dataSummaryService.getSummary(new DataSummaryQuery());

        assertThat(response.rows()).singleElement()
                .satisfies(row -> {
                    assertThat(row.periodStartDate()).isEqualTo(LocalDate.of(2026, 9, 26));
                    assertThat(row.periodEndDate()).isEqualTo(LocalDate.of(2026, 11, 1));
                });
    }

    @Test
    void shouldCalculateRegistrationPeriodFromItsOwnPurchasedPackage() {
        LocalDate signupDate = LocalDate.of(2026, 9, 19);
        LocalDate expectedExpiry = LocalDate.of(2027, 5, 1);
        EnrollmentOrderEntity order = order(1L, "2898.00", "2898.00", "0.00");
        order.setSignupDate(signupDate);
        EnrollmentOrderItemEntity item = item(21L, 1, "2898.00");
        item.setClassId(31L);
        item.setCourseType("GROUP");
        item.setStartDate(signupDate);
        item.setPackageUnit("SESSION");
        item.setPackageQuantity(new BigDecimal("33.00"));

        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(item.getClassId());
        classGroup.setClassName("G5");
        classGroup.setStartDate(LocalDate.of(2026, 9, 12));
        classGroup.setEndDate(LocalDate.of(2027, 6, 12));
        when(classBillingService.compute(eq(classGroup), eq(signupDate), eq(signupDate), eq("SESSION"),
                eq(new BigDecimal("33.00"))))
                .thenReturn(new ClassBillingService.ClassBilling(signupDate, expectedExpiry, 33));

        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(classGroupMapper.selectBatchIds(any())).thenReturn(List.of(classGroup));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment(1L, "2898.00")));

        DataSummaryResponseVO response = dataSummaryService.getSummary(new DataSummaryQuery());

        assertThat(response.rows()).singleElement()
                .extracting(DataSummaryResponseVO.RowVO::periodEndDate)
                .isEqualTo(expectedExpiry);
    }

    @Test
    void shouldDisplayStoppedClassPeriodAndConsumedAmounts() {
        EnrollmentOrderEntity order = order(1L, "60.00", "60.00", "0.00");
        EnrollmentOrderItemEntity item = item(21L, 1, "120.00");
        item.setClassId(31L);
        item.setItemStatus("STOPPED");
        item.setPickupFeeAmount(new BigDecimal("5.00"));
        item.setDropoffFeeAmount(new BigDecimal("5.00"));
        item.setDiscountAmount(new BigDecimal("10.00"));
        item.setStoppedConsumedAmount(new BigDecimal("60.00"));
        item.setStoppedPeriodStartDate(LocalDate.of(2026, 9, 12));
        item.setStoppedPeriodEndDate(LocalDate.of(2026, 9, 19));
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(31L);
        classGroup.setClassName("G8 SHSAT Crash");
        classGroup.setStartDate(LocalDate.of(2026, 9, 1));
        classGroup.setEndDate(LocalDate.of(2026, 12, 20));

        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(classGroupMapper.selectBatchIds(any())).thenReturn(List.of(classGroup));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment(1L, "60.00")));

        DataSummaryResponseVO response = dataSummaryService.getSummary(new DataSummaryQuery());

        assertThat(response.rows()).singleElement().satisfies(row -> {
            assertThat(row.itemStatus()).isEqualTo("STOPPED");
            assertThat(row.periodStartDate()).isEqualTo(LocalDate.of(2026, 9, 12));
            assertThat(row.periodEndDate()).isEqualTo(LocalDate.of(2026, 9, 19));
            assertThat(row.linePriceAmount()).isEqualByComparingTo("60.00");
            assertThat(row.pickupFeeAmount()).isEqualByComparingTo("0.00");
            assertThat(row.dropoffFeeAmount()).isEqualByComparingTo("0.00");
            assertThat(row.discountAmount()).isEqualByComparingTo("0.00");
            assertThat(row.amountDue()).isEqualByComparingTo("60.00");
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
    void shouldDisplayEachOneOnOneEnrollmentHoursFromItsOwnOrderItemSnapshot() {
        EnrollmentOrderEntity firstOrder = order(1L, "5999.00", "5999.00", "0.00");
        EnrollmentOrderEntity secondOrder = order(2L, "5999.00", "5999.00", "0.00");
        secondOrder.setSignupDate(LocalDate.of(2026, 8, 24));

        EnrollmentOrderItemEntity firstItem = item(21L, 1, "5999.00");
        firstItem.setEnrollmentOrderId(1L);
        firstItem.setCourseType("ONE_ON_ONE");
        firstItem.setOneOnOneHours(new BigDecimal("120.00"));
        EnrollmentOrderItemEntity secondItem = item(22L, 1, "5999.00");
        secondItem.setEnrollmentOrderId(2L);
        secondItem.setCourseType("ONE_ON_ONE");
        secondItem.setOneOnOneHours(new BigDecimal("120.00"));

        // 合并课包只关联最新报名；较早订单仍应按自己的报名快照显示购买课时。
        CoursePackageEntity mergedPackage = new CoursePackageEntity();
        mergedPackage.setId(31L);
        mergedPackage.setEnrollmentOrderId(2L);
        mergedPackage.setEnrollmentOrderItemId(22L);
        mergedPackage.setTotalUnits(240);
        mergedPackage.setConsumedUnits(50);
        mergedPackage.setRemainingUnits(190);

        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(firstOrder, secondOrder));
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(firstItem, secondItem));
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(mergedPackage));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(
                payment(1L, "5999.00"), payment(2L, "5999.00")));
        when(hourLedgerMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(packageTeacherAssignmentMapper.selectList(any())).thenReturn(Collections.emptyList());

        DataSummaryResponseVO response = dataSummaryService.getSummary(new DataSummaryQuery());

        assertThat(response.rows()).hasSize(2);
        assertThat(response.rows()).extracting(DataSummaryResponseVO.RowVO::totalHours)
                .containsExactly(120.0, 120.0);
    }

    @Test
    void shouldUseCoursePeriodForLegacyItemsWithoutRegistrationDates() {
        EnrollmentOrderEntity order = order(1L, "100.00", "100.00", "0.00");
        order.setSignupDate(LocalDate.of(2026, 9, 15));
        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(21L);
        item.setEnrollmentOrderId(1L);
        item.setLineNo(1);
        item.setClassId(31L);
        item.setCourseType("GROUP");
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(31L);
        classGroup.setClassName("G1-G5");
        classGroup.setStartDate(LocalDate.of(2026, 9, 1));
        classGroup.setEndDate(LocalDate.of(2026, 12, 20));

        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(item));
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

    @Test
    void shouldSplitOrderAmountsAcrossOrderItemsByPriceShare() {
        // 复刻多课程报名场景：课后班 3499 + 周六班 1399，整单折扣 99，实付 4799。
        EnrollmentOrderEntity order = order(1L, "4799.00", "4799.00", "0.00");
        order.setDiscountAmount(new BigDecimal("99.00"));
        EnrollmentOrderItemEntity afterSchool = item(21L, 1, "3499.00");
        EnrollmentOrderItemEntity saturday = item(22L, 2, "1399.00");

        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(afterSchool, saturday));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment(1L, "4799.00")));

        DataSummaryResponseVO response = dataSummaryService.getSummary(new DataSummaryQuery());

        assertThat(response.rows()).hasSize(2);
        assertThat(response.rows()).extracting(DataSummaryResponseVO.RowVO::paidAmount)
                .containsExactly(new BigDecimal("3428.27"), new BigDecimal("1370.73"));
        assertThat(response.rows()).extracting(DataSummaryResponseVO.RowVO::amountDue)
                .containsExactly(new BigDecimal("3428.27"), new BigDecimal("1370.73"));
        assertThat(response.rows()).extracting(DataSummaryResponseVO.RowVO::discountAmount)
                .containsExactly(new BigDecimal("70.72"), new BigDecimal("28.28"));
        assertThat(response.rows()).extracting(DataSummaryResponseVO.RowVO::balanceAmount)
                .containsExactly(new BigDecimal("0.00"), new BigDecimal("0.00"));
    }

    @Test
    void shouldPreferSavedCourseDiscountAndPaymentSnapshots() {
        EnrollmentOrderEntity order = order(1L, "270.00", "220.00", "50.00");
        order.setDiscountAmount(new BigDecimal("30.00"));
        order.setTuitionSubtotalAmount(new BigDecimal("300.00"));
        EnrollmentOrderItemEntity first = item(21L, 1, "200.00");
        first.setDiscountAmount(new BigDecimal("10.00"));
        first.setAmountDue(new BigDecimal("180.00"));
        first.setPaidAmount(new BigDecimal("150.00"));
        first.setBalanceAmount(new BigDecimal("30.00"));
        EnrollmentOrderItemEntity second = item(22L, 2, "100.00");
        second.setDiscountAmount(new BigDecimal("20.00"));
        second.setAmountDue(new BigDecimal("90.00"));
        second.setPaidAmount(new BigDecimal("70.00"));
        second.setBalanceAmount(new BigDecimal("20.00"));

        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(first, second));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment(1L, "220.00")));

        DataSummaryResponseVO response = dataSummaryService.getSummary(new DataSummaryQuery());

        assertThat(response.rows()).extracting(DataSummaryResponseVO.RowVO::discountAmount)
                .containsExactly(new BigDecimal("10.00"), new BigDecimal("20.00"));
        assertThat(response.rows()).extracting(DataSummaryResponseVO.RowVO::amountDue)
                .containsExactly(new BigDecimal("180.00"), new BigDecimal("90.00"));
        assertThat(response.rows()).extracting(DataSummaryResponseVO.RowVO::paidAmount)
                .containsExactly(new BigDecimal("150.00"), new BigDecimal("70.00"));
        assertThat(response.rows()).extracting(DataSummaryResponseVO.RowVO::balanceAmount)
                .containsExactly(new BigDecimal("-30.00"), new BigDecimal("-20.00"));
    }

    @Test
    void shouldKeepCoursePaymentStatusConsistentWithCourseSnapshotsAndReceiptStatusSeparate() {
        EnrollmentOrderEntity order = order(1L, "200.00", "200.00", "0.00");
        order.setPaymentStatus("PAID");
        EnrollmentOrderItemEntity first = item(21L, 1, "100.00");
        first.setAmountDue(new BigDecimal("100.00"));
        first.setPaidAmount(new BigDecimal("50.00"));
        EnrollmentOrderItemEntity second = item(22L, 2, "100.00");
        second.setAmountDue(new BigDecimal("100.00"));
        second.setPaidAmount(new BigDecimal("120.00"));

        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(first, second));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment(1L, "200.00")));

        DataSummaryResponseVO response = dataSummaryService.getSummary(new DataSummaryQuery());

        assertThat(response.rows()).extracting(DataSummaryResponseVO.RowVO::paidAmount)
                .containsExactly(new BigDecimal("50.00"), new BigDecimal("120.00"));
        assertThat(response.rows()).extracting(DataSummaryResponseVO.RowVO::paymentStatus)
                .containsExactly("PARTIAL", "PAID");
        assertThat(response.rows()).extracting(DataSummaryResponseVO.RowVO::orderPaymentStatus)
                .containsOnly("PAID");
        assertThat(response.totalIncomeAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void shouldSplitAmountsEquallyWhenItemPricesAreMissing() {
        EnrollmentOrderEntity order = order(1L, "200.00", "100.00", "100.00");
        EnrollmentOrderItemEntity first = item(21L, 1, null);
        EnrollmentOrderItemEntity second = item(22L, 2, null);

        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(first, second));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment(1L, "100.00")));

        DataSummaryResponseVO response = dataSummaryService.getSummary(new DataSummaryQuery());

        assertThat(response.rows()).extracting(DataSummaryResponseVO.RowVO::paidAmount)
                .containsExactly(new BigDecimal("50.00"), new BigDecimal("50.00"));
        assertThat(response.rows()).extracting(DataSummaryResponseVO.RowVO::amountDue)
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("100.00"));
        assertThat(response.rows()).extracting(DataSummaryResponseVO.RowVO::balanceAmount)
                .containsExactly(new BigDecimal("-50.00"), new BigDecimal("-50.00"));
    }

    @Test
    void shouldKeepSingleItemOrderAmountsUnchanged() {
        EnrollmentOrderEntity order = order(1L, "100.00", "60.00", "40.00");
        EnrollmentOrderItemEntity only = item(21L, 1, "100.00");

        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(only));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment(1L, "60.00")));

        DataSummaryResponseVO response = dataSummaryService.getSummary(new DataSummaryQuery());

        assertThat(response.rows()).singleElement().satisfies(row -> {
            assertThat(row.amountDue()).isEqualTo(new BigDecimal("100.00"));
            assertThat(row.paidAmount()).isEqualTo(new BigDecimal("60.00"));
            assertThat(row.balanceAmount()).isEqualTo(new BigDecimal("-40.00"));
        });
    }

    @Test
    void shouldUseCourseTransportSnapshotInsteadOfOrderTransportSnapshot() {
        EnrollmentOrderEntity order = order(1L, "200.00", "200.00", "0.00");
        order.setPickupRequiredSnapshot(true);
        order.setPickupAddressSnapshot("Order-level address");
        order.setPickupFeeAmount(new BigDecimal("30.00"));

        EnrollmentOrderItemEntity pickupCourse = item(21L, 1, "100.00");
        pickupCourse.setPickupRequiredSnapshot(true);
        pickupCourse.setPickupAddressSnapshot("Course A address");
        pickupCourse.setPickupFeeAmount(new BigDecimal("10.00"));
        EnrollmentOrderItemEntity nonPickupCourse = item(22L, 2, "100.00");
        nonPickupCourse.setPickupRequiredSnapshot(false);

        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(pickupCourse, nonPickupCourse));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment(1L, "200.00")));

        DataSummaryResponseVO response = dataSummaryService.getSummary(new DataSummaryQuery());

        assertThat(response.rows()).hasSize(2);
        assertThat(response.rows()).extracting(DataSummaryResponseVO.RowVO::pickupRequired)
                .containsExactly(true, false);
        assertThat(response.rows()).extracting(DataSummaryResponseVO.RowVO::pickupAddress)
                .containsExactly("Course A address", null);
        assertThat(response.rows()).extracting(DataSummaryResponseVO.RowVO::pickupFeeAmount)
                .containsExactly(new BigDecimal("10.00"), new BigDecimal("0.00"));
    }

    @Test
    void shouldSplitAmountsAcrossParticipantsAndItemsWithoutDuplication() {
        EnrollmentOrderEntity order = order(1L, "200.00", "100.00", "100.00");
        EnrollmentOrderItemEntity first = item(21L, 1, "300.00");
        EnrollmentOrderItemEntity second = item(22L, 2, "100.00");
        EnrollmentOrderParticipantEntity firstParticipant = participant(1L, 1, "Student 1");
        EnrollmentOrderParticipantEntity secondParticipant = participant(2L, 2, "Student 2");

        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(first, second));
        when(enrollmentOrderParticipantMapper.selectList(any())).thenReturn(List.of(firstParticipant, secondParticipant));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment(1L, "100.00")));

        DataSummaryResponseVO response = dataSummaryService.getSummary(new DataSummaryQuery());

        assertThat(response.rows()).hasSize(4);
        BigDecimal paidSum = response.rows().stream()
                .map(DataSummaryResponseVO.RowVO::paidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal dueSum = response.rows().stream()
                .map(DataSummaryResponseVO.RowVO::amountDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(paidSum).isEqualByComparingTo("100.00");
        assertThat(dueSum).isEqualByComparingTo("200.00");
        assertThat(response.totalIncomeAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldAllocateMixedSharedRegistrationAmountsPerParticipantAndCourse() {
        EnrollmentOrderEntity order = order(1L, "775.00", "210.00", "565.00");
        order.setDiscountAmount(new BigDecimal("25.00"));
        EnrollmentOrderItemEntity privateCourse = item(11L, 1, "200.00");
        privateCourse.setCourseType("ONE_ON_ONE");
        privateCourse.setDiscountAmount(new BigDecimal("10.00"));
        privateCourse.setAmountDue(new BigDecimal("190.00"));
        privateCourse.setPaidAmount(new BigDecimal("40.00"));
        EnrollmentOrderItemEntity groupCourse = item(12L, 2, "600.00");
        groupCourse.setDiscountAmount(new BigDecimal("15.00"));
        groupCourse.setAmountDue(new BigDecimal("585.00"));
        groupCourse.setPaidAmount(new BigDecimal("160.00"));
        List<EnrollmentOrderParticipantEntity> participants = List.of(
                billedParticipant(1L, 1, "Student 1", "266.66", "15.00", "251.66", "60.00",
                        "[{\"label\":\"Private\",\"amount\":10,\"orderItemLineNo\":1},{\"label\":\"Group\",\"amount\":5,\"orderItemLineNo\":2}]"),
                billedParticipant(2L, 2, "Student 2", "266.66", "10.00", "256.66", "100.00",
                        "[{\"label\":\"Group\",\"amount\":10,\"orderItemLineNo\":2}]"),
                billedParticipant(3L, 3, "Student 3", "266.68", "0.00", "266.68", "50.00", "[]"));
        PrivateLessonGroupEntity privateGroup = new PrivateLessonGroupEntity();
        privateGroup.setId(91L);
        privateGroup.setEnrollmentOrderId(1L);
        privateGroup.setEnrollmentOrderItemId(11L);
        privateGroup.setParticipantCount(3);

        // 该参与人有 50 元显式课程分摊，另有 10 元历史未分摊款。
        PaymentRecordEntity firstPayment = payment(1L, "60.00");
        firstPayment.setId(31L);
        firstPayment.setParticipantIndex(1);
        PaymentRecordEntity secondPayment = payment(1L, "100.00");
        secondPayment.setId(32L);
        secondPayment.setParticipantIndex(2);
        PaymentRecordEntity thirdPayment = payment(1L, "50.00");
        thirdPayment.setId(33L);
        thirdPayment.setParticipantIndex(3);

        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(privateCourse, groupCourse));
        when(enrollmentOrderParticipantMapper.selectList(any())).thenReturn(participants);
        when(privateLessonGroupMapper.selectList(any())).thenReturn(List.of(privateGroup));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(firstPayment, secondPayment, thirdPayment));
        when(paymentAllocationMapper.selectList(any())).thenReturn(List.of(
                paymentAllocation(31L, 11L, "30.00"), paymentAllocation(31L, 12L, "20.00"),
                paymentAllocation(32L, 12L, "100.00"),
                paymentAllocation(33L, 11L, "10.00"), paymentAllocation(33L, 12L, "40.00")));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(
                student(1L, "Student 1"), student(2L, "Student 2"), student(3L, "Student 3")));

        DataSummaryResponseVO response = dataSummaryService.getSummary(new DataSummaryQuery());

        assertThat(response.rows()).hasSize(6);
        assertThat(response.rows()).extracting(DataSummaryResponseVO.RowVO::linePriceAmount)
                .containsExactly(new BigDecimal("66.66"), new BigDecimal("66.66"), new BigDecimal("66.68"),
                        new BigDecimal("200.00"), new BigDecimal("200.00"), new BigDecimal("200.00"));
        assertThat(response.rows()).extracting(DataSummaryResponseVO.RowVO::discountAmount)
                .containsExactly(new BigDecimal("10.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                        new BigDecimal("5.00"), new BigDecimal("10.00"), new BigDecimal("0.00"));
        assertThat(response.rows()).extracting(DataSummaryResponseVO.RowVO::paidAmount)
                .containsExactly(new BigDecimal("32.50"), new BigDecimal("0.00"), new BigDecimal("10.00"),
                        new BigDecimal("27.50"), new BigDecimal("100.00"), new BigDecimal("40.00"));
        assertThat(response.rows().stream().map(DataSummaryResponseVO.RowVO::amountDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("775.00");
        assertThat(response.rows().stream().map(DataSummaryResponseVO.RowVO::paidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("210.00");

        // 收款窗口取第一位学生的两门课，不能把三人整单价格显示给她。
        assertThat(dataSummaryService.getParticipantCourseAmounts(1L, 1))
                .extracting(amount -> amount.priceAmount())
                .containsExactly(new BigDecimal("66.66"), new BigDecimal("200.00"));
        assertThat(dataSummaryService.getParticipantCourseAmounts(1L, 1))
                .extracting(amount -> amount.amountDue())
                .containsExactly(new BigDecimal("56.66"), new BigDecimal("195.00"));
        assertThat(dataSummaryService.getParticipantCourseAmounts(1L, 1))
                .extracting(amount -> amount.balanceAmount())
                .containsExactly(new BigDecimal("24.16"), new BigDecimal("167.50"));
    }

    @Test
    void shouldShowEachStudentHalfOfTheTwoStudentCoursePrice() {
        EnrollmentOrderEntity order = order(1L, "150.00", "0.00", "150.00");
        EnrollmentOrderItemEntity course = item(11L, 1, "150.00");
        course.setCourseType("ONE_ON_ONE");
        course.setAmountDue(new BigDecimal("150.00"));
        PrivateLessonGroupEntity privateGroup = new PrivateLessonGroupEntity();
        privateGroup.setId(91L);
        privateGroup.setEnrollmentOrderId(1L);
        privateGroup.setEnrollmentOrderItemId(11L);
        privateGroup.setParticipantCount(2);

        when(enrollmentOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(course));
        when(enrollmentOrderParticipantMapper.selectList(any())).thenReturn(List.of(
                billedParticipant(1L, 1, "Student 1", "75.00", "0.00", "75.00", "0.00", "[]"),
                billedParticipant(2L, 2, "Student 2", "75.00", "0.00", "75.00", "0.00", "[]")));
        when(privateLessonGroupMapper.selectList(any())).thenReturn(List.of(privateGroup));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(
                student(1L, "Student 1"), student(2L, "Student 2")));

        assertThat(dataSummaryService.getParticipantCourseAmounts(1L, 1))
                .singleElement()
                .satisfies(amount -> {
                    assertThat(amount.priceAmount()).isEqualByComparingTo("75.00");
                    assertThat(amount.amountDue()).isEqualByComparingTo("75.00");
                    assertThat(amount.balanceAmount()).isEqualByComparingTo("75.00");
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

    private EnrollmentOrderParticipantEntity billedParticipant(Long studentId,
                                                                int index,
                                                                String name,
                                                                String gross,
                                                                String discount,
                                                                String due,
                                                                String paid,
                                                                String discountDetail) {
        EnrollmentOrderParticipantEntity participant = participant(studentId, index, name);
        participant.setGrossAmount(new BigDecimal(gross));
        participant.setDiscountAmount(new BigDecimal(discount));
        participant.setAmountDue(new BigDecimal(due));
        participant.setPaidAmount(new BigDecimal(paid));
        participant.setDiscountDetail(discountDetail);
        return participant;
    }

    private PaymentAllocationEntity paymentAllocation(Long paymentId, Long orderItemId, String amount) {
        PaymentAllocationEntity allocation = new PaymentAllocationEntity();
        allocation.setPaymentRecordId(paymentId);
        allocation.setEnrollmentOrderItemId(orderItemId);
        allocation.setAmount(new BigDecimal(amount));
        return allocation;
    }

    private StudentEntity student(Long id, String name) {
        StudentEntity student = new StudentEntity();
        student.setId(id);
        student.setStudentName(name);
        return student;
    }

    private EnrollmentOrderItemEntity item(long id, int lineNo, String priceAmount) {
        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(id);
        item.setEnrollmentOrderId(1L);
        item.setLineNo(lineNo);
        item.setCourseType("GROUP");
        if (priceAmount != null) {
            item.setPriceAmount(new BigDecimal(priceAmount));
        }
        return item;
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
