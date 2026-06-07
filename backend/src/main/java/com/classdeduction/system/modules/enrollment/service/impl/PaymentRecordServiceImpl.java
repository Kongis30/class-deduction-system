package com.classdeduction.system.modules.enrollment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.enrollment.dto.PaymentRecordSaveRequest;
import com.classdeduction.system.modules.enrollment.dto.PaymentSplitDTO;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentRecordEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentRecordMapper;
import com.classdeduction.system.modules.enrollment.service.PaymentRecordService;
import com.classdeduction.system.modules.enrollment.util.BusinessNoGenerator;
import com.classdeduction.system.modules.enrollment.vo.PaymentRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
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

        // 2. Validate splits
        List<PaymentSplitDTO> splits = request.getSplits();
        if (splits == null || splits.isEmpty()) {
            throw new BusinessException("INVALID_SPLITS", "At least one payment split is required.");
        }
        for (PaymentSplitDTO split : splits) {
            if (split.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("INVALID_AMOUNT",
                        "Each split amount must be greater than 0.");
            }
        }

        // 3. Calculate total from splits
        BigDecimal totalAmount = splits.stream()
                .map(PaymentSplitDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        validatePaymentAmount(order, totalAmount);

        // 4. Create payment records (one per split)
        List<PaymentRecordEntity> records = new ArrayList<>();
        for (PaymentSplitDTO split : splits) {
            PaymentRecordEntity paymentRecord = new PaymentRecordEntity();
            paymentRecord.setEnrollmentOrderId(request.getEnrollmentOrderId());
            paymentRecord.setPaymentNo(BusinessNoGenerator.generatePaymentNo());
            paymentRecord.setPaymentMethod(split.getPaymentMethod());
            paymentRecord.setPaymentStatus("PAID");
            paymentRecord.setAmount(split.getAmount());
            paymentRecord.setPaidAt(request.getPaidAt() != null ? request.getPaidAt() : AppTime.nowLocalDateTime());
            paymentRecord.setHandledByUserId(request.getHandledByUserId());
            paymentRecord.setHandledByName(request.getHandledByName());
            paymentRecord.setRemark(request.getRemark());
            paymentRecordMapper.insert(paymentRecord);
            records.add(paymentRecord);
        }

        // 5. Update enrollment order received_amount and payment_status
        updateEnrollmentOrderPayment(order, totalAmount);
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
        BigDecimal originalReceived = order.getReceivedAmount() != null ? order.getReceivedAmount() : BigDecimal.ZERO;
        BigDecimal newReceived = originalReceived.add(newPaymentAmount);
        BigDecimal dueAmount = order.getOriginalAmount().subtract(order.getDiscountAmount());

        order.setReceivedAmount(newReceived);
        order.setPaymentStatus(resolvePaymentStatus(dueAmount, newReceived));
        enrollmentOrderMapper.updateById(order);
        updatePackageReceivedAmount(order.getId(), newPaymentAmount);
    }

    private void validatePaymentAmount(EnrollmentOrderEntity order, BigDecimal newPaymentAmount) {
        BigDecimal originalReceived = order.getReceivedAmount() != null ? order.getReceivedAmount() : BigDecimal.ZERO;
        BigDecimal dueAmount = order.getOriginalAmount().subtract(order.getDiscountAmount());
        if (dueAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("ORDER_ALREADY_PAID", "This order has no remaining amount due.");
        }
        BigDecimal newReceived = originalReceived.add(newPaymentAmount);
        if (newReceived.compareTo(dueAmount) > 0) {
            throw new BusinessException("PAYMENT_EXCEEDS_AMOUNT_DUE", "Payment amount cannot exceed the remaining amount due.");
        }
    }

    private void updatePackageReceivedAmount(Long enrollmentOrderId, BigDecimal newPaymentAmount) {
        List<CoursePackageEntity> packages = coursePackageMapper.selectList(
                new LambdaQueryWrapper<CoursePackageEntity>()
                        .eq(CoursePackageEntity::getEnrollmentOrderId, enrollmentOrderId)
        );
        if (packages == null || packages.isEmpty()) {
            return;
        }
        for (CoursePackageEntity coursePackage : packages) {
            BigDecimal currentReceived = coursePackage.getReceivedAmount() == null
                    ? BigDecimal.ZERO
                    : coursePackage.getReceivedAmount();
            coursePackage.setReceivedAmount(currentReceived.add(newPaymentAmount));
            coursePackageMapper.updateById(coursePackage);
        }
    }

    /**
     * 根据实收金额和应收金额计算支付状态。
     */
    private String resolvePaymentStatus(BigDecimal dueAmount, BigDecimal receivedAmount) {
        if (dueAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return "PAID";
        }
        if (receivedAmount.compareTo(BigDecimal.ZERO) <= 0) {
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

    private PaymentRecordVO toVO(PaymentRecordEntity item) {
        return PaymentRecordVO.builder()
                .id(item.getId())
                .paymentNo(item.getPaymentNo())
                .paymentMethod(item.getPaymentMethod())
                .paymentStatus(item.getPaymentStatus())
                .amount(item.getAmount())
                .paidAt(item.getPaidAt())
                .handledByName(item.getHandledByName())
                .remark(item.getRemark())
                .build();
    }
}
