package com.classdeduction.system.modules.enrollment.service.allocation;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentAllocationEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentRecordEntity;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentAllocationMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentRecordMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentAllocationServiceTest {

    @Mock
    private PaymentAllocationMapper paymentAllocationMapper;

    @Mock
    private PaymentRecordMapper paymentRecordMapper;

    @Mock
    private EnrollmentOrderItemMapper enrollmentOrderItemMapper;

    @InjectMocks
    private PaymentAllocationService paymentAllocationService;

    @BeforeAll
    static void initializeMapperLambdaMetadata() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, EnrollmentOrderEntity.class);
        TableInfoHelper.initTableInfo(assistant, EnrollmentOrderItemEntity.class);
        TableInfoHelper.initTableInfo(assistant, PaymentRecordEntity.class);
        TableInfoHelper.initTableInfo(assistant, PaymentAllocationEntity.class);
    }

    @Test
    void replayPreservesSavedCourseAllocationInsteadOfRedistributingIt() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(1L);
        order.setTuitionSubtotalAmount(new BigDecimal("200.00"));
        order.setAmountDue(new BigDecimal("200.00"));
        EnrollmentOrderItemEntity first = item(21L, 1);
        EnrollmentOrderItemEntity second = item(22L, 2);
        PaymentRecordEntity payment = new PaymentRecordEntity();
        payment.setId(501L);
        payment.setEnrollmentOrderId(1L);
        payment.setAmount(new BigDecimal("70.00"));
        payment.setPaidAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        payment.setPaymentStatus("PAID");
        PaymentAllocationEntity saved = new PaymentAllocationEntity();
        saved.setPaymentRecordId(501L);
        saved.setEnrollmentOrderId(1L);
        saved.setEnrollmentOrderItemId(22L);
        saved.setAmount(new BigDecimal("70.00"));

        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(first, second));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment));
        when(paymentAllocationMapper.selectList(any())).thenReturn(List.of(saved));

        paymentAllocationService.replayAllocations(order);

        ArgumentCaptor<EnrollmentOrderItemEntity> itemCaptor =
                ArgumentCaptor.forClass(EnrollmentOrderItemEntity.class);
        verify(enrollmentOrderItemMapper, org.mockito.Mockito.times(2)).updateById(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues())
                .filteredOn(item -> item.getId().equals(21L))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getPaidAmount()).isEqualByComparingTo("0.00");
                    assertThat(item.getBalanceAmount()).isEqualByComparingTo("100.00");
                });
        assertThat(itemCaptor.getAllValues())
                .filteredOn(item -> item.getId().equals(22L))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getPaidAmount()).isEqualByComparingTo("70.00");
                    assertThat(item.getBalanceAmount()).isEqualByComparingTo("30.00");
                });
        verify(paymentAllocationMapper, never()).insert(any(PaymentAllocationEntity.class));
        verify(paymentAllocationMapper, never()).delete(any());
    }

    @Test
    void replayUsesCourseFeeSnapshotsWhenReceiptEditRebuildsMissingItemDue() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2L);
        order.setTuitionSubtotalAmount(new BigDecimal("200.00"));
        order.setPickupFeeAmount(new BigDecimal("300.00"));
        order.setAmountDue(new BigDecimal("500.00"));

        EnrollmentOrderItemEntity first = item(31L, 1);
        first.setAmountDue(null);
        first.setPickupRequiredSnapshot(true);
        first.setPickupFeeAmount(new BigDecimal("300.00"));
        EnrollmentOrderItemEntity second = item(32L, 2);
        second.setAmountDue(null);

        PaymentRecordEntity payment = new PaymentRecordEntity();
        payment.setId(601L);
        payment.setEnrollmentOrderId(2L);
        payment.setAmount(new BigDecimal("400.00"));
        payment.setPaidAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        payment.setPaymentStatus("PAID");
        PaymentAllocationEntity saved = new PaymentAllocationEntity();
        saved.setPaymentRecordId(601L);
        saved.setEnrollmentOrderId(2L);
        saved.setEnrollmentOrderItemId(31L);
        saved.setAmount(new BigDecimal("400.00"));

        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(first, second));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(payment));
        when(paymentAllocationMapper.selectList(any())).thenReturn(List.of(saved));

        paymentAllocationService.replayAllocations(order);

        ArgumentCaptor<EnrollmentOrderItemEntity> itemCaptor =
                ArgumentCaptor.forClass(EnrollmentOrderItemEntity.class);
        verify(enrollmentOrderItemMapper, org.mockito.Mockito.times(4)).updateById(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues())
                .filteredOn(item -> item.getId().equals(31L))
                .last()
                .satisfies(item -> {
                    assertThat(item.getAmountDue()).isEqualByComparingTo("400.00");
                    assertThat(item.getPaidAmount()).isEqualByComparingTo("400.00");
                    assertThat(item.getBalanceAmount()).isEqualByComparingTo("0.00");
                });
        assertThat(itemCaptor.getAllValues())
                .filteredOn(item -> item.getId().equals(32L))
                .last()
                .satisfies(item -> {
                    assertThat(item.getAmountDue()).isEqualByComparingTo("100.00");
                    assertThat(item.getPaidAmount()).isEqualByComparingTo("0.00");
                    assertThat(item.getBalanceAmount()).isEqualByComparingTo("100.00");
                });
        verify(paymentAllocationMapper, never()).insert(any(PaymentAllocationEntity.class));
    }

    @Test
    void replayRebuildsWholeDollarCourseBalancesBeforeAllocatingLaterPayment() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(4L);
        order.setBillingPolicy("WHOLE_DOLLAR_V1");
        order.setPickupFeeAmount(new BigDecimal("1.00"));
        order.setAmountDue(new BigDecimal("201.00"));
        EnrollmentOrderItemEntity first = item(51L, 1);
        EnrollmentOrderItemEntity second = item(52L, 2);
        first.setAmountDue(null);
        second.setAmountDue(null);
        PaymentRecordEntity laterPayment = payment(801L, "1.00", "PAID", 3);
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(first, second));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(laterPayment));
        when(paymentAllocationMapper.selectList(any())).thenReturn(List.of());

        paymentAllocationService.replayAllocations(order);

        assertThat(first.getAmountDue()).isEqualByComparingTo("100.00");
        assertThat(second.getAmountDue()).isEqualByComparingTo("101.00");
        assertThat(first.getPaidAmount()).isEqualByComparingTo("1.00");
        assertThat(second.getPaidAmount()).isEqualByComparingTo("0.00");
        ArgumentCaptor<PaymentAllocationEntity> allocationCaptor =
                ArgumentCaptor.forClass(PaymentAllocationEntity.class);
        verify(paymentAllocationMapper).insert(allocationCaptor.capture());
        assertThat(allocationCaptor.getValue().getAmount()).isEqualByComparingTo("1.00");
    }

    @Test
    void replayPreservesCentFeeSharesForHistoricalOrder() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(5L);
        order.setPickupFeeAmount(new BigDecimal("1.00"));
        order.setAmountDue(new BigDecimal("201.00"));
        EnrollmentOrderItemEntity first = item(61L, 1);
        EnrollmentOrderItemEntity second = item(62L, 2);
        first.setAmountDue(null);
        second.setAmountDue(null);
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(first, second));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of());
        when(paymentAllocationMapper.selectList(any())).thenReturn(List.of());

        paymentAllocationService.replayAllocations(order);

        assertThat(first.getAmountDue()).isEqualByComparingTo("100.50");
        assertThat(second.getAmountDue()).isEqualByComparingTo("100.50");
    }

    @Test
    void replayKeepsStoppedCoursePaidAtConsumedAmountAndLeavesOtherCourseUntouched() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(3L);
        order.setAmountDue(new BigDecimal("840.00"));
        order.setTuitionSubtotalAmount(new BigDecimal("2799.00"));

        EnrollmentOrderItemEntity stoppedCourse = item(41L, 1);
        stoppedCourse.setPriceAmount(new BigDecimal("2299.00"));
        stoppedCourse.setAmountDue(new BigDecimal("340.00"));
        stoppedCourse.setItemStatus("STOPPED");
        EnrollmentOrderItemEntity activeCourse = item(42L, 2);
        activeCourse.setPriceAmount(new BigDecimal("500.00"));
        activeCourse.setAmountDue(new BigDecimal("500.00"));

        PaymentRecordEntity purchase = payment(701L, "2799.00", "PAID", 1);
        PaymentRecordEntity refund = payment(702L, "-1959.00", "REFUNDED", 2);
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(stoppedCourse, activeCourse));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(purchase, refund));
        when(paymentAllocationMapper.selectList(any())).thenReturn(List.of(
                allocation(701L, 41L, "2299.00"),
                allocation(701L, 42L, "500.00"),
                allocation(702L, 41L, "-1959.00")));

        paymentAllocationService.replayAllocations(order);

        ArgumentCaptor<EnrollmentOrderItemEntity> itemCaptor =
                ArgumentCaptor.forClass(EnrollmentOrderItemEntity.class);
        verify(enrollmentOrderItemMapper, org.mockito.Mockito.times(2)).updateById(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues()).filteredOn(item -> item.getId().equals(41L)).singleElement()
                .satisfies(item -> {
                    assertThat(item.getPaidAmount()).isEqualByComparingTo("340.00");
                    assertThat(item.getBalanceAmount()).isEqualByComparingTo("0.00");
                });
        assertThat(itemCaptor.getAllValues()).filteredOn(item -> item.getId().equals(42L)).singleElement()
                .satisfies(item -> {
                    assertThat(item.getPaidAmount()).isEqualByComparingTo("500.00");
                    assertThat(item.getBalanceAmount()).isEqualByComparingTo("0.00");
                });
    }

    private PaymentRecordEntity payment(Long id, String amount, String status, int day) {
        PaymentRecordEntity payment = new PaymentRecordEntity();
        payment.setId(id);
        payment.setEnrollmentOrderId(3L);
        payment.setAmount(new BigDecimal(amount));
        payment.setPaidAt(LocalDateTime.of(2026, 9, day, 10, 0));
        payment.setPaymentStatus(status);
        return payment;
    }

    private PaymentAllocationEntity allocation(Long paymentId, Long itemId, String amount) {
        PaymentAllocationEntity allocation = new PaymentAllocationEntity();
        allocation.setPaymentRecordId(paymentId);
        allocation.setEnrollmentOrderId(3L);
        allocation.setEnrollmentOrderItemId(itemId);
        allocation.setAmount(new BigDecimal(amount));
        return allocation;
    }

    private EnrollmentOrderItemEntity item(Long id, int lineNo) {
        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(id);
        item.setEnrollmentOrderId(1L);
        item.setLineNo(lineNo);
        item.setPriceAmount(new BigDecimal("100.00"));
        item.setDiscountAmount(BigDecimal.ZERO);
        item.setAmountDue(new BigDecimal("100.00"));
        item.setPaidAmount(BigDecimal.ZERO);
        item.setBalanceAmount(new BigDecimal("100.00"));
        return item;
    }
}
