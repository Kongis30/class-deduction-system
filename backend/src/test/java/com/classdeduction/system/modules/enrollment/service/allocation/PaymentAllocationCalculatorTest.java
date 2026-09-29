package com.classdeduction.system.modules.enrollment.service.allocation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 课程级付款分摊算法边界用例（specs/003-payment-allocation §3.3）。
 */
class PaymentAllocationCalculatorTest {

    private static BigDecimal $(String v) {
        return new BigDecimal(v).setScale(2);
    }

    private static PaymentAllocationCalculator.AllocationTarget t(long id, String remaining) {
        return new PaymentAllocationCalculator.AllocationTarget(id, $(remaining));
    }

    private static Map<Long, BigDecimal> toMap(List<PaymentAllocationCalculator.AllocationLine> lines) {
        return lines.stream().collect(Collectors.toMap(
                PaymentAllocationCalculator.AllocationLine::orderItemId,
                PaymentAllocationCalculator.AllocationLine::amount,
                BigDecimal::add));
    }

    @Test
    void threeCoursesEvenSplit() {
        // 3 个课程订单付 $3000，各课程余额充足 → 各 $1000
        List<PaymentAllocationCalculator.AllocationLine> lines = PaymentAllocationCalculator.evenSplitWithCap(
                $("3000"), List.of(t(1, "1000"), t(2, "1000"), t(3, "1000")));
        assertThat(lines).hasSize(3);
        assertThat(toMap(lines)).containsEntry(1L, $("1000")).containsEntry(2L, $("1000")).containsEntry(3L, $("1000"));
    }

    @Test
    void customerScenarioTwoCoursesFiveThousand() {
        // 两课程各应付 $3000，付 $5000 → $2500 / $2500
        List<PaymentAllocationCalculator.AllocationLine> lines = PaymentAllocationCalculator.evenSplitWithCap(
                $("5000"), List.of(t(1, "3000"), t(2, "3000")));
        assertThat(toMap(lines)).containsEntry(1L, $("2500")).containsEntry(2L, $("2500"));
    }

    @Test
    void singleCourseTakesFullAmount() {
        List<PaymentAllocationCalculator.AllocationLine> lines = PaymentAllocationCalculator.evenSplitWithCap(
                $("5000"), List.of(t(1, "5000")));
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).amount()).isEqualTo($("5000"));
    }

    @Test
    void capAndRedistribute() {
        // 3 课付 $3000，一课只剩 $200、另两课各余 $1400 → $200 / $1400 / $1400
        List<PaymentAllocationCalculator.AllocationLine> lines = PaymentAllocationCalculator.evenSplitWithCap(
                $("3000"), List.of(t(1, "200"), t(2, "1400"), t(3, "1400")));
        assertThat(toMap(lines)).containsEntry(1L, $("200")).containsEntry(2L, $("1400")).containsEntry(3L, $("1400"));
    }

    @Test
    void capOnTwoCourses() {
        // 2 课 A 剩 $0.50、B 剩 $2000，付 $1000 → A=$0.50、B=$999.50
        List<PaymentAllocationCalculator.AllocationLine> lines = PaymentAllocationCalculator.evenSplitWithCap(
                $("1000"), List.of(t(1, "0.50"), t(2, "2000")));
        assertThat(toMap(lines)).containsEntry(1L, $("0.50")).containsEntry(2L, $("999.50"));
    }

    @Test
    void twoCoursesPartialCap() {
        // 2 课 A 剩 $500、B 剩 $2000，付 $1200 → 平均 600 超 A 上限 → A=$500、B=$700
        List<PaymentAllocationCalculator.AllocationLine> lines = PaymentAllocationCalculator.evenSplitWithCap(
                $("1200"), List.of(t(1, "500"), t(2, "2000")));
        assertThat(toMap(lines)).containsEntry(1L, $("500")).containsEntry(2L, $("700"));
    }

    @Test
    void centRemainderGoesToFirstItems() {
        // $100 分 3 课 → $33.34 / $33.33 / $33.33
        List<PaymentAllocationCalculator.AllocationLine> lines = PaymentAllocationCalculator.evenSplitWithCap(
                $("100"), List.of(t(1, "100"), t(2, "100"), t(3, "100")));
        assertThat(lines.get(0).amount()).isEqualTo($("33.34"));
        assertThat(lines.get(1).amount()).isEqualTo($("33.33"));
        assertThat(lines.get(2).amount()).isEqualTo($("33.33"));
        assertThat(lines.stream().map(PaymentAllocationCalculator.AllocationLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualTo($("100"));
    }

    @Test
    void wholeDollarRemainderDoesNotCreateCents() {
        List<PaymentAllocationCalculator.AllocationLine> lines = PaymentAllocationCalculator.evenSplitWithCap(
                $("100"), List.of(t(1, "100"), t(2, "100"), t(3, "100")), BigDecimal.ONE);
        assertThat(toMap(lines)).containsEntry(1L, $("34"))
                .containsEntry(2L, $("33")).containsEntry(3L, $("33"));
    }

    @Test
    void zeroRemainingItemsExcluded() {
        // 一课余额为 0 不参与分摊，不生成 0 元行
        List<PaymentAllocationCalculator.AllocationLine> lines = PaymentAllocationCalculator.evenSplitWithCap(
                $("1000"), List.of(t(1, "0"), t(2, "2000")));
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).orderItemId()).isEqualTo(2L);
        assertThat(lines.get(0).amount()).isEqualTo($("1000"));
    }

    @Test
    void exactSettlement() {
        // 订单余额 $1，两课程各 $0.50，付 $1 → 各 $0.50
        List<PaymentAllocationCalculator.AllocationLine> lines = PaymentAllocationCalculator.evenSplitWithCap(
                $("1"), List.of(t(1, "0.50"), t(2, "0.50")));
        assertThat(toMap(lines)).containsEntry(1L, $("0.50")).containsEntry(2L, $("0.50"));
    }

    @Test
    void rejectWhenTotalExceedsSum() {
        // 付 $7000 > 总余额 $6000 → 拒绝
        assertThatThrownBy(() -> PaymentAllocationCalculator.evenSplitWithCap(
                $("7000"), List.of(t(1, "3000"), t(2, "3000"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectNonPositiveTotal() {
        assertThatThrownBy(() -> PaymentAllocationCalculator.evenSplitWithCap(
                $("0"), List.of(t(1, "1000"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaymentAllocationCalculator.evenSplitWithCap(
                $("-5"), List.of(t(1, "1000"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectEmptyTargets() {
        assertThatThrownBy(() -> PaymentAllocationCalculator.evenSplitWithCap(
                $("100"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeRemainingTreatedAsZero() {
        // 脏数据：剩余应付为负数按 0 处理
        List<PaymentAllocationCalculator.AllocationLine> lines = PaymentAllocationCalculator.evenSplitWithCap(
                $("100"), List.of(t(1, "-5"), t(2, "500")));
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).orderItemId()).isEqualTo(2L);
    }
}
