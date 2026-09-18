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

/**
 * 课程级付款分摊账本：
 * - 以"全量重放"方式维护该订单的分摊明细（payment_allocation）与课程项快照
 *   （enrollment_order_item.paid_amount / balance_amount）。
 * - 重放按支付时间顺序逐笔消费每条有效流水：PAYMENT 平均分摊（封顶顺延）、
 *   REFUND 按已分摊比例回冲（负数行），从而与历史写入顺序无关，任何写入路径
 *   （报名收款 / Pay / 更正 / 退款）之后调用一次即可保持一致。
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
     * 全量重放订单分摊并刷新课程项快照。必须在同一事务内于付款写入后调用。
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.MANDATORY)
    public void replayAllocations(EnrollmentOrderEntity order) {
        List<EnrollmentOrderItemEntity> items = listActiveItems(order.getId());
        if (items.isEmpty()) {
            return;
        }
        ensureItemAmountDue(order, items, true);

        // 软删旧分摊后按时间顺序重放
        paymentAllocationMapper.delete(new LambdaQueryWrapper<PaymentAllocationEntity>()
                .eq(PaymentAllocationEntity::getEnrollmentOrderId, order.getId()));

        List<PaymentRecordEntity> ledger = paymentRecordMapper.selectList(
                        new LambdaQueryWrapper<PaymentRecordEntity>()
                                .eq(PaymentRecordEntity::getEnrollmentOrderId, order.getId()))
                .stream()
                .filter(record -> !"VOIDED".equals(record.getPaymentStatus()))
                .sorted(Comparator.comparing(PaymentRecordEntity::getPaidAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PaymentRecordEntity::getId))
                .toList();

        // 重放期间各课程的剩余可收额度（amount_due - 已分摊）
        Map<Long, BigDecimal> remaining = items.stream()
                .collect(Collectors.toMap(EnrollmentOrderItemEntity::getId,
                        item -> defaultAmount(item.getAmountDue())));

        List<PaymentAllocationEntity> allocations = new ArrayList<>();
        boolean isRefund = false;
        for (PaymentRecordEntity record : ledger) {
            BigDecimal amount = defaultAmount(record.getAmount());
            if (amount.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            List<PaymentAllocationCalculator.AllocationLine> lines;
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                isRefund = false;
                lines = evenPay(amount, remaining);
                for (PaymentAllocationCalculator.AllocationLine line : lines) {
                    remaining.merge(line.orderItemId(), line.amount(), BigDecimal::subtract);
                }
            } else {
                isRefund = true;
                // 各课程累计已付 = amount_due - remaining；退款按已付比例回冲，cap 到已付
                Map<Long, BigDecimal> paid = remaining.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey,
                                entry -> defaultAmount(amountDueOf(items, entry.getKey()))
                                        .subtract(entry.getValue())));
                lines = proportionalRefund(amount.negate(), paid);
                for (PaymentAllocationCalculator.AllocationLine line : lines) {
                    remaining.merge(line.orderItemId(), line.amount(), BigDecimal::add);
                }
            }
            for (PaymentAllocationCalculator.AllocationLine line : lines) {
                PaymentAllocationEntity entity = new PaymentAllocationEntity();
                entity.setPaymentRecordId(record.getId());
                entity.setEnrollmentOrderId(order.getId());
                entity.setEnrollmentOrderItemId(line.orderItemId());
                entity.setAmount(isRefund ? line.amount().negate() : line.amount());
                paymentAllocationMapper.insert(entity);
                allocations.add(entity);
            }
        }
        syncItemSnapshots(items);
        log.debug("Replayed {} payment allocations for order {}", allocations.size(), order.getId());
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

    /**
     * item.amount_due 为空时按公式补算：item 价格 + 按比例摊入附加费/折扣，余差归末项。
     *
     * @param persist true 时落库（重放链路）；false 仅在内存补算（预览链路）
     */
    private void ensureItemAmountDue(EnrollmentOrderEntity order, List<EnrollmentOrderItemEntity> items,
                                     boolean persist) {
        boolean missing = items.stream().anyMatch(item -> item.getAmountDue() == null);
        if (!missing) {
            return;
        }
        BigDecimal subtotal = defaultAmount(order.getTuitionSubtotalAmount());
        BigDecimal feeTotal = defaultAmount(order.getPickupFeeAmount())
                .add(defaultAmount(order.getExtraFeeAmount()))
                .subtract(defaultAmount(order.getDiscountAmount()));
        BigDecimal assigned = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            EnrollmentOrderItemEntity item = items.get(i);
            BigDecimal due;
            if (i == items.size() - 1) {
                // 余差归末项，保证 SUM(item.amount_due) = order.amount_due
                due = defaultAmount(order.getAmountDue()).subtract(assigned);
            } else if (subtotal.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal gross = defaultAmount(item.getPriceAmount());
                due = gross.add(feeTotal.multiply(gross)
                                .divide(subtotal, 6, RoundingMode.HALF_UP))
                        .setScale(2, RoundingMode.HALF_UP);
            } else {
                due = BigDecimal.ZERO;
            }
            if (due.compareTo(BigDecimal.ZERO) < 0) {
                due = BigDecimal.ZERO;
            }
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

    private void syncItemSnapshots(List<EnrollmentOrderItemEntity> items) {
        for (EnrollmentOrderItemEntity item : items) {
            BigDecimal paid = paymentAllocationMapper.selectList(
                            new LambdaQueryWrapper<PaymentAllocationEntity>()
                                    .eq(PaymentAllocationEntity::getEnrollmentOrderItemId, item.getId()))
                    .stream()
                    .map(PaymentAllocationEntity::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
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
