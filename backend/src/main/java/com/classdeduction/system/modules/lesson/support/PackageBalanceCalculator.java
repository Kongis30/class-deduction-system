package com.classdeduction.system.modules.lesson.support;

import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.lesson.entity.HourLedgerEntity;

import java.util.Collection;
import java.util.Set;

/**
 * 课包余额推导工具。
 *
 * <p>课时余额的权威口径为：</p>
 * <pre>
 *   consumed  = opening_used_units + 消课净额
 *   remaining = total_units - consumed
 * </pre>
 *
 * <p>其中「消课净额」只统计 {@link HourLedgerEntity#getLedgerType()} 为消课/冲正类型的账本流水。
 * 赠课、手工修正等只影响 {@code total_units}（同时也调整余额）的调整不进消课口径，
 * 因此不能以账本最后一笔的 {@code balance_after_units} 直接作为当前余额——
 * 续费只增加总量而未写账本流水，会导致该快照值过期。</p>
 */
public final class PackageBalanceCalculator {

    /** 影响"已消耗课时"的账本类型：正常消课与冲正回滚。 */
    private static final Set<String> DEDUCTION_LEDGER_TYPES = Set.of("DEDUCT", "REVOKE");

    private PackageBalanceCalculator() {
    }

    /** 消课净额（正数表示净消耗的半课时数）。 */
    public static int netDeductedUnits(Collection<HourLedgerEntity> ledgers) {
        if (ledgers == null || ledgers.isEmpty()) {
            return 0;
        }
        int net = 0;
        for (HourLedgerEntity ledger : ledgers) {
            if (ledger == null || ledger.getChangeUnits() == null) {
                continue;
            }
            if (ledger.getLedgerType() != null && DEDUCTION_LEDGER_TYPES.contains(ledger.getLedgerType())) {
                net -= ledger.getChangeUnits();
            }
        }
        return net;
    }

    /** 按权威口径推导的已消耗课时（半课时单位）。 */
    public static int expectedConsumedUnits(CoursePackageEntity coursePackage, Collection<HourLedgerEntity> ledgers) {
        return Math.max(0, safeUnits(coursePackage.getOpeningUsedUnits()) + netDeductedUnits(ledgers));
    }

    /** 按权威口径推导的剩余课时（半课时单位）。 */
    public static int expectedRemainingUnits(CoursePackageEntity coursePackage, Collection<HourLedgerEntity> ledgers) {
        return Math.max(0, safeUnits(coursePackage.getTotalUnits()) - expectedConsumedUnits(coursePackage, ledgers));
    }

    private static int safeUnits(Integer value) {
        return value == null ? 0 : value;
    }
}
