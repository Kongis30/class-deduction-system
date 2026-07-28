package com.classdeduction.system.modules.enrollment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.enrollment.dto.PaymentRecordSaveRequest;
import com.classdeduction.system.modules.enrollment.dto.PaymentSplitDTO;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentMethodEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentRecordEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentMethodMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentRecordMapper;
import com.classdeduction.system.modules.enrollment.service.PaymentRecordService;
import com.classdeduction.system.modules.enrollment.util.BusinessNoGenerator;
import com.classdeduction.system.modules.enrollment.vo.PaymentRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * 支付记录服务实现。
 */
@Service
@RequiredArgsConstructor
public class PaymentRecordServiceImpl implements PaymentRecordService {

    private final PaymentRecordMapper paymentRecordMapper;
    private final EnrollmentOrderMapper enrollmentOrderMapper;
    private final CoursePackageMapper coursePackageMapper;
    private final PaymentMethodMapper paymentMethodMapper;

    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO;

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
    public void createPaymentRecord(PaymentRecordSaveRequest request) {
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
        }

        // 3. Calculate total from splits
        BigDecimal totalAmount = splits.stream()
                .map(PaymentSplitDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        validatePaymentAmount(order, totalAmount);

        // 4. Create payment records (one per split)
        for (PaymentSplitDTO split : splits) {
            PaymentRecordEntity paymentRecord = new PaymentRecordEntity();
            paymentRecord.setEnrollmentOrderId(request.getEnrollmentOrderId());
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
        }

        // Payment records are the immutable order-level ledger; update the derived order snapshot only.
        updateEnrollmentOrderPayment(order, totalAmount);
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
