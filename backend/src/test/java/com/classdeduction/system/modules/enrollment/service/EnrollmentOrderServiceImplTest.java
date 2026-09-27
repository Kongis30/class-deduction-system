package com.classdeduction.system.modules.enrollment.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderUpdateRequest;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentCreateRequest;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderCorrectionRequest;
import com.classdeduction.system.modules.enrollment.dto.RegistrationIntakeRequest;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderParticipantEntity;
import com.classdeduction.system.modules.enrollment.entity.PackageTeacherAssignmentEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentAllocationEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentRecordEntity;
import com.classdeduction.system.modules.enrollment.entity.ReceiptRecordEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderParticipantMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentMethodMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentRecordMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMemberMapper;
import com.classdeduction.system.modules.enrollment.mapper.ReceiptRecordMapper;
import com.classdeduction.system.modules.enrollment.service.impl.ClassBillingServiceImpl;
import com.classdeduction.system.modules.enrollment.service.impl.EnrollmentOrderServiceImpl;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentCreateResultVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentOrderDetailVO;
import com.classdeduction.system.modules.enrollment.vo.ReceiptRecordVO;
import com.classdeduction.system.modules.lesson.entity.HourLedgerEntity;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.HourLedgerMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.lesson.mapper.PackageBalanceSnapshotMapper;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.LessonAdjustmentLogEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonAdjustmentLogMapper;
import com.classdeduction.system.modules.systemconfig.entity.NotificationLogEntity;
import com.classdeduction.system.modules.systemconfig.mapper.NotificationLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 报名开单服务测试：覆盖报名后生成课时包、支付和收据的主链路。
 */
@ExtendWith(MockitoExtension.class)
class EnrollmentOrderServiceImplTest {

    @Mock
    private EnrollmentOrderMapper enrollmentOrderMapper;

    @Mock
    private EnrollmentOrderItemMapper enrollmentOrderItemMapper;

    @Mock
    private EnrollmentOrderParticipantMapper enrollmentOrderParticipantMapper;

    @Mock
    private CoursePackageMapper coursePackageMapper;

    @Mock
    private PaymentRecordMapper paymentRecordMapper;

    private com.classdeduction.system.modules.enrollment.mapper.PaymentAllocationMapper paymentAllocationMapper;

    private com.classdeduction.system.modules.enrollment.service.allocation.PaymentAllocationService paymentAllocationService;

    @Mock
    private PrivateLessonGroupMemberMapper privateLessonGroupMemberMapper;

    @Mock
    private PaymentMethodMapper paymentMethodMapper;

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

    // 直接用真实实现，让测试负载与生产一致的到期日计算逻辑。
    private ClassBillingServiceImpl classBillingService;

    @Mock
    private com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper recurringScheduleRuleMapper;

    @Mock
    private com.classdeduction.system.modules.schedule.mapper.RecurringScheduleSlotMapper recurringScheduleSlotMapper;

    @Mock
    private com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper lessonOccurrenceMapper;

    @Mock
    private LessonRecordMapper lessonRecordMapper;

    @Mock
    private HourLedgerMapper hourLedgerMapper;

    @Mock
    private PackageBalanceSnapshotMapper packageBalanceSnapshotMapper;

    @Mock
    private LessonAdjustmentLogMapper lessonAdjustmentLogMapper;

    @Mock
    private NotificationLogMapper notificationLogMapper;

    @Mock
    private com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper classGroupMapper;

    @Mock
    private com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper classEnrollmentMapper;

    @Mock
    private ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;

    @Captor
    private ArgumentCaptor<EnrollmentOrderEntity> orderCaptor;

    @Captor
    private ArgumentCaptor<CoursePackageEntity> packageCaptor;

    @Captor
    private ArgumentCaptor<PaymentRecordEntity> paymentCaptor;

    @Captor
    private ArgumentCaptor<ReceiptRecordEntity> receiptCaptor;

    @Captor
    private ArgumentCaptor<ClassEnrollmentEntity> classEnrollmentCaptor;

    private EnrollmentOrderServiceImpl enrollmentOrderService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        // 单元测试不启动 MyBatis 上下文，手动初始化 LambdaQueryWrapper 字段缓存。
        Configuration configuration = new Configuration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), LessonOccurrenceEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ClassEnrollmentEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), EnrollmentOrderEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), CoursePackageEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), EnrollmentOrderItemEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), LessonRecordEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), HourLedgerEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), LessonAdjustmentLogEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), NotificationLogEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RecurringScheduleRuleEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RecurringScheduleSlotEntity.class);
    }

    @BeforeEach
    void setUp() {
        classBillingService = new ClassBillingServiceImpl(classGroupScheduleSlotMapper);
        paymentAllocationService = org.mockito.Mockito.mock(com.classdeduction.system.modules.enrollment.service.allocation.PaymentAllocationService.class);
        paymentAllocationMapper = org.mockito.Mockito.mock(com.classdeduction.system.modules.enrollment.mapper.PaymentAllocationMapper.class);
        enrollmentOrderService = new EnrollmentOrderServiceImpl(
                enrollmentOrderMapper,
                enrollmentOrderItemMapper,
                enrollmentOrderParticipantMapper,
                coursePackageMapper,
                paymentRecordMapper,
                paymentAllocationMapper,
                paymentAllocationService,
                privateLessonGroupMemberMapper,
                receiptRecordMapper,
                classBillingService,
                packageTeacherAssignmentMapper,
                studentMapper,
                courseProductMapper,
                teacherMapper,
                jsonHelper,
                recurringScheduleRuleMapper,
                recurringScheduleSlotMapper,
                lessonOccurrenceMapper,
                lessonRecordMapper,
                hourLedgerMapper,
                packageBalanceSnapshotMapper,
                lessonAdjustmentLogMapper,
                notificationLogMapper,
                classGroupMapper,
                classEnrollmentMapper,
                classGroupScheduleSlotMapper,
                paymentMethodMapper
        );
        lenient().when(enrollmentOrderItemMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(enrollmentOrderParticipantMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(privateLessonGroupMemberMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(paymentMethodMapper.selectCount(any())).thenReturn(1L);
    }

    @Test
    void shouldCreateOrderPackagePaymentAndReceipt() {
        EnrollmentCreateRequest request = buildCreateRequest();
        when(studentMapper.selectById(101L)).thenReturn(buildStudent());
        when(courseProductMapper.selectById(201L)).thenReturn(buildCourseProduct());
        when(jsonHelper.toJson(any())).thenReturn("{\"receipt\":true}");
        stubGeneratedIds(true);

        EnrollmentCreateResultVO result = enrollmentOrderService.createOrder(request);

        verify(enrollmentOrderMapper).insert(orderCaptor.capture());
        verify(coursePackageMapper).insert(packageCaptor.capture());
        verify(paymentRecordMapper).insert(paymentCaptor.capture());
        verify(receiptRecordMapper).insert(receiptCaptor.capture());

        EnrollmentOrderEntity order = orderCaptor.getValue();
        CoursePackageEntity coursePackage = packageCaptor.getValue();
        PaymentRecordEntity payment = paymentCaptor.getValue();
        ReceiptRecordEntity receipt = receiptCaptor.getValue();

        assertThat(order.getEnrollmentNo()).startsWith("EO");
        assertThat(order.getPaymentStatus()).isEqualTo("PAID");
        assertThat(coursePackage.getPackageNo()).startsWith("PK");
        assertThat(coursePackage.getTotalUnits()).isEqualTo(160);
        assertThat(coursePackage.getRemainingUnits()).isEqualTo(160);
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
        when(studentMapper.selectById(101L)).thenReturn(buildStudent());
        when(courseProductMapper.selectById(201L)).thenReturn(buildCourseProduct());
        when(jsonHelper.toJson(any())).thenReturn("{\"receipt\":true}");
        stubGeneratedIds(false);

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
    void shouldRejectCancelWhenOrderHasReceivedPayment() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PAID");
        order.setReceivedAmount(new BigDecimal("1100.00"));

        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);

        assertThatThrownBy(() -> enrollmentOrderService.cancelOrder(2001L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("PAID_ORDER_REQUIRES_REFUND");

        verify(coursePackageMapper, never()).selectList(any());
        verify(lessonOccurrenceMapper, never()).update(any());
        verify(enrollmentOrderMapper, never()).updateById(any(EnrollmentOrderEntity.class));
    }

    @Test
    void shouldRejectStatusChangeThroughGenericUpdate() {
        // 通用 updateOrder 不得改状态，取消必须走 cancelOrder，避免绕过已收款限制与资格撤销。
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PAID");
        order.setReceivedAmount(new BigDecimal("1100.00"));

        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);

        EnrollmentOrderUpdateRequest request = new EnrollmentOrderUpdateRequest();
        request.setOrderStatus("CANCELLED");

        assertThatThrownBy(() -> enrollmentOrderService.updateOrder(2001L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("ORDER_STATUS_UPDATE_NOT_ALLOWED");

        verify(enrollmentOrderMapper, never()).updateById(any(EnrollmentOrderEntity.class));
    }

    @Test
    void shouldCancelUnpaidOneOnOneOrderAndReleaseFutureScheduling() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("UNPAID");
        order.setReceivedAmount(BigDecimal.ZERO);

        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(3001L);
        coursePackage.setEnrollmentOrderId(2001L);
        coursePackage.setPackageStatus("ACTIVE");

        RecurringScheduleRuleEntity rule = new RecurringScheduleRuleEntity();
        rule.setId(7001L);
        rule.setPackageId(3001L);
        rule.setRuleStatus("ACTIVE");

        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);
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
        assertThat(orderCaptor.getValue().getPaymentStatus()).isEqualTo("UNPAID");
    }

    @Test
    void shouldCorrectGroupReceiptGradeChangeAndRecomputeExpiryDate() {
        // 回归：Edit Receipt 修改 grade（班课换班）时，重建 class_enrollment 的 expiry_date
        // 必须按计费规则重算（MONTH × 2 → 结课当月月末），不能落回 NULL。
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PAID");
        order.setStudentId(101L);
        order.setClassId(801L);
        order.setSignupDate(LocalDate.of(2026, 9, 16));

        StudentEntity student = buildStudent();
        student.setGrade("G1");

        ClassGroupEntity oldClass = buildClassGroup(202L);
        oldClass.setGradeBand("G1-G5");
        ClassGroupEntity newClass = buildClassGroup(202L);
        newClass.setId(802L);
        newClass.setGradeBand("G1-G5");

        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(8001L);
        item.setEnrollmentOrderId(2001L);
        item.setCourseType("GROUP");
        item.setCourseProductId(202L);
        item.setClassId(801L);
        item.setGradeBand("G1-G5");
        item.setPackageUnit("MONTH");
        item.setPackageQuantity(new BigDecimal("2"));
        item.setStartDate(LocalDate.of(2026, 9, 16));
        item.setPickupRequiredSnapshot(true);
        item.setPickupAddressSnapshot("123 Old Pickup Address");

        ClassEnrollmentEntity activeEnrollment = new ClassEnrollmentEntity();
        activeEnrollment.setId(9001L);
        activeEnrollment.setClassId(801L);
        activeEnrollment.setStudentId(101L);
        activeEnrollment.setEnrollmentOrderItemId(8001L);
        activeEnrollment.setEnrollmentDate(LocalDate.of(2026, 9, 16));
        activeEnrollment.setStatus("ACTIVE");

        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(studentMapper.selectById(101L)).thenReturn(student);
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(classGroupMapper.selectById(802L)).thenReturn(newClass);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(activeEnrollment));
        when(receiptRecordMapper.selectList(any())).thenReturn(List.of());

        EnrollmentOrderCorrectionRequest request = new EnrollmentOrderCorrectionRequest();
        RegistrationIntakeRequest.CourseItemPayload payload = new RegistrationIntakeRequest.CourseItemPayload();
        payload.setOrderItemId(8001L);
        payload.setClassId(802L);
        payload.setGradeBand("G1-G5");
        payload.setCourseType("GROUP");
        payload.setCourseProductId(202L);
        payload.setPackageUnit("MONTH");
        payload.setPackageQuantity(new BigDecimal("2"));
        payload.setStartDate(LocalDate.of(2026, 9, 16));
        request.setItems(List.of(payload));

        enrollmentOrderService.correctOrder(2001L, request);

        // 即使旧客户端把 false 省略为 null，课程项接送快照也必须明确清零并清除旧地址。
        verify(enrollmentOrderItemMapper).updateTransportSnapshot(8001L, false, null, false, null);

        verify(classEnrollmentMapper).insert(classEnrollmentCaptor.capture());
        ClassEnrollmentEntity replacement = classEnrollmentCaptor.getValue();
        assertThat(replacement.getExpiryDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(replacement.getEnrollmentDate()).isEqualTo(LocalDate.of(2026, 9, 16));
        assertThat(replacement.getBillingType()).isEqualTo("MONTH");
        assertThat(replacement.getPurchasedQuantity()).isEqualTo(2);

        // 旧班级的 ACTIVE 在册记录应被标记为 CANCELLED
        ArgumentCaptor<ClassEnrollmentEntity> updatedCaptor = ArgumentCaptor.forClass(ClassEnrollmentEntity.class);
        verify(classEnrollmentMapper, atLeastOnce()).updateById(updatedCaptor.capture());
        assertThat(updatedCaptor.getAllValues()).anySatisfy(updated ->
                assertThat(updated.getStatus()).isEqualTo("CANCELLED"));
    }

    @Test
    void shouldRejectConcurrentCancelSoEnrollmentChangesRollBack() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("UNPAID");
        order.setReceivedAmount(BigDecimal.ZERO);

        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(0);
        when(coursePackageMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> enrollmentOrderService.cancelOrder(2001L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("ORDER_CONCURRENT_UPDATE");
    }

    @Test
    void shouldDeleteOneOnOneOrderWithFullCascade() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("UNPAID");
        order.setReceivedAmount(BigDecimal.ZERO);

        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(3001L);
        coursePackage.setEnrollmentOrderId(2001L);
        coursePackage.setPackageStatus("ACTIVE");

        LessonRecordEntity lessonRecord = new LessonRecordEntity();
        lessonRecord.setId(4001L);
        lessonRecord.setPackageId(3001L);

        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of());
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(lessonRecord));
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of());

        enrollmentOrderService.deleteOrder(2001L);

        // 全量级联：包及其下游软删/物理删，订单最后软删
        verify(coursePackageMapper).delete(any());
        verify(lessonOccurrenceMapper).delete(any());
        verify(lessonRecordMapper).delete(any());
        verify(hourLedgerMapper, atLeastOnce()).delete(any());
        verify(notificationLogMapper, atLeastOnce()).delete(any());
        verify(packageTeacherAssignmentMapper).delete(any());
        verify(packageBalanceSnapshotMapper).deleteById(3001L);
        verify(paymentRecordMapper).delete(any());
        verify(receiptRecordMapper).delete(any());
        verify(enrollmentOrderMapper).update(org.mockito.ArgumentMatchers.isNull(), any());
        verify(enrollmentOrderMapper).deleteById(2001L);
    }

    @Test
    void shouldRequireRefundBeforeDeletingWholePaidOrder() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PAID");
        order.setReceivedAmount(new BigDecimal("1100.00"));
        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        assertThatThrownBy(() -> enrollmentOrderService.deleteOrder(2001L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("COURSE_REFUND_REQUIRED");
        verify(paymentRecordMapper, never()).delete(any());
        verify(receiptRecordMapper, never()).delete(any());
        verify(enrollmentOrderMapper, never()).deleteById(2001L);
    }

    @Test
    void shouldWithdrawClassItemWhenMixedOrderHasNoOrderLevelClassId() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setStudentId(1001L);
        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(2101L);
        item.setClassId(3101L);
        ClassEnrollmentEntity seat = new ClassEnrollmentEntity();
        seat.setId(4101L);
        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(seat));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of());
        when(coursePackageMapper.selectList(any())).thenReturn(List.of());

        enrollmentOrderService.deleteOrder(2001L);

        verify(classEnrollmentMapper).update(any());
        verify(enrollmentOrderMapper).deleteById(2001L);
    }

    @Test
    void shouldDeleteOnlySelectedUnpaidCourseFromMixedReceipt() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setStudentId(1001L);
        order.setTuitionSubtotalAmount(new BigDecimal("300.00"));
        order.setOriginalAmount(new BigDecimal("300.00"));
        order.setAmountDue(new BigDecimal("300.00"));
        order.setReceivedAmount(new BigDecimal("100.00"));
        EnrollmentOrderItemEntity removed = new EnrollmentOrderItemEntity();
        removed.setId(2101L);
        removed.setEnrollmentOrderId(2001L);
        removed.setPriceAmount(new BigDecimal("100.00"));
        removed.setAmountDue(new BigDecimal("100.00"));
        removed.setPaidAmount(BigDecimal.ZERO);
        EnrollmentOrderItemEntity retained = new EnrollmentOrderItemEntity();
        retained.setId(2102L);
        retained.setEnrollmentOrderId(2001L);
        retained.setPriceAmount(new BigDecimal("200.00"));
        retained.setPaidAmount(new BigDecimal("100.00"));
        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(removed, retained));
        when(paymentAllocationMapper.selectList(any())).thenReturn(List.of());
        when(coursePackageMapper.selectList(any())).thenReturn(List.of());
        when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);
        ReceiptRecordEntity receipt = new ReceiptRecordEntity();
        receipt.setReceiptPayload("saved receipt");
        when(receiptRecordMapper.selectList(any())).thenReturn(List.of(receipt));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", List.of(Map.of("orderItemId", "2101", "lineNo", 1),
                Map.of("orderItemId", "2102", "lineNo", 2)));
        when(jsonHelper.fromJson(any(), any())).thenReturn(payload);

        enrollmentOrderService.deleteOrderItem(2001L, 2101L);

        assertThat(order.getAmountDue()).isEqualByComparingTo("200.00");
        assertThat(order.getReceivedAmount()).isEqualByComparingTo("100.00");
        assertThat(order.getBalanceAmount()).isEqualByComparingTo("100.00");
        verify(enrollmentOrderItemMapper).deleteById(2101L);
        verify(enrollmentOrderItemMapper, never()).deleteById(2102L);
        verify(receiptRecordMapper, never()).delete(any());
        verify(receiptRecordMapper).updateById(receipt);
        assertThat((List<?>) payload.get("items")).hasSize(1);
        assertThat(((Map<?, ?>) ((List<?>) payload.get("items")).get(0)).get("orderItemId"))
                .isEqualTo("2102");
        verify(enrollmentOrderMapper, never()).deleteById(2001L);
    }

    @Test
    void shouldRequireRefundBeforeDeletingPaidCourseItem() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(2101L);
        item.setPaidAmount(new BigDecimal("50.00"));
        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(paymentAllocationMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> enrollmentOrderService.deleteOrderItem(2001L, 2101L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("COURSE_REFUND_REQUIRED");
        verify(enrollmentOrderItemMapper, never()).deleteById(2101L);
    }

    @Test
    void shouldRejectMixedReceiptWhenLegacyPaymentCannotBeAssignedToRemainingCourse() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setReceivedAmount(new BigDecimal("100.00"));
        EnrollmentOrderItemEntity removed = new EnrollmentOrderItemEntity();
        removed.setId(2101L);
        EnrollmentOrderItemEntity retained = new EnrollmentOrderItemEntity();
        retained.setId(2102L);
        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(removed, retained));
        when(paymentAllocationMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> enrollmentOrderService.deleteOrderItem(2001L, 2101L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("COURSE_PAYMENT_ALLOCATION_REQUIRED");
        verify(enrollmentOrderItemMapper, never()).deleteById(2101L);
    }

    @Test
    void shouldRemoveOnlySelectedOneOnOnePackageAndItsSchedule() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setAmountDue(new BigDecimal("300.00"));
        order.setOriginalAmount(new BigDecimal("300.00"));
        EnrollmentOrderItemEntity removed = new EnrollmentOrderItemEntity();
        removed.setId(2101L);
        removed.setAmountDue(new BigDecimal("100.00"));
        removed.setPriceAmount(new BigDecimal("100.00"));
        removed.setCourseType("ONE_ON_ONE");
        EnrollmentOrderItemEntity retained = new EnrollmentOrderItemEntity();
        retained.setId(2102L);
        retained.setClassId(3102L);
        CoursePackageEntity packageToDelete = new CoursePackageEntity();
        packageToDelete.setId(3201L);
        packageToDelete.setEnrollmentOrderItemId(2101L);
        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(removed, retained));
        when(paymentAllocationMapper.selectList(any())).thenReturn(List.of());
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(packageToDelete));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of());
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of());
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of());
        when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);

        enrollmentOrderService.deleteOrderItem(2001L, 2101L);

        verify(lessonOccurrenceMapper).delete(any());
        verify(recurringScheduleRuleMapper).delete(any());
        verify(coursePackageMapper).delete(any());
        verify(classEnrollmentMapper, never()).update(any());
        verify(enrollmentOrderItemMapper, never()).deleteById(2102L);
    }

    @Test
    void shouldWithdrawOnlySelectedClassSeatAndKeepSharedClassSchedule() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setStudentId(1001L);
        order.setAmountDue(new BigDecimal("300.00"));
        order.setOriginalAmount(new BigDecimal("300.00"));
        EnrollmentOrderItemEntity removed = new EnrollmentOrderItemEntity();
        removed.setId(2101L);
        removed.setClassId(3101L);
        removed.setAmountDue(new BigDecimal("100.00"));
        removed.setPriceAmount(new BigDecimal("100.00"));
        EnrollmentOrderItemEntity retained = new EnrollmentOrderItemEntity();
        retained.setId(2102L);
        retained.setClassId(3102L);
        ClassEnrollmentEntity targetSeat = new ClassEnrollmentEntity();
        targetSeat.setId(4101L);
        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(removed, retained));
        when(paymentAllocationMapper.selectList(any())).thenReturn(List.of());
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(targetSeat));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of());
        when(coursePackageMapper.selectList(any())).thenReturn(List.of());
        when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);

        enrollmentOrderService.deleteOrderItem(2001L, 2101L);

        verify(classEnrollmentMapper).update(any());
        verify(lessonOccurrenceMapper, never()).delete(any());
        verify(enrollmentOrderItemMapper, never()).deleteById(2102L);
    }

    @Test
    void shouldRefundOneOnOneOrderAndRecordNegativePayment() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PAID");
        order.setReceivedAmount(new BigDecimal("1100.00"));
        order.setAmountDue(new BigDecimal("1100.00"));
        order.setBalanceAmount(BigDecimal.ZERO);

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
        request.setRefundAmount(new BigDecimal("1100.00"));
        request.setPaymentMethod("CASH");
        request.setHandledByName("Amy");
        request.setHandledByUserId(88L);
        request.setRemark("Student withdrew");
        when(jsonHelper.toJson(any())).thenReturn("{\"receiptType\":\"REFUND\"}");
        ReceiptRecordEntity registrationReceipt = new ReceiptRecordEntity();
        registrationReceipt.setId(9001L);
        registrationReceipt.setReceiptNo("RCP202607150001");
        registrationReceipt.setEnrollmentOrderId(2001L);
        registrationReceipt.setTemplateCode("DEFAULT_RECEIPT");
        registrationReceipt.setReceiptPayload("{}");
        when(receiptRecordMapper.selectList(any())).thenReturn(List.of(registrationReceipt));

        ReceiptRecordVO receipt = enrollmentOrderService.refundOrder(2001L, request);

        // Negative refund payment record persisted.
        verify(paymentRecordMapper).insert(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getPaymentType()).isEqualTo("REFUND");
        assertThat(paymentCaptor.getValue().getPaymentStatus()).isEqualTo("REFUNDED");
        assertThat(paymentCaptor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("-1100.00"));
        assertThat(paymentCaptor.getValue().getHandledByName()).isEqualTo("Amy");
        assertThat(paymentCaptor.getValue().getHandledByUserId()).isEqualTo(88L);

        ArgumentCaptor<ReceiptRecordEntity> receiptCaptor = ArgumentCaptor.forClass(ReceiptRecordEntity.class);
        verify(receiptRecordMapper).updateById(receiptCaptor.capture());
        assertThat(receiptCaptor.getValue().getTemplateCode()).isEqualTo("DEFAULT_RECEIPT");
        assertThat(receipt.templateCode()).isEqualTo("DEFAULT_RECEIPT");
        assertThat(receipt.id()).isEqualTo(9001L);

        // Order set to REFUNDED + CANCELLED, with no paid or collectible amount remaining.
        verify(enrollmentOrderMapper).updateById(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getPaymentStatus()).isEqualTo("REFUNDED");
        assertThat(orderCaptor.getValue().getOrderStatus()).isEqualTo("CANCELLED");
        assertThat(orderCaptor.getValue().getReceivedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(orderCaptor.getValue().getBalanceAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(orderCaptor.getValue().getAmountDue()).isEqualByComparingTo(new BigDecimal("1100.00"));

        // Course package received amount zeroed.
        verify(coursePackageMapper, atLeastOnce()).updateById(packageCaptor.capture());
        assertThat(packageCaptor.getAllValues())
                .anySatisfy(pkg -> assertThat(pkg.getReceivedAmount()).isEqualByComparingTo(BigDecimal.ZERO));
    }

    @Test
    void shouldRejectRefundWithUnavailablePaymentMethodBeforeChangingArtifacts() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PAID");
        order.setReceivedAmount(new BigDecimal("1100.00"));

        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(paymentMethodMapper.selectCount(any())).thenReturn(0L);

        com.classdeduction.system.modules.enrollment.dto.RefundRequest request =
                new com.classdeduction.system.modules.enrollment.dto.RefundRequest();
        request.setRefundAmount(new BigDecimal("1100.00"));
        request.setPaymentMethod("DISABLED_METHOD");
        request.setHandledByName("Amy");

        assertThatThrownBy(() -> enrollmentOrderService.refundOrder(2001L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("PAYMENT_METHOD_NOT_AVAILABLE");

        verify(coursePackageMapper, never()).selectList(any());
        verify(paymentRecordMapper, never()).insert(any(PaymentRecordEntity.class));
        verify(enrollmentOrderMapper, never()).updateById(any(EnrollmentOrderEntity.class));
    }

    @Test
    void shouldRefundMixedOrderAndReleaseClassAndPackageArtifacts() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setStudentId(101L);
        order.setClassId(401L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PAID");
        order.setReceivedAmount(new BigDecimal("1100.00"));

        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(3001L);
        coursePackage.setEnrollmentOrderId(2001L);
        coursePackage.setPackageStatus("ACTIVE");
        coursePackage.setReceivedAmount(new BigDecimal("700.00"));

        ClassEnrollmentEntity classEnrollment = new ClassEnrollmentEntity();
        classEnrollment.setId(5001L);
        classEnrollment.setClassId(401L);
        classEnrollment.setStudentId(101L);
        classEnrollment.setStatus("ACTIVE");

        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of());
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(classEnrollment));

        com.classdeduction.system.modules.enrollment.dto.RefundRequest request =
                new com.classdeduction.system.modules.enrollment.dto.RefundRequest();
        request.setRefundAmount(new BigDecimal("1100.00"));
        request.setPaymentMethod("CASH");
        request.setHandledByName("Amy");

        enrollmentOrderService.refundOrder(2001L, request);

        verify(lessonOccurrenceMapper).update(any());
        verify(coursePackageMapper, atLeastOnce()).updateById(packageCaptor.capture());
        assertThat(packageCaptor.getAllValues())
                .anySatisfy(pkg -> assertThat(pkg.getPackageStatus()).isEqualTo("CLOSED"));
        verify(classEnrollmentMapper).update(any());
        verify(paymentRecordMapper).insert(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("-1100.00"));
    }

    @Test
    void shouldRefundOnlySelectedPrivateParticipantAndKeepSharedOrderActive() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PAID");
        order.setOriginalAmount(new BigDecimal("375.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setAmountDue(new BigDecimal("375.00"));
        order.setReceivedAmount(new BigDecimal("375.00"));
        order.setBalanceAmount(BigDecimal.ZERO);

        EnrollmentOrderParticipantEntity first = buildParticipant(1, 101L, new BigDecimal("125.00"));
        EnrollmentOrderParticipantEntity selected = buildParticipant(2, 102L, new BigDecimal("125.00"));
        EnrollmentOrderParticipantEntity third = buildParticipant(3, 103L, new BigDecimal("125.00"));
        List<EnrollmentOrderParticipantEntity> participants = List.of(first, selected, third);

        CoursePackageEntity selectedPackage = new CoursePackageEntity();
        selectedPackage.setId(3202L);
        selectedPackage.setEnrollmentOrderId(2001L);
        selectedPackage.setStudentId(102L);
        selectedPackage.setPackageStatus("ACTIVE");
        selectedPackage.setReceivedAmount(new BigDecimal("125.00"));

        List<PaymentRecordEntity> payments = new ArrayList<>(List.of(
                buildPayment(1, new BigDecimal("125.00"), 1),
                buildPayment(2, new BigDecimal("125.00"), 2),
                buildPayment(3, new BigDecimal("125.00"), 3)
        ));
        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);
        when(enrollmentOrderParticipantMapper.selectOne(any())).thenReturn(selected);
        when(enrollmentOrderParticipantMapper.selectList(any())).thenReturn(participants);
        when(paymentRecordMapper.selectList(any())).thenAnswer(invocation -> payments);
        doAnswer(invocation -> {
            PaymentRecordEntity record = invocation.getArgument(0);
            record.setId(9002L);
            payments.add(record);
            return 1;
        }).when(paymentRecordMapper).insert(any(PaymentRecordEntity.class));
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(selectedPackage));
        when(receiptRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        com.classdeduction.system.modules.enrollment.dto.RefundRequest request =
                new com.classdeduction.system.modules.enrollment.dto.RefundRequest();
        request.setParticipantIndex(2);
        request.setRefundAmount(new BigDecimal("125.00"));
        request.setPaymentMethod("CASH");
        request.setHandledByName("Amy");

        enrollmentOrderService.refundOrder(2001L, request);

        verify(paymentRecordMapper).insert(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getParticipantIndex()).isEqualTo(2);
        assertThat(paymentCaptor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("-125.00"));
        assertThat(order.getReceivedAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(order.getBalanceAmount()).isEqualByComparingTo(new BigDecimal("125.00"));
        assertThat(order.getPaymentStatus()).isEqualTo("PARTIAL");
        assertThat(order.getOrderStatus()).isEqualTo("CONFIRMED");
        assertThat(selected.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(first.getPaidAmount()).isEqualByComparingTo(new BigDecimal("125.00"));
        assertThat(third.getPaidAmount()).isEqualByComparingTo(new BigDecimal("125.00"));
        assertThat(selectedPackage.getReceivedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(selectedPackage.getPackageStatus()).isEqualTo("CLOSED");
        verify(lessonOccurrenceMapper, never()).update(any());
    }

    @Test
    void courseRefundAtConsumedValueStopsOnlyThatClassEnrollment() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PAID");
        order.setReceivedAmount(new BigDecimal("2299.00"));
        order.setAmountDue(new BigDecimal("2299.00"));
        order.setStudentId(101L);
        order.setSignupDate(LocalDate.of(2026, 9, 1));
        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(4501L);
        item.setEnrollmentOrderId(order.getId());
        item.setClassId(401L);
        item.setCourseType("GROUP");
        item.setPackageUnit("SESSION");
        item.setPackageQuantity(new BigDecimal("16"));
        item.setPriceAmount(new BigDecimal("2299.00"));
        item.setDiscountAmount(BigDecimal.ZERO);
        item.setAmountDue(new BigDecimal("2299.00"));
        item.setPaidAmount(new BigDecimal("2299.00"));
        item.setItemStatus("ACTIVE");
        ClassEnrollmentEntity enrollment = new ClassEnrollmentEntity();
        enrollment.setId(5001L);
        enrollment.setEnrollmentOrderItemId(item.getId());
        enrollment.setClassId(401L);
        enrollment.setStudentId(101L);
        enrollment.setEnrollmentDate(LocalDate.of(2026, 9, 1));
        enrollment.setExpiryDate(LocalDate.of(2026, 12, 1));
        enrollment.setBillingType("PER_SESSION");
        enrollment.setPurchasedQuantity(16);
        enrollment.setStatus("ACTIVE");
        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setId(6001L);
        occurrence.setClassGroupId(401L);
        occurrence.setPlannedStartAt(LocalDateTime.of(2026, 9, 12, 10, 0));
        LessonRecordEntity usedLesson = new LessonRecordEntity();
        usedLesson.setOccurrenceId(6001L);
        usedLesson.setStudentId(101L);
        usedLesson.setResultStatus("COMPLETED");
        usedLesson.setConfirmStage("CONFIRMED");
        PaymentAllocationEntity originalAllocation = new PaymentAllocationEntity();
        originalAllocation.setEnrollmentOrderItemId(item.getId());
        originalAllocation.setAmount(new BigDecimal("2299.00"));

        when(enrollmentOrderMapper.selectById(order.getId())).thenReturn(order);
        when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);
        when(enrollmentOrderItemMapper.selectOne(any())).thenReturn(item);
        when(enrollmentOrderItemMapper.selectById(item.getId())).thenReturn(item);
        when(paymentAllocationMapper.selectList(any())).thenReturn(List.of(originalAllocation));
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(occurrence));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(usedLesson));
        when(receiptRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        com.classdeduction.system.modules.enrollment.dto.RefundRequest request =
                new com.classdeduction.system.modules.enrollment.dto.RefundRequest();
        request.setOrderItemId(item.getId());
        request.setRefundAmount(new BigDecimal("2155.31"));
        request.setPaymentMethod("CASH");
        request.setHandledByName("Amy");

        enrollmentOrderService.refundOrder(order.getId(), request);

        verify(classEnrollmentMapper).updateById(classEnrollmentCaptor.capture());
        assertThat(classEnrollmentCaptor.getValue().getStatus()).isEqualTo("WITHDRAWN");
        verify(enrollmentOrderItemMapper).updateById(item);
        assertThat(item.getItemStatus()).isEqualTo("STOPPED");
        assertThat(item.getStoppedSessionCount()).isEqualTo(1);
        assertThat(item.getStoppedConsumedAmount()).isEqualByComparingTo("143.69");
        assertThat(item.getStoppedPeriodStartDate()).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(item.getStoppedPeriodEndDate()).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(item.getAmountDue()).isEqualByComparingTo("143.69");
        assertThat(order.getAmountDue()).isEqualByComparingTo("143.69");
        verify(lessonOccurrenceMapper, never()).update(any());
        verify(paymentAllocationService).recordRefundAllocation(any(PaymentRecordEntity.class),
                org.mockito.ArgumentMatchers.eq(order.getId()), org.mockito.ArgumentMatchers.eq(item.getId()),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("2155.31")));
    }

    @Test
    void shouldRejectFullRefundWhenLessonsAreRecorded() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PAID");
        order.setReceivedAmount(new BigDecimal("1100.00"));

        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(3001L);
        coursePackage.setEnrollmentOrderId(2001L);

        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonRecordMapper.selectCount(any())).thenReturn(1L);

        com.classdeduction.system.modules.enrollment.dto.RefundRequest request =
                new com.classdeduction.system.modules.enrollment.dto.RefundRequest();
        request.setRefundAmount(new BigDecimal("1100.00"));
        request.setPaymentMethod("CASH");
        request.setHandledByName("Amy");

        assertThatThrownBy(() -> enrollmentOrderService.refundOrder(2001L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Full refund is unavailable after lessons are recorded. Contact staff for manual review.");

        verify(paymentRecordMapper, never()).insert(any(PaymentRecordEntity.class));
        verify(enrollmentOrderMapper, never()).updateById(any(EnrollmentOrderEntity.class));
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
        request.setRefundAmount(new BigDecimal("1.00"));
        request.setPaymentMethod("CASH");
        request.setHandledByName("Amy");

        assertThatThrownBy(() -> enrollmentOrderService.refundOrder(2002L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("There is no received payment to refund.");
    }

    @Test
    void shouldPartiallyRefundWithoutCancellingEnrollmentArtifacts() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PAID");
        order.setOriginalAmount(new BigDecimal("1100.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setAmountDue(new BigDecimal("1100.00"));
        order.setReceivedAmount(new BigDecimal("1100.00"));
        order.setBalanceAmount(BigDecimal.ZERO);
        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);

        com.classdeduction.system.modules.enrollment.dto.RefundRequest request =
                new com.classdeduction.system.modules.enrollment.dto.RefundRequest();
        request.setRefundAmount(new BigDecimal("200.00"));
        request.setPaymentMethod("CASH");
        request.setHandledByName("Amy");
        request.setRemark("Discount adjustment");

        enrollmentOrderService.refundOrder(2001L, request);

        verify(paymentRecordMapper).insert(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("-200.00"));
        verify(enrollmentOrderMapper).updateById(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getOrderStatus()).isEqualTo("CONFIRMED");
        assertThat(orderCaptor.getValue().getPaymentStatus()).isEqualTo("PARTIAL");
        assertThat(orderCaptor.getValue().getReceivedAmount()).isEqualByComparingTo(new BigDecimal("900.00"));
        assertThat(orderCaptor.getValue().getBalanceAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        verify(coursePackageMapper, never()).selectList(any());
        verify(classEnrollmentMapper, never()).update(any());
        verify(lessonOccurrenceMapper, never()).update(any());
    }

    @Test
    void shouldRejectRefundGreaterThanCurrentReceivedAmount() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PAID");
        order.setReceivedAmount(new BigDecimal("100.00"));
        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);

        com.classdeduction.system.modules.enrollment.dto.RefundRequest request =
                new com.classdeduction.system.modules.enrollment.dto.RefundRequest();
        request.setRefundAmount(new BigDecimal("100.01"));
        request.setPaymentMethod("CASH");
        request.setHandledByName("Amy");

        assertThatThrownBy(() -> enrollmentOrderService.refundOrder(2001L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("REFUND_AMOUNT_EXCEEDS_RECEIVED");

        verify(paymentRecordMapper, never()).insert(any(PaymentRecordEntity.class));
        verify(enrollmentOrderMapper, never()).updateById(any(EnrollmentOrderEntity.class));
    }

    @Test
    void shouldReturnReceiptPayloadInOrderDetailReceipts() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setEnrollmentNo("EO202607150001");
        order.setStudentId(101L);
        order.setOrderStatus("CONFIRMED");
        order.setCurrencyCode("USD");
        order.setOriginalAmount(new BigDecimal("1200.00"));
        order.setDiscountAmount(new BigDecimal("100.00"));
        order.setReceivedAmount(new BigDecimal("1100.00"));
        order.setPaymentStatus("PAID");

        ReceiptRecordEntity receipt = new ReceiptRecordEntity();
        receipt.setId(6001L);
        receipt.setReceiptNo("RCP202607150001");
        receipt.setEnrollmentOrderId(2001L);
        receipt.setStudentId(101L);
        receipt.setTemplateCode("DEFAULT_RECEIPT");
        receipt.setReceiptPayload("{\"receiptNo\":\"RCP202607150001\",\"studentName\":\"Olivia Lin\"}");

        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(studentMapper.selectById(101L)).thenReturn(buildStudent());
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of());
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of());
        when(receiptRecordMapper.selectList(any())).thenReturn(List.of(receipt));
        when(coursePackageMapper.selectList(any())).thenReturn(List.of());

        EnrollmentOrderDetailVO result = enrollmentOrderService.getOrder(2001L);

        assertThat(result.receipts()).singleElement()
                .extracting(ReceiptRecordVO::receiptPayload)
                .isEqualTo(receipt.getReceiptPayload());
    }

    private void stubGeneratedIds(boolean includePaymentInsert) {
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

    private EnrollmentOrderParticipantEntity buildParticipant(int index, Long studentId, BigDecimal amount) {
        EnrollmentOrderParticipantEntity participant = new EnrollmentOrderParticipantEntity();
        participant.setEnrollmentOrderId(2001L);
        participant.setParticipantIndex(index);
        participant.setStudentId(studentId);
        participant.setAmountDue(amount);
        participant.setPaidAmount(amount);
        participant.setBalanceAmount(BigDecimal.ZERO);
        return participant;
    }

    private PaymentRecordEntity buildPayment(long id, BigDecimal amount, int participantIndex) {
        PaymentRecordEntity payment = new PaymentRecordEntity();
        payment.setId(id);
        payment.setEnrollmentOrderId(2001L);
        payment.setParticipantIndex(participantIndex);
        payment.setPaymentType("PAYMENT");
        payment.setPaymentStatus("PAID");
        payment.setAmount(amount);
        return payment;
    }

    private EnrollmentCreateRequest buildCreateRequest() {
        EnrollmentCreateRequest request = new EnrollmentCreateRequest();
        request.setStudentId(101L);
        request.setCourseProductId(201L);
        request.setCashierUserId(401L);
        request.setHandledByName("Amy");
        request.setCurrencyCode("USD");
        request.setPackageName("SHSAT Math Premium");
        request.setStartDate(LocalDate.of(2026, 4, 7));
        request.setEstimatedEndDate(LocalDate.of(2026, 7, 7));
        request.setPaidAt(LocalDateTime.of(2026, 4, 7, 10, 0));
        request.setIssueReceipt(true);
        request.setTemplateCode("DEFAULT_RECEIPT");
        request.setOriginalAmount(new BigDecimal("1200.00"));
        request.setDiscountAmount(new BigDecimal("100.00"));
        request.setReceivedAmount(new BigDecimal("1100.00"));
        request.setTotalUnits(80);
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

    private TeacherEntity buildTeacher() {
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(301L);
        teacher.setTeacherNo("T-301");
        teacher.setTeacherName("Emma Wang");
        return teacher;
    }
}
