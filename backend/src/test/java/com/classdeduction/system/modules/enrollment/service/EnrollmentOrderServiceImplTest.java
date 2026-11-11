package com.classdeduction.system.modules.enrollment.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.GroupCourseProjectEntity;
import com.classdeduction.system.modules.base.entity.GroupRegistrationPlanEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.GroupCourseProjectMapper;
import com.classdeduction.system.modules.base.mapper.GroupRegistrationPlanMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderUpdateRequest;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentCreateRequest;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderCorrectionRequest;
import com.classdeduction.system.modules.enrollment.dto.RegistrationIntakeRequest;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentCorrectionLogEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderParticipantEntity;
import com.classdeduction.system.modules.enrollment.entity.PackageTeacherAssignmentEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentAllocationEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentRecordEntity;
import com.classdeduction.system.modules.enrollment.entity.ReceiptRecordEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentCorrectionLogMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderParticipantMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentMethodMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentRecordMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMemberMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMapper;
import com.classdeduction.system.modules.enrollment.mapper.ReceiptRecordMapper;
import com.classdeduction.system.modules.enrollment.service.impl.ClassBillingServiceImpl;
import com.classdeduction.system.modules.enrollment.service.impl.EnrollmentOrderServiceImpl;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentCreateResultVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentOrderDetailVO;
import com.classdeduction.system.modules.enrollment.vo.ReceiptRecordVO;
import com.classdeduction.system.modules.finance.service.StudentCreditService;
import com.classdeduction.system.modules.lesson.entity.HourLedgerEntity;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.HourLedgerMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.lesson.mapper.PackageBalanceSnapshotMapper;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.entity.ClassHolidayEntity;
import com.classdeduction.system.modules.schedule.entity.LessonAdjustmentLogEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassHolidayMapper;
import com.classdeduction.system.modules.schedule.service.ClassAdjustedSlotReservations;
import com.classdeduction.system.modules.schedule.mapper.LessonAdjustmentLogMapper;
import com.classdeduction.system.modules.schedule.vo.ClassHolidayVO;
import com.classdeduction.system.modules.systemconfig.entity.NotificationLogEntity;
import com.classdeduction.system.modules.systemconfig.mapper.NotificationLogMapper;
import com.fasterxml.jackson.core.type.TypeReference;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;
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
    private com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderCourseRefundStateMapper enrollmentOrderCourseRefundStateMapper;

    @Mock
    private CoursePackageMapper coursePackageMapper;

    @Mock
    private PaymentRecordMapper paymentRecordMapper;

    private com.classdeduction.system.modules.enrollment.mapper.PaymentAllocationMapper paymentAllocationMapper;

    private com.classdeduction.system.modules.enrollment.service.allocation.PaymentAllocationService paymentAllocationService;

    @Mock
    private PrivateLessonGroupMemberMapper privateLessonGroupMemberMapper;
    @Mock
    private PrivateLessonGroupMapper privateLessonGroupMapper;

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

    @Mock
    private StudentCreditService studentCreditService;

    // 直接用真实实现，让测试负载与生产一致的到期日计算逻辑。
    private ClassBillingServiceImpl classBillingService;

    @Mock
    private com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper recurringScheduleRuleMapper;

    @Mock
    private com.classdeduction.system.modules.schedule.mapper.RecurringScheduleStartingBalanceMapper recurringScheduleStartingBalanceMapper;

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

    @Mock
    private ClassAdjustedSlotReservations adjustedSlotReservations;

    @Mock
    private ClassHolidayMapper classHolidayMapper;

    @Mock
    private GroupCourseProjectMapper groupCourseProjectMapper;

    @Mock
    private GroupRegistrationPlanMapper groupRegistrationPlanMapper;

    @Mock
    private com.classdeduction.system.modules.schedule.service.ClassHolidayService classHolidayService;

    @Mock
    private com.classdeduction.system.modules.finance.mapper.StudentCreditLedgerMapper studentCreditLedgerMapper;

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
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), PaymentAllocationEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ReceiptRecordEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""),
                com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupMemberEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), EnrollmentOrderItemEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""),
                com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderCourseRefundStateEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), LessonRecordEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), HourLedgerEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), LessonAdjustmentLogEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), NotificationLogEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RecurringScheduleRuleEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RecurringScheduleSlotEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), GroupCourseProjectEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), GroupRegistrationPlanEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ClassHolidayEntity.class);
    }

    @BeforeEach
    void setUp() {
        lenient().when(enrollmentOrderMapper.selectByIdForUpdate(any()))
                .thenAnswer(invocation -> enrollmentOrderMapper.selectById(invocation.getArgument(0)));
        lenient().when(enrollmentOrderItemMapper.selectByOrderIdForUpdate(any()))
                .thenAnswer(invocation -> enrollmentOrderItemMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>()));
        lenient().when(classGroupMapper.selectByIdForUpdate(any()))
                .thenAnswer(invocation -> classGroupMapper.selectById(invocation.getArgument(0)));
        lenient().when(adjustedSlotReservations.load(any())).thenReturn(Set.of());
        lenient().when(groupCourseProjectMapper.selectByIdForUpdate(any()))
                .thenAnswer(invocation -> groupCourseProjectMapper.selectById(invocation.getArgument(0)));
        lenient().when(groupRegistrationPlanMapper.selectByIdForUpdate(any()))
                .thenAnswer(invocation -> groupRegistrationPlanMapper.selectById(invocation.getArgument(0)));
        lenient().when(courseProductMapper.selectByIdForUpdate(any()))
                .thenAnswer(invocation -> courseProductMapper.selectById(invocation.getArgument(0)));
        lenient().when(studentMapper.selectByIdForUpdate(any())).thenAnswer(invocation -> {
            StudentEntity student = new StudentEntity();
            student.setId(invocation.getArgument(0));
            return student;
        });
        classBillingService = new ClassBillingServiceImpl(classGroupScheduleSlotMapper, classHolidayService);
        paymentAllocationService = org.mockito.Mockito.mock(com.classdeduction.system.modules.enrollment.service.allocation.PaymentAllocationService.class);
        paymentAllocationMapper = org.mockito.Mockito.mock(com.classdeduction.system.modules.enrollment.mapper.PaymentAllocationMapper.class);
        enrollmentOrderService = new EnrollmentOrderServiceImpl(
                enrollmentOrderMapper,
                enrollmentOrderItemMapper,
                enrollmentOrderParticipantMapper,
                enrollmentOrderCourseRefundStateMapper,
                coursePackageMapper,
                paymentRecordMapper,
                paymentAllocationMapper,
                paymentAllocationService,
                privateLessonGroupMemberMapper,
                privateLessonGroupMapper,
                receiptRecordMapper,
                classBillingService,
                packageTeacherAssignmentMapper,
                studentMapper,
                courseProductMapper,
                teacherMapper,
                jsonHelper,
                recurringScheduleRuleMapper,
                recurringScheduleStartingBalanceMapper,
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
                adjustedSlotReservations,
                classHolidayMapper,
                groupCourseProjectMapper,
                groupRegistrationPlanMapper,
                paymentMethodMapper,
                classHolidayService,
                studentCreditLedgerMapper
        );
        ReflectionTestUtils.setField(enrollmentOrderService, "studentCreditService", studentCreditService);
        lenient().when(enrollmentOrderItemMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(enrollmentOrderParticipantMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(privateLessonGroupMemberMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(paymentMethodMapper.selectCount(any())).thenReturn(1L);
    }

    @Test
    void shouldUndoLegacyRefundWithoutSnapshotByRebuildingPreRefundState() throws Exception {
        // 撤销功能上线之前的历史退款没有内嵌快照；撤销应按当前状态反推退款前状态并恢复。
        RefundUndoFixture fixture = stubLegacyRefundWithoutSnapshot();
        EnrollmentCorrectionLogMapper correctionLogMapper =
                org.mockito.Mockito.mock(EnrollmentCorrectionLogMapper.class);
        ReflectionTestUtils.setField(enrollmentOrderService, "correctionLogMapper", correctionLogMapper);

        enrollmentOrderService.deleteRefundReceipt(7001L, null);

        // 订单收款按退款额回加并重算付款状态（1000 / 1100 → PARTIAL）。
        ArgumentCaptor<EnrollmentOrderEntity> restoredOrder = ArgumentCaptor.forClass(EnrollmentOrderEntity.class);
        verify(enrollmentOrderMapper).updateById(restoredOrder.capture());
        assertThat(restoredOrder.getValue().getPaymentStatus()).isEqualTo("PARTIAL");
        assertThat(restoredOrder.getValue().getReceivedAmount()).isEqualByComparingTo("1000.00");

        // 课程行金额回加（800 + 100），退款造成的停课状态恢复为 ACTIVE。
        ArgumentCaptor<EnrollmentOrderItemEntity> restoredItem = ArgumentCaptor.forClass(EnrollmentOrderItemEntity.class);
        verify(enrollmentOrderItemMapper).updateById(restoredItem.capture());
        assertThat(restoredItem.getValue().getItemStatus()).isEqualTo("ACTIVE");
        assertThat(restoredItem.getValue().getPaidAmount()).isEqualByComparingTo("900.00");

        // 课包从 CLOSED 恢复为 ACTIVE，收款回加（900 + 100）。
        ArgumentCaptor<CoursePackageEntity> restoredPackage = ArgumentCaptor.forClass(CoursePackageEntity.class);
        verify(coursePackageMapper).updateById(restoredPackage.capture());
        assertThat(restoredPackage.getValue().getPackageStatus()).isEqualTo("ACTIVE");
        assertThat(restoredPackage.getValue().getReceivedAmount()).isEqualByComparingTo("1000.00");

        // 未来课次恢复为待上课。
        ArgumentCaptor<LessonOccurrenceEntity> restoredOccurrence =
                ArgumentCaptor.forClass(LessonOccurrenceEntity.class);
        verify(lessonOccurrenceMapper).updateById(restoredOccurrence.capture());
        assertThat(restoredOccurrence.getValue().getPlanningStatus()).isEqualTo("SCHEDULED");

        // 退款生成的负向分摊被删除，退款流水与收据作废。
        verify(paymentAllocationMapper).deleteById(9001L);
        verify(paymentRecordMapper).updateById(fixture.refund);
        assertThat(fixture.refund.getPaymentStatus()).isEqualTo("VOIDED");
        verify(paymentRecordMapper).deleteById(5001L);
        verify(receiptRecordMapper).deleteById(7001L);
    }

    @Test
    void shouldPreviewLegacyRefundWithoutSnapshotAsUndoable() throws Exception {
        // 预览必须与执行结论一致：历史无快照退款可自动撤销。
        stubLegacyRefundWithoutSnapshot();

        assertThat(enrollmentOrderService.evaluateRefundUndo(7001L)).isNull();
        verify(paymentRecordMapper, never()).deleteById(5001L);
        verify(receiptRecordMapper, never()).deleteById(7001L);
    }

    @Test
    void shouldRejectLegacyRefundUndoWhenLessonsWereConfirmedAfterRefund() throws Exception {
        // 重建路径不跳过硬性安全检查：退款后已消课的历史退款仍然拒绝自动撤销。
        stubLegacyRefundWithoutSnapshot();
        lenient().when(lessonRecordMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> enrollmentOrderService.deleteRefundReceipt(7001L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("REFUND_UNDO_ACTIVITY");
        verify(paymentRecordMapper, never()).deleteById(5001L);
        verify(receiptRecordMapper, never()).deleteById(7001L);
    }

    @Test
    void shouldRejectLegacyRefundUndoWhenRestoringOccurrenceWouldConflict() throws Exception {
        // 重建路径保留排课冲突检查。
        RefundUndoFixture fixture = stubLegacyRefundWithoutSnapshot();
        lenient().when(lessonOccurrenceMapper.selectList(any())).thenAnswer(invocation -> {
            Wrapper<?> wrapper = invocation.getArgument(0);
            if (wrapper == null) {
                return Collections.emptyList();
            }
            return queryUsesValue(wrapper, 4001L)
                    ? List.of(fixture.occurrenceAfter)
                    : List.of(new LessonOccurrenceEntity());
        });

        assertThatThrownBy(() -> enrollmentOrderService.deleteRefundReceipt(7001L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("REFUND_UNDO_SCHEDULE_CONFLICT");
        verify(paymentRecordMapper, never()).deleteById(5001L);
    }

    /**
     * 构造一笔缺少快照的历史行级退款：数据库当前处于退款后状态
     * （订单收款 900、课程行停课 800、课包 CLOSED、未来课次 CANCELLED），
     * 收据 payload 只有 orderItemId / participantIndex，没有 refundUndoSnapshot。
     */
    private RefundUndoFixture stubLegacyRefundWithoutSnapshot() throws Exception {
        RefundUndoFixture fixture = new RefundUndoFixture();
        // 当前（退款后）状态。
        fixture.orderAfter = refundOrderEntity(2001L, "PARTIAL", "900.00");
        fixture.itemAfter = refundItemEntity("STOPPED", "800.00");
        fixture.packageAfter = refundPackageEntity(0, "CLOSED");
        fixture.packageAfter.setOriginalAmount(new BigDecimal("1100.00"));
        fixture.packageAfter.setDiscountAmount(BigDecimal.ZERO);
        fixture.packageAfter.setReceivedAmount(new BigDecimal("900.00"));
        LocalDateTime futureStart = LocalDateTime.now().withNano(0).plusDays(3);
        fixture.occurrenceAfter = refundOccurrenceEntity("CANCELLED", futureStart);
        fixture.participant = refundParticipantEntity();
        fixture.refund = refundPaymentEntity(futureStart.minusDays(5));
        fixture.originalAllocation = refundAllocationEntity(9000L, "1000.00");
        fixture.refundAllocation = refundAllocationEntity(9001L, "-100.00");

        ReceiptRecordEntity receipt = new ReceiptRecordEntity();
        receipt.setId(7001L);
        receipt.setReceiptNo("RCP-REFUND-7001");
        receipt.setEnrollmentOrderId(2001L);
        receipt.setStudentId(101L);
        receipt.setPaymentRecordId(5001L);
        receipt.setTemplateCode("REFUND_RECEIPT");
        receipt.setReceiptPayload("{}");
        fixture.receipt = receipt;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderItemId", 3001L);
        payload.put("participantIndex", 1);

        lenient().when(receiptRecordMapper.selectById(7001L)).thenReturn(receipt);
        lenient().when(enrollmentOrderMapper.selectById(2001L)).thenReturn(fixture.orderAfter);
        lenient().when(paymentRecordMapper.selectById(5001L)).thenReturn(fixture.refund);
        lenient().when(enrollmentOrderItemMapper.selectOne(any())).thenReturn(fixture.itemAfter);
        lenient().when(enrollmentOrderParticipantMapper.selectOne(any())).thenReturn(fixture.participant);
        lenient().when(paymentAllocationMapper.selectList(any())).thenAnswer(invocation -> {
            Wrapper<?> wrapper = invocation.getArgument(0);
            if (wrapper == null) {
                return Collections.emptyList();
            }
            return queryUsesValue(wrapper, 5001L)
                    ? List.of(fixture.refundAllocation)
                    : List.of(fixture.originalAllocation, fixture.refundAllocation);
        });

        // captureRefundState 采集当前（退款后）状态。
        lenient().when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(fixture.itemAfter));
        lenient().when(enrollmentOrderParticipantMapper.selectList(any())).thenReturn(List.of(fixture.participant));
        lenient().when(coursePackageMapper.selectList(any())).thenReturn(List.of(fixture.packageAfter));
        lenient().when(enrollmentOrderCourseRefundStateMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(paymentRecordMapper.selectList(any())).thenReturn(List.of(fixture.refund));
        lenient().when(recurringScheduleRuleMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(classEnrollmentMapper.selectRefundScopeIncludingDeleted(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(lessonOccurrenceMapper.selectList(any())).thenAnswer(invocation -> {
            Wrapper<?> wrapper = invocation.getArgument(0);
            if (wrapper == null) {
                return Collections.emptyList();
            }
            return queryUsesValue(wrapper, 4001L) ? List.of(fixture.occurrenceAfter) : Collections.emptyList();
        });
        lenient().when(jsonHelper.fromJson(any(String.class), any(TypeReference.class)))
                .thenReturn(payload);

        // 恢复写入与删除返回成功。
        lenient().when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);
        lenient().when(enrollmentOrderItemMapper.updateById(any(EnrollmentOrderItemEntity.class))).thenReturn(1);
        lenient().when(enrollmentOrderParticipantMapper.updateById(any(EnrollmentOrderParticipantEntity.class))).thenReturn(1);
        lenient().when(coursePackageMapper.updateById(any(CoursePackageEntity.class))).thenReturn(1);
        lenient().when(lessonOccurrenceMapper.updateById(any(LessonOccurrenceEntity.class))).thenReturn(1);
        lenient().when(enrollmentOrderItemMapper.selectById(3001L)).thenReturn(fixture.itemAfter);
        lenient().when(enrollmentOrderParticipantMapper.selectById(20001L)).thenReturn(fixture.participant);
        lenient().when(coursePackageMapper.selectById(4001L)).thenReturn(fixture.packageAfter);
        lenient().when(lessonOccurrenceMapper.selectById(6001L)).thenReturn(fixture.occurrenceAfter);
        lenient().when(paymentAllocationMapper.selectById(9000L)).thenReturn(fixture.originalAllocation);
        lenient().when(paymentAllocationMapper.selectById(9001L)).thenReturn(fixture.refundAllocation);
        lenient().when(paymentAllocationMapper.deleteById(9001L)).thenReturn(1);
        lenient().when(paymentRecordMapper.deleteById(5001L)).thenReturn(1);
        lenient().when(receiptRecordMapper.deleteById(7001L)).thenReturn(1);
        return fixture;
    }

    @Test
    void shouldUndoOnlyTheSelectedLineRefundAndRestoreRefundedState() throws Exception {
        RefundUndoFixture fixture = stubUndoableLineRefund();
        EnrollmentCorrectionLogMapper correctionLogMapper =
                org.mockito.Mockito.mock(EnrollmentCorrectionLogMapper.class);
        ReflectionTestUtils.setField(enrollmentOrderService, "correctionLogMapper", correctionLogMapper);

        enrollmentOrderService.deleteRefundReceipt(7001L, null);

        // 订单恢复到退款前的收款状态。
        ArgumentCaptor<EnrollmentOrderEntity> restoredOrder = ArgumentCaptor.forClass(EnrollmentOrderEntity.class);
        verify(enrollmentOrderMapper).updateById(restoredOrder.capture());
        assertThat(restoredOrder.getValue().getPaymentStatus()).isEqualTo("PAID");
        assertThat(restoredOrder.getValue().getReceivedAmount()).isEqualByComparingTo("1100.00");

        // 课程购买记录恢复为可继续上课。
        ArgumentCaptor<EnrollmentOrderItemEntity> restoredItem = ArgumentCaptor.forClass(EnrollmentOrderItemEntity.class);
        verify(enrollmentOrderItemMapper).updateById(restoredItem.capture());
        assertThat(restoredItem.getValue().getItemStatus()).isEqualTo("ACTIVE");
        assertThat(restoredItem.getValue().getPaidAmount()).isEqualByComparingTo("1000.00");

        // 课包与未来课次一并恢复。
        ArgumentCaptor<CoursePackageEntity> restoredPackage = ArgumentCaptor.forClass(CoursePackageEntity.class);
        verify(coursePackageMapper).updateById(restoredPackage.capture());
        assertThat(restoredPackage.getValue().getRemainingUnits()).isEqualTo(2);
        assertThat(restoredPackage.getValue().getPackageStatus()).isEqualTo("ACTIVE");
        ArgumentCaptor<LessonOccurrenceEntity> restoredOccurrence =
                ArgumentCaptor.forClass(LessonOccurrenceEntity.class);
        verify(lessonOccurrenceMapper).updateById(restoredOccurrence.capture());
        assertThat(restoredOccurrence.getValue().getPlanningStatus()).isEqualTo("SCHEDULED");

        // 退款自身生成的负向分摊被删除，退款流水与收据同时作废。
        verify(paymentAllocationMapper).deleteById(9001L);
        verify(paymentRecordMapper).updateById(fixture.refund);
        assertThat(fixture.refund.getPaymentStatus()).isEqualTo("VOIDED");
        verify(paymentRecordMapper).deleteById(5001L);
        verify(receiptRecordMapper).deleteById(7001L);

        ArgumentCaptor<EnrollmentCorrectionLogEntity> auditCaptor =
                ArgumentCaptor.forClass(EnrollmentCorrectionLogEntity.class);
        verify(correctionLogMapper).insert(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getOperationCode()).isEqualTo("UNDO_REFUND_RECEIPT");
        assertThat(auditCaptor.getValue().getBizId()).isEqualTo(7001L);
    }

    @Test
    void shouldRejectRefundUndoWhenStateChangedAfterRefund() throws Exception {
        stubUndoableLineRefund();
        // 退款之后又发生了一次收款，当前订单与退款收据中的退款后快照不再一致。
        when(enrollmentOrderMapper.selectById(2001L))
                .thenReturn(refundOrderEntity(2001L, "PAID", "950.00"));

        assertThatThrownBy(() -> enrollmentOrderService.deleteRefundReceipt(7001L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("REFUND_UNDO_STATE_CHANGED");
        verify(paymentRecordMapper, never()).deleteById(5001L);
        verify(receiptRecordMapper, never()).deleteById(7001L);
        verify(paymentAllocationMapper, never()).deleteById(9001L);
    }

    @Test
    void shouldRejectRefundUndoWhenRefundSpansMultipleCourseLines() throws Exception {
        RefundUndoFixture fixture = stubUndoableLineRefund();
        // 同一笔退款分摊到两门课程时属于跨行退款，不能按行撤销。
        lenient().when(paymentAllocationMapper.selectList(any())).thenAnswer(invocation -> {
            Wrapper<?> wrapper = invocation.getArgument(0);
            if (wrapper == null) {
                return Collections.emptyList();
            }
            return queryUsesValue(wrapper, 5001L)
                    ? List.of(fixture.refundAllocation, fixture.refundAllocation)
                    : List.of(fixture.originalAllocation, fixture.refundAllocation);
        });

        assertThatThrownBy(() -> enrollmentOrderService.deleteRefundReceipt(7001L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("REFUND_UNDO_LINE_AMBIGUOUS");
        verify(paymentRecordMapper, never()).deleteById(5001L);
    }

    @Test
    void shouldRejectRefundUndoWhenLessonsWereConfirmedAfterRefund() throws Exception {
        stubUndoableLineRefund();
        lenient().when(lessonRecordMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> enrollmentOrderService.deleteRefundReceipt(7001L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("REFUND_UNDO_ACTIVITY");
        verify(paymentRecordMapper, never()).deleteById(5001L);
        verify(receiptRecordMapper, never()).deleteById(7001L);
    }

    @Test
    void shouldRejectRefundUndoWhenRestoringOccurrenceWouldConflict() throws Exception {
        RefundUndoFixture fixture = stubUndoableLineRefund();
        // 恢复课次所需的时间段已经被另一节课占用。
        lenient().when(lessonOccurrenceMapper.selectList(any())).thenAnswer(invocation -> {
            Wrapper<?> wrapper = invocation.getArgument(0);
            if (wrapper == null) {
                return Collections.emptyList();
            }
            return queryUsesValue(wrapper, 4001L)
                    ? List.of(fixture.occurrenceAfter)
                    : List.of(new LessonOccurrenceEntity());
        });

        assertThatThrownBy(() -> enrollmentOrderService.deleteRefundReceipt(7001L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("REFUND_UNDO_SCHEDULE_CONFLICT");
        verify(paymentRecordMapper, never()).deleteById(5001L);
        verify(receiptRecordMapper, never()).deleteById(7001L);
    }

    @Test
    void shouldPreviewUnchangedLineRefundAsUndoable() throws Exception {
        stubUndoableLineRefund();

        assertThat(enrollmentOrderService.evaluateRefundUndo(7001L)).isNull();
        // 预览只读，不得改动任何财务或课程记录。
        verify(paymentRecordMapper, never()).deleteById(5001L);
        verify(receiptRecordMapper, never()).deleteById(7001L);
        verify(enrollmentOrderMapper, never()).updateById(any(EnrollmentOrderEntity.class));
    }

    @Test
    void shouldUndoRefundDaysLaterWithoutRevivingAlreadyElapsedOccurrence() throws Exception {
        // 退款发生在一周前，被取消的课次日期早已过去：撤销应恢复课程与课包余额，
        // 但已过去的课次保持取消，不能重新变成待上。
        stubUndoableLineRefund(LocalDateTime.now().withNano(0).minusDays(2));

        enrollmentOrderService.deleteRefundReceipt(7001L, null);

        // 财务与课包按退款前状态恢复。
        ArgumentCaptor<EnrollmentOrderEntity> restoredOrder = ArgumentCaptor.forClass(EnrollmentOrderEntity.class);
        verify(enrollmentOrderMapper).updateById(restoredOrder.capture());
        assertThat(restoredOrder.getValue().getReceivedAmount()).isEqualByComparingTo("1100.00");
        ArgumentCaptor<CoursePackageEntity> restoredPackage = ArgumentCaptor.forClass(CoursePackageEntity.class);
        verify(coursePackageMapper).updateById(restoredPackage.capture());
        assertThat(restoredPackage.getValue().getRemainingUnits()).isEqualTo(2);
        assertThat(restoredPackage.getValue().getPackageStatus()).isEqualTo("ACTIVE");

        // 关键断言：过期课次不被复活。
        verify(lessonOccurrenceMapper, never()).updateById(any(LessonOccurrenceEntity.class));

        // 撤销本身成功并留痕。
        verify(paymentRecordMapper).deleteById(5001L);
        verify(receiptRecordMapper).deleteById(7001L);
    }

    @Test
    void shouldPreviewRefundWithElapsedOccurrenceAsUndoable() throws Exception {
        // 预览与执行必须给出一致结论：过期课次不复活，但整笔撤销仍可执行。
        stubUndoableLineRefund(LocalDateTime.now().withNano(0).minusDays(2));

        assertThat(enrollmentOrderService.evaluateRefundUndo(7001L)).isNull();
        verify(paymentRecordMapper, never()).deleteById(5001L);
        verify(lessonOccurrenceMapper, never()).updateById(any(LessonOccurrenceEntity.class));
    }

    @Test
    void shouldReviveOccurrenceStartingJustAfterUndoMoment() throws Exception {
        // 时间边界：课次在撤销提交后 1 分钟开始，仍属于未来，必须复活。
        stubUndoableLineRefund(LocalDateTime.now().withNano(0).plusMinutes(1));

        enrollmentOrderService.deleteRefundReceipt(7001L, null);

        ArgumentCaptor<LessonOccurrenceEntity> restoredOccurrence =
                ArgumentCaptor.forClass(LessonOccurrenceEntity.class);
        verify(lessonOccurrenceMapper).updateById(restoredOccurrence.capture());
        assertThat(restoredOccurrence.getValue().getPlanningStatus()).isEqualTo("SCHEDULED");
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
        verify(courseProductMapper).selectByIdForUpdate(201L);
        verify(coursePackageMapper).insert(packageCaptor.capture());
        verify(paymentRecordMapper).insert(paymentCaptor.capture());
        verify(receiptRecordMapper).insert(receiptCaptor.capture());

        EnrollmentOrderEntity order = orderCaptor.getValue();
        CoursePackageEntity coursePackage = packageCaptor.getValue();
        PaymentRecordEntity payment = paymentCaptor.getValue();
        ReceiptRecordEntity receipt = receiptCaptor.getValue();

        assertThat(order.getEnrollmentNo()).startsWith("EO");
        assertThat(order.getBillingPolicy()).isEqualTo("WHOLE_DOLLAR_V1");
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
    void shouldLockAndReloadMonthlyClassBeforeRenewingOccurrences() {
        LocalDate startDate = LocalDate.now(com.classdeduction.system.common.time.AppTime.APP_ZONE_ID).plusDays(1);
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(901L);
        classGroup.setStatus("ACTIVE");
        classGroup.setBillingType("MONTHLY");
        classGroup.setStartMonth(startDate);
        classGroup.setEndMonth(startDate.plusMonths(1));
        classGroup.setStartDate(startDate);
        classGroup.setEndDate(startDate.plusDays(7));

        when(classGroupMapper.selectList(any())).thenReturn(List.of(classGroup));
        when(classGroupMapper.selectById(901L)).thenReturn(classGroup);
        when(classHolidayService.listHolidays((Long) null)).thenReturn(List.of());
        when(classGroupScheduleSlotMapper.selectList(any())).thenReturn(List.of());

        int inserted = enrollmentOrderService.renewActiveClassOccurrences();

        assertThat(inserted).isZero();
        verify(classGroupMapper).selectByIdForUpdate(901L);
        verify(classGroupScheduleSlotMapper).selectList(any());
    }

    @Test
    void shouldRenewOnlyOccurrencesInsideSegmentedSchedulePeriods() {
        LocalDate firstMonday = LocalDate.now(com.classdeduction.system.common.time.AppTime.APP_ZONE_ID).plusDays(1);
        while (firstMonday.getDayOfWeek().getValue() != 1) firstMonday = firstMonday.plusDays(1);
        LocalDate firstPeriodEnd = firstMonday.plusDays(6);
        LocalDate secondPeriodStart = firstMonday.plusDays(14);
        LocalDate secondPeriodEnd = secondPeriodStart.plusDays(6);

        ClassGroupEntity group = new ClassGroupEntity();
        group.setId(901L);
        group.setStatus("ACTIVE");
        group.setBillingType("MONTHLY");
        group.setStartMonth(firstMonday);
        group.setEndMonth(secondPeriodEnd);
        group.setStartDate(firstMonday);
        group.setEndDate(secondPeriodEnd);
        group.setScheduleBaseEndDate(secondPeriodEnd);
        when(classGroupMapper.selectList(any())).thenReturn(List.of(group));
        when(classGroupMapper.selectById(901L)).thenReturn(group);

        ClassGroupScheduleSlotEntity firstSlot = segmentedSlot(801L, 702L, firstMonday, firstPeriodEnd, 1);
        ClassGroupScheduleSlotEntity secondSlot = segmentedSlot(802L, 703L, secondPeriodStart, secondPeriodEnd, 1);
        when(classGroupScheduleSlotMapper.selectList(any())).thenReturn(List.of(firstSlot, secondSlot));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of());
        when(classHolidayService.listHolidays((Long) null)).thenReturn(List.of());

        int inserted = enrollmentOrderService.renewActiveClassOccurrences();

        ArgumentCaptor<LessonOccurrenceEntity> occurrences = ArgumentCaptor.forClass(LessonOccurrenceEntity.class);
        verify(lessonOccurrenceMapper, times(2)).insert(occurrences.capture());
        assertThat(inserted).isEqualTo(2);
        assertThat(occurrences.getAllValues()).extracting(LessonOccurrenceEntity::getPlannedStartAt)
                .containsExactly(firstMonday.atTime(10, 0), secondPeriodStart.atTime(10, 0));
        assertThat(occurrences.getAllValues()).extracting(LessonOccurrenceEntity::getPlannedTeacherId)
                .containsExactly(702L, 703L);
    }

    private ClassGroupScheduleSlotEntity segmentedSlot(Long id, Long teacherId, LocalDate start, LocalDate end, int weekday) {
        ClassGroupScheduleSlotEntity slot = new ClassGroupScheduleSlotEntity();
        slot.setId(id);
        slot.setSlotLineageId(id);
        slot.setTeacherId(teacherId);
        slot.setWeekday(weekday);
        slot.setStartTime(LocalTime.of(10, 0));
        slot.setEndTime(LocalTime.of(12, 0));
        slot.setEffectiveStartDate(start);
        slot.setEffectiveEndDate(end);
        return slot;
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
    void shouldRejectFractionalAmountsOnLegacyOrderCreationEndpoint() {
        EnrollmentCreateRequest request = buildCreateRequest();
        request.setOriginalAmount(new BigDecimal("1200.01"));
        assertThatThrownBy(() -> enrollmentOrderService.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WHOLE_DOLLAR_REQUIRED");

        request.setOriginalAmount(new BigDecimal("1200"));
        request.setDiscountAmount(new BigDecimal("100.01"));
        assertThatThrownBy(() -> enrollmentOrderService.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WHOLE_DOLLAR_REQUIRED");

        request.setDiscountAmount(new BigDecimal("100"));
        request.setReceivedAmount(new BigDecimal("1100.01"));
        assertThatThrownBy(() -> enrollmentOrderService.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WHOLE_DOLLAR_REQUIRED");
        verify(enrollmentOrderMapper, never()).insert(any(EnrollmentOrderEntity.class));
    }

    @Test
    void shouldRejectFractionalCourseDiscountBeforeCorrectingWholeDollarOrder() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setBillingPolicy("WHOLE_DOLLAR_V1");
        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        EnrollmentOrderCorrectionRequest request = new EnrollmentOrderCorrectionRequest();
        RegistrationIntakeRequest.CourseItemPayload item = new RegistrationIntakeRequest.CourseItemPayload();
        item.setOrderItemId(8001L);
        item.setDiscountAmount(new BigDecimal("1.01"));
        request.setItems(List.of(item));

        assertThatThrownBy(() -> enrollmentOrderService.correctOrder(2001L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WHOLE_DOLLAR_REQUIRED");
        verify(enrollmentOrderItemMapper, never()).updateById(any(EnrollmentOrderItemEntity.class));
    }

    @Test
    void shouldAppendEditReceiptPaymentToTheCurrentOrder() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setStudentId(101L);
        order.setHandledByName("Admin");
        RegistrationIntakeRequest.PaymentPayload payload = new RegistrationIntakeRequest.PaymentPayload();
        payload.setPaymentMethod("CASH");
        payload.setAmount(new BigDecimal("125.00"));
        payload.setPaidAt(LocalDateTime.of(2026, 10, 23, 10, 30));
        payload.setCorrectionChangedFields(List.of("paymentMethod", "amount", "paidAt"));

        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                enrollmentOrderService, "createCorrectionPayment", order, payload, null, Map.of());

        verify(paymentRecordMapper).insert(paymentCaptor.capture());
        PaymentRecordEntity added = paymentCaptor.getValue();
        assertThat(added.getEnrollmentOrderId()).isEqualTo(2001L);
        assertThat(added.getPaymentType()).isEqualTo("PAYMENT");
        assertThat(added.getPaymentMethod()).isEqualTo("CASH");
        assertThat(added.getAmount()).isEqualByComparingTo("125.00");
        assertThat(added.getPaidAt()).isEqualTo(payload.getPaidAt());
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
    void shouldCorrectV2ReceiptWhenProjectGradeDiffersFromActualClass() {
        // 回归：V2 以 project/plan/actual class 归属链为准，不再比较项目档位与实际班级名称。
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
        newClass.setGradeBand("G2");
        newClass.setActualClass("G2");
        newClass.setGroupProjectId(910L);
        newClass.setStartDate(LocalDate.of(2026, 9, 1));
        newClass.setEndDate(LocalDate.of(2026, 12, 31));

        GroupCourseProjectEntity project = new GroupCourseProjectEntity();
        project.setId(910L);
        project.setCourseProductId(202L);
        project.setStatus("ACTIVE");
        GroupRegistrationPlanEntity plan = new GroupRegistrationPlanEntity();
        plan.setId(920L);
        plan.setProjectId(910L);
        plan.setPackageUnit("MONTH");
        plan.setPackageQuantity(new BigDecimal("12"));
        plan.setCoverageType("ROLLING_MONTHS");
        plan.setFullDayPrice(new BigDecimal("1200"));
        plan.setStatus("ACTIVE");

        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(8001L);
        item.setEnrollmentOrderId(2001L);
        item.setCourseType("GROUP");
        item.setCourseProductId(202L);
        item.setClassId(801L);
        item.setGroupProjectId(910L);
        item.setGroupRegistrationPlanId(920L);
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
        activeEnrollment.setPurchasedQuantity(2);
        activeEnrollment.setBillingType("MONTHLY");
        activeEnrollment.setGroupProjectId(910L);
        activeEnrollment.setGroupRegistrationPlanId(920L);
        activeEnrollment.setStatus("ACTIVE");

        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(studentMapper.selectById(101L)).thenReturn(student);
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(classGroupMapper.selectById(802L)).thenReturn(newClass);
        when(groupCourseProjectMapper.selectById(910L)).thenReturn(project);
        when(groupRegistrationPlanMapper.selectById(920L)).thenReturn(plan);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(activeEnrollment));
        ClassGroupScheduleSlotEntity weeklySlot = new ClassGroupScheduleSlotEntity();
        weeklySlot.setWeekday(3);
        weeklySlot.setStartTime(java.time.LocalTime.of(9, 0));
        weeklySlot.setEndTime(java.time.LocalTime.of(12, 0));
        when(classGroupScheduleSlotMapper.selectList(any())).thenReturn(List.of(weeklySlot));
        when(classHolidayMapper.selectList(any())).thenReturn(List.of());
        when(receiptRecordMapper.selectList(any())).thenReturn(List.of());

        EnrollmentOrderCorrectionRequest request = new EnrollmentOrderCorrectionRequest();
        RegistrationIntakeRequest.CourseItemPayload payload = new RegistrationIntakeRequest.CourseItemPayload();
        payload.setOrderItemId(8001L);
        payload.setClassId(802L);
        payload.setGroupProjectId(910L);
        payload.setGroupRegistrationPlanId(920L);
        payload.setGradeBand("G1-G5");
        payload.setCourseType("GROUP");
        payload.setCourseProductId(202L);
        payload.setPackageUnit("MONTH");
        payload.setPackageQuantity(new BigDecimal("12"));
        payload.setPurchasedMonths(12);
        payload.setPriceAmount(new BigDecimal("1200"));
        payload.setStartDate(LocalDate.of(2026, 10, 15));
        request.setItems(List.of(payload));

        enrollmentOrderService.correctOrder(2001L, request);

        var lockOrder = inOrder(groupCourseProjectMapper, groupRegistrationPlanMapper, classGroupMapper);
        lockOrder.verify(groupCourseProjectMapper).selectByIdForUpdate(910L);
        lockOrder.verify(groupRegistrationPlanMapper).selectByIdForUpdate(920L);
        lockOrder.verify(classGroupMapper).selectByIdForUpdate(801L);
        lockOrder.verify(classGroupMapper).selectByIdForUpdate(802L);

        // 即使旧客户端把 false 省略为 null，课程项接送快照也必须明确清零并清除旧地址。
        verify(enrollmentOrderItemMapper).updateTransportSnapshot(8001L, false, null, false, null);

        verify(classEnrollmentMapper).insert(classEnrollmentCaptor.capture());
        ClassEnrollmentEntity replacement = classEnrollmentCaptor.getValue();
        assertThat(replacement.getExpiryDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(replacement.getEnrollmentDate()).isEqualTo(LocalDate.of(2026, 10, 21));
        assertThat(replacement.getBillingType()).isEqualTo("MONTHLY");
        assertThat(replacement.getPurchasedQuantity()).isEqualTo(12);
        assertThat(replacement.getGroupProjectId()).isEqualTo(910L);
        assertThat(replacement.getGroupRegistrationPlanId()).isEqualTo(920L);

        // 旧班级的 ACTIVE 在册记录应被标记为 CANCELLED
        ArgumentCaptor<ClassEnrollmentEntity> updatedCaptor = ArgumentCaptor.forClass(ClassEnrollmentEntity.class);
        verify(classEnrollmentMapper, atLeastOnce()).updateById(updatedCaptor.capture());
        assertThat(updatedCaptor.getAllValues()).anySatisfy(updated ->
                assertThat(updated.getStatus()).isEqualTo("CANCELLED"));
    }

    @Test
    void shouldNotRestoreCancelledEnrollmentWhenCorrectingReceipt() {
        // 已取消权益可能来自停课；编辑旧收据绝不能悄悄恢复在册状态。
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PARTIAL");
        order.setStudentId(101L);
        order.setClassId(801L);
        order.setSignupDate(LocalDate.of(2026, 9, 19));

        ClassGroupEntity clazz = buildClassGroup(202L);
        clazz.setId(801L);
        clazz.setGradeBand("G1-G5");
        clazz.setGroupProjectId(910L);
        clazz.setStartDate(LocalDate.of(2026, 9, 12));
        clazz.setEndDate(LocalDate.of(2027, 6, 12));

        GroupCourseProjectEntity project = new GroupCourseProjectEntity();
        project.setId(910L);
        project.setCourseProductId(202L);
        project.setStatus("ACTIVE");
        GroupRegistrationPlanEntity plan = new GroupRegistrationPlanEntity();
        plan.setId(920L);
        plan.setProjectId(910L);
        plan.setPackageUnit("SESSION");
        plan.setPackageQuantity(new BigDecimal("33"));
        plan.setCoverageType("FIXED_DATES");
        plan.setCoverageStartDate(LocalDate.of(2026, 9, 12));
        plan.setCoverageEndDate(LocalDate.of(2027, 6, 12));
        plan.setStatus("ACTIVE");

        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(8001L);
        item.setEnrollmentOrderId(2001L);
        item.setCourseType("GROUP");
        item.setCourseProductId(202L);
        item.setClassId(801L);
        item.setGroupProjectId(910L);
        item.setGroupRegistrationPlanId(920L);
        item.setGradeBand("G1-G5");
        item.setPackageUnit("SESSION");
        item.setPackageQuantity(new BigDecimal("33"));
        item.setStartDate(LocalDate.of(2026, 9, 19));
        item.setAttendanceOption("MORNING");

        ClassEnrollmentEntity orphan = new ClassEnrollmentEntity();
        orphan.setId(9001L);
        orphan.setClassId(801L);
        orphan.setStudentId(101L);
        orphan.setEnrollmentOrderItemId(8001L);
        orphan.setEnrollmentDate(LocalDate.of(2026, 9, 26));
        orphan.setExpiryDate(LocalDate.of(2026, 9, 30));
        orphan.setBillingType("SESSION");
        orphan.setPurchasedQuantity(1);
        orphan.setStatus("CANCELLED");
        orphan.setIsDeleted(0);

        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(studentMapper.selectById(101L)).thenReturn(buildStudent());
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(classGroupMapper.selectById(801L)).thenReturn(clazz);
        when(groupCourseProjectMapper.selectById(910L)).thenReturn(project);
        when(groupRegistrationPlanMapper.selectById(920L)).thenReturn(plan);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(orphan));
        lenient().when(classHolidayMapper.selectList(any())).thenReturn(List.of());
        lenient().when(receiptRecordMapper.selectList(any())).thenReturn(List.of());

        EnrollmentOrderCorrectionRequest request = new EnrollmentOrderCorrectionRequest();
        RegistrationIntakeRequest.CourseItemPayload payload = new RegistrationIntakeRequest.CourseItemPayload();
        payload.setOrderItemId(8001L);
        payload.setClassId(801L);
        payload.setGroupProjectId(910L);
        payload.setGroupRegistrationPlanId(920L);
        payload.setGradeBand("G1-G5");
        payload.setCourseType("GROUP");
        payload.setCourseProductId(202L);
        payload.setPackageUnit("SESSION");
        payload.setPackageQuantity(new BigDecimal("33"));
        payload.setSessions(33);
        payload.setStartDate(LocalDate.of(2026, 9, 19));
        payload.setAttendanceOption("MORNING");
        request.setItems(List.of(payload));

        enrollmentOrderService.correctOrder(2001L, request);

        verify(classEnrollmentMapper, never()).insert(any(ClassEnrollmentEntity.class));
    }

    @Test
    void shouldNotSelfHealOrphanWhenOverlappingActiveEntitlementExists() {
        // 回归：若该班已存在重叠 ACTIVE 权益（如续报转挂），不得重建，避免把旧时段重新入册。
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PARTIAL");
        order.setStudentId(101L);
        order.setClassId(801L);
        order.setSignupDate(LocalDate.of(2026, 9, 19));

        ClassGroupEntity clazz = buildClassGroup(202L);
        clazz.setId(801L);
        clazz.setGradeBand("G1-G5");
        clazz.setGroupProjectId(910L);
        clazz.setStartDate(LocalDate.of(2026, 9, 12));
        clazz.setEndDate(LocalDate.of(2027, 6, 12));

        GroupCourseProjectEntity project = new GroupCourseProjectEntity();
        project.setId(910L);
        project.setCourseProductId(202L);
        project.setStatus("ACTIVE");
        GroupRegistrationPlanEntity plan = new GroupRegistrationPlanEntity();
        plan.setId(920L);
        plan.setProjectId(910L);
        plan.setPackageUnit("SESSION");
        plan.setPackageQuantity(new BigDecimal("33"));
        plan.setCoverageType("FIXED_DATES");
        plan.setCoverageStartDate(LocalDate.of(2026, 9, 12));
        plan.setCoverageEndDate(LocalDate.of(2027, 6, 12));
        plan.setStatus("ACTIVE");

        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(8001L);
        item.setEnrollmentOrderId(2001L);
        item.setCourseType("GROUP");
        item.setCourseProductId(202L);
        item.setClassId(801L);
        item.setGroupProjectId(910L);
        item.setGroupRegistrationPlanId(920L);
        item.setGradeBand("G1-G5");
        item.setPackageUnit("SESSION");
        item.setPackageQuantity(new BigDecimal("33"));
        item.setStartDate(LocalDate.of(2026, 9, 19));
        item.setAttendanceOption("MORNING");

        ClassEnrollmentEntity orphan = new ClassEnrollmentEntity();
        orphan.setId(9001L);
        orphan.setClassId(801L);
        orphan.setStudentId(101L);
        orphan.setEnrollmentOrderItemId(8001L);
        orphan.setEnrollmentDate(LocalDate.of(2026, 9, 26));
        orphan.setExpiryDate(LocalDate.of(2026, 9, 30));
        orphan.setBillingType("SESSION");
        orphan.setPurchasedQuantity(1);
        orphan.setStatus("CANCELLED");
        orphan.setIsDeleted(0);

        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(studentMapper.selectById(101L)).thenReturn(buildStudent());
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(classGroupMapper.selectById(801L)).thenReturn(clazz);
        when(groupCourseProjectMapper.selectById(910L)).thenReturn(project);
        when(groupRegistrationPlanMapper.selectById(920L)).thenReturn(plan);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(orphan));
        lenient().when(classHolidayMapper.selectList(any())).thenReturn(List.of());
        lenient().when(receiptRecordMapper.selectList(any())).thenReturn(List.of());

        EnrollmentOrderCorrectionRequest request = new EnrollmentOrderCorrectionRequest();
        RegistrationIntakeRequest.CourseItemPayload payload = new RegistrationIntakeRequest.CourseItemPayload();
        payload.setOrderItemId(8001L);
        payload.setClassId(801L);
        payload.setGroupProjectId(910L);
        payload.setGroupRegistrationPlanId(920L);
        payload.setGradeBand("G1-G5");
        payload.setCourseType("GROUP");
        payload.setCourseProductId(202L);
        payload.setPackageUnit("SESSION");
        payload.setPackageQuantity(new BigDecimal("33"));
        payload.setSessions(33);
        payload.setStartDate(LocalDate.of(2026, 9, 19));
        payload.setAttendanceOption("MORNING");
        request.setItems(List.of(payload));

        enrollmentOrderService.correctOrder(2001L, request);

        verify(classEnrollmentMapper, never()).insert(any(ClassEnrollmentEntity.class));
    }

    @Test
    void shouldPreserveExistingSessionEnrollmentWhenOnlyReceiptPriceChanges() {
        // 旧订单只购买 4 次，定价选项上限为 13 次；收据改金额不应按 13 次重算或重验新假期。
        EnrollmentOrderCorrectionRequest request = stubSessionCorrection(4, 4);
        enrollmentOrderService.correctOrder(2001L, request);

        verify(classGroupScheduleSlotMapper, never()).selectList(any());
        verify(classEnrollmentMapper, never()).updateById(any(ClassEnrollmentEntity.class));
        verify(classEnrollmentMapper, never()).insert(any(ClassEnrollmentEntity.class));
    }

    @Test
    void shouldRecalculateZeroPickupFromCurrentCourseWithoutChangingPayment() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setStudentId(101L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PAID");
        order.setTuitionSubtotalAmount(new BigDecimal("3499.00"));
        order.setPickupFeeAmount(new BigDecimal("300.00"));
        order.setDiscountAmount(new BigDecimal("400.00"));
        order.setAmountDue(new BigDecimal("3399.00"));
        order.setReceivedAmount(new BigDecimal("3399.00"));

        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(8001L);
        item.setEnrollmentOrderId(2001L);
        item.setLineNo(1);
        item.setCourseType("GROUP");
        item.setPriceAmount(new BigDecimal("3499.00"));
        item.setPickupRequiredSnapshot(true);
        item.setPickupFeeAmount(BigDecimal.ZERO);
        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(studentMapper.selectById(101L)).thenReturn(buildStudent());
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(receiptRecordMapper.selectList(any())).thenReturn(List.of());

        RegistrationIntakeRequest.CourseItemPayload edit = new RegistrationIntakeRequest.CourseItemPayload();
        edit.setOrderItemId(8001L);
        edit.setPickupRequired(false);
        edit.setCorrectionChangedFields(List.of("pickupRequired"));
        EnrollmentOrderCorrectionRequest request = new EnrollmentOrderCorrectionRequest();
        request.setItems(List.of(edit));
        request.setPickupFeeAmount(BigDecimal.ZERO);
        request.setPickupFeeCharged(true);
        enrollmentOrderService.correctOrder(2001L, request);

        assertThat(order.getPickupFeeAmount()).isEqualByComparingTo("0.00");
        assertThat(order.getAmountDue()).isEqualByComparingTo("3099.00");
        assertThat(order.getReceivedAmount()).isEqualByComparingTo("3399.00");
        assertThat(order.getBalanceAmount()).isEqualByComparingTo("-300.00");
        verify(paymentRecordMapper, never()).updateById(any(PaymentRecordEntity.class));
    }

    @Test
    void shouldKeepPaymentAndUpdateAttendanceAndBalanceAcrossRepeatedReceiptCorrections() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PAID");
        order.setStudentId(101L);
        order.setClassId(801L);
        order.setTuitionSubtotalAmount(new BigDecimal("3398.00"));
        order.setOriginalAmount(new BigDecimal("3398.00"));
        order.setAmountDue(new BigDecimal("3398.00"));
        order.setReceivedAmount(new BigDecimal("3398.00"));
        order.setBalanceAmount(BigDecimal.ZERO);

        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(8001L);
        item.setEnrollmentOrderId(2001L);
        item.setLineNo(1);
        item.setCourseType("GROUP");
        item.setCourseProductId(202L);
        item.setGroupProjectId(910L);
        item.setGroupRegistrationPlanId(920L);
        item.setClassId(801L);
        item.setPackageUnit("SESSION");
        item.setPackageQuantity(new BigDecimal("8"));
        item.setAttendanceOption("FULL_DAY");
        item.setPriceAmount(new BigDecimal("3398.00"));
        item.setOriginalPriceAmount(new BigDecimal("3398.00"));

        ClassEnrollmentEntity enrollment = new ClassEnrollmentEntity();
        enrollment.setId(9001L);
        enrollment.setEnrollmentOrderItemId(8001L);
        enrollment.setStatus("ACTIVE");
        enrollment.setAttendanceOption("FULL_DAY");
        enrollment.setIsDeleted(0);

        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(studentMapper.selectById(101L)).thenReturn(buildStudent());
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));
        when(receiptRecordMapper.selectList(any())).thenReturn(List.of());

        EnrollmentOrderCorrectionRequest halfDay = attendanceAndPriceCorrection("MORNING", "2898.00");
        enrollmentOrderService.correctOrder(2001L, halfDay);

        assertThat(item.getAttendanceOption()).isEqualTo("MORNING");
        assertThat(enrollment.getAttendanceOption()).isEqualTo("MORNING");
        assertThat(order.getAmountDue()).isEqualByComparingTo("2898.00");
        assertThat(order.getReceivedAmount()).isEqualByComparingTo("3398.00");
        assertThat(order.getBalanceAmount()).isEqualByComparingTo("-500.00");
        assertThat(order.getPaymentStatus()).isEqualTo("PAID");

        EnrollmentOrderCorrectionRequest fullDay = attendanceAndPriceCorrection("FULL_DAY", "3398.00");
        enrollmentOrderService.correctOrder(2001L, fullDay);

        assertThat(item.getAttendanceOption()).isEqualTo("FULL_DAY");
        assertThat(enrollment.getAttendanceOption()).isEqualTo("FULL_DAY");
        assertThat(order.getAmountDue()).isEqualByComparingTo("3398.00");
        assertThat(order.getReceivedAmount()).isEqualByComparingTo("3398.00");
        assertThat(order.getBalanceAmount()).isEqualByComparingTo("0.00");
        verify(paymentRecordMapper, never()).updateById(any(PaymentRecordEntity.class));
        verify(paymentRecordMapper, never()).delete(any());
    }

    private EnrollmentOrderCorrectionRequest attendanceAndPriceCorrection(String attendance, String price) {
        EnrollmentOrderCorrectionRequest request = new EnrollmentOrderCorrectionRequest();
        RegistrationIntakeRequest.CourseItemPayload payload = new RegistrationIntakeRequest.CourseItemPayload();
        payload.setOrderItemId(8001L);
        payload.setAttendanceOption(attendance);
        payload.setPriceAmount(new BigDecimal(price));
        payload.setOriginalPriceAmount(new BigDecimal(price));
        payload.setPackageTotalPrice(new BigDecimal(price));
        payload.setCorrectionChangedFields(List.of("attendanceOption", "priceAmount", "originalPriceAmount", "packageTotalPrice"));
        request.setItems(List.of(payload));
        return request;
    }

    @Test
    void shouldAllowReceiptCorrectionWhenUnchangedPlanWasReducedAfterPurchase() {
        // 订单购买时的方案上限是 13；管理员后来把当前方案上限调到 3，旧收据仍可编辑其他字段。
        EnrollmentOrderCorrectionRequest request = stubSessionCorrection(4, 4, new BigDecimal("3"));
        stubSavedSessionReceipt(4);

        enrollmentOrderService.correctOrder(2001L, request);

        verify(enrollmentOrderItemMapper).updateById(any(EnrollmentOrderItemEntity.class));
        verify(classEnrollmentMapper, never()).updateById(any(ClassEnrollmentEntity.class));
        verify(classEnrollmentMapper, never()).insert(any(ClassEnrollmentEntity.class));
    }

    @Test
    void shouldRejectChangedPackageQuantityAboveCurrentPlanLimit() {
        EnrollmentOrderCorrectionRequest request = stubSessionCorrection(4, 4, new BigDecimal("3"));
        request.getItems().get(0).setPackageQuantity(new BigDecimal("4"));

        assertThatThrownBy(() -> enrollmentOrderService.correctOrder(2001L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("GROUP_V2_PLAN_MISMATCH");
        verify(enrollmentOrderItemMapper, never()).updateById(any(EnrollmentOrderItemEntity.class));
    }

    @Test
    void shouldRejectChangedSelectedSessionsAboveCurrentPlanLimit() {
        EnrollmentOrderCorrectionRequest request = stubSessionCorrection(4, 5, new BigDecimal("3"));
        stubSavedSessionReceipt(4);

        assertThatThrownBy(() -> enrollmentOrderService.correctOrder(2001L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("GROUP_V2_PLAN_MISMATCH");
        verify(enrollmentOrderItemMapper, never()).updateById(any(EnrollmentOrderItemEntity.class));
    }

    @Test
    void shouldRecomputeSessionEnrollmentUsingSelectedSessionsWhenCountChanges() {
        EnrollmentOrderCorrectionRequest request = stubSessionCorrection(2, 3);
        ClassGroupScheduleSlotEntity slot = new ClassGroupScheduleSlotEntity();
        slot.setWeekday(1);
        when(classGroupScheduleSlotMapper.selectList(any())).thenReturn(List.of(slot));
        // 13 个周一里有一天放假：完整定价课包放不下，但实际购买的 3 次可以排完。
        ClassHolidayEntity holiday = new ClassHolidayEntity();
        holiday.setStartDate(LocalDate.of(2026, 11, 2));
        holiday.setEndDate(LocalDate.of(2026, 11, 2));
        when(classHolidayMapper.selectList(any())).thenReturn(List.of(holiday));

        enrollmentOrderService.correctOrder(2001L, request);

        verify(classEnrollmentMapper).updateById(classEnrollmentCaptor.capture());
        assertThat(classEnrollmentCaptor.getValue().getPurchasedQuantity()).isEqualTo(3);
        assertThat(classEnrollmentCaptor.getValue().getExpiryDate()).isEqualTo(LocalDate.of(2026, 9, 28));
        verify(classEnrollmentMapper, never()).insert(any(ClassEnrollmentEntity.class));
    }

    @Test
    void shouldNotRecreateClassEnrollmentWhenEditingAnOldRenewalReceipt() {
        EnrollmentOrderCorrectionRequest request = stubSessionCorrection(4, 4);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of());
        stubSavedSessionReceipt(4);

        enrollmentOrderService.correctOrder(2001L, request);

        verify(classEnrollmentMapper, never()).insert(any(ClassEnrollmentEntity.class));
        verify(classEnrollmentMapper, never()).updateById(any(ClassEnrollmentEntity.class));
    }

    @Test
    void shouldRejectSessionChangesWhenOldReceiptNoLongerOwnsEnrollment() {
        EnrollmentOrderCorrectionRequest request = stubSessionCorrection(4, 5);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of());
        stubSavedSessionReceipt(4);

        assertThatThrownBy(() -> enrollmentOrderService.correctOrder(2001L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("CORRECTION_ENROLLMENT_DETACHED");
        verify(classEnrollmentMapper, never()).insert(any(ClassEnrollmentEntity.class));
    }

    @Test
    void shouldPreserveCumulativeSessionsWhenEditingRenewalReceiptPrice() {
        EnrollmentOrderCorrectionRequest request = stubSessionCorrection(14, 4);
        stubSavedSessionReceipt(4);

        enrollmentOrderService.correctOrder(2001L, request);

        verify(classEnrollmentMapper, never()).updateById(any(ClassEnrollmentEntity.class));
        verify(classEnrollmentMapper, never()).insert(any(ClassEnrollmentEntity.class));
    }

    @Test
    void shouldRejectChangingRenewalSessionsFromReceipt() {
        EnrollmentOrderCorrectionRequest request = stubSessionCorrection(14, 5);
        stubSavedSessionReceipt(4);

        assertThatThrownBy(() -> enrollmentOrderService.correctOrder(2001L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("CORRECTION_RENEWAL_SESSIONS_UNSUPPORTED");
        verify(classEnrollmentMapper, never()).updateById(any(ClassEnrollmentEntity.class));
    }

    private void stubSavedSessionReceipt(int sessions) {
        ReceiptRecordEntity receipt = new ReceiptRecordEntity();
        receipt.setId(6001L);
        receipt.setReceiptPayload("{\"items\":[]}");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("items", List.of(Map.of("lineNo", 1, "sessions", sessions)));
        when(receiptRecordMapper.selectList(any())).thenReturn(List.of(receipt));
        when(jsonHelper.fromJson(any(), any())).thenReturn(snapshot);
    }

    private EnrollmentOrderCorrectionRequest stubSessionCorrection(int previousSessions, int selectedSessions) {
        return stubSessionCorrection(previousSessions, selectedSessions, new BigDecimal("13"));
    }

    private EnrollmentOrderCorrectionRequest stubSessionCorrection(int previousSessions, int selectedSessions,
                                                                    BigDecimal currentPlanQuantity) {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setOrderStatus("CONFIRMED");
        order.setStudentId(101L);
        order.setClassId(801L);
        order.setSignupDate(LocalDate.of(2026, 9, 14));

        ClassGroupEntity group = buildClassGroup(202L);
        group.setGroupProjectId(910L);
        group.setStartDate(LocalDate.of(2026, 9, 1));
        group.setEndDate(LocalDate.of(2026, 12, 7));
        group.setGradeBand("G1-G5");

        GroupCourseProjectEntity project = new GroupCourseProjectEntity();
        project.setId(910L);
        project.setCourseProductId(202L);
        project.setStatus("ACTIVE");
        GroupRegistrationPlanEntity plan = new GroupRegistrationPlanEntity();
        plan.setId(920L);
        plan.setProjectId(910L);
        plan.setCoverageType("SESSION_COUNT");
        plan.setPackageUnit("SESSION");
        plan.setPackageQuantity(currentPlanQuantity);
        plan.setStatus("ACTIVE");

        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(8001L);
        item.setLineNo(1);
        item.setEnrollmentOrderId(2001L);
        item.setCourseType("GROUP");
        item.setCourseProductId(202L);
        item.setClassId(801L);
        item.setGroupProjectId(910L);
        item.setGroupRegistrationPlanId(920L);
        item.setGradeBand("G1-G5");
        item.setPackageUnit("SESSION");
        item.setPackageQuantity(new BigDecimal("13"));
        item.setStartDate(LocalDate.of(2026, 9, 14));

        ClassEnrollmentEntity enrollment = new ClassEnrollmentEntity();
        enrollment.setId(9001L);
        enrollment.setClassId(801L);
        enrollment.setStudentId(101L);
        enrollment.setEnrollmentOrderItemId(8001L);
        enrollment.setEnrollmentDate(LocalDate.of(2026, 9, 14));
        enrollment.setExpiryDate(LocalDate.of(2026, 10, 5));
        enrollment.setPurchasedQuantity(previousSessions);
        enrollment.setBillingType("PER_SESSION");
        enrollment.setGroupProjectId(910L);
        enrollment.setGroupRegistrationPlanId(920L);
        enrollment.setStatus("ACTIVE");

        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(studentMapper.selectById(101L)).thenReturn(buildStudent());
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(classGroupMapper.selectById(801L)).thenReturn(group);
        when(groupCourseProjectMapper.selectById(910L)).thenReturn(project);
        when(groupRegistrationPlanMapper.selectById(920L)).thenReturn(plan);
        lenient().when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));
        lenient().when(receiptRecordMapper.selectList(any())).thenReturn(List.of());

        RegistrationIntakeRequest.CourseItemPayload payload = new RegistrationIntakeRequest.CourseItemPayload();
        payload.setOrderItemId(8001L);
        payload.setClassId(801L);
        payload.setGroupProjectId(910L);
        payload.setGroupRegistrationPlanId(920L);
        payload.setGradeBand("G1-G5");
        payload.setCourseType("GROUP");
        payload.setCourseProductId(202L);
        payload.setPackageUnit("SESSION");
        payload.setPackageQuantity(new BigDecimal("13"));
        payload.setSessions(selectedSessions);
        payload.setStartDate(LocalDate.of(2026, 9, 14));
        payload.setPriceAmount(new BigDecimal("800"));
        EnrollmentOrderCorrectionRequest request = new EnrollmentOrderCorrectionRequest();
        request.setItems(List.of(payload));
        return request;
    }

    /**
     * 按月班课更正场景：9 月开课、指定结束日的班级，ROLLING_MONTHS 方案（标称 1 个月、$200/月）。
     * 学生 9 月购 6 个月（覆盖到 2027-02-28），当前在册权益对应该购买。
     */
    private EnrollmentOrderCorrectionRequest stubMonthlyCorrection(int purchasedMonths,
                                                                    int selectedMonths,
                                                                    LocalDate classEndDate) {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PAID");
        order.setStudentId(101L);
        order.setClassId(801L);
        order.setSignupDate(LocalDate.of(2026, 9, 14));

        ClassGroupEntity group = buildClassGroup(202L);
        group.setGroupProjectId(910L);
        group.setStartDate(LocalDate.of(2026, 9, 1));
        group.setEndDate(classEndDate);
        group.setGradeBand("G1-G5");

        GroupCourseProjectEntity project = new GroupCourseProjectEntity();
        project.setId(910L);
        project.setCourseProductId(202L);
        project.setStatus("ACTIVE");
        GroupRegistrationPlanEntity plan = new GroupRegistrationPlanEntity();
        plan.setId(920L);
        plan.setProjectId(910L);
        plan.setCoverageType("ROLLING_MONTHS");
        plan.setPackageUnit("MONTH");
        // 标称 1 个月、月费率 200：价格一致性校验 expected = 200 × 购买月数，整数便于 stub。
        plan.setPackageQuantity(BigDecimal.ONE);
        plan.setFullDayPrice(new BigDecimal("200"));
        plan.setStatus("ACTIVE");

        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(8001L);
        item.setLineNo(1);
        item.setEnrollmentOrderId(2001L);
        item.setCourseType("GROUP");
        item.setCourseProductId(202L);
        item.setClassId(801L);
        item.setGroupProjectId(910L);
        item.setGroupRegistrationPlanId(920L);
        item.setGradeBand("G1-G5");
        item.setPackageUnit("MONTH");
        item.setPackageQuantity(new BigDecimal(purchasedMonths));
        item.setStartDate(LocalDate.of(2026, 9, 14));

        ClassEnrollmentEntity enrollment = new ClassEnrollmentEntity();
        enrollment.setId(9001L);
        enrollment.setClassId(801L);
        enrollment.setStudentId(101L);
        enrollment.setEnrollmentOrderItemId(8001L);
        enrollment.setEnrollmentDate(LocalDate.of(2026, 9, 14));
        enrollment.setExpiryDate(LocalDate.of(2027, 2, 28));
        enrollment.setPurchasedQuantity(purchasedMonths);
        enrollment.setBillingType("MONTH");
        enrollment.setGroupProjectId(910L);
        enrollment.setGroupRegistrationPlanId(920L);
        enrollment.setStatus("ACTIVE");

        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(studentMapper.selectById(101L)).thenReturn(buildStudent());
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(classGroupMapper.selectById(801L)).thenReturn(group);
        when(groupCourseProjectMapper.selectById(910L)).thenReturn(project);
        when(groupRegistrationPlanMapper.selectById(920L)).thenReturn(plan);
        lenient().when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));
        lenient().when(classHolidayMapper.selectList(any())).thenReturn(List.of());
        lenient().when(receiptRecordMapper.selectList(any())).thenReturn(List.of());
        // 班级每周一节课，覆盖期内的排课日期由服务按 slot 推导。
        ClassGroupScheduleSlotEntity weeklySlot = new ClassGroupScheduleSlotEntity();
        weeklySlot.setWeekday(1);
        weeklySlot.setStartTime(java.time.LocalTime.of(9, 0));
        weeklySlot.setEndTime(java.time.LocalTime.of(12, 0));
        lenient().when(classGroupScheduleSlotMapper.selectList(any())).thenReturn(List.of(weeklySlot));

        RegistrationIntakeRequest.CourseItemPayload payload = new RegistrationIntakeRequest.CourseItemPayload();
        payload.setOrderItemId(8001L);
        payload.setClassId(801L);
        payload.setGroupProjectId(910L);
        payload.setGroupRegistrationPlanId(920L);
        payload.setGradeBand("G1-G5");
        payload.setCourseType("GROUP");
        payload.setCourseProductId(202L);
        payload.setPackageUnit("MONTH");
        payload.setPackageQuantity(new BigDecimal(selectedMonths));
        payload.setPurchasedMonths(selectedMonths);
        payload.setStartDate(LocalDate.of(2026, 9, 14));
        // 月费率 200 × 购买月数，满足更正的价格一致性校验。
        payload.setPriceAmount(new BigDecimal(200 * selectedMonths));
        EnrollmentOrderCorrectionRequest request = new EnrollmentOrderCorrectionRequest();
        request.setItems(List.of(payload));
        return request;
    }

    private LessonRecordEntity consumedLesson(Long classEnrollmentId, LocalDate lessonDate) {
        LessonRecordEntity record = new LessonRecordEntity();
        record.setClassEnrollmentId(classEnrollmentId);
        record.setStudentId(101L);
        record.setConfirmStage("CONFIRMED");
        record.setResultStatus("COMPLETED");
        record.setPlannedStartAt(lessonDate.atTime(9, 0));
        record.setIsDeleted(0);
        return record;
    }

    @Test
    void shouldRejectReducingMonthsBelowConsumedLessons() {
        // 学生 9 月购 6 个月，10 月已确认上课；把月数缩到 1 个月（覆盖仅至 9-30）须拦截，
        // 否则 10-05 的已消耗课次被甩出覆盖期。
        EnrollmentOrderCorrectionRequest request = stubMonthlyCorrection(6, 1, LocalDate.of(2027, 12, 31));
        // 历史已消耗课次落在 10 月（1 个月到期日 2026-09-30 之前）。
        lenient().when(lessonRecordMapper.selectList(any()))
                .thenReturn(List.of(consumedLesson(9001L, LocalDate.of(2026, 10, 5))));

        assertThatThrownBy(() -> enrollmentOrderService.correctOrder(2001L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("CORRECTION_MONTHS_BELOW_CONSUMED");
        verify(classEnrollmentMapper, never()).updateById(any(ClassEnrollmentEntity.class));
    }

    @Test
    void shouldAllowRenewingMonthsWhenConsumedLessonsStayCovered() {
        // 6 → 10 个月（同课续月）：已消耗课次仍在覆盖期内，正常放行。
        EnrollmentOrderCorrectionRequest request = stubMonthlyCorrection(6, 10, LocalDate.of(2027, 12, 31));
        lenient().when(lessonRecordMapper.selectList(any()))
                .thenReturn(List.of(consumedLesson(9001L, LocalDate.of(2026, 10, 5))));

        enrollmentOrderService.correctOrder(2001L, request);

        verify(classEnrollmentMapper).updateById(classEnrollmentCaptor.capture());
        assertThat(classEnrollmentCaptor.getValue().getPurchasedQuantity()).isEqualTo(10);
    }

    @Test
    void shouldAllowUnchangedMonthsEvenWithoutConsumedLessonData() {
        // 无消耗记录时不动月数（等值更正其他字段）不受守卫影响。
        EnrollmentOrderCorrectionRequest request = stubMonthlyCorrection(6, 6, LocalDate.of(2026, 12, 31));
        lenient().when(lessonRecordMapper.selectList(any())).thenReturn(List.of());

        enrollmentOrderService.correctOrder(2001L, request);
    }

    @Test
    void shouldRejectRenewalMonthsBeyondClassPeriodWithClearGuidance() {
        // 6 → 10 个月但班级只开到 12-31：续月超出班级周期，提示先延长班级。
        EnrollmentOrderCorrectionRequest request = stubMonthlyCorrection(6, 10, LocalDate.of(2026, 12, 31));

        assertThatThrownBy(() -> enrollmentOrderService.correctOrder(2001L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("message").asString()
                .contains("class end date")
                .contains("2026-12-31")
                .contains("Extend the class period");
    }

    @Test
    void shouldAllowNormalMonthlyPurchaseWithinPlanQuantityEvenIfRollingEndExceedsClassEnd() {
        // 正常购买（月数不超过方案标称 6）即使滚动末月超班级结束日，也靠 cap 截断而不是前置拦截。
        // 这是 db9103e 引入、本次恢复 quantity > configuredQuantity 前置条件后修复的回归。
        EnrollmentOrderCorrectionRequest request = stubMonthlyCorrection(6, 6, LocalDate.of(2026, 12, 31));

        enrollmentOrderService.correctOrder(2001L, request);
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
        verify(paymentRecordMapper, never()).delete(any());
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
    void shouldReverseCreditAndDeleteStandaloneTopUpReceipt() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setStudentId(101L);
        order.setAmountDue(new BigDecimal("100.00"));
        order.setReceivedAmount(new BigDecimal("100.00"));
        ReceiptRecordEntity receipt = new ReceiptRecordEntity();
        receipt.setId(3001L);
        receipt.setEnrollmentOrderId(2001L);
        receipt.setStudentId(101L);
        receipt.setReceiptNo("RCP202610120001");
        receipt.setTemplateCode("DEFAULT_RECEIPT");
        receipt.setReceiptPayload("top-up snapshot");
        EnrollmentOrderParticipantEntity participant = new EnrollmentOrderParticipantEntity();
        participant.setStudentId(101L);
        PaymentRecordEntity payment = new PaymentRecordEntity();
        payment.setPaymentType("PAYMENT");
        payment.setPaymentMethod("ZELLE");
        payment.setPaymentStatus("COMPLETED");
        payment.setAmount(new BigDecimal("100.00"));

        when(enrollmentOrderMapper.selectByIdForUpdate(2001L)).thenReturn(order);
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(coursePackageMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(receiptRecordMapper.selectById(3001L)).thenReturn(receipt);
        when(jsonHelper.fromJson(any(), any())).thenReturn(Map.of(
                "creditTopUpAmount", new BigDecimal("100.00"),
                "amountDue", new BigDecimal("100.00"),
                "items", Collections.emptyList()));
        when(enrollmentOrderParticipantMapper.selectList(any())).thenReturn(List.of(participant));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment));
        when(paymentAllocationMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(enrollmentOrderMapper.updateById(order)).thenReturn(1);
        when(enrollmentOrderMapper.deleteById(2001L)).thenReturn(1);
        when(studentCreditService.reverseTopUpUpToAvailable(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new BigDecimal("100.00"));

        boolean transactionDeleted = enrollmentOrderService.deleteCreditTopUpReceipt(2001L, 3001L,
                new BigDecimal("100.00"),
                new CurrentUserResponse(9L, "admin", "Admin", "STAFF", null, null, List.of("STAFF")));

        assertThat(transactionDeleted).isTrue();
        verify(studentCreditService).reverseTopUpUpToAvailable(101L, 2001L, new BigDecimal("100.00"),
                new BigDecimal("100.00"), 9L, "Admin", "Deleted Credit top-up receipt RCP202610120001");
        verify(paymentRecordMapper, never()).delete(any());
        verify(receiptRecordMapper).delete(any());
        verify(enrollmentOrderParticipantMapper).delete(any());
        verify(enrollmentOrderMapper).deleteById(2001L);
    }

    @Test
    void shouldKeepSpentTopUpPaymentAndOnlyHideReceiptWhenAvailableCreditIsInsufficient() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setStudentId(101L);
        order.setAmountDue(new BigDecimal("100.00"));
        order.setReceivedAmount(new BigDecimal("100.00"));
        ReceiptRecordEntity receipt = new ReceiptRecordEntity();
        receipt.setId(3001L);
        receipt.setEnrollmentOrderId(2001L);
        receipt.setStudentId(101L);
        receipt.setReceiptNo("RCP202610120001");
        receipt.setTemplateCode("DEFAULT_RECEIPT");
        receipt.setReceiptPayload("top-up snapshot");
        EnrollmentOrderParticipantEntity participant = new EnrollmentOrderParticipantEntity();
        participant.setStudentId(101L);
        PaymentRecordEntity payment = new PaymentRecordEntity();
        payment.setPaymentType("PAYMENT");
        payment.setPaymentMethod("ZELLE");
        payment.setPaymentStatus("COMPLETED");
        payment.setAmount(new BigDecimal("100.00"));

        when(enrollmentOrderMapper.selectByIdForUpdate(2001L)).thenReturn(order);
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(coursePackageMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(receiptRecordMapper.selectById(3001L)).thenReturn(receipt);
        when(jsonHelper.fromJson(any(), any())).thenReturn(Map.of(
                "creditTopUpAmount", new BigDecimal("100.00"),
                "amountDue", new BigDecimal("100.00"),
                "items", Collections.emptyList()));
        when(enrollmentOrderParticipantMapper.selectList(any())).thenReturn(List.of(participant));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment));
        when(paymentAllocationMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(enrollmentOrderMapper.deleteById(2001L)).thenReturn(1);
        when(studentCreditService.reverseTopUpUpToAvailable(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new BigDecimal("30.00"));
        when(receiptRecordMapper.update(org.mockito.ArgumentMatchers.isNull(), any())).thenReturn(1);
        when(receiptRecordMapper.deleteById(3001L)).thenReturn(1);

        boolean transactionDeleted = enrollmentOrderService.deleteCreditTopUpReceipt(2001L, 3001L,
                new BigDecimal("30.00"),
                new CurrentUserResponse(9L, "admin", "Admin", "STAFF", null, null, List.of("STAFF")));

        assertThat(transactionDeleted).isFalse();
        assertThat(order.getReceivedAmount()).isEqualByComparingTo("100.00");
        verify(receiptRecordMapper).deleteById(3001L);
        verify(paymentRecordMapper, never()).delete(any());
        verify(enrollmentOrderParticipantMapper, never()).delete(any());
        verify(enrollmentOrderMapper).deleteById(2001L);
    }

    @Test
    void shouldRejectTopUpReceiptWhenPaymentTotalDoesNotMatchBeforeReversingCredit() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setStudentId(101L);
        order.setAmountDue(new BigDecimal("100.00"));
        order.setReceivedAmount(new BigDecimal("100.00"));
        ReceiptRecordEntity receipt = new ReceiptRecordEntity();
        receipt.setId(3001L);
        receipt.setEnrollmentOrderId(2001L);
        receipt.setStudentId(101L);
        receipt.setTemplateCode("DEFAULT_RECEIPT");
        receipt.setReceiptPayload("top-up snapshot");
        EnrollmentOrderParticipantEntity participant = new EnrollmentOrderParticipantEntity();
        participant.setStudentId(101L);
        PaymentRecordEntity payment = new PaymentRecordEntity();
        payment.setPaymentType("PAYMENT");
        payment.setPaymentMethod("ZELLE");
        payment.setAmount(new BigDecimal("90.00"));

        when(enrollmentOrderMapper.selectByIdForUpdate(2001L)).thenReturn(order);
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(coursePackageMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(receiptRecordMapper.selectById(3001L)).thenReturn(receipt);
        when(jsonHelper.fromJson(any(), any())).thenReturn(Map.of(
                "creditTopUpAmount", new BigDecimal("100.00"),
                "amountDue", new BigDecimal("100.00"),
                "items", Collections.emptyList()));
        when(enrollmentOrderParticipantMapper.selectList(any())).thenReturn(List.of(participant));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment));

        assertThatThrownBy(() -> enrollmentOrderService.deleteCreditTopUpReceipt(
                2001L, 3001L, BigDecimal.ZERO, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("CREDIT_TOP_UP_PAYMENT_MISMATCH");
        verify(studentCreditService, never()).reverseTopUpUpToAvailable(
                any(), any(), any(), any(), any(), any(), any());
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
    void shouldDeleteOnlyLessonRecordsLinkedToTheDeletedV2Entitlement() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setStudentId(1001L);
        order.setReceivedAmount(BigDecimal.ZERO);
        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(2101L);
        item.setEnrollmentOrderId(2001L);
        item.setClassId(3101L);
        item.setGroupProjectId(5101L);
        item.setPaidAmount(BigDecimal.ZERO);
        ClassEnrollmentEntity seat = new ClassEnrollmentEntity();
        seat.setId(4101L);
        seat.setEnrollmentOrderItemId(2101L);
        seat.setStudentId(1001L);
        seat.setClassId(3101L);
        seat.setStatus("ACTIVE");
        LessonRecordEntity lessonRecord = new LessonRecordEntity();
        lessonRecord.setId(6101L);
        lessonRecord.setClassEnrollmentId(4101L);

        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(seat));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(lessonRecord));
        when(coursePackageMapper.selectList(any())).thenReturn(List.of());

        enrollmentOrderService.deleteOrder(2001L);

        ArgumentCaptor<Wrapper<LessonRecordEntity>> recordQuery = ArgumentCaptor.forClass(Wrapper.class);
        verify(lessonRecordMapper).selectList(recordQuery.capture());
        assertThat(recordQuery.getValue().getCustomSqlSegment()).contains("classEnrollmentId");
        assertThat(((AbstractWrapper<?, ?, ?>) recordQuery.getValue()).getParamNameValuePairs().values())
                .contains(4101L);
        ArgumentCaptor<Wrapper<LessonRecordEntity>> deletedRecordIds = ArgumentCaptor.forClass(Wrapper.class);
        verify(lessonRecordMapper).delete(deletedRecordIds.capture());
        assertThat(deletedRecordIds.getValue().getCustomSqlSegment()).contains("id IN");
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
    void shouldDeletePaidCourseItemAndKeepOrderReceivedAmount() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setOriginalAmount(new BigDecimal("50.00"));
        order.setAmountDue(new BigDecimal("50.00"));
        order.setReceivedAmount(new BigDecimal("50.00"));
        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(2101L);
        item.setPriceAmount(new BigDecimal("50.00"));
        item.setAmountDue(new BigDecimal("50.00"));
        item.setPaidAmount(new BigDecimal("50.00"));
        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(paymentAllocationMapper.selectList(any())).thenReturn(List.of());
        when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);
        when(receiptRecordMapper.selectList(any())).thenReturn(List.of());

        enrollmentOrderService.deleteOrderItem(2001L, 2101L);

        assertThat(order.getAmountDue()).isEqualByComparingTo("0.00");
        assertThat(order.getReceivedAmount()).isEqualByComparingTo("50.00");
        assertThat(order.getBalanceAmount()).isEqualByComparingTo("-50.00");
        verify(enrollmentOrderItemMapper).deleteById(2101L);
        verify(enrollmentOrderMapper, never()).deleteById(2001L);
        verify(paymentRecordMapper, never()).delete(any());
    }

    @Test
    void shouldKeepLegacyPaymentOnMixedReceiptWhenCourseIsDeleted() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setOriginalAmount(new BigDecimal("200.00"));
        order.setAmountDue(new BigDecimal("200.00"));
        order.setReceivedAmount(new BigDecimal("100.00"));
        EnrollmentOrderItemEntity removed = new EnrollmentOrderItemEntity();
        removed.setId(2101L);
        removed.setPriceAmount(new BigDecimal("100.00"));
        removed.setAmountDue(new BigDecimal("100.00"));
        EnrollmentOrderItemEntity retained = new EnrollmentOrderItemEntity();
        retained.setId(2102L);
        retained.setPriceAmount(new BigDecimal("100.00"));
        retained.setAmountDue(new BigDecimal("100.00"));
        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(removed, retained));
        when(paymentAllocationMapper.selectList(any())).thenReturn(List.of());
        when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);

        enrollmentOrderService.deleteOrderItem(2001L, 2101L);

        // 未分摊的历史付款保留在订单上，删除课程只移除课程及其分摊，不再要求先退款。
        assertThat(order.getAmountDue()).isEqualByComparingTo("100.00");
        assertThat(order.getReceivedAmount()).isEqualByComparingTo("100.00");
        assertThat(order.getBalanceAmount()).isEqualByComparingTo("0.00");
        verify(enrollmentOrderItemMapper).deleteById(2101L);
        verify(paymentAllocationMapper).delete(any());
        verify(paymentRecordMapper, never()).delete(any());
        verify(enrollmentOrderMapper, never()).deleteById(2001L);
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
    void shouldEndOnlySelectedGroupEnrollmentWithoutChangingMoney() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setAmountDue(new BigDecimal("4599.00"));
        order.setReceivedAmount(new BigDecimal("460.00"));
        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(2101L);
        item.setEnrollmentOrderId(2001L);
        item.setClassId(3001L);
        ClassEnrollmentEntity enrollment = new ClassEnrollmentEntity();
        enrollment.setId(4001L);
        enrollment.setEnrollmentOrderItemId(2101L);
        enrollment.setStudentId(101L);
        enrollment.setStatus("ACTIVE");
        ClassEnrollmentEntity otherStudent = new ClassEnrollmentEntity();
        otherStudent.setId(4002L);
        otherStudent.setEnrollmentOrderItemId(2101L);
        otherStudent.setStudentId(102L);
        otherStudent.setStatus("ACTIVE");
        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(enrollmentOrderItemMapper.selectOne(any())).thenReturn(item);
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment, otherStudent));

        var request = new com.classdeduction.system.modules.enrollment.dto.EndCourseRequest();
        request.setOrderItemId(2101L);
        request.setStudentId(101L);
        enrollmentOrderService.endCourse(2001L, request);

        assertThat(enrollment.getStatus()).isEqualTo("WITHDRAWN");
        assertThat(otherStudent.getStatus()).isEqualTo("ACTIVE");
        verify(enrollmentOrderCourseRefundStateMapper).insert(any(
                com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderCourseRefundStateEntity.class));
        verify(enrollmentOrderMapper, never()).updateById(any(EnrollmentOrderEntity.class));
        verify(enrollmentOrderItemMapper, never()).updateById(any(EnrollmentOrderItemEntity.class));
        verify(paymentRecordMapper, never()).insert(any(PaymentRecordEntity.class));
        verify(lessonRecordMapper, never()).delete(any());
        verify(lessonOccurrenceMapper, never()).update(any());
    }

    @Test
    void shouldEndPrivateCourseWithoutChangingHoursOrMoney() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(2101L);
        item.setEnrollmentOrderId(2001L);
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(4101L);
        coursePackage.setPackageStatus("ACTIVE");
        coursePackage.setStudentId(101L);
        coursePackage.setTotalUnits(120);
        coursePackage.setRemainingUnits(30);
        coursePackage.setReceivedAmount(new BigDecimal("460.00"));
        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(enrollmentOrderItemMapper.selectOne(any())).thenReturn(item);
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(coursePackageMapper.selectByIdForUpdate(4101L)).thenReturn(coursePackage);

        var request = new com.classdeduction.system.modules.enrollment.dto.EndCourseRequest();
        request.setOrderItemId(2101L);
        request.setStudentId(101L);
        enrollmentOrderService.endCourse(2001L, request);

        assertThat(coursePackage.getPackageStatus()).isEqualTo("CLOSED");
        assertThat(coursePackage.getTotalUnits()).isEqualTo(120);
        assertThat(coursePackage.getRemainingUnits()).isEqualTo(30);
        assertThat(coursePackage.getReceivedAmount()).isEqualByComparingTo("460.00");
        verify(enrollmentOrderMapper, never()).updateById(any(EnrollmentOrderEntity.class));
        verify(enrollmentOrderItemMapper, never()).updateById(any(EnrollmentOrderItemEntity.class));
        verify(lessonOccurrenceMapper).update(any());
        verify(lessonRecordMapper, never()).delete(any());
    }

    @Test
    void shouldKeepSharedPrivateScheduleWhenAnotherStudentRemains() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(2101L);
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(4101L);
        coursePackage.setPackageStatus("ACTIVE");
        var member = new com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupMemberEntity();
        member.setPackageId(4101L);
        member.setPrivateLessonGroupId(5101L);
        member.setMemberStatus("ACTIVE");
        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(enrollmentOrderItemMapper.selectOne(any())).thenReturn(item);
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(coursePackageMapper.selectByIdForUpdate(4101L)).thenReturn(coursePackage);
        when(privateLessonGroupMemberMapper.selectOne(any())).thenReturn(member);
        when(privateLessonGroupMemberMapper.selectCount(any())).thenReturn(1L);

        var request = new com.classdeduction.system.modules.enrollment.dto.EndCourseRequest();
        request.setOrderItemId(2101L);
        request.setStudentId(101L);
        enrollmentOrderService.endCourse(2001L, request);

        assertThat(member.getMemberStatus()).isEqualTo("INACTIVE");
        verify(lessonOccurrenceMapper, never()).update(any());
        verify(recurringScheduleRuleMapper, never()).update(any());
        verify(lessonRecordMapper, never()).delete(any());
    }

    @Test
    void shouldNotClosePrivatePackageSharedByMultiplePurchases() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(2101L);
        item.setLogicalCourseId(7101L);
        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(enrollmentOrderItemMapper.selectOne(any())).thenReturn(item);
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(enrollmentOrderItemMapper.selectCount(any())).thenReturn(2L);
        var request = new com.classdeduction.system.modules.enrollment.dto.EndCourseRequest();
        request.setOrderItemId(2101L);
        request.setStudentId(101L);

        assertThatThrownBy(() -> enrollmentOrderService.endCourse(2001L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("LINKED_COURSE_MANUAL_REFUND_REQUIRED");
        verify(coursePackageMapper, never()).updateById(any(CoursePackageEntity.class));
        verify(lessonOccurrenceMapper, never()).update(any());
    }

    @Test
    void shouldRequireCourseForPartialRefundOnMultiCourseReceipt() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setPaymentStatus("PAID");
        order.setReceivedAmount(new BigDecimal("100.00"));
        EnrollmentOrderParticipantEntity first = buildParticipant(1, 101L, new BigDecimal("50.00"));
        EnrollmentOrderParticipantEntity second = buildParticipant(2, 102L, new BigDecimal("50.00"));

        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(enrollmentOrderParticipantMapper.selectList(any())).thenReturn(List.of(first, second));
        when(enrollmentOrderParticipantMapper.selectCount(any())).thenReturn(2L);
        when(enrollmentOrderItemMapper.selectCount(any())).thenReturn(2L);

        com.classdeduction.system.modules.enrollment.dto.RefundRequest request =
                new com.classdeduction.system.modules.enrollment.dto.RefundRequest();
        request.setRefundAmount(new BigDecimal("50.00"));
        request.setPaymentMethod("CASH");
        request.setHandledByName("Amy");

        assertThatThrownBy(() -> enrollmentOrderService.refundOrder(2001L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("REFUND_COURSE_REQUIRED");

        verify(paymentRecordMapper, never()).insert(any(PaymentRecordEntity.class));
        verify(enrollmentOrderMapper, never()).updateById(any(EnrollmentOrderEntity.class));
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
        doAnswer(invocation -> {
            PaymentRecordEntity entity = invocation.getArgument(0);
            entity.setId(8001L);
            return 1;
        }).when(paymentRecordMapper).insert(any(PaymentRecordEntity.class));
        doAnswer(invocation -> {
            ReceiptRecordEntity entity = invocation.getArgument(0);
            entity.setId(9002L);
            return 1;
        }).when(receiptRecordMapper).insert(any(ReceiptRecordEntity.class));

        ReceiptRecordVO receipt = enrollmentOrderService.refundOrder(2001L, request);

        // Negative refund payment record persisted.
        verify(paymentRecordMapper).insert(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getPaymentType()).isEqualTo("REFUND");
        assertThat(paymentCaptor.getValue().getPaymentStatus()).isEqualTo("REFUNDED");
        assertThat(paymentCaptor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("-1100.00"));
        assertThat(paymentCaptor.getValue().getHandledByName()).isEqualTo("Amy");
        assertThat(paymentCaptor.getValue().getHandledByUserId()).isEqualTo(88L);

        ArgumentCaptor<ReceiptRecordEntity> receiptCaptor = ArgumentCaptor.forClass(ReceiptRecordEntity.class);
        verify(receiptRecordMapper).insert(receiptCaptor.capture());
        assertThat(receiptCaptor.getValue().getTemplateCode()).isEqualTo("REFUND_RECEIPT");
        assertThat(receiptCaptor.getValue().getPaymentRecordId()).isEqualTo(8001L);
        assertThat(receipt.templateCode()).isEqualTo("REFUND_RECEIPT");
        assertThat(receipt.id()).isEqualTo(9002L);
        verify(receiptRecordMapper, never()).updateById(any(ReceiptRecordEntity.class));

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
    void shouldKeepPrivateCourseActiveAfterPartialRefundAtConsumedThreshold() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setStudentId(101L);
        order.setStudentNameSnapshot("Student 1");
        order.setCreatedAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PAID");
        order.setReceivedAmount(new BigDecimal("100.00"));
        order.setAmountDue(new BigDecimal("100.00"));
        order.setBalanceAmount(BigDecimal.ZERO);

        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(4401L);
        item.setEnrollmentOrderId(order.getId());
        item.setLineNo(1);
        item.setCourseType("ONE_ON_ONE");
        item.setPriceAmount(new BigDecimal("100.00"));
        item.setDiscountAmount(BigDecimal.ZERO);
        item.setAmountDue(new BigDecimal("100.00"));
        item.setOneOnOneUnits(10);
        item.setItemStatus("ACTIVE");

        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(3001L);
        coursePackage.setEnrollmentOrderId(order.getId());
        coursePackage.setEnrollmentOrderItemId(item.getId());
        coursePackage.setStudentId(order.getStudentId());
        coursePackage.setPackageStatus("ACTIVE");
        coursePackage.setTotalUnits(10);
        coursePackage.setConsumedUnits(2);
        coursePackage.setRemainingUnits(8);
        coursePackage.setReceivedAmount(new BigDecimal("100.00"));

        PaymentRecordEntity originalPayment = buildPayment(7001L, new BigDecimal("100.00"), 1);
        PaymentAllocationEntity originalAllocation = new PaymentAllocationEntity();
        originalAllocation.setPaymentRecordId(originalPayment.getId());
        originalAllocation.setEnrollmentOrderId(order.getId());
        originalAllocation.setEnrollmentOrderItemId(item.getId());
        originalAllocation.setAmount(new BigDecimal("100.00"));

        LessonRecordEntity usedLesson = new LessonRecordEntity();
        usedLesson.setPackageId(coursePackage.getId());
        usedLesson.setConfirmStage("CONFIRMED");
        usedLesson.setIsDeleted(0);
        usedLesson.setDeductedUnits(2);
        usedLesson.setPlannedStartAt(LocalDateTime.of(2026, 9, 10, 18, 0));

        when(enrollmentOrderMapper.selectById(order.getId())).thenReturn(order);
        when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);
        when(enrollmentOrderItemMapper.selectOne(any())).thenReturn(item);
        when(enrollmentOrderItemMapper.selectById(item.getId())).thenReturn(item);
        when(enrollmentOrderParticipantMapper.selectCount(any())).thenReturn(null);
        when(paymentAllocationMapper.selectList(any())).thenReturn(List.of(originalAllocation));
        when(paymentRecordMapper.selectBatchIds(any())).thenReturn(List.of(originalPayment));
        when(paymentMethodMapper.selectCount(any())).thenReturn(1L);
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(usedLesson));
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of());
        when(jsonHelper.toJson(any())).thenReturn("{\"receiptType\":\"REFUND\"}");
        doAnswer(invocation -> {
            PaymentRecordEntity entity = invocation.getArgument(0);
            entity.setId(8001L);
            return 1;
        }).when(paymentRecordMapper).insert(any(PaymentRecordEntity.class));
        doAnswer(invocation -> {
            ReceiptRecordEntity entity = invocation.getArgument(0);
            entity.setId(9002L);
            return 1;
        }).when(receiptRecordMapper).insert(any(ReceiptRecordEntity.class));

        com.classdeduction.system.modules.enrollment.dto.RefundRequest request =
                new com.classdeduction.system.modules.enrollment.dto.RefundRequest();
        request.setOrderItemId(item.getId());
        request.setRefundAmount(new BigDecimal("80.00"));
        request.setPaymentMethod("CASH");
        request.setHandledByName("Amy");

        enrollmentOrderService.refundOrder(order.getId(), request);

        assertThat(item.getItemStatus()).isEqualTo("ACTIVE");
        assertThat(item.getAmountDue()).isEqualByComparingTo("100.00");
        assertThat(order.getAmountDue()).isEqualByComparingTo("100.00");
        assertThat(order.getBalanceAmount()).isEqualByComparingTo("80.00");
        assertThat(coursePackage.getTotalUnits()).isEqualTo(10);
        assertThat(coursePackage.getRemainingUnits()).isEqualTo(8);
        assertThat(coursePackage.getPackageStatus()).isEqualTo("ACTIVE");
        verify(lessonOccurrenceMapper, never()).update(any());
    }

    @Test
    void shouldEndPrivateCourseWhenFullyRefunded() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setStudentId(101L);
        order.setStudentNameSnapshot("Student 1");
        order.setCreatedAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PAID");
        order.setReceivedAmount(new BigDecimal("100.00"));
        order.setAmountDue(new BigDecimal("100.00"));
        order.setBalanceAmount(BigDecimal.ZERO);

        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(4401L);
        item.setEnrollmentOrderId(order.getId());
        item.setLineNo(1);
        item.setCourseType("ONE_ON_ONE");
        item.setPriceAmount(new BigDecimal("100.00"));
        item.setDiscountAmount(BigDecimal.ZERO);
        item.setAmountDue(new BigDecimal("100.00"));
        item.setOneOnOneUnits(10);
        item.setItemStatus("ACTIVE");

        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(3001L);
        coursePackage.setEnrollmentOrderId(order.getId());
        coursePackage.setEnrollmentOrderItemId(item.getId());
        coursePackage.setStudentId(order.getStudentId());
        coursePackage.setPackageStatus("ACTIVE");
        coursePackage.setTotalUnits(10);
        coursePackage.setConsumedUnits(2);
        coursePackage.setRemainingUnits(8);
        coursePackage.setReceivedAmount(new BigDecimal("100.00"));

        PaymentRecordEntity originalPayment = buildPayment(7001L, new BigDecimal("100.00"), 1);
        PaymentAllocationEntity originalAllocation = new PaymentAllocationEntity();
        originalAllocation.setPaymentRecordId(originalPayment.getId());
        originalAllocation.setEnrollmentOrderId(order.getId());
        originalAllocation.setEnrollmentOrderItemId(item.getId());
        originalAllocation.setAmount(new BigDecimal("100.00"));

        LessonRecordEntity usedLesson = new LessonRecordEntity();
        usedLesson.setPackageId(coursePackage.getId());
        usedLesson.setConfirmStage("CONFIRMED");
        usedLesson.setIsDeleted(0);
        usedLesson.setDeductedUnits(2);
        usedLesson.setPlannedStartAt(LocalDateTime.of(2026, 9, 10, 18, 0));

        when(enrollmentOrderMapper.selectById(order.getId())).thenReturn(order);
        when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);
        when(enrollmentOrderItemMapper.selectOne(any())).thenReturn(item);
        when(enrollmentOrderItemMapper.selectById(item.getId())).thenReturn(item);
        when(enrollmentOrderParticipantMapper.selectCount(any())).thenReturn(null);
        when(paymentAllocationMapper.selectList(any())).thenReturn(List.of(originalAllocation));
        when(paymentRecordMapper.selectBatchIds(any())).thenReturn(List.of(originalPayment));
        when(paymentMethodMapper.selectCount(any())).thenReturn(1L);
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(coursePackageMapper.selectByIdForUpdate(3001L)).thenReturn(coursePackage);
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(usedLesson));
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of());
        when(jsonHelper.toJson(any())).thenReturn("{\"receiptType\":\"REFUND\"}");
        doAnswer(invocation -> {
            PaymentRecordEntity entity = invocation.getArgument(0);
            entity.setId(8001L);
            return 1;
        }).when(paymentRecordMapper).insert(any(PaymentRecordEntity.class));
        doAnswer(invocation -> {
            ReceiptRecordEntity entity = invocation.getArgument(0);
            entity.setId(9002L);
            return 1;
        }).when(receiptRecordMapper).insert(any(ReceiptRecordEntity.class));

        com.classdeduction.system.modules.enrollment.dto.RefundRequest request =
                new com.classdeduction.system.modules.enrollment.dto.RefundRequest();
        request.setOrderItemId(item.getId());
        request.setRefundAmount(new BigDecimal("100.00"));
        request.setPaymentMethod("CASH");
        request.setHandledByName("Amy");

        enrollmentOrderService.refundOrder(order.getId(), request);

        assertThat(item.getItemStatus()).isEqualTo("ACTIVE");
        assertThat(item.getAmountDue()).isEqualByComparingTo("100.00");
        assertThat(order.getAmountDue()).isEqualByComparingTo("100.00");
        assertThat(order.getBalanceAmount()).isEqualByComparingTo("100.00");
        assertThat(coursePackage.getTotalUnits()).isEqualTo(10);
        assertThat(coursePackage.getRemainingUnits()).isEqualTo(8);
        assertThat(coursePackage.getPackageStatus()).isEqualTo("CLOSED");
        verify(lessonOccurrenceMapper).update(any());
    }

    @Test
    void shouldEndPrivateCourseWithPartialRefundWhenRequested() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setStudentId(101L);
        order.setStudentNameSnapshot("Student 1");
        order.setCreatedAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PAID");
        order.setReceivedAmount(new BigDecimal("100.00"));
        order.setAmountDue(new BigDecimal("100.00"));
        order.setBalanceAmount(BigDecimal.ZERO);

        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(4401L);
        item.setEnrollmentOrderId(order.getId());
        item.setLineNo(1);
        item.setCourseType("ONE_ON_ONE");
        item.setPriceAmount(new BigDecimal("100.00"));
        item.setDiscountAmount(BigDecimal.ZERO);
        item.setAmountDue(new BigDecimal("100.00"));
        item.setOneOnOneUnits(10);
        item.setItemStatus("ACTIVE");

        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(3001L);
        coursePackage.setEnrollmentOrderId(order.getId());
        coursePackage.setEnrollmentOrderItemId(item.getId());
        coursePackage.setStudentId(order.getStudentId());
        coursePackage.setPackageStatus("ACTIVE");
        coursePackage.setTotalUnits(10);
        coursePackage.setConsumedUnits(2);
        coursePackage.setRemainingUnits(8);
        coursePackage.setReceivedAmount(new BigDecimal("100.00"));

        PaymentRecordEntity originalPayment = buildPayment(7001L, new BigDecimal("100.00"), 1);
        PaymentAllocationEntity originalAllocation = new PaymentAllocationEntity();
        originalAllocation.setPaymentRecordId(originalPayment.getId());
        originalAllocation.setEnrollmentOrderId(order.getId());
        originalAllocation.setEnrollmentOrderItemId(item.getId());
        originalAllocation.setAmount(new BigDecimal("100.00"));

        LessonRecordEntity usedLesson = new LessonRecordEntity();
        usedLesson.setPackageId(coursePackage.getId());
        usedLesson.setConfirmStage("CONFIRMED");
        usedLesson.setIsDeleted(0);
        usedLesson.setDeductedUnits(2);
        usedLesson.setPlannedStartAt(LocalDateTime.of(2026, 9, 10, 18, 0));

        when(enrollmentOrderMapper.selectById(order.getId())).thenReturn(order);
        when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);
        when(enrollmentOrderItemMapper.selectOne(any())).thenReturn(item);
        when(enrollmentOrderItemMapper.selectById(item.getId())).thenReturn(item);
        when(enrollmentOrderParticipantMapper.selectCount(any())).thenReturn(null);
        when(paymentAllocationMapper.selectList(any())).thenReturn(List.of(originalAllocation));
        when(paymentRecordMapper.selectBatchIds(any())).thenReturn(List.of(originalPayment));
        when(paymentMethodMapper.selectCount(any())).thenReturn(1L);
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(coursePackage));
        when(coursePackageMapper.selectByIdForUpdate(3001L)).thenReturn(coursePackage);
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(usedLesson));
        when(recurringScheduleRuleMapper.selectList(any())).thenReturn(List.of());
        when(jsonHelper.toJson(any())).thenReturn("{\"receiptType\":\"REFUND\"}");
        doAnswer(invocation -> {
            PaymentRecordEntity entity = invocation.getArgument(0);
            entity.setId(8001L);
            return 1;
        }).when(paymentRecordMapper).insert(any(PaymentRecordEntity.class));
        doAnswer(invocation -> {
            ReceiptRecordEntity entity = invocation.getArgument(0);
            entity.setId(9002L);
            return 1;
        }).when(receiptRecordMapper).insert(any(ReceiptRecordEntity.class));

        com.classdeduction.system.modules.enrollment.dto.RefundRequest request =
                new com.classdeduction.system.modules.enrollment.dto.RefundRequest();
        request.setOrderItemId(item.getId());
        request.setRefundAmount(new BigDecimal("80.00"));
        request.setEndCourse(true);
        request.setPaymentMethod("CASH");
        request.setHandledByName("Amy");

        enrollmentOrderService.refundOrder(order.getId(), request);

        assertThat(item.getItemStatus()).isEqualTo("ACTIVE");
        assertThat(item.getAmountDue()).isEqualByComparingTo("100.00");
        assertThat(order.getAmountDue()).isEqualByComparingTo("100.00");
        assertThat(order.getBalanceAmount()).isEqualByComparingTo("80.00");
        assertThat(coursePackage.getTotalUnits()).isEqualTo(10);
        assertThat(coursePackage.getRemainingUnits()).isEqualTo(8);
        assertThat(coursePackage.getPackageStatus()).isEqualTo("CLOSED");
        verify(lessonOccurrenceMapper).update(any());
        // 退款快照也会读取排课规则，这里只要求确实发生过停用所需的查询。
        verify(recurringScheduleRuleMapper, atLeastOnce()).selectList(any());
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

        verify(coursePackageMapper, never()).updateById(any(CoursePackageEntity.class));
        verify(paymentRecordMapper, never()).insert(any(PaymentRecordEntity.class));
        verify(enrollmentOrderMapper, never()).updateById(any(EnrollmentOrderEntity.class));
    }

    @Test
    void shouldCompareRefundSnapshotsByMoneyValueButKeepOtherChangesSignificant() throws Exception {
        ReflectionTestUtils.setField(enrollmentOrderService, "jsonHelper",
                new com.classdeduction.system.common.util.JsonHelper(new ObjectMapper().findAndRegisterModules()));

        EnrollmentOrderEntity amountWithOneDecimal = new EnrollmentOrderEntity();
        amountWithOneDecimal.setReceivedAmount(new BigDecimal("600.0"));
        EnrollmentOrderEntity amountWithTwoDecimals = new EnrollmentOrderEntity();
        amountWithTwoDecimals.setReceivedAmount(new BigDecimal("600.00"));

        Object expected = createRefundStateSnapshot(amountWithOneDecimal, List.of());
        Object current = createRefundStateSnapshot(amountWithTwoDecimals, List.of());
        assertRefundSnapshotsMatch(expected, current, true);

        CoursePackageEntity endedPackage = new CoursePackageEntity();
        endedPackage.setId(4101L);
        endedPackage.setPackageStatus("CLOSED");
        Object laterCourseChange = createRefundStateSnapshot(amountWithTwoDecimals, List.of(endedPackage));
        assertRefundSnapshotsMatch(expected, laterCourseChange, false);
    }

    @Test
    void shouldRestoreCourseAndScheduleWhenUndoingRefundWithDatabaseMoneyScale() throws Exception {
        RefundUndoFixture fixture = stubUndoableLineRefund();
        // 快照可能按业务精度记录 900.0，数据库按 DECIMAL(10,2) 读回 900.00；
        // 撤销前的状态校验必须按数值比较，不能把精度差异当成退款后状态已变化。
        when(enrollmentOrderMapper.selectById(2001L))
                .thenReturn(refundOrderEntity(2001L, "PARTIAL", "900.0"));

        enrollmentOrderService.deleteRefundReceipt(7001L, null);

        ArgumentCaptor<EnrollmentOrderEntity> restoredOrder = ArgumentCaptor.forClass(EnrollmentOrderEntity.class);
        verify(enrollmentOrderMapper).updateById(restoredOrder.capture());
        assertThat(restoredOrder.getValue().getReceivedAmount()).isEqualByComparingTo("1100.00");
        assertThat(fixture.packageAfter.getPackageStatus()).isEqualTo("ACTIVE");
        assertThat(fixture.occurrenceAfter.getPlanningStatus()).isEqualTo("SCHEDULED");
        verify(coursePackageMapper).updateById(fixture.packageAfter);
        verify(lessonOccurrenceMapper).updateById(fixture.occurrenceAfter);
        verify(paymentRecordMapper).deleteById(5001L);
        verify(receiptRecordMapper).deleteById(7001L);
    }

    private Object createRefundStateSnapshot(EnrollmentOrderEntity order, List<CoursePackageEntity> packages)
            throws Exception {
        Class<?> snapshotType = Class.forName(EnrollmentOrderServiceImpl.class.getName() + "$RefundStateSnapshot");
        Constructor<?> constructor = snapshotType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object[] components = { order, List.of(), List.of(), packages, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of() };
        return constructor.newInstance(components);
    }

    private void assertRefundSnapshotsMatch(Object expected, Object current, boolean matches) throws Exception {
        Class<?> snapshotType = expected.getClass();
        Method comparator = EnrollmentOrderServiceImpl.class.getDeclaredMethod(
                "refundSnapshotsMatch", snapshotType, snapshotType);
        comparator.setAccessible(true);
        assertThat(comparator.invoke(enrollmentOrderService, expected, current)).isEqualTo(matches);
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
        EnrollmentOrderItemEntity sharedCourse = new EnrollmentOrderItemEntity();
        sharedCourse.setId(4401L);
        sharedCourse.setEnrollmentOrderId(order.getId());
        sharedCourse.setLineNo(1);
        sharedCourse.setCourseType("ONE_ON_ONE");
        sharedCourse.setPriceAmount(new BigDecimal("375.00"));
        sharedCourse.setAmountDue(new BigDecimal("375.00"));
        sharedCourse.setOneOnOneUnits(20);
        List<PaymentAllocationEntity> allocations = new ArrayList<>();
        for (PaymentRecordEntity payment : payments) {
            PaymentAllocationEntity allocation = new PaymentAllocationEntity();
            allocation.setPaymentRecordId(payment.getId());
            allocation.setEnrollmentOrderId(order.getId());
            allocation.setEnrollmentOrderItemId(sharedCourse.getId());
            allocation.setAmount(payment.getAmount());
            allocations.add(allocation);
        }
        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);
        when(enrollmentOrderParticipantMapper.selectOne(any())).thenReturn(selected);
        when(enrollmentOrderParticipantMapper.selectList(any())).thenReturn(participants);
        when(paymentRecordMapper.selectList(any())).thenAnswer(invocation -> payments);
        when(paymentRecordMapper.selectBatchIds(any())).thenAnswer(invocation -> payments);
        when(paymentAllocationMapper.selectList(any())).thenReturn(allocations);
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(sharedCourse));
        doAnswer(invocation -> {
            PaymentRecordEntity record = invocation.getArgument(0);
            record.setId(9002L);
            payments.add(record);
            return 1;
        }).when(paymentRecordMapper).insert(any(PaymentRecordEntity.class));
        when(coursePackageMapper.selectList(any())).thenReturn(List.of(selectedPackage));

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
        assertThat(order.getAmountDue()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(order.getBalanceAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order.getPaymentStatus()).isEqualTo("PAID");
        assertThat(selected.getAmountDue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order.getOrderStatus()).isEqualTo("CONFIRMED");
        assertThat(selected.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(first.getPaidAmount()).isEqualByComparingTo(new BigDecimal("125.00"));
        assertThat(third.getPaidAmount()).isEqualByComparingTo(new BigDecimal("125.00"));
        assertThat(selectedPackage.getReceivedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(selectedPackage.getPackageStatus()).isEqualTo("CLOSED");
        verify(lessonOccurrenceMapper, never()).update(any());
    }

    @Test
    void partialCourseRefundAtConsumedValueKeepsClassEnrollmentActive() {
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
        originalAllocation.setPaymentRecordId(7001L);
        originalAllocation.setEnrollmentOrderId(order.getId());
        originalAllocation.setEnrollmentOrderItemId(item.getId());
        originalAllocation.setAmount(new BigDecimal("2299.00"));
        PaymentRecordEntity originalPayment = new PaymentRecordEntity();
        originalPayment.setId(7001L);
        originalPayment.setEnrollmentOrderId(order.getId());
        originalPayment.setAmount(new BigDecimal("2299.00"));
        originalPayment.setPaymentStatus("PAID");

        when(enrollmentOrderMapper.selectById(order.getId())).thenReturn(order);
        when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);
        when(enrollmentOrderItemMapper.selectOne(any())).thenReturn(item);
        when(enrollmentOrderItemMapper.selectById(item.getId())).thenReturn(item);
        when(paymentAllocationMapper.selectList(any())).thenReturn(List.of(originalAllocation));
        when(paymentRecordMapper.selectBatchIds(any())).thenReturn(List.of(originalPayment));
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(occurrence));
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(usedLesson));

        com.classdeduction.system.modules.enrollment.dto.RefundRequest request =
                new com.classdeduction.system.modules.enrollment.dto.RefundRequest();
        request.setOrderItemId(item.getId());
        request.setRefundAmount(new BigDecimal("2155.31"));
        request.setPaymentMethod("CASH");
        request.setHandledByName("Amy");

        enrollmentOrderService.refundOrder(order.getId(), request);

        verify(classEnrollmentMapper, never()).updateById(any(ClassEnrollmentEntity.class));
        assertThat(enrollment.getStatus()).isEqualTo("ACTIVE");
        assertThat(item.getItemStatus()).isEqualTo("ACTIVE");
        assertThat(item.getAmountDue()).isEqualByComparingTo("2299.00");
        verify(enrollmentOrderCourseRefundStateMapper, never()).insert(any(
                com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderCourseRefundStateEntity.class));
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
        // 退款快照会读取课包用于记录退款前状态，但部分退款不得修改任何课程产物。
        verify(coursePackageMapper, never()).update(any());
        verify(coursePackageMapper, never()).updateById(any(CoursePackageEntity.class));
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

    @Test
    void shouldLoadOneOnOneOrderDetailWhenItemHasNoClassId() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setStudentId(101L);
        order.setClassId(null);
        order.setOrderStatus("CONFIRMED");
        order.setCurrencyCode("USD");

        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(8001L);
        item.setEnrollmentOrderId(2001L);
        item.setCourseType("ONE_ON_ONE");
        item.setClassId(null);
        item.setDetail("G1-G5 | 60 hours");

        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(studentMapper.selectById(101L)).thenReturn(buildStudent());
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(coursePackageMapper.selectList(any())).thenReturn(List.of());
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of());
        when(receiptRecordMapper.selectList(any())).thenReturn(List.of());

        EnrollmentOrderDetailVO detail = enrollmentOrderService.getOrder(2001L);

        assertThat(detail.items()).singleElement().satisfies(line -> {
            assertThat(line.id()).isEqualTo(8001L);
            assertThat(line.courseType()).isEqualTo("ONE_ON_ONE");
            assertThat(line.classId()).isNull();
            assertThat(line.className()).isNull();
        });
    }

    @Test
    void shouldExposeV2GroupLinksAndClassNameInOrderDetailForReceiptRecovery() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        order.setStudentId(101L);
        order.setClassId(801L);
        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(8001L);
        item.setEnrollmentOrderId(2001L);
        item.setCourseType("GROUP");
        item.setClassId(801L);
        item.setGroupProjectId(9101L);
        item.setGroupRegistrationPlanId(9201L);
        item.setPackageUnit("SESSION");
        item.setPackageQuantity(new BigDecimal("13"));
        ClassEnrollmentEntity enrollment = new ClassEnrollmentEntity();
        enrollment.setEnrollmentOrderItemId(8001L);
        enrollment.setPurchasedQuantity(4);
        enrollment.setStatus("ACTIVE");

        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(order);
        when(studentMapper.selectById(101L)).thenReturn(buildStudent());
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));
        ClassGroupEntity actualClass = buildClassGroup(202L);
        when(classGroupMapper.selectById(801L)).thenReturn(actualClass);
        when(classGroupMapper.selectBatchIds(any())).thenReturn(List.of(actualClass));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of());
        when(receiptRecordMapper.selectList(any())).thenReturn(List.of());

        EnrollmentOrderDetailVO detail = enrollmentOrderService.getOrder(2001L);

        assertThat(detail.items()).singleElement().satisfies(line -> {
            assertThat(line.sessions()).isEqualTo(4);
            assertThat(line.groupProjectId()).isEqualTo(9101L);
            assertThat(line.groupRegistrationPlanId()).isEqualTo(9201L);
            assertThat(line.className()).isEqualTo("SHSAT Group");
        });
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

    // ------------------------------------------------------------------
    // 行级退款撤销（005-undo-refund）测试场景
    // ------------------------------------------------------------------

    /**
     * 构造一笔可撤销的行级退款：订单 2001 / 学生 101 / 课程购买记录 3001 退款 100 元。
     *
     * <p>退款前该课程处于可上课状态，退款后课程停课、课包结课、未来课次取消，
     * 收据中保存了退款前后两份完整快照。</p>
     */
    private RefundUndoFixture stubUndoableLineRefund() throws Exception {
        return stubUndoableLineRefund(LocalDateTime.now().withNano(0).plusDays(3));
    }

    /**
     * 按指定的课次开始时间构造可撤销退款的场景。
     *
     * <p>课次是"退款后过几天才撤销"场景的核心变量：只有撤销提交时仍在未来的课次会被复活，
     * 已经过去的保持取消，因此这里把时间边界开放给各用例。
     */
    private RefundUndoFixture stubUndoableLineRefund(LocalDateTime occurrenceStartAt) throws Exception {
        RefundUndoFixture fixture = new RefundUndoFixture();
        fixture.orderAfter = refundOrderEntity(2001L, "PARTIAL", "900.00");
        fixture.orderBefore = refundOrderEntity(2001L, "PAID", "1100.00");
        fixture.itemAfter = refundItemEntity("STOPPED", "800.00");
        fixture.itemBefore = refundItemEntity("ACTIVE", "1000.00");
        fixture.packageAfter = refundPackageEntity(0, "CLOSED");
        fixture.packageBefore = refundPackageEntity(2, "ACTIVE");
        LocalDateTime futureStart = occurrenceStartAt;
        fixture.occurrenceAfter = refundOccurrenceEntity("CANCELLED", futureStart);
        fixture.occurrenceBefore = refundOccurrenceEntity("SCHEDULED", futureStart);
        fixture.participant = refundParticipantEntity();
        fixture.refund = refundPaymentEntity(futureStart.minusDays(5));
        fixture.originalAllocation = refundAllocationEntity(9000L, "1000.00");
        fixture.refundAllocation = refundAllocationEntity(9001L, "-100.00");

        Object beforeState = refundStateSnapshot(fixture.orderBefore, List.of(fixture.itemBefore),
                List.of(fixture.participant), List.of(fixture.packageBefore), Collections.emptyList(),
                Collections.emptyList(), List.of(fixture.originalAllocation), Collections.emptyList(),
                Collections.emptyList(), List.of(fixture.occurrenceBefore), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        Object afterState = refundStateSnapshot(fixture.orderAfter, List.of(fixture.itemAfter),
                List.of(fixture.participant), List.of(fixture.packageAfter), Collections.emptyList(),
                Collections.emptyList(), List.of(fixture.originalAllocation, fixture.refundAllocation),
                Collections.emptyList(), Collections.emptyList(), List.of(fixture.occurrenceAfter),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        Object undoSnapshot = refundUndoSnapshot(beforeState, afterState, false);

        ReceiptRecordEntity receipt = new ReceiptRecordEntity();
        receipt.setId(7001L);
        receipt.setReceiptNo("RCP-REFUND-7001");
        receipt.setEnrollmentOrderId(2001L);
        receipt.setStudentId(101L);
        receipt.setPaymentRecordId(5001L);
        receipt.setTemplateCode("REFUND_RECEIPT");
        receipt.setReceiptPayload("{}");
        fixture.receipt = receipt;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderItemId", 3001L);
        payload.put("participantIndex", 1);
        payload.put("refundUndoSnapshot", "{\"snapshot\":true}");

        // 读接口：收据、订单、退款流水、课程项、参与人和负向分摊。
        lenient().when(receiptRecordMapper.selectById(7001L)).thenReturn(receipt);
        lenient().when(enrollmentOrderMapper.selectById(2001L)).thenReturn(fixture.orderAfter);
        lenient().when(paymentRecordMapper.selectById(5001L)).thenReturn(fixture.refund);
        lenient().when(enrollmentOrderItemMapper.selectOne(any())).thenReturn(fixture.itemAfter);
        lenient().when(enrollmentOrderParticipantMapper.selectOne(any())).thenReturn(fixture.participant);
        lenient().when(paymentAllocationMapper.selectList(any())).thenAnswer(invocation -> {
            Wrapper<?> wrapper = invocation.getArgument(0);
            if (wrapper == null) {
                return Collections.emptyList();
            }
            // 撤销按退款流水筛分摊；其余查询用于采集退款后状态。
            return queryUsesValue(wrapper, 5001L)
                    ? List.of(fixture.refundAllocation)
                    : List.of(fixture.originalAllocation, fixture.refundAllocation);
        });

        // 退款后状态的采集查询。
        lenient().when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(fixture.itemAfter));
        lenient().when(enrollmentOrderParticipantMapper.selectList(any())).thenReturn(List.of(fixture.participant));
        lenient().when(coursePackageMapper.selectList(any())).thenReturn(List.of(fixture.packageAfter));
        lenient().when(enrollmentOrderCourseRefundStateMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(paymentRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(recurringScheduleRuleMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(classEnrollmentMapper.selectRefundScopeIncludingDeleted(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(lessonOccurrenceMapper.selectList(any())).thenAnswer(invocation -> {
            Wrapper<?> wrapper = invocation.getArgument(0);
            if (wrapper == null) {
                return Collections.emptyList();
            }
            // 资源冲突探测返回空表示该教师/学生时段没有被占用。
            return queryUsesValue(wrapper, 4001L) ? List.of(fixture.occurrenceAfter) : Collections.emptyList();
        });
        lenient().when(jsonHelper.fromJson(any(String.class), any(TypeReference.class))).thenAnswer(invocation -> {
            TypeReference<?> typeReference = invocation.getArgument(1);
            return typeReference.getType().getTypeName().contains("RefundUndoSnapshot") ? undoSnapshot : payload;
        });

        // 恢复写入时逐行读取当前值。
        lenient().when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);
        lenient().when(enrollmentOrderItemMapper.updateById(any(EnrollmentOrderItemEntity.class))).thenReturn(1);
        lenient().when(enrollmentOrderParticipantMapper.updateById(any(EnrollmentOrderParticipantEntity.class))).thenReturn(1);
        lenient().when(coursePackageMapper.updateById(any(CoursePackageEntity.class))).thenReturn(1);
        lenient().when(lessonOccurrenceMapper.updateById(any(LessonOccurrenceEntity.class))).thenReturn(1);
        lenient().when(enrollmentOrderItemMapper.selectById(3001L)).thenReturn(fixture.itemAfter);
        lenient().when(enrollmentOrderParticipantMapper.selectById(20001L)).thenReturn(fixture.participant);
        lenient().when(coursePackageMapper.selectById(4001L)).thenReturn(fixture.packageAfter);
        lenient().when(lessonOccurrenceMapper.selectById(6001L)).thenReturn(fixture.occurrenceAfter);
        lenient().when(paymentAllocationMapper.selectById(9000L)).thenReturn(fixture.originalAllocation);
        lenient().when(paymentAllocationMapper.selectById(9001L)).thenReturn(fixture.refundAllocation);
        lenient().when(paymentAllocationMapper.deleteById(9001L)).thenReturn(1);
        lenient().when(paymentRecordMapper.deleteById(5001L)).thenReturn(1);
        lenient().when(receiptRecordMapper.deleteById(7001L)).thenReturn(1);
        return fixture;
    }

    private EnrollmentOrderEntity refundOrderEntity(Long id, String paymentStatus, String receivedAmount) {
        BigDecimal received = new BigDecimal(receivedAmount);
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(id);
        order.setEnrollmentNo("EO-2001");
        order.setStudentId(101L);
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus(paymentStatus);
        order.setOriginalAmount(new BigDecimal("1100.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setAmountDue(new BigDecimal("1100.00"));
        order.setReceivedAmount(received);
        order.setBalanceAmount(new BigDecimal("1100.00").subtract(received));
        order.setVersion(3);
        return order;
    }

    private EnrollmentOrderItemEntity refundItemEntity(String itemStatus, String paidAmount) {
        BigDecimal paid = new BigDecimal(paidAmount);
        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(3001L);
        item.setEnrollmentOrderId(2001L);
        item.setLineNo(1);
        item.setItemStatus(itemStatus);
        item.setAmountDue(new BigDecimal("1000.00"));
        item.setPaidAmount(paid);
        item.setBalanceAmount(new BigDecimal("1000.00").subtract(paid));
        item.setVersion(2);
        return item;
    }

    private CoursePackageEntity refundPackageEntity(int remainingUnits, String packageStatus) {
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(4001L);
        coursePackage.setPackageNo("PKG-4001");
        coursePackage.setEnrollmentOrderId(2001L);
        coursePackage.setEnrollmentOrderItemId(3001L);
        coursePackage.setStudentId(101L);
        coursePackage.setTotalUnits(10);
        coursePackage.setConsumedUnits(10 - remainingUnits);
        coursePackage.setRemainingUnits(remainingUnits);
        coursePackage.setPackageStatus(packageStatus);
        coursePackage.setVersion(4);
        return coursePackage;
    }

    private LessonOccurrenceEntity refundOccurrenceEntity(String planningStatus, LocalDateTime startAt) {
        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setId(6001L);
        occurrence.setLessonNo("LO-6001");
        occurrence.setPackageId(4001L);
        occurrence.setStudentId(101L);
        occurrence.setPlannedTeacherId(301L);
        occurrence.setPlannedStartAt(startAt);
        occurrence.setPlannedEndAt(startAt.plusMinutes(60));
        occurrence.setPlannedUnits(1);
        occurrence.setPlanningStatus(planningStatus);
        occurrence.setVersion(5);
        return occurrence;
    }

    private EnrollmentOrderParticipantEntity refundParticipantEntity() {
        EnrollmentOrderParticipantEntity participant = new EnrollmentOrderParticipantEntity();
        participant.setId(20001L);
        participant.setEnrollmentOrderId(2001L);
        participant.setParticipantIndex(1);
        participant.setStudentId(101L);
        participant.setStudentNameSnapshot("Olivia Lin");
        participant.setAmountDue(new BigDecimal("1000.00"));
        participant.setPaidAmount(new BigDecimal("800.00"));
        participant.setBalanceAmount(new BigDecimal("200.00"));
        participant.setVersion(1);
        return participant;
    }

    private PaymentRecordEntity refundPaymentEntity(LocalDateTime paidAt) {
        PaymentRecordEntity refund = new PaymentRecordEntity();
        refund.setId(5001L);
        refund.setPaymentNo("PAY-5001");
        refund.setEnrollmentOrderId(2001L);
        refund.setParticipantIndex(1);
        refund.setPaymentType("REFUND");
        refund.setPaymentStatus("REFUNDED");
        refund.setAmount(new BigDecimal("-100.00"));
        refund.setPaidAt(paidAt);
        refund.setVersion(1);
        return refund;
    }

    private PaymentAllocationEntity refundAllocationEntity(Long id, String amount) {
        PaymentAllocationEntity allocation = new PaymentAllocationEntity();
        allocation.setId(id);
        allocation.setEnrollmentOrderId(2001L);
        // 9001 是退款流水生成的负向分摊；9000 是原始收款分摊。
        allocation.setPaymentRecordId(Long.valueOf(9001L).equals(id) ? 5001L : 5000L);
        allocation.setEnrollmentOrderItemId(3001L);
        allocation.setAmount(new BigDecimal(amount));
        allocation.setVersion(1);
        return allocation;
    }

    /** 判断查询条件参数中是否包含指定值，用于区分同一 mapper 上的不同查询。 */
    private static boolean queryUsesValue(Wrapper<?> wrapper, Object value) {
        // getCustomSqlSegment 负责把条件值写入参数表，必须先调用再读取参数。
        wrapper.getCustomSqlSegment();
        return ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().containsValue(value);
    }

    /** 快照记录类型是服务实现内部的私有 record，测试通过反射构造。 */
    private Object refundStateSnapshot(EnrollmentOrderEntity order,
                                       List<EnrollmentOrderItemEntity> items,
                                       List<EnrollmentOrderParticipantEntity> participants,
                                       List<CoursePackageEntity> packages,
                                       List<com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderCourseRefundStateEntity> refundStates,
                                       List<PaymentRecordEntity> payments,
                                       List<PaymentAllocationEntity> allocations,
                                       List<com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupMemberEntity> groupMembers,
                                       List<ClassEnrollmentEntity> classEnrollments,
                                       List<LessonOccurrenceEntity> occurrences,
                                       List<RecurringScheduleRuleEntity> rules,
                                       List<RecurringScheduleSlotEntity> slots,
                                       List<com.classdeduction.system.modules.finance.entity.StudentCreditLedgerEntity> creditLedger)
            throws Exception {
        Class<?> stateType = Class.forName("com.classdeduction.system.modules.enrollment.service.impl."
                + "EnrollmentOrderServiceImpl$RefundStateSnapshot");
        Constructor<?> constructor = stateType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(order, items, participants, packages, refundStates, payments, allocations,
                groupMembers, classEnrollments, occurrences, rules, slots, creditLedger);
    }

    private Object refundUndoSnapshot(Object before, Object after, boolean creditRefund) throws Exception {
        Class<?> undoType = Class.forName("com.classdeduction.system.modules.enrollment.service.impl."
                + "EnrollmentOrderServiceImpl$RefundUndoSnapshot");
        Constructor<?> constructor = undoType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(before, after, null, null, creditRefund);
    }

    /** 测试用退款撤销场景的数据载体。 */
    private static final class RefundUndoFixture {
        EnrollmentOrderEntity orderBefore;
        EnrollmentOrderEntity orderAfter;
        EnrollmentOrderItemEntity itemBefore;
        EnrollmentOrderItemEntity itemAfter;
        CoursePackageEntity packageBefore;
        CoursePackageEntity packageAfter;
        LessonOccurrenceEntity occurrenceBefore;
        LessonOccurrenceEntity occurrenceAfter;
        EnrollmentOrderParticipantEntity participant;
        PaymentRecordEntity refund;
        PaymentAllocationEntity originalAllocation;
        PaymentAllocationEntity refundAllocation;
        ReceiptRecordEntity receipt;
    }
}
