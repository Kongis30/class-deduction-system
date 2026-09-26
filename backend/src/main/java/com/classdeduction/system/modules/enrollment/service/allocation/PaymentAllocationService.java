package com.classdeduction.system.modules.enrollment.service.allocation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentAllocationEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentRecordEntity;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentAllocationMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 课程级付款分摊账本：
 * - 维护付款到课程项的显式分摊账本，并由账本刷新课程已付与余额快照。
 * - 新流水保留提交时的课程分配；只有尚无 allocation 的历史流水才沿用旧的自动分摊规则。
 *   这样后续付款或退款重算时不会覆盖人工指定的课程归属。
 * - 校验语义：任何一笔流水分摊失败（超付等）都会让整个事务回滚，等效于拦截。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentAllocationService {

    private final PaymentAllocationMapper paymentAllocationMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final EnrollmentOrderItemMapper enrollmentOrderItemMapper;

    /**
     * 按时间顺序补齐历史流水分摊并刷新课程快照。由付款、报名、退款或金额更正事务调用。
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.MANDATORY)
    public void replayAllocations(EnrollmentOrderEntity order) {
        List<EnrollmentOrderItemEntity> items = listActiveItems(order.getId());
        if (items.isEmpty()) {
            return;
        }
        ensureItemAmountDue(order, items, true);

        List<PaymentRecordEntity> ledger = paymentRecordMapper.selectList(
                        new LambdaQueryWrapper<PaymentRecordEntity>()
                                .eq(PaymentRecordEntity::getEnrollmentOrderId, order.getId()))
                .stream()
                .filter(record -> !"VOIDED".equals(record.getPaymentStatus()))
                .sorted(Comparator.comparing(PaymentRecordEntity::getPaidAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PaymentRecordEntity::getId))
                .toList();

        Map<Long, List<PaymentAllocationEntity>> existingByPaymentId = paymentAllocationMapper.selectList(
                        new LambdaQueryWrapper<PaymentAllocationEntity>()
                                .eq(PaymentAllocationEntity::getEnrollmentOrderId, order.getId()))
                .stream()
                .collect(Collectors.groupingBy(PaymentAllocationEntity::getPaymentRecordId));

        // 重放期间各课程的剩余可收额度（amount_due - 已分摊）
        Map<Long, BigDecimal> remaining = items.stream()
                .collect(Collectors.toMap(EnrollmentOrderItemEntity::getId,
                        item -> defaultAmount(item.getAmountDue())));

        int createdAllocationCount = 0;
        for (PaymentRecordEntity record : ledger) {
            BigDecimal amount = defaultAmount(record.getAmount());
            if (amount.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            List<PaymentAllocationEntity> saved = existingByPaymentId.get(record.getId());
            if (saved != null && !saved.isEmpty()) {
                validateSavedAllocation(record, saved, remaining, items);
                applySavedAllocation(saved, remaining);
            } else {
                List<PaymentAllocationCalculator.AllocationLine> lines = amount.compareTo(BigDecimal.ZERO) > 0
                        ? evenPay(amount, remaining)
                        : legacyRefund(amount.negate(), items, remaining);
                for (PaymentAllocationCalculator.AllocationLine line : lines) {
                    BigDecimal signedAmount = amount.signum() > 0 ? line.amount() : line.amount().negate();
                    insertAllocation(record, order.getId(), line.orderItemId(), signedAmount);
                    remaining.merge(line.orderItemId(), signedAmount.negate(), BigDecimal::add);
                    createdAllocationCount++;
                }
            }
        }
        syncItemSnapshots(items, remaining);
        log.debug("Created {} missing payment allocations for order {}", createdAllocationCount, order.getId());
    }

    /** 保存前端明确指定的课程分配，后续重算会原样保留。 */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.MANDATORY)
    public void recordExplicitAllocations(PaymentRecordEntity payment,
                                          Long orderId,
                                          Map<Long, BigDecimal> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            return;
        }
        BigDecimal total = allocations.values().stream()
                .map(this::defaultAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (payment.getAmount() == null || total.compareTo(payment.getAmount()) != 0) {
            throw new BusinessException("PAYMENT_ALLOCATION_TOTAL_MISMATCH",
                    "Course allocations must add up to the payment amount.");
        }
        Map<Long, EnrollmentOrderItemEntity> itemsById = listActiveItems(orderId).stream()
                .collect(Collectors.toMap(EnrollmentOrderItemEntity::getId, Function.identity()));
        for (Map.Entry<Long, BigDecimal> entry : allocations.entrySet()) {
            EnrollmentOrderItemEntity item = itemsById.get(entry.getKey());
            BigDecimal amount = defaultAmount(entry.getValue());
            if (item == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("PAYMENT_ALLOCATION_ITEM_INVALID",
                        "Each allocation must reference a course in this enrollment order and be greater than zero.");
            }
            insertAllocation(payment, orderId, item.getId(), amount);
        }
    }

    /** 退款窗口明确选择课程时，将负数退款流水只分摊到该课程。 */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.MANDATORY)
    public void recordRefundAllocation(PaymentRecordEntity refund,
                                       Long orderId,
                                       Long itemId,
                                       BigDecimal amount) {
        BigDecimal refundAmount = defaultAmount(amount);
        boolean itemExists = listActiveItems(orderId).stream()
                .anyMatch(item -> Objects.equals(item.getId(), itemId));
        if (!itemExists || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("REFUND_ALLOCATION_INVALID",
                    "The refund must reference an active course and a positive amount.");
        }
        insertAllocation(refund, orderId, itemId, refundAmount.negate());
    }

    /** 更正报名收款时替换该流水的课程分配，避免旧分配与新金额不一致。 */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.MANDATORY)
    public void replaceExplicitAllocations(PaymentRecordEntity payment,
                                           Long orderId,
                                           Map<Long, BigDecimal> allocations) {
        paymentAllocationMapper.delete(new LambdaQueryWrapper<PaymentAllocationEntity>()
                .eq(PaymentAllocationEntity::getPaymentRecordId, payment.getId()));
        recordExplicitAllocations(payment, orderId, allocations);
    }

    /**
     * 校验并计算一次付款的建议分摊（预览用，只读、不落库）。
     */
    public List<AllocationPreview> preview(EnrollmentOrderEntity order, BigDecimal amount) {
        List<EnrollmentOrderItemEntity> items = listActiveItems(order.getId());
        if (items.isEmpty()) {
            throw new BusinessException("PAYMENT_ALLOCATION_FAILED",
                    "This enrollment order has no course items to allocate.");
        }
        ensureItemAmountDue(order, items, false);
        Map<Long, BigDecimal> remaining = currentRemaining(items);
        List<PaymentAllocationCalculator.AllocationLine> lines =
                PaymentAllocationCalculator.evenSplitWithCap(amount, toTargets(remaining));

        Map<Long, EnrollmentOrderItemEntity> itemById = items.stream()
                .collect(Collectors.toMap(EnrollmentOrderItemEntity::getId, Function.identity()));
        return lines.stream()
                .map(line -> {
                    EnrollmentOrderItemEntity item = itemById.get(line.orderItemId());
                    BigDecimal paid = defaultAmount(item.getPaidAmount());
                    BigDecimal due = defaultAmount(item.getAmountDue());
                    return new AllocationPreview(
                            line.orderItemId(),
                            item.getDetail(),
                            due,
                            paid,
                            due.subtract(paid),
                            line.amount());
                })
                .toList();
    }

    /**
     * 课程级分摊的展示负载（订单详情 / 收据 payload 的 items 数组）。
     */
    public List<Map<String, Object>> listItemAllocationPayloads(Long orderId) {
        List<EnrollmentOrderItemEntity> items = listActiveItems(orderId);
        if (items.isEmpty()) {
            return List.of();
        }
        return items.stream().map(item -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("enrollmentOrderItemId", item.getId());
            payload.put("courseName", item.getDetail());
            payload.put("courseType", item.getCourseType());
            payload.put("priceAmount", item.getPriceAmount());
            payload.put("amountDue", defaultAmount(item.getAmountDue()));
            payload.put("paidAmount", defaultAmount(item.getPaidAmount()));
            payload.put("balanceAmount", defaultAmount(item.getBalanceAmount()));
            return payload;
        }).toList();
    }

    // ─── 内部 ───────────────────────────────────────────────────────────

    private List<PaymentAllocationCalculator.AllocationLine> evenPay(BigDecimal amount,
                                                                     Map<Long, BigDecimal> remaining) {
        try {
            return PaymentAllocationCalculator.evenSplitWithCap(amount, toTargets(remaining));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("PAYMENT_ALLOCATION_FAILED",
                    "Unable to allocate this payment across the enrolled courses: " + ex.getMessage());
        }
    }

    /**
     * 退款按各课程累计已付比例回冲，cap 到该课程已付（不会退成负余额）。
     */
    private List<PaymentAllocationCalculator.AllocationLine> proportionalRefund(BigDecimal refundAmount,
                                                                                Map<Long, BigDecimal> paid) {
        List<PaymentAllocationCalculator.AllocationTarget> targets = paid.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                .map(entry -> new PaymentAllocationCalculator.AllocationTarget(entry.getKey(), entry.getValue()))
                .toList();
        if (targets.isEmpty()) {
            throw new BusinessException("PAYMENT_ALLOCATION_FAILED", "No paid courses to refund.");
        }
        try {
            return PaymentAllocationCalculator.evenSplitWithCap(refundAmount, targets);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("REFUND_AMOUNT_EXCEEDS_RECEIVED",
                    "Refund amount cannot exceed the currently received amount.");
        }
    }

    private List<PaymentAllocationCalculator.AllocationLine> legacyRefund(BigDecimal refundAmount,
                                                                           List<EnrollmentOrderItemEntity> items,
                                                                           Map<Long, BigDecimal> remaining) {
        Map<Long, BigDecimal> paid = remaining.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> defaultAmount(amountDueOf(items, entry.getKey())).subtract(entry.getValue())));
        return proportionalRefund(refundAmount, paid);
    }

    private void validateSavedAllocation(PaymentRecordEntity payment,
                                         List<PaymentAllocationEntity> allocations,
                                         Map<Long, BigDecimal> remaining,
                                         List<EnrollmentOrderItemEntity> items) {
        BigDecimal allocatedTotal = allocations.stream()
                .map(PaymentAllocationEntity::getAmount)
                .map(this::defaultAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocatedTotal.compareTo(defaultAmount(payment.getAmount())) != 0) {
            throw new BusinessException("PAYMENT_ALLOCATION_TOTAL_MISMATCH",
                    "Saved course allocations no longer match the payment amount.");
        }
        for (PaymentAllocationEntity allocation : allocations) {
            Long itemId = allocation.getEnrollmentOrderItemId();
            BigDecimal amount = defaultAmount(allocation.getAmount());
            BigDecimal available = remaining.get(itemId);
            BigDecimal paid = available == null ? BigDecimal.ZERO
                    : defaultAmount(amountDueOf(items, itemId)).subtract(available);
            if (available == null) {
                throw new BusinessException("PAYMENT_ALLOCATION_INVALID",
                        "Saved course allocation references a course that is no longer in this order.");
            }
            if (amount.compareTo(BigDecimal.ZERO) == 0) {
                throw new BusinessException("PAYMENT_ALLOCATION_INVALID",
                        "Saved course allocation cannot be zero.");
            }
            BigDecimal paymentCapacity = "STOPPED".equalsIgnoreCase(
                    items.stream().filter(item -> Objects.equals(item.getId(), itemId)).findFirst()
                            .map(EnrollmentOrderItemEntity::getItemStatus).orElse(null))
                    ? historicalAmountDue(items, itemId).subtract(paid).max(BigDecimal.ZERO) : available;
            if (amount.signum() > 0 && amount.compareTo(paymentCapacity) > 0) {
                throw new BusinessException("PAYMENT_ALLOCATION_INVALID",
                        "Saved course payment allocation exceeds the course's valid payment amount.");
            }
            if (amount.signum() < 0 && amount.negate().compareTo(paid) > 0) {
                throw new BusinessException("PAYMENT_ALLOCATION_INVALID",
                        "Saved refund allocation exceeds the amount already paid for this course.");
            }
        }
    }

    private void applySavedAllocation(List<PaymentAllocationEntity> allocations,
                                      Map<Long, BigDecimal> remaining) {
        for (PaymentAllocationEntity allocation : allocations) {
            remaining.merge(allocation.getEnrollmentOrderItemId(),
                    defaultAmount(allocation.getAmount()).negate(), BigDecimal::add);
        }
    }

    private void insertAllocation(PaymentRecordEntity payment, Long orderId, Long itemId, BigDecimal amount) {
        PaymentAllocationEntity entity = new PaymentAllocationEntity();
        entity.setPaymentRecordId(payment.getId());
        entity.setEnrollmentOrderId(orderId);
        entity.setEnrollmentOrderItemId(itemId);
        entity.setAmount(amount);
        paymentAllocationMapper.insert(entity);
    }

    private List<PaymentAllocationCalculator.AllocationTarget> toTargets(Map<Long, BigDecimal> remaining) {
        return remaining.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                .map(entry -> new PaymentAllocationCalculator.AllocationTarget(entry.getKey(), entry.getValue()))
                .toList();
    }

    private Map<Long, BigDecimal> currentRemaining(List<EnrollmentOrderItemEntity> items) {
        return items.stream().collect(Collectors.toMap(EnrollmentOrderItemEntity::getId,
                item -> defaultAmount(item.getAmountDue()).subtract(defaultAmount(item.getPaidAmount()))));
    }

    private BigDecimal amountDueOf(List<EnrollmentOrderItemEntity> items, Long itemId) {
        return items.stream()
                .filter(item -> Objects.equals(item.getId(), itemId))
                .findFirst()
                .map(item -> defaultAmount(item.getAmountDue()))
                .orElse(BigDecimal.ZERO.setScale(2));
    }

    private BigDecimal historicalAmountDue(List<EnrollmentOrderItemEntity> items, Long itemId) {
        return items.stream()
                .filter(item -> Objects.equals(item.getId(), itemId))
                .findFirst()
                .map(item -> defaultAmount(item.getPriceAmount())
                        .add(defaultAmount(item.getPickupFeeAmount()))
                        .add(defaultAmount(item.getDropoffFeeAmount()))
                        .add(defaultAmount(item.getExtraFeeAmount()))
                        .subtract(defaultAmount(item.getDiscountAmount()))
                        .max(defaultAmount(item.getAmountDue())))
                .orElse(BigDecimal.ZERO.setScale(2));
    }

    /**
     * item.amount_due 为空时按课程费用/优惠快照补算；旧订单没有课程费用快照时沿用整单费用的加权拆分。
     *
     * @param persist true 时落库（重放链路）；false 仅在内存补算（预览链路）
     */
    private void ensureItemAmountDue(EnrollmentOrderEntity order, List<EnrollmentOrderItemEntity> items,
                                     boolean persist) {
        boolean missing = items.stream().anyMatch(item -> item.getAmountDue() == null);
        if (!missing) {
            return;
        }
        List<BigDecimal> weights = itemWeights(items);
        List<BigDecimal> feeShares = resolveFeeShares(order, items, weights);
        boolean explicitDiscounts = items.stream().allMatch(item -> item.getDiscountAmount() != null);
        List<BigDecimal> discountShares = explicitDiscounts
                ? items.stream().map(item -> defaultAmount(item.getDiscountAmount())).toList()
                : allocateByWeights(defaultAmount(order.getDiscountAmount()), weights);
        BigDecimal assigned = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            EnrollmentOrderItemEntity item = items.get(i);
            BigDecimal discount = discountShares.get(i);
            BigDecimal due = defaultAmount(item.getPriceAmount()).add(feeShares.get(i)).subtract(discount);
            if (i == items.size() - 1) {
                // 尾项吸收舍入差，确保课程应收合计与订单一致。
                due = due.add(defaultAmount(order.getAmountDue())
                        .subtract(assigned.add(due)));
            }
            if (due.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("COURSE_DISCOUNT_EXCEEDS_AMOUNT",
                        "A course discount cannot exceed that course's gross amount and allocated fees.");
            }
            item.setDiscountAmount(discount);
            item.setAmountDue(due);
            assigned = assigned.add(due);
            if (persist) {
                enrollmentOrderItemMapper.updateById(item);
            }
        }
        BigDecimal expected = defaultAmount(order.getAmountDue());
        BigDecimal actual = items.stream().map(EnrollmentOrderItemEntity::getAmountDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (actual.subtract(expected).abs().compareTo(new BigDecimal("0.01")) > 0) {
            log.warn("Item amount_due sum {} deviates from order amount_due {} for order {}",
                    actual, expected, order.getId());
        }
    }

    private List<BigDecimal> itemWeights(List<EnrollmentOrderItemEntity> items) {
        List<BigDecimal> weights = items.stream().map(item -> defaultAmount(item.getPriceAmount())).toList();
        if (weights.stream().anyMatch(weight -> weight.compareTo(BigDecimal.ZERO) <= 0)) {
            return items.stream().map(item -> BigDecimal.ONE).toList();
        }
        return weights;
    }

    /**
     * 课程级费用存在时必须按课程快照恢复应收，不能把总费用再次按学费比例摊开。
     * 首次报名和收据编辑都会把 pickup/drop-off/service fee 固定到订单明细；只有旧订单
     * 没有课程费用快照时，才回退到历史的整单加权分摊规则。
     */
    private List<BigDecimal> resolveFeeShares(EnrollmentOrderEntity order,
                                              List<EnrollmentOrderItemEntity> items,
                                              List<BigDecimal> weights) {
        boolean hasCourseFeeSnapshots = items.stream().anyMatch(this::hasCourseFeeSnapshot);
        if (hasCourseFeeSnapshots) {
            List<BigDecimal> courseFees = items.stream()
                    .map(item -> defaultAmount(item.getPickupFeeAmount())
                            .add(defaultAmount(item.getDropoffFeeAmount()))
                            .add(defaultAmount(item.getExtraFeeAmount())))
                    .toList();
            BigDecimal orderFeeTotal = defaultAmount(order.getPickupFeeAmount())
                    .add(defaultAmount(order.getDropoffFeeAmount()))
                    .add(defaultAmount(order.getExtraFeeAmount()));
            BigDecimal explicitFeeTotal = courseFees.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal unassignedFeeTotal = orderFeeTotal.subtract(explicitFeeTotal).max(BigDecimal.ZERO);
            if (unassignedFeeTotal.compareTo(BigDecimal.ZERO) == 0) {
                return courseFees;
            }
            List<BigDecimal> unassignedShares = allocateByWeights(unassignedFeeTotal, weights);
            return IntStream.range(0, items.size())
                    .mapToObj(index -> courseFees.get(index).add(unassignedShares.get(index)))
                    .toList();
        }
        BigDecimal feeTotal = defaultAmount(order.getPickupFeeAmount())
                .add(defaultAmount(order.getDropoffFeeAmount()))
                .add(defaultAmount(order.getExtraFeeAmount()));
        return allocateByWeights(feeTotal, weights);
    }

    private boolean hasCourseFeeSnapshot(EnrollmentOrderItemEntity item) {
        return Boolean.TRUE.equals(item.getPickupRequiredSnapshot())
                || Boolean.TRUE.equals(item.getDropoffRequiredSnapshot())
                || defaultAmount(item.getPickupFeeAmount()).compareTo(BigDecimal.ZERO) > 0
                || defaultAmount(item.getDropoffFeeAmount()).compareTo(BigDecimal.ZERO) > 0
                || defaultAmount(item.getExtraFeeAmount()).compareTo(BigDecimal.ZERO) > 0;
    }

    private List<BigDecimal> allocateByWeights(BigDecimal total, List<BigDecimal> weights) {
        if (weights.isEmpty()) {
            return List.of();
        }
        BigDecimal normalizedTotal = defaultAmount(total);
        BigDecimal weightSum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal assigned = BigDecimal.ZERO;
        List<BigDecimal> result = new ArrayList<>(weights.size());
        for (int i = 0; i < weights.size(); i++) {
            BigDecimal share = i == weights.size() - 1
                    ? normalizedTotal.subtract(assigned)
                    : normalizedTotal.multiply(weights.get(i))
                    .divide(weightSum, 2, RoundingMode.DOWN);
            result.add(share);
            assigned = assigned.add(share);
        }
        return result;
    }

    private void syncItemSnapshots(List<EnrollmentOrderItemEntity> items, Map<Long, BigDecimal> remaining) {
        for (EnrollmentOrderItemEntity item : items) {
            BigDecimal paid = defaultAmount(item.getAmountDue()).subtract(
                    defaultAmount(remaining.get(item.getId())));
            item.setPaidAmount(paid);
            item.setBalanceAmount(defaultAmount(item.getAmountDue()).subtract(paid));
            enrollmentOrderItemMapper.updateById(item);
        }
    }

    private List<EnrollmentOrderItemEntity> listActiveItems(Long orderId) {
        return enrollmentOrderItemMapper.selectList(
                new LambdaQueryWrapper<EnrollmentOrderItemEntity>()
                        .eq(EnrollmentOrderItemEntity::getEnrollmentOrderId, orderId)
                        .eq(EnrollmentOrderItemEntity::getIsDeleted, 0)
                        .orderByAsc(EnrollmentOrderItemEntity::getId));
    }

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO.setScale(2) : amount;
    }

    /**
     * 分摊预览行。
     */
    public record AllocationPreview(Long enrollmentOrderItemId,
                                    String courseName,
                                    BigDecimal itemAmountDue,
                                    BigDecimal itemPaidAmount,
                                    BigDecimal itemBalanceAmount,
                                    BigDecimal allocatedAmount) {
    }
}
