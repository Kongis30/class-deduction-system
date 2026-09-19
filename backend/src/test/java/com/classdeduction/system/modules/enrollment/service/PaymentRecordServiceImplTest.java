package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.modules.enrollment.dto.PaymentRecordSaveRequest;
import com.classdeduction.system.modules.enrollment.dto.PaymentSplitDTO;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderParticipantEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentRecordEntity;
import com.classdeduction.system.modules.enrollment.entity.ReceiptRecordEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderParticipantMapper;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private EnrollmentOrderParticipantMapper enrollmentOrderParticipantMapper;

    @Mock
    private CoursePackageMapper coursePackageMapper;

    @Mock
    private PaymentMethodMapper paymentMethodMapper;

    @Mock
    private ReceiptRecordMapper receiptRecordMapper;

    @Mock
    private JsonHelper jsonHelper;

    @Mock
    private com.classdeduction.system.modules.enrollment.service.allocation.PaymentAllocationService paymentAllocationService;

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
        lenient().when(receiptRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(paymentRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(enrollmentOrderParticipantMapper.selectList(any())).thenReturn(Collections.emptyList());
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
            assertThat(receiptCaptor.getValue().getTemplateCode()).isEqualTo("DEFAULT_RECEIPT");
            assertThat(receiptCaptor.getValue().getEnrollmentOrderId()).isEqualTo(1L);
            verify(receiptRecordMapper).updateById(any(ReceiptRecordEntity.class));
            assertThat(receipt.id()).isEqualTo(9001L);
            assertThat(receipt.templateCode()).isEqualTo("DEFAULT_RECEIPT");
        }

        @Test
        @DisplayName("Should persist an explicitly selected course allocation")
        void shouldPersistExplicitCourseAllocation() {
            PaymentSplitDTO split = new PaymentSplitDTO("CASH", new BigDecimal("500.00"));
            split.setAllocations(List.of(new PaymentSplitDTO.CoursePaymentAllocationDTO(
                    21L, new BigDecimal("500.00"))));
            PaymentRecordSaveRequest request = new PaymentRecordSaveRequest();
            request.setEnrollmentOrderId(1L);
            request.setSplits(List.of(split));
            request.setHandledByName("Admin");

            when(enrollmentOrderMapper.selectById(1L)).thenReturn(testOrder);
            when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);
            when(paymentRecordMapper.insert(any(PaymentRecordEntity.class))).thenAnswer(invocation -> {
                PaymentRecordEntity payment = invocation.getArgument(0);
                payment.setId(7101L);
                return 1;
            });
            when(jsonHelper.toJson(any())).thenReturn("{}");
            when(receiptRecordMapper.insert(any(ReceiptRecordEntity.class))).thenAnswer(invocation -> {
                ReceiptRecordEntity receipt = invocation.getArgument(0);
                receipt.setId(9003L);
                return 1;
            });

            paymentRecordService.createPaymentRecord(request);

            verify(paymentAllocationService).recordExplicitAllocations(
                    any(PaymentRecordEntity.class),
                    org.mockito.ArgumentMatchers.eq(1L),
                    org.mockito.ArgumentMatchers.eq(Map.of(21L, new BigDecimal("500.00"))));
            verify(paymentAllocationService).replayAllocations(testOrder);
        }

        @Test
        @DisplayName("Should record a course allocation against the selected private lesson participant")
        void shouldRecordCourseAllocationAgainstSelectedPrivateLessonParticipant() {
            testOrder.setOriginalAmount(new BigDecimal("150.00"));
            testOrder.setDiscountAmount(BigDecimal.ZERO);
            testOrder.setAmountDue(new BigDecimal("150.00"));
            testOrder.setReceivedAmount(new BigDecimal("75.00"));
            testOrder.setBalanceAmount(new BigDecimal("75.00"));

            EnrollmentOrderParticipantEntity participant = new EnrollmentOrderParticipantEntity();
            participant.setId(2L);
            participant.setEnrollmentOrderId(1L);
            participant.setParticipantIndex(2);
            participant.setStudentId(602L);
            participant.setStudentNameSnapshot("Student 2");
            participant.setAmountDue(new BigDecimal("75.00"));
            participant.setPaidAmount(BigDecimal.ZERO);
            participant.setBalanceAmount(new BigDecimal("75.00"));

            List<PaymentRecordEntity> savedPayments = new ArrayList<>();
            when(enrollmentOrderMapper.selectById(1L)).thenReturn(testOrder);
            when(enrollmentOrderParticipantMapper.selectOne(any())).thenReturn(participant);
            when(enrollmentOrderParticipantMapper.selectList(any())).thenReturn(List.of(participant));
            when(enrollmentOrderParticipantMapper.updateById(any(EnrollmentOrderParticipantEntity.class))).thenReturn(1);
            when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);
            when(paymentRecordMapper.insert(any(PaymentRecordEntity.class))).thenAnswer(invocation -> {
                PaymentRecordEntity saved = invocation.getArgument(0);
                saved.setId(2L);
                savedPayments.add(saved);
                return 1;
            });
            when(paymentRecordMapper.selectList(any())).thenReturn(savedPayments);
            when(jsonHelper.toJson(any())).thenReturn("{}");
            when(receiptRecordMapper.insert(any(ReceiptRecordEntity.class))).thenAnswer(invocation -> {
                ReceiptRecordEntity receipt = invocation.getArgument(0);
                receipt.setId(9002L);
                return 1;
            });

            PaymentSplitDTO split = new PaymentSplitDTO("CASH", new BigDecimal("75.00"));
            split.setParticipantIndex(2);
            split.setAllocations(List.of(new PaymentSplitDTO.CoursePaymentAllocationDTO(
                    31L, new BigDecimal("75.00"))));
            PaymentRecordSaveRequest request = new PaymentRecordSaveRequest();
            request.setEnrollmentOrderId(1L);
            request.setSplits(List.of(split));
            request.setHandledByName("Admin");

            paymentRecordService.createPaymentRecord(request);

            assertThat(savedPayments).singleElement()
                    .extracting(PaymentRecordEntity::getParticipantIndex)
                    .isEqualTo(2);
            assertThat(testOrder.getReceivedAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
            assertThat(testOrder.getBalanceAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(participant.getPaidAmount()).isEqualByComparingTo(new BigDecimal("75.00"));
            assertThat(participant.getBalanceAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            verify(enrollmentOrderParticipantMapper).updateById(participant);
            verify(paymentAllocationService).recordExplicitAllocations(
                    any(PaymentRecordEntity.class),
                    org.mockito.ArgumentMatchers.eq(1L),
                    org.mockito.ArgumentMatchers.eq(Map.of(31L, new BigDecimal("75.00"))));
            verify(paymentAllocationService).replayAllocations(testOrder);
        }

        @Test
        @DisplayName("Should keep participant paid amount net of refunds when collecting again")
        void shouldKeepParticipantPaidAmountNetOfRefundsWhenCollectingAgain() {
            testOrder.setOriginalAmount(new BigDecimal("150.00"));
            testOrder.setDiscountAmount(BigDecimal.ZERO);
            testOrder.setAmountDue(new BigDecimal("150.00"));
            testOrder.setReceivedAmount(new BigDecimal("50.00"));
            testOrder.setBalanceAmount(new BigDecimal("100.00"));

            EnrollmentOrderParticipantEntity firstParticipant = new EnrollmentOrderParticipantEntity();
            firstParticipant.setId(1L);
            firstParticipant.setEnrollmentOrderId(1L);
            firstParticipant.setParticipantIndex(1);
            firstParticipant.setStudentId(601L);
            firstParticipant.setStudentNameSnapshot("Student 1");
            firstParticipant.setAmountDue(new BigDecimal("75.00"));
            firstParticipant.setPaidAmount(BigDecimal.ZERO);
            firstParticipant.setBalanceAmount(new BigDecimal("75.00"));

            EnrollmentOrderParticipantEntity secondParticipant = new EnrollmentOrderParticipantEntity();
            secondParticipant.setId(2L);
            secondParticipant.setEnrollmentOrderId(1L);
            secondParticipant.setParticipantIndex(2);
            secondParticipant.setStudentId(602L);
            secondParticipant.setStudentNameSnapshot("Student 2");
            secondParticipant.setAmountDue(new BigDecimal("75.00"));
            secondParticipant.setPaidAmount(new BigDecimal("50.00"));
            secondParticipant.setBalanceAmount(new BigDecimal("25.00"));

            PaymentRecordEntity originalPayment = new PaymentRecordEntity();
            originalPayment.setEnrollmentOrderId(1L);
            originalPayment.setParticipantIndex(2);
            originalPayment.setPaymentType("PAYMENT");
            originalPayment.setPaymentStatus("PAID");
            originalPayment.setAmount(new BigDecimal("75.00"));

            PaymentRecordEntity refund = new PaymentRecordEntity();
            refund.setEnrollmentOrderId(1L);
            refund.setParticipantIndex(2);
            refund.setPaymentType("REFUND");
            refund.setPaymentStatus("REFUNDED");
            refund.setAmount(new BigDecimal("-25.00"));

            List<PaymentRecordEntity> ledger = new ArrayList<>(List.of(originalPayment, refund));
            when(enrollmentOrderMapper.selectById(1L)).thenReturn(testOrder);
            when(enrollmentOrderParticipantMapper.selectOne(any())).thenReturn(secondParticipant);
            when(enrollmentOrderParticipantMapper.selectList(any()))
                    .thenReturn(List.of(firstParticipant, secondParticipant));
            when(enrollmentOrderParticipantMapper.updateById(any(EnrollmentOrderParticipantEntity.class))).thenReturn(1);
            when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);
            when(paymentRecordMapper.insert(any(PaymentRecordEntity.class))).thenAnswer(invocation -> {
                PaymentRecordEntity saved = invocation.getArgument(0);
                ledger.add(saved);
                return 1;
            });
            when(paymentRecordMapper.selectList(any())).thenReturn(ledger);
            when(jsonHelper.fromJson(any(), any())).thenReturn(new LinkedHashMap<>());
            when(jsonHelper.toJson(any())).thenReturn("{}");
            when(receiptRecordMapper.insert(any(ReceiptRecordEntity.class))).thenReturn(1);

            PaymentSplitDTO split = new PaymentSplitDTO("CASH", new BigDecimal("25.00"));
            split.setParticipantIndex(2);
            PaymentRecordSaveRequest request = new PaymentRecordSaveRequest();
            request.setEnrollmentOrderId(1L);
            request.setSplits(List.of(split));
            request.setHandledByName("Admin");

            paymentRecordService.createPaymentRecord(request);

            assertThat(testOrder.getReceivedAmount()).isEqualByComparingTo(new BigDecimal("75.00"));
            assertThat(secondParticipant.getPaidAmount()).isEqualByComparingTo(new BigDecimal("75.00"));
            assertThat(secondParticipant.getBalanceAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should update the registration receipt with the order payment ledger")
        void shouldUpdateRegistrationReceiptWithOrderPaymentLedger() {
            testOrder.setReceivedAmount(new BigDecimal("2099.00"));
            testOrder.setBalanceAmount(new BigDecimal("500.00"));

            ReceiptRecordEntity existingReceipt = new ReceiptRecordEntity();
            existingReceipt.setId(8001L);
            existingReceipt.setReceiptNo("RC202604280001");
            existingReceipt.setEnrollmentOrderId(1L);
            existingReceipt.setStudentId(601L);
            existingReceipt.setTemplateCode("DEFAULT_RECEIPT");
            existingReceipt.setIssuedAt(LocalDateTime.of(2026, 4, 28, 9, 0));
            existingReceipt.setReceiptPayload("{\"studentName\":\"Edited Name\"}");

            PaymentRecordEntity oldPayment = new PaymentRecordEntity();
            oldPayment.setId(10L);
            oldPayment.setEnrollmentOrderId(1L);
            oldPayment.setPaymentNo("PMT202604270001");
            oldPayment.setPaymentMethod("CASH");
            oldPayment.setPaymentType("PAYMENT");
            oldPayment.setPaymentStatus("PAID");
            oldPayment.setAmount(new BigDecimal("2099.00"));
            oldPayment.setPaidAt(LocalDateTime.of(2026, 4, 27, 10, 0));
            oldPayment.setHandledByName("Amy");

            PaymentRecordSaveRequest request = new PaymentRecordSaveRequest();
            request.setEnrollmentOrderId(1L);
            request.setSplits(List.of(new PaymentSplitDTO("CARD", new BigDecimal("500.00"))));
            request.setHandledByName("Admin");

            when(enrollmentOrderMapper.selectById(1L)).thenReturn(testOrder);
            when(enrollmentOrderMapper.updateById(any(EnrollmentOrderEntity.class))).thenReturn(1);
            when(paymentRecordMapper.insert(any(PaymentRecordEntity.class))).thenAnswer(i -> {
                PaymentRecordEntity entity = i.getArgument(0);
                entity.setId(11L);
                entity.setPaymentNo("PMT202604280002");
                return 1;
            });
            when(receiptRecordMapper.selectList(any())).thenReturn(List.of(existingReceipt));
            when(paymentRecordMapper.selectList(any())).thenAnswer(i -> List.of(oldPayment, testPayment));
            when(jsonHelper.fromJson(any(), any())).thenReturn(new LinkedHashMap<>(Map.of("studentName", "Edited Name")));
            when(jsonHelper.toJson(any())).thenReturn("{\"receiptType\":\"REGISTRATION\",\"paidAmount\":2599.00}");

            ReceiptRecordVO receipt = paymentRecordService.createPaymentRecord(request);

            verify(receiptRecordMapper, never()).insert(any(ReceiptRecordEntity.class));
            ArgumentCaptor<ReceiptRecordEntity> updatedReceiptCaptor = ArgumentCaptor.forClass(ReceiptRecordEntity.class);
            verify(receiptRecordMapper).updateById(updatedReceiptCaptor.capture());
            assertThat(updatedReceiptCaptor.getValue().getId()).isEqualTo(8001L);
            assertThat(updatedReceiptCaptor.getValue().getTemplateCode()).isEqualTo("DEFAULT_RECEIPT");
            assertThat(receipt.id()).isEqualTo(8001L);
            assertThat(receipt.templateCode()).isEqualTo("DEFAULT_RECEIPT");
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
