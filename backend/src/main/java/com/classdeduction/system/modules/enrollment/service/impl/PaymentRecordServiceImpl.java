package com.classdeduction.system.modules.enrollment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.modules.enrollment.dto.PaymentRecordSaveRequest;
import com.classdeduction.system.modules.enrollment.dto.PaymentSplitDTO;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderParticipantEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentMethodEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentRecordEntity;
import com.classdeduction.system.modules.enrollment.entity.ReceiptRecordEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderParticipantMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentMethodMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentRecordMapper;
import com.classdeduction.system.modules.enrollment.mapper.ReceiptRecordMapper;
import com.classdeduction.system.modules.enrollment.service.PaymentRecordService;
import com.classdeduction.system.modules.enrollment.service.allocation.PaymentAllocationService;
import com.classdeduction.system.modules.enrollment.util.BusinessNoGenerator;
import com.classdeduction.system.modules.enrollment.vo.PaymentRecordVO;
import com.classdeduction.system.modules.enrollment.vo.ReceiptRecordVO;
import com.classdeduction.system.modules.finance.service.StudentCreditService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 支付记录服务实现。
 */
@Service
@RequiredArgsConstructor
public class PaymentRecordServiceImpl implements PaymentRecordService {

    private final PaymentRecordMapper paymentRecordMapper;
    private final EnrollmentOrderMapper enrollmentOrderMapper;
    private final EnrollmentOrderParticipantMapper enrollmentOrderParticipantMapper;
    private final CoursePackageMapper coursePackageMapper;
    private final PaymentMethodMapper paymentMethodMapper;
    private final ReceiptRecordMapper receiptRecordMapper;
    private final PaymentAllocationService paymentAllocationService;
    private final JsonHelper jsonHelper;
    private final StudentCreditService studentCreditService;

    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO;
    private static final String DEFAULT_RECEIPT_TEMPLATE = "DEFAULT_RECEIPT";

    @Override
    public List<PaymentRecordVO> listByEnrollmentOrderId(Long enrollmentOrderId) {
        if (enrollmentOrderId == null) {
            return Collections.emptyList();
        }
        List<PaymentRecordEntity> records = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecordEntity>()
                        .eq(PaymentRecordEntity::getEnrollmentOrderId, enrollmentOrderId)
                        .orderByDesc(PaymentRecordEntity::getPaidAt)
        );
        return records.stream().map(this::toVO).toList();
    }

    @Override
    public PaymentRecordVO getPaymentRecord(Long id) {
        PaymentRecordEntity record = requirePaymentRecord(id);
        return toVO(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReceiptRecordVO createPaymentRecord(PaymentRecordSaveRequest request) {
        // 1. Validate enrollment order exists
        EnrollmentOrderEntity order = requireEnrollmentOrder(request.getEnrollmentOrderId());

        // 1.1 Guard against payments on a non-collectable order (cancelled or refunded),
        // otherwise a refunded/cancelled order could be "revived" and corrupt the books.
        if ("CANCELLED".equals(order.getOrderStatus())) {
            throw new BusinessException("ORDER_CANCELLED", "Cannot collect payment on a cancelled order.");
        }
        if ("REFUNDED".equals(order.getPaymentStatus())) {
            throw new BusinessException("ORDER_REFUNDED", "Cannot collect payment on a refunded order.");
        }

        // 2. Validate splits
        List<PaymentSplitDTO> splits = request.getSplits();
        if (splits == null || splits.isEmpty()) {
            throw new BusinessException("INVALID_SPLITS", "At least one payment split is required.");
        }
        for (PaymentSplitDTO split : splits) {
            if (split == null || split.getAmount() == null || split.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("INVALID_AMOUNT",
                        "Each split amount must be greater than 0.");
            }
            if (split.getAmount().stripTrailingZeros().scale() > 2) {
                throw new BusinessException("INVALID_AMOUNT_SCALE",
                        "Each split amount must use at most 2 decimal places.");
            }
            requireActivePaymentMethod(split.getPaymentMethod());
            if (!StringUtils.hasText(resolveHandledByName(request, split))) {
                throw new BusinessException("PAYMENT_HANDLER_REQUIRED",
                        "Handler name is required for every payment.");
            }
            if ("OTHER".equalsIgnoreCase(split.getPaymentMethod())
                    && !StringUtils.hasText(resolveRemark(request, split))) {
                throw new BusinessException("PAYMENT_METHOD_DETAIL_REQUIRED",
                        "Payment method details are required when using Other.");
            }
            if (split.getAllocations() != null && !split.getAllocations().isEmpty()) {
                for (PaymentSplitDTO.CoursePaymentAllocationDTO allocation : split.getAllocations()) {
                    if (allocation == null || allocation.getEnrollmentOrderItemId() == null
                            || allocation.getAmount() == null
                            || allocation.getAmount().compareTo(BigDecimal.ZERO) <= 0
                            || allocation.getAmount().stripTrailingZeros().scale() > 2) {
                        throw new BusinessException("PAYMENT_ALLOCATION_INVALID",
                                "Each course allocation must have a course and a positive amount with at most 2 decimals.");
                    }
                }
                BigDecimal allocationTotal = split.getAllocations().stream()
                        .map(PaymentSplitDTO.CoursePaymentAllocationDTO::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (allocationTotal.compareTo(split.getAmount()) != 0) {
                    throw new BusinessException("PAYMENT_ALLOCATION_TOTAL_MISMATCH",
                            "Course allocations must add up to each payment method amount.");
                }
                long uniqueItemCount = split.getAllocations().stream()
                        .map(PaymentSplitDTO.CoursePaymentAllocationDTO::getEnrollmentOrderItemId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .count();
                if (uniqueItemCount != split.getAllocations().size()) {
                    throw new BusinessException("PAYMENT_ALLOCATION_DUPLICATE_ITEM",
                            "Each course can appear only once in a payment allocation.");
                }
            }
        }

        // 3. Calculate total from splits
        BigDecimal totalAmount = splits.stream()
                .map(PaymentSplitDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Integer> participantIndexes = splits.stream()
                .map(PaymentSplitDTO::getParticipantIndex)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        boolean containsUnscopedSplit = splits.stream()
                .anyMatch(split -> split.getParticipantIndex() == null);
        if (participantIndexes.size() > 1 || (!participantIndexes.isEmpty() && containsUnscopedSplit)) {
            throw new BusinessException("PAYMENT_PARTICIPANT_MISMATCH",
                    "All payment splits must belong to the same student.");
        }
        EnrollmentOrderParticipantEntity targetParticipant = participantIndexes.isEmpty()
                ? null
                : enrollmentOrderParticipantMapper.selectOne(
                        new LambdaQueryWrapper<EnrollmentOrderParticipantEntity>()
                                .eq(EnrollmentOrderParticipantEntity::getEnrollmentOrderId, order.getId())
                                .eq(EnrollmentOrderParticipantEntity::getParticipantIndex, participantIndexes.get(0))
                                .eq(EnrollmentOrderParticipantEntity::getIsDeleted, 0));
        if (!participantIndexes.isEmpty() && targetParticipant == null) {
            throw new BusinessException("PAYMENT_PARTICIPANT_NOT_FOUND",
                    "The selected student is not part of this enrollment order.");
        }
        if (targetParticipant == null && participantIndexes.isEmpty()) {
            List<EnrollmentOrderParticipantEntity> participants = enrollmentOrderParticipantMapper.selectList(
                    new LambdaQueryWrapper<EnrollmentOrderParticipantEntity>()
                            .eq(EnrollmentOrderParticipantEntity::getEnrollmentOrderId, order.getId())
                            .eq(EnrollmentOrderParticipantEntity::getIsDeleted, 0));
            if (participants != null && participants.size() > 1) {
                throw new BusinessException("PAYMENT_PARTICIPANT_REQUIRED",
                        "Select the student who is making this payment.");
            }
        }
        if (targetParticipant == null) {
            validatePaymentAmount(order, totalAmount);
        } else {
            // 多人私教既要限制目标学生的应付余额，也要限制整单余额，避免历史付款分摊异常导致订单累计收款超额。
            validatePaymentAmount(order, totalAmount);
            validateParticipantPaymentAmount(targetParticipant, totalAmount);
        }

        // 4. Create payment records (one per split)
        List<PaymentRecordEntity> paymentRecords = new ArrayList<>(splits.size());
        for (PaymentSplitDTO split : splits) {
            PaymentRecordEntity paymentRecord = new PaymentRecordEntity();
            paymentRecord.setEnrollmentOrderId(request.getEnrollmentOrderId());
            paymentRecord.setParticipantIndex(split.getParticipantIndex());
            paymentRecord.setPaymentNo(BusinessNoGenerator.generatePaymentNo());
            paymentRecord.setPaymentMethod(split.getPaymentMethod());
            paymentRecord.setPaymentType("PAYMENT");
            paymentRecord.setPaymentStatus("PAID");
            paymentRecord.setAmount(split.getAmount());
            paymentRecord.setPaidAt(split.getPaidAt() != null
                    ? split.getPaidAt()
                    : request.getPaidAt() != null ? request.getPaidAt() : AppTime.nowLocalDateTime());
            paymentRecord.setHandledByUserId(request.getHandledByUserId());
            paymentRecord.setHandledByName(resolveHandledByName(request, split));
            paymentRecord.setRemark(resolveRemark(request, split));
            paymentRecordMapper.insert(paymentRecord);
            paymentRecords.add(paymentRecord);
            if (StudentCreditService.PAYMENT_METHOD_CODE.equalsIgnoreCase(split.getPaymentMethod())) {
                Long creditStudentId = targetParticipant == null
                        ? order.getStudentId() : targetParticipant.getStudentId();
                studentCreditService.consumeForPayment(creditStudentId, split.getAmount(), order.getId(), paymentRecord.getId(),
                        request.getHandledByUserId(), resolveHandledByName(request, split), resolveRemark(request, split));
            }
            if (split.getAllocations() != null && !split.getAllocations().isEmpty()) {
                Map<Long, BigDecimal> allocations = new LinkedHashMap<>();
                split.getAllocations().forEach(allocation -> allocations.put(
                        allocation.getEnrollmentOrderItemId(), allocation.getAmount()));
                paymentAllocationService.recordExplicitAllocations(paymentRecord, order.getId(), allocations);
            }
        }

        // 支付流水保留为审计账本；报名收据只维护这一笔报名的累计支付状态，不再新增支付收据。
        updateEnrollmentOrderPayment(order, totalAmount);
        syncParticipantPaymentSnapshots(order.getId());
        // 有明确课程分配时重放账本，刷新课程付款快照；多人付款仍保留流水上的参与人归属。
        boolean hasCourseAllocations = splits.stream()
                .anyMatch(split -> split.getAllocations() != null && !split.getAllocations().isEmpty());
        if (targetParticipant == null || hasCourseAllocations) {
            paymentAllocationService.replayAllocations(order);
        }
        return syncRegistrationReceipt(order, request.getHandledByUserId());
    }

    private void syncParticipantPaymentSnapshots(Long orderId) {
        List<EnrollmentOrderParticipantEntity> participants = enrollmentOrderParticipantMapper.selectList(
                new LambdaQueryWrapper<EnrollmentOrderParticipantEntity>()
                        .eq(EnrollmentOrderParticipantEntity::getEnrollmentOrderId, orderId)
                        .eq(EnrollmentOrderParticipantEntity::getIsDeleted, 0));
        if (participants.isEmpty()) return;
        // 学生已付金额按净流水计算：PAYMENT 为正、REFUND 为负，VOIDED 不参与统计。
        // 这样退款后再次付款时，个人快照不会把历史已退金额重复算入。
        List<PaymentRecordEntity> payments = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecordEntity>()
                        .eq(PaymentRecordEntity::getEnrollmentOrderId, orderId));
        for (EnrollmentOrderParticipantEntity participant : participants) {
            BigDecimal paid = payments.stream()
                    .filter(item -> Objects.equals(item.getParticipantIndex(), participant.getParticipantIndex())
                            || (Objects.equals(participant.getParticipantIndex(), 1)
                            && item.getParticipantIndex() == null))
                    .filter(item -> !"VOIDED".equals(item.getPaymentStatus()))
                    .map(PaymentRecordEntity::getAmount)
                    .filter(java.util.Objects::nonNull)
                    .reduce(ZERO_AMOUNT, BigDecimal::add);
            participant.setPaidAmount(paid);
            participant.setBalanceAmount(defaultAmount(participant.getAmountDue()).subtract(paid));
            enrollmentOrderParticipantMapper.updateById(participant);
        }
    }

    /**
     * 将完整支付流水写回报名收据；收据编号和报名课程信息保持不变，付款列表及金额字段实时更新。
     */
    private ReceiptRecordVO syncRegistrationReceipt(EnrollmentOrderEntity order, Long issuedBy) {
        ReceiptRecordEntity receipt = receiptRecordMapper.selectList(
                        new LambdaQueryWrapper<ReceiptRecordEntity>()
                                .eq(ReceiptRecordEntity::getEnrollmentOrderId, order.getId())
                                .eq(ReceiptRecordEntity::getTemplateCode, DEFAULT_RECEIPT_TEMPLATE)
                                .orderByDesc(ReceiptRecordEntity::getIssuedAt))
                .stream()
                .findFirst()
                .orElseGet(() -> createFallbackRegistrationReceipt(order, issuedBy));

        Map<String, Object> payload = jsonHelper.fromJson(
                receipt.getReceiptPayload(),
                new TypeReference<LinkedHashMap<String, Object>>() {});
        if (payload == null) {
            payload = new LinkedHashMap<>();
        }
        payload.put("receiptType", "REGISTRATION");
        payload.put("receiptNo", receipt.getReceiptNo());
        payload.put("enrollmentOrderId", order.getId());
        payload.put("enrollmentNo", order.getEnrollmentNo());
        payload.put("studentId", order.getStudentId());
        payload.putIfAbsent("studentName", order.getStudentNameSnapshot());
        payload.putIfAbsent("currencyCode", order.getCurrencyCode());
        payload.put("amountDue", order.getAmountDue());
        payload.put("receivedAmount", order.getReceivedAmount());
        payload.put("paidAmount", order.getReceivedAmount());
        payload.put("balanceAmount", order.getBalanceAmount());
        payload.put("paymentStatus", order.getPaymentStatus());
        // Pay 更新参与学生快照，保证收据里的分摊金额与 Data Summary、订单详情一致。
        payload.put("participants", receiptParticipantPayloads(order.getId()));
        // 课程级付款分摊结果（每个课程的应付/已付/余额）
        payload.put("items", paymentAllocationService.listItemAllocationPayloads(order.getId()));
        payload.put("payments", listPaymentLedger(order.getId()));
        receipt.setReceiptPayload(jsonHelper.toJson(payload));
        receiptRecordMapper.updateById(receipt);

        return ReceiptRecordVO.builder()
                .id(receipt.getId())
                .receiptNo(receipt.getReceiptNo())
                .enrollmentOrderId(order.getId())
                .studentId(order.getStudentId())
                .paymentRecordId(receipt.getPaymentRecordId())
                .studentName(order.getStudentNameSnapshot())
                .templateCode(DEFAULT_RECEIPT_TEMPLATE)
                .receiptPayload(receipt.getReceiptPayload())
                .issuedAt(receipt.getIssuedAt())
                .build();
    }

    private ReceiptRecordEntity createFallbackRegistrationReceipt(EnrollmentOrderEntity order, Long issuedBy) {
        LocalDateTime issuedAt = AppTime.nowLocalDateTime();
        ReceiptRecordEntity receipt = new ReceiptRecordEntity();
        receipt.setReceiptNo(BusinessNoGenerator.generateReceiptNo());
        receipt.setEnrollmentOrderId(order.getId());
        receipt.setStudentId(order.getStudentId());
        receipt.setTemplateCode(DEFAULT_RECEIPT_TEMPLATE);
        receipt.setIssuedAt(issuedAt);
        receipt.setIssuedBy(issuedBy);
        receipt.setReceiptPayload(jsonHelper.toJson(new LinkedHashMap<>()));
        receiptRecordMapper.insert(receipt);
        return receipt;
    }

    private List<Map<String, Object>> receiptParticipantPayloads(Long orderId) {
        List<EnrollmentOrderParticipantEntity> participants = enrollmentOrderParticipantMapper.selectList(
                new LambdaQueryWrapper<EnrollmentOrderParticipantEntity>()
                        .eq(EnrollmentOrderParticipantEntity::getEnrollmentOrderId, orderId)
                        .eq(EnrollmentOrderParticipantEntity::getIsDeleted, 0)
                        .orderByAsc(EnrollmentOrderParticipantEntity::getParticipantIndex));
        if (participants == null) {
            return Collections.emptyList();
        }
        return participants.stream().map(item -> {
            Map<String, Object> participant = new LinkedHashMap<>();
            participant.put("studentId", item.getStudentId());
            participant.put("studentNo", item.getStudentNoSnapshot());
            participant.put("studentName", item.getStudentNameSnapshot());
            participant.put("participantIndex", item.getParticipantIndex());
            participant.put("grade", item.getGradeSnapshot());
            participant.put("school", item.getSchoolSnapshot());
            participant.put("studentClass", item.getStudentClassSnapshot());
            participant.put("guardianName", item.getGuardianNameSnapshot());
            participant.put("guardianPhone", item.getGuardianPhoneSnapshot());
            participant.put("guardianPhone2", item.getGuardianPhone2Snapshot());
            participant.put("guardianEmail", item.getGuardianEmailSnapshot());
            participant.put("pickupRequired", item.getPickupRequiredSnapshot());
            participant.put("pickupAddress", item.getPickupAddressSnapshot());
            participant.put("dropoffRequired", item.getDropoffRequiredSnapshot());
            participant.put("dropoffAddress", item.getDropoffAddressSnapshot());
            participant.put("grossAmount", item.getGrossAmount());
            participant.put("priceAmount", item.getGrossAmount());
            participant.put("discountAmount", item.getDiscountAmount());
            participant.put("discounts", item.getDiscountDetail() == null ? Collections.emptyList()
                    : jsonHelper.fromJson(item.getDiscountDetail(), new TypeReference<List<Map<String, Object>>>() {}));
            participant.put("amountDue", item.getAmountDue());
            participant.put("paidAmount", item.getPaidAmount());
            participant.put("balanceAmount", item.getBalanceAmount());
            return participant;
        }).toList();
    }

    private List<Map<String, Object>> listPaymentLedger(Long orderId) {
        List<PaymentRecordEntity> records = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecordEntity>()
                        .eq(PaymentRecordEntity::getEnrollmentOrderId, orderId)
                        .orderByAsc(PaymentRecordEntity::getPaidAt)
                        .orderByAsc(PaymentRecordEntity::getId));
        if (records == null) {
            return Collections.emptyList();
        }
        return records.stream()
                .filter(record -> record.getAmount() != null
                        && record.getAmount().compareTo(ZERO_AMOUNT) != 0
                        && !"VOIDED".equals(record.getPaymentStatus()))
                .map(this::toReceiptPaymentPayload)
                .toList();
    }

    private Map<String, Object> toReceiptPaymentPayload(PaymentRecordEntity payment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentId", payment.getId());
        payload.put("participantIndex", payment.getParticipantIndex());
        payload.put("paymentNo", payment.getPaymentNo());
        payload.put("paymentMethod", payment.getPaymentMethod());
        payload.put("paymentType", payment.getPaymentType());
        payload.put("paymentStatus", payment.getPaymentStatus());
        payload.put("amount", payment.getAmount());
        payload.put("paidAt", payment.getPaidAt());
        payload.put("handledByName", payment.getHandledByName());
        payload.put("remark", payment.getRemark());
        return payload;
    }

    private String resolveHandledByName(PaymentRecordSaveRequest request, PaymentSplitDTO split) {
        if (StringUtils.hasText(split.getHandledByName())) {
            return split.getHandledByName().trim();
        }
        return StringUtils.hasText(request.getHandledByName()) ? request.getHandledByName().trim() : null;
    }

    private String resolveRemark(PaymentRecordSaveRequest request, PaymentSplitDTO split) {
        return StringUtils.hasText(split.getRemark()) ? split.getRemark().trim() : request.getRemark();
    }

    @Override
    public List<PaymentAllocationService.AllocationPreview> previewAllocation(Long enrollmentOrderId, BigDecimal amount) {
        EnrollmentOrderEntity order = requireEnrollmentOrder(enrollmentOrderId);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_AMOUNT", "Payment amount must be greater than 0.");
        }
        return paymentAllocationService.preview(order, amount);
    }

    @Override
    public Integer getPackageBalance(Long packageId) {
        CoursePackageEntity packageEntity = requireCoursePackage(packageId);
        return packageEntity.getRemainingUnits();
    }

    /**
     * 更新报名订单的已收金额和支付状态。
     */
    private void updateEnrollmentOrderPayment(EnrollmentOrderEntity order, BigDecimal newPaymentAmount) {
        BigDecimal dueAmount = resolveAmountDue(order);
        BigDecimal currentBalance = resolveBalanceAmount(order, dueAmount);
        BigDecimal newBalance = currentBalance.subtract(newPaymentAmount);
        BigDecimal newReceived = dueAmount.subtract(newBalance);

        order.setReceivedAmount(newReceived);
        order.setBalanceAmount(newBalance);
        order.setPaymentStatus(resolvePaymentStatus(dueAmount, newReceived));
        int updated = enrollmentOrderMapper.updateById(order);
        if (updated != 1) {
            throw new BusinessException("ORDER_CONCURRENT_UPDATE", "The order was updated by another request. Please retry.");
        }
        // A subsequent payment has no package allocation. Package received amounts only retain
        // explicit enrollment-time allocations until per-package payment allocation is supported.
    }

    private void validatePaymentAmount(EnrollmentOrderEntity order, BigDecimal newPaymentAmount) {
        BigDecimal dueAmount = resolveAmountDue(order);
        BigDecimal currentBalance = resolveBalanceAmount(order, dueAmount);
        if (currentBalance.compareTo(ZERO_AMOUNT) <= 0) {
            throw new BusinessException("ORDER_ALREADY_PAID", "This order has no remaining amount due.");
        }
        if (newPaymentAmount.compareTo(currentBalance) > 0) {
            throw new BusinessException("PAYMENT_EXCEEDS_AMOUNT_DUE", "Payment amount cannot exceed the remaining amount due.");
        }
    }

    private void validateParticipantPaymentAmount(EnrollmentOrderParticipantEntity participant,
                                                  BigDecimal newPaymentAmount) {
        BigDecimal currentBalance = participant.getBalanceAmount() != null
                ? participant.getBalanceAmount()
                : defaultAmount(participant.getAmountDue()).subtract(defaultAmount(participant.getPaidAmount()));
        if (currentBalance.compareTo(ZERO_AMOUNT) <= 0) {
            throw new BusinessException("PARTICIPANT_ALREADY_PAID",
                    "This student's enrollment has no remaining amount due.");
        }
        if (newPaymentAmount.compareTo(currentBalance) > 0) {
            throw new BusinessException("PARTICIPANT_PAYMENT_EXCEEDS_AMOUNT_DUE",
                    "Payment amount cannot exceed this student's remaining amount due.");
        }
    }

    private BigDecimal resolveAmountDue(EnrollmentOrderEntity order) {
        if (order.getAmountDue() != null) {
            return order.getAmountDue();
        }
        if (order.getBalanceAmount() != null && order.getReceivedAmount() != null) {
            return order.getReceivedAmount().add(order.getBalanceAmount());
        }
        BigDecimal originalAmount = order.getOriginalAmount() == null ? ZERO_AMOUNT : order.getOriginalAmount();
        BigDecimal discountAmount = order.getDiscountAmount() == null ? ZERO_AMOUNT : order.getDiscountAmount();
        return originalAmount.subtract(discountAmount);
    }

    private BigDecimal resolveBalanceAmount(EnrollmentOrderEntity order, BigDecimal amountDue) {
        if (order.getBalanceAmount() != null) {
            return order.getBalanceAmount();
        }
        BigDecimal receivedAmount = order.getReceivedAmount() == null ? ZERO_AMOUNT : order.getReceivedAmount();
        return amountDue.subtract(receivedAmount);
    }

    /**
     * 根据实收金额和应收金额计算支付状态。
     */
    private String resolvePaymentStatus(BigDecimal dueAmount, BigDecimal receivedAmount) {
        if (dueAmount.compareTo(ZERO_AMOUNT) <= 0) {
            return "PAID";
        }
        if (receivedAmount.compareTo(ZERO_AMOUNT) <= 0) {
            return "UNPAID";
        }
        if (receivedAmount.compareTo(dueAmount) >= 0) {
            return "PAID";
        }
        return "PARTIAL";
    }

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount == null ? ZERO_AMOUNT : amount;
    }

    private PaymentRecordEntity requirePaymentRecord(Long id) {
        PaymentRecordEntity record = paymentRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("PAYMENT_RECORD_NOT_FOUND", "Payment record not found.");
        }
        return record;
    }

    private EnrollmentOrderEntity requireEnrollmentOrder(Long id) {
        EnrollmentOrderEntity order = enrollmentOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("ENROLLMENT_ORDER_NOT_FOUND", "Enrollment order not found.");
        }
        return order;
    }

    private CoursePackageEntity requireCoursePackage(Long id) {
        CoursePackageEntity packageEntity = coursePackageMapper.selectById(id);
        if (packageEntity == null) {
            throw new BusinessException("COURSE_PACKAGE_NOT_FOUND", "Course package not found.");
        }
        return packageEntity;
    }

    private void requireActivePaymentMethod(String methodCode) {
        if (StudentCreditService.PAYMENT_METHOD_CODE.equalsIgnoreCase(methodCode)) {
            return;
        }
        Long count = paymentMethodMapper.selectCount(new LambdaQueryWrapper<PaymentMethodEntity>()
                .eq(PaymentMethodEntity::getMethodCode, methodCode)
                .eq(PaymentMethodEntity::getStatus, "ACTIVE")
                .eq(PaymentMethodEntity::getIsDeleted, 0));
        if (count == null || count == 0) {
            throw new BusinessException("PAYMENT_METHOD_NOT_AVAILABLE",
                    "Payment method not found or inactive.");
        }
    }

    private PaymentRecordVO toVO(PaymentRecordEntity item) {
        return PaymentRecordVO.builder()
                .id(item.getId())
                .paymentNo(item.getPaymentNo())
                .participantIndex(item.getParticipantIndex())
                .paymentMethod(item.getPaymentMethod())
                .paymentType(item.getPaymentType())
                .paymentStatus(item.getPaymentStatus())
                .amount(item.getAmount())
                .paidAt(item.getPaidAt())
                .handledByName(item.getHandledByName())
                .remark(item.getRemark())
                .build();
    }
}
