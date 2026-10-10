package com.classdeduction.system.modules.lesson.support;

import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.lesson.entity.HourLedgerEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 课包余额推导测试：续费/赠课只增加总量、不写消课流水，余额必须以 total 为基准重算，
 * 不能沿用最后一笔账本的 balance_after。
 */
class PackageBalanceCalculatorTest {

    @Test
    void renewalAfterOpeningKeepsFullRemaining() {
        // Bella 场景：120h 课包续费 42h（total 162h），仅有一笔 10h 开户已用，且无消课记录。
        CoursePackageEntity pkg = packageWith(324, 20);
        List<HourLedgerEntity> ledgers = List.of(ledger("MANUAL_ADJUST", -20));

        assertThat(PackageBalanceCalculator.expectedConsumedUnits(pkg, ledgers)).isEqualTo(20);
        assertThat(PackageBalanceCalculator.expectedRemainingUnits(pkg, ledgers)).isEqualTo(304);
    }

    @Test
    void deductionAndRevokeAreNettedIntoConsumed() {
        CoursePackageEntity pkg = packageWith(240, 0);
        List<HourLedgerEntity> ledgers = List.of(
                ledger("DEDUCT", -6),
                ledger("DEDUCT", -6),
                ledger("REVOKE", 6));

        assertThat(PackageBalanceCalculator.expectedConsumedUnits(pkg, ledgers)).isEqualTo(6);
        assertThat(PackageBalanceCalculator.expectedRemainingUnits(pkg, ledgers)).isEqualTo(234);
    }

    @Test
    void bonusAdjustmentDoesNotCountAsConsumption() {
        // 赠课 +20：total 260，余额也应 260，但 +20 的调整流水不计入消费。
        CoursePackageEntity pkg = packageWith(260, 0);
        List<HourLedgerEntity> ledgers = List.of(ledger("MANUAL_ADJUST", 20));

        assertThat(PackageBalanceCalculator.expectedConsumedUnits(pkg, ledgers)).isZero();
        assertThat(PackageBalanceCalculator.expectedRemainingUnits(pkg, ledgers)).isEqualTo(260);
    }

    private CoursePackageEntity packageWith(int totalUnits, int openingUsedUnits) {
        CoursePackageEntity pkg = new CoursePackageEntity();
        pkg.setTotalUnits(totalUnits);
        pkg.setOpeningUsedUnits(openingUsedUnits);
        return pkg;
    }

    private HourLedgerEntity ledger(String ledgerType, int changeUnits) {
        HourLedgerEntity ledger = new HourLedgerEntity();
        ledger.setLedgerType(ledgerType);
        ledger.setChangeUnits(changeUnits);
        return ledger;
    }
}
