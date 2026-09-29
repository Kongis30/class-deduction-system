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
 *   2. 平均分摊：旧订单以分、新订单以美元为最小单位，余差依次补给前 k 项
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
        return evenSplitWithCap(total, targets, CENT);
    }

    /** 新报名按美元分配，旧订单仍按分分配；两种路径都保持总额与课程余额守恒。 */
    public static List<AllocationLine> evenSplitWithCap(BigDecimal total, List<AllocationTarget> targets,
                                                         BigDecimal unit) {
        if (unit == null || unit.compareTo(CENT) < 0) {
            throw new IllegalArgumentException("Allocation unit must be at least one cent");
        }
        if (total != null && total.remainder(unit).compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException("Allocation total must match the order's money unit");
        }
        if (total == null || total.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Allocation total must be positive");
        }
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("Allocation targets must not be empty");
        }
        for (AllocationTarget target : targets) {
            if (target.remainingDue() != null
                    && target.remainingDue().remainder(unit).compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalArgumentException("Course balance must match the order's money unit");
            }
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
            // 按订单计价单位均分；余数依次补给前面的课程。
            BigDecimal base = remainingTotal
                    .divide(new BigDecimal(n), unit.scale(), RoundingMode.DOWN);
            long remainderUnits = remainingTotal.subtract(base.multiply(new BigDecimal(n)))
                    .divide(unit, 0, RoundingMode.DOWN)
                    .setScale(0, RoundingMode.DOWN)
                    .longValueExact();

            BigDecimal allocatedThisRound = ZERO;
            List<Target> nextActive = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Target target = active.get(i);
                BigDecimal share = base;
                if (i < remainderUnits) {
                    share = share.add(unit);
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
