package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentCreateRequest;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.PackageTeacherAssignmentEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentRecordEntity;
import com.classdeduction.system.modules.enrollment.entity.ReceiptRecordEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentRecordMapper;
import com.classdeduction.system.modules.enrollment.mapper.ReceiptRecordMapper;
import com.classdeduction.system.modules.enrollment.service.impl.EnrollmentOrderServiceImpl;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentCreateResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 报名开单服务测试：覆盖报名后自动生成课时包、支付、收据和主老师分配的主链路。
 */
@ExtendWith(MockitoExtension.class)
class EnrollmentOrderServiceImplTest {

    @Mock
    private EnrollmentOrderMapper enrollmentOrderMapper;

    @Mock
    private CoursePackageMapper coursePackageMapper;

    @Mock
    private PaymentRecordMapper paymentRecordMapper;

    @Mock
    private ReceiptRecordMapper receiptRecordMapper;

    @Mock
    private PackageTeacherAssignmentMapper packageTeacherAssignmentMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private CourseProductMapper courseProductMapper;

    @Mock
    private TeacherMapper teacherMapper;

    @Mock
    private JsonHelper jsonHelper;

    @Mock
    private com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper recurringScheduleRuleMapper;

    @Mock
    private com.classdeduction.system.modules.schedule.mapper.RecurringScheduleSlotMapper recurringScheduleSlotMapper;

    @Mock
    private com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper lessonOccurrenceMapper;

    @Captor
    private ArgumentCaptor<EnrollmentOrderEntity> orderCaptor;

    @Captor
    private ArgumentCaptor<CoursePackageEntity> packageCaptor;

    @Captor
    private ArgumentCaptor<PaymentRecordEntity> paymentCaptor;

    @Captor
    private ArgumentCaptor<ReceiptRecordEntity> receiptCaptor;

    @Captor
    private ArgumentCaptor<PackageTeacherAssignmentEntity> assignmentCaptor;

    private EnrollmentOrderServiceImpl enrollmentOrderService;

    @BeforeEach
    void setUp() {
        enrollmentOrderService = new EnrollmentOrderServiceImpl(
                enrollmentOrderMapper,
                coursePackageMapper,
                paymentRecordMapper,
                receiptRecordMapper,
                packageTeacherAssignmentMapper,
                studentMapper,
                courseProductMapper,
                teacherMapper,
                jsonHelper,
                recurringScheduleRuleMapper,
                recurringScheduleSlotMapper,
                lessonOccurrenceMapper
        );
    }

    @Test
    void shouldCreateOrderPackagePaymentReceiptAndTeacherAssignment() {
        EnrollmentCreateRequest request = buildCreateRequest();
        when(studentMapper.selectById(101L)).thenReturn(buildStudent());
        when(courseProductMapper.selectById(201L)).thenReturn(buildCourseProduct());
        when(teacherMapper.selectById(301L)).thenReturn(buildTeacher());
        when(jsonHelper.toJson(any())).thenReturn("{\"receipt\":true}");
        stubGeneratedIds(true, true);

        EnrollmentCreateResultVO result = enrollmentOrderService.createOrder(request);

        verify(enrollmentOrderMapper).insert(orderCaptor.capture());
        verify(coursePackageMapper).insert(packageCaptor.capture());
        verify(packageTeacherAssignmentMapper).insert(assignmentCaptor.capture());
        verify(paymentRecordMapper).insert(paymentCaptor.capture());
        verify(receiptRecordMapper).insert(receiptCaptor.capture());

        EnrollmentOrderEntity order = orderCaptor.getValue();
        CoursePackageEntity coursePackage = packageCaptor.getValue();
        PackageTeacherAssignmentEntity assignment = assignmentCaptor.getValue();
        PaymentRecordEntity payment = paymentCaptor.getValue();
        ReceiptRecordEntity receipt = receiptCaptor.getValue();

        assertThat(order.getEnrollmentNo()).startsWith("EO");
        assertThat(order.getPaymentStatus()).isEqualTo("PAID");
        assertThat(coursePackage.getPackageNo()).startsWith("PK");
        assertThat(coursePackage.getTotalUnits()).isEqualTo(80);
        assertThat(coursePackage.getDefaultLessonUnits()).isEqualTo(4);
        assertThat(coursePackage.getRemainingUnits()).isEqualTo(80);
        assertThat(assignment.getPackageId()).isEqualTo(3001L);
        assertThat(assignment.getTeacherId()).isEqualTo(301L);
        assertThat(payment.getPaymentNo()).startsWith("PMT");
        assertThat(payment.getPaymentStatus()).isEqualTo("PAID");
        assertThat(receipt.getReceiptNo()).startsWith("RCP");
        assertThat(receipt.getTemplateCode()).isEqualTo("DEFAULT_RECEIPT");
        assertThat(result.enrollmentOrderId()).isEqualTo(2001L);
        assertThat(result.packageIds()).containsExactly(3001L);
        assertThat(result.paymentIds()).containsExactly(5001L);
        assertThat(result.receiptIds()).containsExactly(6001L);
    }

    @Test
    void shouldTreatFullyDiscountedOrderAsPaidWithoutPaymentRecord() {
        EnrollmentCreateRequest request = buildCreateRequest();
        request.setDiscountAmount(new BigDecimal("1200.00"));
        request.setReceivedAmount(BigDecimal.ZERO);
        request.setPrimaryTeacherId(null);
        when(studentMapper.selectById(101L)).thenReturn(buildStudent());
        when(courseProductMapper.selectById(201L)).thenReturn(buildCourseProduct());
        when(jsonHelper.toJson(any())).thenReturn("{\"receipt\":true}");
        stubGeneratedIds(false, false);

        EnrollmentCreateResultVO result = enrollmentOrderService.createOrder(request);

        verify(enrollmentOrderMapper).insert(orderCaptor.capture());
        verify(paymentRecordMapper, never()).insert(any(PaymentRecordEntity.class));
        assertThat(orderCaptor.getValue().getPaymentStatus()).isEqualTo("PAID");
        assertThat(result.paymentIds()).isEmpty();
    }

    @Test
    void shouldRejectWhenDefaultLessonUnitsExceedTotalUnits() {
        EnrollmentCreateRequest request = buildCreateRequest();
        request.setTotalUnits(4);
        request.setDefaultLessonUnits(6);

        assertThatThrownBy(() -> enrollmentOrderService.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("默认单节课时单位不能大于总课时单位。");
    }

    private void stubGeneratedIds(boolean includeAssignmentInsert, boolean includePaymentInsert) {
        doAnswer(invocation -> {
            EnrollmentOrderEntity entity = invocation.getArgument(0);
            entity.setId(2001L);
            return 1;
        }).when(enrollmentOrderMapper).insert(any(EnrollmentOrderEntity.class));
        doAnswer(invocation -> {
            CoursePackageEntity entity = invocation.getArgument(0);
            entity.setId(3001L);
            return 1;
        }).when(coursePackageMapper).insert(any(CoursePackageEntity.class));
        if (includeAssignmentInsert) {
            doAnswer(invocation -> {
                PackageTeacherAssignmentEntity entity = invocation.getArgument(0);
                entity.setId(4001L);
                return 1;
            }).when(packageTeacherAssignmentMapper).insert(any(PackageTeacherAssignmentEntity.class));
        }
        if (includePaymentInsert) {
            doAnswer(invocation -> {
                PaymentRecordEntity entity = invocation.getArgument(0);
                entity.setId(5001L);
                return 1;
            }).when(paymentRecordMapper).insert(any(PaymentRecordEntity.class));
        }
        doAnswer(invocation -> {
            ReceiptRecordEntity entity = invocation.getArgument(0);
            entity.setId(6001L);
            return 1;
        }).when(receiptRecordMapper).insert(any(ReceiptRecordEntity.class));
    }

    private EnrollmentCreateRequest buildCreateRequest() {
        EnrollmentCreateRequest request = new EnrollmentCreateRequest();
        request.setStudentId(101L);
        request.setCourseProductId(201L);
        request.setPrimaryTeacherId(301L);
        request.setCashierUserId(401L);
        request.setHandledByName("Amy");
        request.setCurrencyCode("USD");
        request.setPackageName("SHSAT Math Premium");
        request.setStartDate(LocalDate.of(2026, 4, 7));
        request.setEstimatedEndDate(LocalDate.of(2026, 7, 7));
        request.setAssignmentEffectiveFrom(LocalDateTime.of(2026, 4, 7, 9, 0));
        request.setPaidAt(LocalDateTime.of(2026, 4, 7, 10, 0));
        request.setIssueReceipt(true);
        request.setTemplateCode("DEFAULT_RECEIPT");
        request.setOriginalAmount(new BigDecimal("1200.00"));
        request.setDiscountAmount(new BigDecimal("100.00"));
        request.setReceivedAmount(new BigDecimal("1100.00"));
        request.setTotalUnits(80);
        request.setDefaultLessonUnits(4);
        request.setRemark("first enrollment");
        return request;
    }

    private StudentEntity buildStudent() {
        StudentEntity student = new StudentEntity();
        student.setId(101L);
        student.setStudentNo("STU-101");
        student.setStudentName("Olivia Lin");
        return student;
    }

    private CourseProductEntity buildCourseProduct() {
        CourseProductEntity product = new CourseProductEntity();
        product.setId(201L);
        product.setProductCode("COURSE-201");
        product.setProductName("SHSAT Math 1:1");
        product.setSubjectName("SHSAT Math");
        product.setTeachingMode("ONE_ON_ONE");
        product.setDefaultTotalUnits(80);
        product.setDefaultLessonUnits(4);
        return product;
    }

    private TeacherEntity buildTeacher() {
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(301L);
        teacher.setTeacherNo("T-301");
        teacher.setTeacherName("Emma Wang");
        return teacher;
    }
}
