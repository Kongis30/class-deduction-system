package com.classdeduction.system.modules.enrollment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.dto.DataSummaryQuery;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentRecordEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentRecordMapper;
import com.classdeduction.system.modules.enrollment.service.impl.DataSummaryServiceImpl;
import com.classdeduction.system.modules.enrollment.vo.DataSummaryResponseVO;
import com.classdeduction.system.modules.lesson.entity.HourLedgerEntity;
import com.classdeduction.system.modules.lesson.mapper.HourLedgerMapper;
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

    private DataSummaryServiceImpl dataSummaryService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        // 单元测试不启动 MyBatis 上下文，手动初始化 LambdaQueryWrapper 字段缓存。
        Configuration configuration = new Configuration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), EnrollmentOrderEntity.class);
    }

    @BeforeEach
    void setUp() {
        dataSummaryService = new DataSummaryServiceImpl(
                enrollmentOrderMapper,
                enrollmentOrderItemMapper,
                coursePackageMapper,
                packageTeacherAssignmentMapper,
                paymentRecordMapper,
                classEnrollmentMapper,
                classGroupMapper,
                courseProductMapper,
                teacherMapper,
                hourLedgerMapper
        );
        lenient().when(enrollmentOrderItemMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(coursePackageMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(paymentRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
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
}
