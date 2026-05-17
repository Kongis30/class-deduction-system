package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.enrollment.dto.PaymentRecordSaveRequest;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentRecordEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentRecordMapper;
import com.classdeduction.system.modules.enrollment.service.impl.PaymentRecordServiceImpl;
import com.classdeduction.system.modules.enrollment.vo.PaymentRecordVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentRecordServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
class PaymentRecordServiceImplTest {

    @Mock
    private PaymentRecordMapper paymentRecordMapper;

    @Mock
    private EnrollmentOrderMapper enrollmentOrderMapper;

    @Mock
    private CoursePackageMapper coursePackageMapper;

    @InjectMocks
    private PaymentRecordServiceImpl paymentRecordService;

    private EnrollmentOrderEntity testOrder;
    private PaymentRecordEntity testPayment;
    private CoursePackageEntity testPackage;

    @BeforeEach
    void setUp() {
        testOrder = new EnrollmentOrderEntity();
        testOrder.setId(1L);
        testOrder.setEnrollmentNo("EO202604280001");
        testOrder.setOriginalAmount(new BigDecimal("2699.00"));
        testOrder.setDiscountAmount(new BigDecimal("100.00"));
        testOrder.setReceivedAmount(new BigDecimal("1000.00"));
        testOrder.setPaymentStatus("PARTIAL");

        testPayment = new PaymentRecordEntity();
        testPayment.setId(1L);
        testPayment.setEnrollmentOrderId(1L);
        testPayment.setPaymentNo("PMT202604280001");
        testPayment.setPaymentMethod("CASH");
        testPayment.setPaymentStatus("PAID");
        testPayment.setAmount(new BigDecimal("500.00"));

        testPackage = new CoursePackageEntity();
        testPackage.setId(1L);
        testPackage.setPackageNo("PK202604280001");
        testPackage.setRemainingUnits(40);
    }

    @Nested
    @DisplayName("Create Payment Record Tests")
    class CreatePaymentRecordTests {

        @Test
        @DisplayName("Should create payment record and update order status to PAID")
        void shouldCreatePaymentRecordAndUpdateOrderStatusToPaid() {
            // Given
            testOrder.setReceivedAmount(new BigDecimal("2099.00")); // Will reach total

            PaymentRecordSaveRequest request = new PaymentRecordSaveRequest();
            request.setEnrollmentOrderId(1L);
            request.setAmount(new BigDecimal("500.00"));
            request.setPaymentMethod("CASH");
            request.setHandledByName("Admin");

            when(enrollmentOrderMapper.selectById(1L)).thenReturn(testOrder);
            when(paymentRecordMapper.insert(any(PaymentRecordEntity.class))).thenAnswer(i -> {
                PaymentRecordEntity entity = i.getArgument(0);
                entity.setId(1L);
                return 1;
            });

            // When
            Long result = paymentRecordService.createPaymentRecord(request);

            // Then
            assertThat(result).isEqualTo(1L);

            // Verify payment record was saved
            ArgumentCaptor<PaymentRecordEntity> paymentCaptor = ArgumentCaptor.forClass(PaymentRecordEntity.class);
            verify(paymentRecordMapper).insert(paymentCaptor.capture());
            PaymentRecordEntity savedPayment = paymentCaptor.getValue();
            assertThat(savedPayment.getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
            assertThat(savedPayment.getPaymentMethod()).isEqualTo("CASH");
            assertThat(savedPayment.getPaymentStatus()).isEqualTo("PAID");

            // Verify order was updated
            ArgumentCaptor<EnrollmentOrderEntity> orderCaptor = ArgumentCaptor.forClass(EnrollmentOrderEntity.class);
            verify(enrollmentOrderMapper).updateById(orderCaptor.capture());
            EnrollmentOrderEntity updatedOrder = orderCaptor.getValue();
            assertThat(updatedOrder.getReceivedAmount()).isEqualByComparingTo(new BigDecimal("2599.00")); // 2099 + 500
            assertThat(updatedOrder.getPaymentStatus()).isEqualTo("PAID");
        }

        @Test
        @DisplayName("Should create payment record and update order status to PARTIAL")
        void shouldCreatePaymentRecordAndUpdateOrderStatusToPartial() {
            // Given
            PaymentRecordSaveRequest request = new PaymentRecordSaveRequest();
            request.setEnrollmentOrderId(1L);
            request.setAmount(new BigDecimal("200.00"));
            request.setPaymentMethod("CASH");
            request.setHandledByName("Admin");

            when(enrollmentOrderMapper.selectById(1L)).thenReturn(testOrder);
            when(paymentRecordMapper.insert(any(PaymentRecordEntity.class))).thenAnswer(i -> {
                PaymentRecordEntity entity = i.getArgument(0);
                entity.setId(1L);
                return 1;
            });

            // When
            Long result = paymentRecordService.createPaymentRecord(request);

            // Then
            assertThat(result).isEqualTo(1L);

            ArgumentCaptor<EnrollmentOrderEntity> orderCaptor = ArgumentCaptor.forClass(EnrollmentOrderEntity.class);
            verify(enrollmentOrderMapper).updateById(orderCaptor.capture());
            EnrollmentOrderEntity updatedOrder = orderCaptor.getValue();
            assertThat(updatedOrder.getReceivedAmount()).isEqualByComparingTo(new BigDecimal("1200.00")); // 1000 + 200
            assertThat(updatedOrder.getPaymentStatus()).isEqualTo("PARTIAL");
        }

        @Test
        @DisplayName("Should throw exception when enrollment order not found")
        void shouldThrowExceptionWhenEnrollmentOrderNotFound() {
            // Given
            PaymentRecordSaveRequest request = new PaymentRecordSaveRequest();
            request.setEnrollmentOrderId(999L);
            request.setAmount(new BigDecimal("500.00"));
            request.setPaymentMethod("CASH");
            request.setHandledByName("Admin");

            when(enrollmentOrderMapper.selectById(999L)).thenReturn(null);

            // When/Then
            assertThatThrownBy(() -> paymentRecordService.createPaymentRecord(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("报名订单不存在");
        }

        @Test
        @DisplayName("Should throw exception when amount is negative")
        void shouldThrowExceptionWhenAmountIsNegative() {
            // Given
            PaymentRecordSaveRequest request = new PaymentRecordSaveRequest();
            request.setEnrollmentOrderId(1L);
            request.setAmount(new BigDecimal("-100.00"));
            request.setPaymentMethod("CASH");
            request.setHandledByName("Admin");

            when(enrollmentOrderMapper.selectById(1L)).thenReturn(testOrder);

            // When/Then
            assertThatThrownBy(() -> paymentRecordService.createPaymentRecord(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("支付金额不能为负数");
        }
    }

    @Nested
    @DisplayName("Get Payment Record Tests")
    class GetPaymentRecordTests {

        @Test
        @DisplayName("Should return payment record VO when found")
        void shouldReturnPaymentRecordVOWhenFound() {
            // Given
            when(paymentRecordMapper.selectById(1L)).thenReturn(testPayment);

            // When
            PaymentRecordVO result = paymentRecordService.getPaymentRecord(1L);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.paymentNo()).isEqualTo("PMT202604280001");
            assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("500.00"));
        }

        @Test
        @DisplayName("Should throw exception when payment record not found")
        void shouldThrowExceptionWhenPaymentRecordNotFound() {
            // Given
            when(paymentRecordMapper.selectById(999L)).thenReturn(null);

            // When/Then
            assertThatThrownBy(() -> paymentRecordService.getPaymentRecord(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("支付记录不存在");
        }
    }

    @Nested
    @DisplayName("List By Enrollment Order ID Tests")
    class ListByEnrollmentOrderIdTests {

        @Test
        @DisplayName("Should return empty list when no payments found")
        void shouldReturnEmptyListWhenNoPaymentsFound() {
            // Given
            when(paymentRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

            // When
            List<PaymentRecordVO> result = paymentRecordService.listByEnrollmentOrderId(1L);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list when enrollment order id is null")
        void shouldReturnEmptyListWhenEnrollmentOrderIdIsNull() {
            // When
            List<PaymentRecordVO> result = paymentRecordService.listByEnrollmentOrderId(null);

            // Then
            assertThat(result).isEmpty();
            verify(paymentRecordMapper, never()).selectList(any());
        }
    }

    @Nested
    @DisplayName("Get Package Balance Tests")
    class GetPackageBalanceTests {

        @Test
        @DisplayName("Should return remaining units")
        void shouldReturnRemainingUnits() {
            // Given
            when(coursePackageMapper.selectById(1L)).thenReturn(testPackage);

            // When
            Integer result = paymentRecordService.getPackageBalance(1L);

            // Then
            assertThat(result).isEqualTo(40);
        }

        @Test
        @DisplayName("Should throw exception when package not found")
        void shouldThrowExceptionWhenPackageNotFound() {
            // Given
            when(coursePackageMapper.selectById(999L)).thenReturn(null);

            // When/Then
            assertThatThrownBy(() -> paymentRecordService.getPackageBalance(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("课时包不存在");
        }
    }
}
