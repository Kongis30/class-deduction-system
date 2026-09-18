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
