package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.CourseProductScheduleSlotEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.dto.ClassEnrollmentCreateRequest;
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
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
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
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Mock
    private LessonRecordMapper lessonRecordMapper;

    @Mock
    private com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper classGroupMapper;

    @Mock
    private com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper classEnrollmentMapper;

    @Mock
    private com.classdeduction.system.modules.base.mapper.CourseProductScheduleSlotMapper courseProductScheduleSlotMapper;

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
                lessonOccurrenceMapper,
                lessonRecordMapper,
                classGroupMapper,
                classEnrollmentMapper,
                courseProductScheduleSlotMapper
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
        assertThat(coursePackage.getTotalUnits()).isEqualTo(160);
        assertThat(coursePackage.getDefaultLessonUnits()).isEqualTo(4);
        assertThat(coursePackage.getRemainingUnits()).isEqualTo(160);
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
    void shouldRejectInactiveOneOnOneProduct() {
        EnrollmentCreateRequest request = buildCreateRequest();
        CourseProductEntity product = buildCourseProduct();
        product.setStatus("INACTIVE");
        when(studentMapper.selectById(101L)).thenReturn(buildStudent());
        when(courseProductMapper.selectById(201L)).thenReturn(product);

        assertThatThrownBy(() -> enrollmentOrderService.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Course product is not active.");
    }

    @Test
    void shouldRejectGroupProductForOneOnOneEnrollment() {
        EnrollmentCreateRequest request = buildCreateRequest();
        CourseProductEntity product = buildCourseProduct();
        product.setTeachingMode("GROUP");
        when(studentMapper.selectById(101L)).thenReturn(buildStudent());
        when(courseProductMapper.selectById(201L)).thenReturn(product);

        assertThatThrownBy(() -> enrollmentOrderService.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only 1:1 course products can be used for new enrollment.");
    }

    @Test
    void shouldRejectWhenDefaultLessonUnitsExceedTotalUnits() {
        EnrollmentCreateRequest request = buildCreateRequest();
        request.setTotalUnits(4);
        request.setDefaultLessonUnits(6);

        assertThatThrownBy(() -> enrollmentOrderService.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Default lesson units cannot exceed total units.");
    }

    @Test
    void shouldCancelOneOnOneOrderAndReleaseFutureScheduling() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PAID");
        order.setReceivedAmount(new BigDecimal("1100.00"));

        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(3001L);
        coursePackage.setEnrollmentOrderId(2001L);
        coursePackage.setPackageStatus("ACTIVE");

        RecurringScheduleRuleEntity rule = new RecurringScheduleRuleEntity();
        rule.setId(7001L);
        rule.setPackageId(3001L);
        rule.setRuleStatus("ACTIVE");

        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of(rule));

        enrollmentOrderService.cancelOrder(2001L);

        verify(lessonOccurrenceMapper).update(any());
        verify(recurringScheduleRuleMapper).update(any());
        verify(recurringScheduleSlotMapper).update(any());
        verify(coursePackageMapper).updateById(packageCaptor.capture());
        verify(enrollmentOrderMapper).updateById(orderCaptor.capture());

        assertThat(packageCaptor.getValue().getPackageStatus()).isEqualTo("CLOSED");
        assertThat(orderCaptor.getValue().getOrderStatus()).isEqualTo("CANCELLED");
        assertThat(orderCaptor.getValue().getPaymentStatus()).isEqualTo("PAID");
    }

    @Test
    void shouldDeleteOneOnOneOrderAndReleaseFutureScheduling() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("UNPAID");
        order.setReceivedAmount(BigDecimal.ZERO);

        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(3001L);
        coursePackage.setEnrollmentOrderId(2001L);
        coursePackage.setPackageStatus("ACTIVE");

        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of());

        enrollmentOrderService.deleteOrder(2001L);

        verify(lessonOccurrenceMapper).update(any());
        verify(coursePackageMapper).updateById(packageCaptor.capture());
        verify(enrollmentOrderMapper).deleteById(2001L);
        assertThat(packageCaptor.getValue().getPackageStatus()).isEqualTo("CLOSED");
    }

    @Test
    void shouldRejectDeleteWhenOrderHasReceivedPayment() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PAID");
        order.setReceivedAmount(new BigDecimal("1100.00"));
        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);

        assertThatThrownBy(() -> enrollmentOrderService.deleteOrder(2001L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only unpaid mistaken orders can be deleted. Use Refund for paid orders.");

        verify(enrollmentOrderMapper, never()).deleteById(2001L);
    }

    @Test
    void shouldRefundOneOnOneOrderAndRecordNegativePayment() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PAID");
        order.setReceivedAmount(new BigDecimal("1100.00"));

        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(3001L);
        coursePackage.setEnrollmentOrderId(2001L);
        coursePackage.setPackageStatus("ACTIVE");
        coursePackage.setReceivedAmount(new BigDecimal("1100.00"));

        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of());

        com.classdeduction.system.modules.enrollment.dto.RefundRequest request =
                new com.classdeduction.system.modules.enrollment.dto.RefundRequest();
        request.setPaymentMethod("CASH");
        request.setHandledByName("Amy");
        request.setRemark("Student withdrew");

        enrollmentOrderService.refundOrder(2001L, request);

        // Negative refund payment record persisted.
        verify(paymentRecordMapper).insert(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getPaymentType()).isEqualTo("REFUND");
        assertThat(paymentCaptor.getValue().getPaymentStatus()).isEqualTo("REFUNDED");
        assertThat(paymentCaptor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("-1100.00"));
        assertThat(paymentCaptor.getValue().getHandledByName()).isEqualTo("Amy");

        // Order set to REFUNDED + CANCELLED, received reset to 0.
        verify(enrollmentOrderMapper).updateById(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getPaymentStatus()).isEqualTo("REFUNDED");
        assertThat(orderCaptor.getValue().getOrderStatus()).isEqualTo("CANCELLED");
        assertThat(orderCaptor.getValue().getReceivedAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        // Course package received amount zeroed.
        verify(coursePackageMapper, atLeastOnce()).updateById(packageCaptor.capture());
        assertThat(packageCaptor.getAllValues())
                .anySatisfy(pkg -> assertThat(pkg.getReceivedAmount()).isEqualByComparingTo(BigDecimal.ZERO));
    }

    @Test
    void shouldRejectRefundWhenNoPaymentReceived() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2002L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("UNPAID");
        order.setReceivedAmount(BigDecimal.ZERO);
        when(enrollmentOrderMapper.selectById(2002L)).thenReturn(order);

        com.classdeduction.system.modules.enrollment.dto.RefundRequest request =
                new com.classdeduction.system.modules.enrollment.dto.RefundRequest();
        request.setPaymentMethod("CASH");
        request.setHandledByName("Amy");

        assertThatThrownBy(() -> enrollmentOrderService.refundOrder(2002L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("There is no received payment to refund.");
    }

    @Test
    void shouldComputeProratedPriceAndOccurrencesForPerSessionEnrollment() {
        // Product: PER_SESSION, start Monday 2026-09-07, total 20 sessions, weekly Monday slot, list price 1200.
        LocalDate startDate = LocalDate.of(2026, 9, 7); // Monday
        CourseProductEntity product = buildGroupCourseProduct();
        product.setBillingType("PER_SESSION");
        product.setStartDate(startDate);
        product.setTotalSessions(20);
        CourseProductScheduleSlotEntity slot = buildClassSlot(startDate.getDayOfWeek().getValue());
        ClassGroupEntity existingGroup = buildClassGroup(product.getId());

        ClassEnrollmentCreateRequest request = new ClassEnrollmentCreateRequest();
        request.setStudentId(101L);
        request.setProductId(202L);
        request.setSessions(10); // buy 10 of 20 sessions -> 1200 * 10/20 = 600.00
        request.setHandledByName("Amy");
        request.setCurrencyCode("USD");
        request.setDiscountAmount(BigDecimal.ZERO);
        request.setReceivedAmount(BigDecimal.ZERO);

        when(studentMapper.selectById(101L)).thenReturn(buildStudent());
        when(courseProductMapper.selectById(product.getId())).thenReturn(product);
        when(classGroupMapper.selectOne(any())).thenReturn(existingGroup);
        when(courseProductScheduleSlotMapper.selectList(any())).thenReturn(List.of(slot));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of());
        when(classEnrollmentMapper.selectCount(any())).thenReturn(0L);
        ArgumentCaptor<EnrollmentOrderEntity> orderInsertCaptor = ArgumentCaptor.forClass(EnrollmentOrderEntity.class);
        doAnswer(invocation -> {
            EnrollmentOrderEntity entity = invocation.getArgument(0);
            entity.setId(2001L);
            return 1;
        }).when(enrollmentOrderMapper).insert(any(EnrollmentOrderEntity.class));
        ArgumentCaptor<ClassEnrollmentEntity> enrollInsertCaptor = ArgumentCaptor.forClass(ClassEnrollmentEntity.class);

        enrollmentOrderService.createClassEnrollmentOrder(request);

        // Server computes original amount by proration (10/20 of 1200).
        verify(enrollmentOrderMapper).insert(orderInsertCaptor.capture());
        assertThat(orderInsertCaptor.getValue().getOriginalAmount()).isEqualByComparingTo(new BigDecimal("600.00"));

        // Occurrences cover the FULL product period (20 weekly sessions), independent of purchased quantity.
        ArgumentCaptor<LessonOccurrenceEntity> occurrenceCaptor = ArgumentCaptor.forClass(LessonOccurrenceEntity.class);
        verify(lessonOccurrenceMapper, times(20)).insert(occurrenceCaptor.capture());
        LocalDate twentiethSession = startDate.plusWeeks(19);
        assertThat(occurrenceCaptor.getAllValues())
                .anySatisfy(occ -> assertThat(occ.getPlannedStartAt().toLocalDate()).isEqualTo(twentiethSession));

        // Enrollment snapshot carries billing type + purchased quantity; expiry = student's 10th session date.
        verify(classEnrollmentMapper).insert(enrollInsertCaptor.capture());
        assertThat(enrollInsertCaptor.getValue().getBillingType()).isEqualTo("PER_SESSION");
        assertThat(enrollInsertCaptor.getValue().getPurchasedQuantity()).isEqualTo(10);
        assertThat(enrollInsertCaptor.getValue().getExpiryDate()).isEqualTo(startDate.plusWeeks(9));
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
        product.setStatus("ACTIVE");
        product.setDefaultTotalUnits(80);
        product.setDefaultLessonUnits(4);
        return product;
    }

    private CourseProductEntity buildGroupCourseProduct() {
        CourseProductEntity product = new CourseProductEntity();
        product.setId(202L);
        product.setProductCode("COURSE-202");
        product.setProductName("SHSAT Group");
        product.setSubjectName("SHSAT Math");
        product.setTeachingMode("GROUP");
        product.setStatus("ACTIVE");
        product.setDefaultTeacherId(301L);
        product.setDefaultClassroomId(401L);
        product.setListPrice(new BigDecimal("1200.00"));
        return product;
    }

    private ClassGroupEntity buildClassGroup(Long productId) {
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(801L);
        classGroup.setClassName("SHSAT Group");
        classGroup.setProductId(productId);
        classGroup.setTeacherId(301L);
        classGroup.setClassroomId(401L);
        classGroup.setStatus("ACTIVE");
        return classGroup;
    }

    private CourseProductScheduleSlotEntity buildClassSlot(int weekday) {
        CourseProductScheduleSlotEntity slot = new CourseProductScheduleSlotEntity();
        slot.setId(901L);
        slot.setProductId(202L);
        slot.setWeekday(weekday);
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setEndTime(LocalTime.of(10, 0));
        return slot;
    }

    private TeacherEntity buildTeacher() {
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(301L);
        teacher.setTeacherNo("T-301");
        teacher.setTeacherName("Emma Wang");
        return teacher;
    }
}
