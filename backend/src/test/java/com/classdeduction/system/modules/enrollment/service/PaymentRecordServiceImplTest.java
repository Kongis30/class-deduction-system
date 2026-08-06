package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.modules.enrollment.dto.PaymentRecordSaveRequest;
import com.classdeduction.system.modules.enrollment.dto.PaymentSplitDTO;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentRecordEntity;
import com.classdeduction.system.modules.enrollment.entity.ReceiptRecordEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentMethodMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentRecordMapper;
import com.classdeduction.system.modules.enrollment.mapper.ReceiptRecordMapper;
import com.classdeduction.system.modules.enrollment.service.impl.PaymentRecordServiceImpl;
import com.classdeduction.system.modules.enrollment.vo.PaymentRecordVO;
import com.classdeduction.system.modules.enrollment.vo.ReceiptRecordVO;
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
import java.time.LocalDateTime;
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

    @Mock
    private PaymentMethodMapper paymentMethodMapper;

    @Mock
    private ReceiptRecordMapper receiptRecordMapper;

    @Mock
    private JsonHelper jsonHelper;

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
        testOrder.setStudentId(601L);
        testOrder.setStudentNameSnapshot("Amy Wang");
        testOrder.setCurrencyCode("USD");
        testOrder.setOriginalAmount(new BigDecimal("2699.00"));
        testOrder.setDiscountAmount(new BigDecimal("100.00"));
        testOrder.setAmountDue(new BigDecimal("2599.00"));
        testOrder.setReceivedAmount(new BigDecimal("1000.00"));
        testOrder.setBalanceAmount(new BigDecimal("1599.00"));
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
        lenient().when(paymentMethodMapper.selectCount(any())).thenReturn(1L);
    }

    @Nested
    @DisplayName("Create Payment Record Tests")
    class CreatePaymentRecordTests {

        @Test
        @DisplayName("Should create payment record and update order status to PAID")
        void shouldCreatePaymentRecordAndUpdateOrderStatusToPaid() {
            // Given
            testOrder.setReceivedAmount(new BigDecimal("2099.00")); // Will reach total
            testOrder.setBalanceAmount(new BigDecimal("500.00"));

            PaymentRecordSaveRequest request = new PaymentRecordSaveRequest();
            request.setEnrollmentOrderId(1L);
            request.setSplits(List.of(new PaymentSplitDTO("CASH", new BigDecimal("500.00"))));
            request.setHandledByName("Admin");

            when(enrollmentOrderMapper.selectById(1L)).thenReturn(testOrder);
            when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);
            when(paymentRecordMapper.insert(any(PaymentRecordEntity.class))).thenAnswer(i -> {
                PaymentRecordEntity entity = i.getArgument(0);
                entity.setId(1L);
                return 1;
            });
            when(jsonHelper.toJson(any())).thenReturn("{\"receiptType\":\"PAYMENT\"}");
            when(receiptRecordMapper.insert(any(ReceiptRecordEntity.class))).thenAnswer(i -> {
                ReceiptRecordEntity entity = i.getArgument(0);
                entity.setId(9001L);
                return 1;
            });

            // When
            ReceiptRecordVO receipt = paymentRecordService.createPaymentRecord(request);

            // Then
            // Verify payment record was saved
            ArgumentCaptor<PaymentRecordEntity> paymentCaptor = ArgumentCaptor.forClass(PaymentRecordEntity.class);
            verify(paymentRecordMapper).insert(paymentCaptor.capture());
            PaymentRecordEntity savedPayment = paymentCaptor.getValue();
            assertThat(savedPayment.getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
            assertThat(savedPayment.getPaymentMethod()).isEqualTo("CASH");
            assertThat(savedPayment.getPaymentType()).isEqualTo("PAYMENT");
            assertThat(savedPayment.getPaymentStatus()).isEqualTo("PAID");

            // Verify order was updated
            ArgumentCaptor<EnrollmentOrderEntity> orderCaptor = ArgumentCaptor.forClass(EnrollmentOrderEntity.class);
            verify(enrollmentOrderMapper).updateById(orderCaptor.capture());
            EnrollmentOrderEntity updatedOrder = orderCaptor.getValue();
            assertThat(updatedOrder.getReceivedAmount()).isEqualByComparingTo(new BigDecimal("2599.00")); // 2099 + 500
            assertThat(updatedOrder.getBalanceAmount()).isEqualByComparingTo(new BigDecimal("0.00"));
            assertThat(updatedOrder.getPaymentStatus()).isEqualTo("PAID");

            ArgumentCaptor<ReceiptRecordEntity> receiptCaptor = ArgumentCaptor.forClass(ReceiptRecordEntity.class);
            verify(receiptRecordMapper).insert(receiptCaptor.capture());
            assertThat(receiptCaptor.getValue().getTemplateCode()).isEqualTo("PAYMENT_RECEIPT");
            assertThat(receiptCaptor.getValue().getEnrollmentOrderId()).isEqualTo(1L);
            assertThat(receipt.id()).isEqualTo(9001L);
            assertThat(receipt.templateCode()).isEqualTo("PAYMENT_RECEIPT");
        }

        @Test
        @DisplayName("Should create payment record and update order status to PARTIAL")
        void shouldCreatePaymentRecordAndUpdateOrderStatusToPartial() {
            // Given
            PaymentRecordSaveRequest request = new PaymentRecordSaveRequest();
            request.setEnrollmentOrderId(1L);
            request.setSplits(List.of(new PaymentSplitDTO("CASH", new BigDecimal("200.00"))));
            request.setHandledByName("Admin");

            when(enrollmentOrderMapper.selectById(1L)).thenReturn(testOrder);
            when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);
            when(paymentRecordMapper.insert(any(PaymentRecordEntity.class))).thenAnswer(i -> {
                PaymentRecordEntity entity = i.getArgument(0);
                entity.setId(1L);
                return 1;
            });

            // When
            paymentRecordService.createPaymentRecord(request);

            // Then
            ArgumentCaptor<EnrollmentOrderEntity> orderCaptor = ArgumentCaptor.forClass(EnrollmentOrderEntity.class);
            verify(enrollmentOrderMapper).updateById(orderCaptor.capture());
            EnrollmentOrderEntity updatedOrder = orderCaptor.getValue();
            assertThat(updatedOrder.getReceivedAmount()).isEqualByComparingTo(new BigDecimal("1200.00")); // 1000 + 200
            assertThat(updatedOrder.getBalanceAmount()).isEqualByComparingTo(new BigDecimal("1399.00"));
            assertThat(updatedOrder.getPaymentStatus()).isEqualTo("PARTIAL");
        }

        @Test
        @DisplayName("Should persist split-level payment details in one order update")
        void shouldPersistSplitLevelPaymentDetailsInOneOrderUpdate() {
            PaymentSplitDTO cash = new PaymentSplitDTO("CASH", new BigDecimal("200.00"));
            cash.setPaidAt(LocalDateTime.of(2026, 4, 20, 0, 0));
            cash.setHandledByName("Amy");
            cash.setRemark("First installment");
            PaymentSplitDTO check = new PaymentSplitDTO("CHECK", new BigDecimal("300.00"));
            check.setPaidAt(LocalDateTime.of(2026, 4, 21, 0, 0));
            check.setHandledByName("Ben");
            check.setRemark("Second installment");

            PaymentRecordSaveRequest request = new PaymentRecordSaveRequest();
            request.setEnrollmentOrderId(1L);
            request.setSplits(List.of(cash, check));

            when(enrollmentOrderMapper.selectById(1L)).thenReturn(testOrder);
            when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);

            paymentRecordService.createPaymentRecord(request);

            ArgumentCaptor<PaymentRecordEntity> paymentCaptor =
                    ArgumentCaptor.forClass(PaymentRecordEntity.class);
            verify(paymentRecordMapper, times(2)).insert(paymentCaptor.capture());
            assertThat(paymentCaptor.getAllValues())
                    .extracting(PaymentRecordEntity::getHandledByName)
                    .containsExactly("Amy", "Ben");
            assertThat(paymentCaptor.getAllValues())
                    .extracting(PaymentRecordEntity::getRemark)
                    .containsExactly("First installment", "Second installment");
            assertThat(paymentCaptor.getAllValues())
                    .extracting(PaymentRecordEntity::getPaidAt)
                    .containsExactly(cash.getPaidAt(), check.getPaidAt());
            verify(enrollmentOrderMapper).updateById(any(EnrollmentOrderEntity.class));
            assertThat(testOrder.getReceivedAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));
            assertThat(testOrder.getBalanceAmount()).isEqualByComparingTo(new BigDecimal("1099.00"));
        }

        @Test
        @DisplayName("Should inherit request-level payment defaults when split fields are omitted")
        void shouldInheritRequestLevelPaymentDefaultsWhenSplitFieldsAreOmitted() {
            LocalDateTime requestPaidAt = LocalDateTime.of(2026, 4, 22, 10, 30);
            LocalDateTime cardPaidAt = LocalDateTime.of(2026, 4, 23, 11, 45);
            PaymentSplitDTO cash = new PaymentSplitDTO("CASH", new BigDecimal("200.00"));
            PaymentSplitDTO card = new PaymentSplitDTO("CARD", new BigDecimal("300.00"));
            card.setPaidAt(cardPaidAt);
            card.setHandledByName(" Ben ");
            card.setRemark(" Card authorization ");

            PaymentRecordSaveRequest request = new PaymentRecordSaveRequest();
            request.setEnrollmentOrderId(1L);
            request.setPaidAt(requestPaidAt);
            request.setHandledByUserId(999L);
            request.setHandledByName(" Front Desk ");
            request.setRemark("Request-level payment note");
            request.setSplits(List.of(cash, card));

            when(enrollmentOrderMapper.selectById(1L)).thenReturn(testOrder);
            when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);

            paymentRecordService.createPaymentRecord(request);

            ArgumentCaptor<PaymentRecordEntity> paymentCaptor =
                    ArgumentCaptor.forClass(PaymentRecordEntity.class);
            verify(paymentRecordMapper, times(2)).insert(paymentCaptor.capture());
            List<PaymentRecordEntity> savedPayments = paymentCaptor.getAllValues();
            assertThat(savedPayments)
                    .extracting(PaymentRecordEntity::getPaidAt)
                    .containsExactly(requestPaidAt, cardPaidAt);
            assertThat(savedPayments)
                    .extracting(PaymentRecordEntity::getHandledByName)
                    .containsExactly("Front Desk", "Ben");
            assertThat(savedPayments)
                    .extracting(PaymentRecordEntity::getRemark)
                    .containsExactly("Request-level payment note", "Card authorization");
            assertThat(savedPayments)
                    .extracting(PaymentRecordEntity::getHandledByUserId)
                    .containsExactly(999L, 999L);
        }

        @Test
        @DisplayName("Should retain package allocations across multiple subsequent order payments")
        void shouldRetainPackageAllocationsAcrossMultipleSubsequentOrderPayments() {
            CoursePackageEntity firstPackage = new CoursePackageEntity();
            firstPackage.setId(11L);
            firstPackage.setReceivedAmount(new BigDecimal("600.00"));
            CoursePackageEntity secondPackage = new CoursePackageEntity();
            secondPackage.setId(12L);
            secondPackage.setReceivedAmount(new BigDecimal("400.00"));

            when(enrollmentOrderMapper.selectById(1L)).thenReturn(testOrder);
            when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);
            when(paymentRecordMapper.insert(any(PaymentRecordEntity.class))).thenReturn(1);

            PaymentRecordSaveRequest firstPayment = new PaymentRecordSaveRequest();
            firstPayment.setEnrollmentOrderId(1L);
            firstPayment.setSplits(List.of(new PaymentSplitDTO("CASH", new BigDecimal("200.00"))));
            firstPayment.setHandledByName("Admin");
            paymentRecordService.createPaymentRecord(firstPayment);

            assertThat(testOrder.getReceivedAmount()).isEqualByComparingTo(new BigDecimal("1200.00"));
            assertThat(testOrder.getBalanceAmount()).isEqualByComparingTo(new BigDecimal("1399.00"));
            assertThat(testOrder.getPaymentStatus()).isEqualTo("PARTIAL");

            PaymentRecordSaveRequest secondPayment = new PaymentRecordSaveRequest();
            secondPayment.setEnrollmentOrderId(1L);
            secondPayment.setSplits(List.of(new PaymentSplitDTO("CARD", new BigDecimal("300.00"))));
            secondPayment.setHandledByName("Admin");
            paymentRecordService.createPaymentRecord(secondPayment);

            assertThat(testOrder.getReceivedAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));
            assertThat(testOrder.getBalanceAmount()).isEqualByComparingTo(new BigDecimal("1099.00"));
            assertThat(testOrder.getPaymentStatus()).isEqualTo("PARTIAL");
            assertThat(firstPackage.getReceivedAmount()).isEqualByComparingTo(new BigDecimal("600.00"));
            assertThat(secondPackage.getReceivedAmount()).isEqualByComparingTo(new BigDecimal("400.00"));
            verify(paymentRecordMapper, times(2)).insert(any(PaymentRecordEntity.class));
            verify(coursePackageMapper, never()).selectList(any());
            verify(coursePackageMapper, never()).updateById(any(CoursePackageEntity.class));
        }

        @Test
        @DisplayName("Should reject payment on a cancelled order")
        void shouldRejectPaymentOnCancelledOrder() {
            testOrder.setOrderStatus("CANCELLED");
            PaymentRecordSaveRequest request = new PaymentRecordSaveRequest();
            request.setEnrollmentOrderId(1L);
            request.setSplits(List.of(new PaymentSplitDTO("CASH", new BigDecimal("100.00"))));
            request.setHandledByName("Admin");
            when(enrollmentOrderMapper.selectById(1L)).thenReturn(testOrder);

            assertThatThrownBy(() -> paymentRecordService.createPaymentRecord(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Cannot collect payment on a cancelled order.");
            verify(paymentRecordMapper, never()).insert(any(PaymentRecordEntity.class));
        }

        @Test
        @DisplayName("Should reject payment on a refunded order")
        void shouldRejectPaymentOnRefundedOrder() {
            testOrder.setPaymentStatus("REFUNDED");
            PaymentRecordSaveRequest request = new PaymentRecordSaveRequest();
            request.setEnrollmentOrderId(1L);
            request.setSplits(List.of(new PaymentSplitDTO("CASH", new BigDecimal("100.00"))));
            request.setHandledByName("Admin");
            when(enrollmentOrderMapper.selectById(1L)).thenReturn(testOrder);

            assertThatThrownBy(() -> paymentRecordService.createPaymentRecord(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Cannot collect payment on a refunded order.");
            verify(paymentRecordMapper, never()).insert(any(PaymentRecordEntity.class));
        }

        @Test
        @DisplayName("Should throw exception when enrollment order not found")
        void shouldThrowExceptionWhenEnrollmentOrderNotFound() {
            // Given
            PaymentRecordSaveRequest request = new PaymentRecordSaveRequest();
            request.setEnrollmentOrderId(999L);
            request.setSplits(List.of(new PaymentSplitDTO("CASH", new BigDecimal("500.00"))));
            request.setHandledByName("Admin");

            when(enrollmentOrderMapper.selectById(999L)).thenReturn(null);

            // When/Then
            assertThatThrownBy(() -> paymentRecordService.createPaymentRecord(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Enrollment order not found");
        }

        @Test
        @DisplayName("Should throw exception when amount is negative")
        void shouldThrowExceptionWhenAmountIsNegative() {
            // Given
            PaymentRecordSaveRequest request = new PaymentRecordSaveRequest();
            request.setEnrollmentOrderId(1L);
            request.setSplits(List.of(new PaymentSplitDTO("CASH", new BigDecimal("-100.00"))));
            request.setHandledByName("Admin");

            when(enrollmentOrderMapper.selectById(1L)).thenReturn(testOrder);

            // When/Then
            assertThatThrownBy(() -> paymentRecordService.createPaymentRecord(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Each split amount must be greater than 0");
        }

        @Test
        @DisplayName("Should reject payment amounts with more than two decimal places")
        void shouldRejectPaymentAmountWithExcessDecimalPlaces() {
            PaymentRecordSaveRequest request = new PaymentRecordSaveRequest();
            request.setEnrollmentOrderId(1L);
            request.setSplits(List.of(new PaymentSplitDTO("CASH", new BigDecimal("0.015"))));
            request.setHandledByName("Admin");
            when(enrollmentOrderMapper.selectById(1L)).thenReturn(testOrder);

            assertThatThrownBy(() -> paymentRecordService.createPaymentRecord(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("INVALID_AMOUNT_SCALE");
            verify(paymentRecordMapper, never()).insert(any(PaymentRecordEntity.class));
        }

        @Test
        @DisplayName("Should require payment method details when using Other")
        void shouldRequirePaymentMethodDetailsForOther() {
            PaymentRecordSaveRequest request = new PaymentRecordSaveRequest();
            request.setEnrollmentOrderId(1L);
            request.setSplits(List.of(new PaymentSplitDTO("OTHER", new BigDecimal("100.00"))));
            request.setHandledByName("Admin");
            when(enrollmentOrderMapper.selectById(1L)).thenReturn(testOrder);

            assertThatThrownBy(() -> paymentRecordService.createPaymentRecord(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("PAYMENT_METHOD_DETAIL_REQUIRED");
            verify(paymentRecordMapper, never()).insert(any(PaymentRecordEntity.class));
        }

        @Test
        @DisplayName("Should reject payment when amount exceeds remaining due")
        void shouldRejectPaymentWhenAmountExceedsRemainingDue() {
            PaymentRecordSaveRequest request = new PaymentRecordSaveRequest();
            request.setEnrollmentOrderId(1L);
            request.setSplits(List.of(new PaymentSplitDTO("CASH", new BigDecimal("2000.00"))));
            request.setHandledByName("Admin");

            when(enrollmentOrderMapper.selectById(1L)).thenReturn(testOrder);

            assertThatThrownBy(() -> paymentRecordService.createPaymentRecord(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Payment amount cannot exceed the remaining amount due.");
            verify(paymentRecordMapper, never()).insert(any(PaymentRecordEntity.class));
            verify(enrollmentOrderMapper, never()).updateById(any(EnrollmentOrderEntity.class));
        }

        @Test
        @DisplayName("Should reject payment that exceeds current balance amount")
        void shouldRejectPaymentThatExceedsCurrentBalanceAmount() {
            testOrder.setAmountDue(new BigDecimal("2599.00"));
            testOrder.setReceivedAmount(new BigDecimal("1000.00"));
            testOrder.setBalanceAmount(new BigDecimal("300.00"));
            PaymentRecordSaveRequest request = new PaymentRecordSaveRequest();
            request.setEnrollmentOrderId(1L);
            request.setSplits(List.of(new PaymentSplitDTO("CASH", new BigDecimal("300.01"))));
            request.setHandledByName("Admin");

            when(enrollmentOrderMapper.selectById(1L)).thenReturn(testOrder);

            assertThatThrownBy(() -> paymentRecordService.createPaymentRecord(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Payment amount cannot exceed the remaining amount due.");
            verify(paymentRecordMapper, never()).insert(any(PaymentRecordEntity.class));
            verify(enrollmentOrderMapper, never()).updateById(any(EnrollmentOrderEntity.class));
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
                    .hasMessageContaining("Payment record not found");
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
                    .hasMessageContaining("Course package not found");
        }
    }
}
