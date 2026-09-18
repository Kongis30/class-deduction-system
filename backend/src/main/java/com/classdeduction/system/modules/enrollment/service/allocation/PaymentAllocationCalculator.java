package com.classdeduction.system.modules.enrollment.service.allocation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 课程级付款分摊算法（纯函数，无副作用）。
 *
 * 策略：平均分摊（EVEN）+ 上限封顶 + 超额顺延：
 *   1. 总额校验：total > SUM(remaining) 时拒绝（订单级不超付）
 *   2. 平均分摊：A_i = total / n，四舍五入到分，分位余差依次补给前 k 项
 *   3. 封顶顺延：A_i 超过该课程剩余应付时截到上限，超出部分重新均分给其余课程，
 *      循环直至分完或全部封满
 *   4. 最终校验：SUM(A_i) = total 且每个 A_i ≤ R_i
 *   5. 剩余应付为 0 的课程不参与分摊，不生成金额为 0 的分摊行
 */
public final class PaymentAllocationCalculator {

    private static final BigDecimal CENT = new BigDecimal("0.01");

    private PaymentAllocationCalculator() {
    }

    /**
     * 分摊目标：课程项及其剩余应付（amount_due - paid_amount）。
     */
    public record AllocationTarget(Long orderItemId, BigDecimal remainingDue) {
    }

    /**
     * 分摊结果行（仅含分摊额 &gt; 0 的课程项）。
     */
    public record AllocationLine(Long orderItemId, BigDecimal amount) {
    }

    /**
     * 把 total 平均分摊到各课程项，任一课程不超过其剩余应付。
     *
     * @throws IllegalArgumentException total 非法、超过总剩余应付、或无法分完（兜底）
     */
    public static List<AllocationLine> evenSplitWithCap(BigDecimal total, List<AllocationTarget> targets) {
        if (total == null || total.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Allocation total must be positive");
        }
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("Allocation targets must not be empty");
        }

        BigDecimal sumRemaining = targets.stream()
                .map(PaymentAllocationCalculator.AllocationTarget::remainingDue)
                .map(PaymentAllocationCalculator::nonNegative)
                .reduce(ZERO, BigDecimal::add);
        if (total.compareTo(sumRemaining) > 0) {
            throw new IllegalArgumentException("Allocation total exceeds the sum of remaining amounts due");
        }

        // 工作副本：id + 剩余可分额度
        List<Target> active = new ArrayList<>();
        for (AllocationTarget target : targets) {
            BigDecimal remaining = nonNegative(target.remainingDue());
            if (remaining.compareTo(ZERO) > 0) {
                active.add(new Target(target.orderItemId(), remaining));
            }
        }

        List<AllocationLine> lines = new ArrayList<>();
        Map<Long, BigDecimal> allocated = new LinkedHashMap<>();
        BigDecimal remainingTotal = total;
        while (remainingTotal.compareTo(ZERO) > 0 && !active.isEmpty()) {
            int n = active.size();
            // 均分到分：base 为每项基础额，remCents 为分位余差（补给前 remCents 项）
            BigDecimal base = remainingTotal
                    .divide(new BigDecimal(n), 2, RoundingMode.DOWN)
                    .setScale(2, RoundingMode.UNNECESSARY);
            long remCents = remainingTotal.subtract(base.multiply(new BigDecimal(n)))
                    .movePointRight(2)
                    .setScale(0, RoundingMode.DOWN)
                    .longValueExact();

            BigDecimal allocatedThisRound = ZERO;
            List<Target> nextActive = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Target target = active.get(i);
                BigDecimal share = base;
                if (i < remCents) {
                    share = share.add(CENT);
                }
                BigDecimal amount = share.min(target.remaining);
                if (amount.compareTo(ZERO) > 0) {
                    allocated.merge(target.orderItemId(), amount, BigDecimal::add);
                    allocatedThisRound = allocatedThisRound.add(amount);
                }
                BigDecimal left = target.remaining.subtract(amount);
                if (left.compareTo(ZERO) > 0) {
                    nextActive.add(new Target(target.orderItemId, left));
                }
            }
            if (allocatedThisRound.compareTo(ZERO) == 0) {
                // 兜底：本轮无任何进展，避免死循环（正常不会触达）
                throw new IllegalArgumentException("Unable to allocate the remaining amount");
            }
            remainingTotal = remainingTotal.subtract(allocatedThisRound);
            active = nextActive;
        }

        if (remainingTotal.compareTo(ZERO) > 0) {
            throw new IllegalArgumentException("Unable to allocate the full payment amount");
        }
        return allocated.entrySet().stream()
                .map(entry -> new AllocationLine(entry.getKey(), entry.getValue().setScale(2, RoundingMode.HALF_UP)))
                .toList();
    }

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private static BigDecimal nonNegative(BigDecimal amount) {
        if (amount == null) {
            return ZERO;
        }
        return amount.max(ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private record Target(Long orderItemId, BigDecimal remaining) {
    }
}
