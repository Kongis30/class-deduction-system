package com.classdeduction.system.modules.systemconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.lesson.entity.HourLedgerEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.lesson.mapper.HourLedgerMapper;
import com.classdeduction.system.modules.lesson.entity.PackageBalanceSnapshotEntity;
import com.classdeduction.system.modules.lesson.mapper.PackageBalanceSnapshotMapper;
import com.classdeduction.system.modules.lesson.support.PackageBalanceCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 课时余额同步定时任务：定期将 course_package.remaining_units 与 hour_ledger 账本对齐，
 * 并更新 package_balance_snapshot 快照。
 *
 * 正常情况下 lesson confirmation 会同步更新 snapshot，本任务用于处理极端场景下的数据漂移。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PackageBalanceSyncScheduler {

    private final CoursePackageMapper coursePackageMapper;
    private final HourLedgerMapper hourLedgerMapper;
    private final PackageBalanceSnapshotMapper snapshotMapper;

    /**
     * 每 6 小时运行一次：从账本重新计算各课包余额，与 course_package 对齐。
     * 仅同步 ACTIVE 状态的课包。
     */
    @Scheduled(fixedDelayString = "${app.balance.sync-interval-ms:21600000}", initialDelayString = "${app.balance.sync-initial-delay-ms:30000}")
    @Transactional(rollbackFor = Exception.class)
    public void syncPackageBalances() {
        log.info("Package balance sync job started.");
        List<CoursePackageEntity> activePackages = coursePackageMapper.selectList(
                new LambdaQueryWrapper<CoursePackageEntity>()
                        .eq(CoursePackageEntity::getPackageStatus, "ACTIVE")
                        .in(CoursePackageEntity::getIsDeleted, 0));

        int total = activePackages.size();
        int corrected = 0;
        int skipped = 0;

        for (CoursePackageEntity pkg : activePackages) {
            try {
                boolean fixed = syncOnePackage(pkg);
                if (fixed) corrected++;
                else skipped++;
            } catch (Exception e) {
                log.error("Failed to sync package id={}: {}", pkg.getId(), e.getMessage());
            }
        }

        log.info("Package balance sync job finished. total={}, corrected={}, skipped={}", total, corrected, skipped);
    }

    private boolean syncOnePackage(CoursePackageEntity pkg) {
        // 账本可能缺少"只增加总量、不写流水"的续费记录，因此不能用最后一笔的 balance_after 作为当前余额，
        // 必须以 total_units 为基准、结合消课流水重新推导。
        List<HourLedgerEntity> ledgers = hourLedgerMapper.selectList(
                new LambdaQueryWrapper<HourLedgerEntity>()
                        .eq(HourLedgerEntity::getPackageId, pkg.getId()));

        if (ledgers.isEmpty()) {
            // 无账本记录，跳过（新建课包可能还没上过课）
            return false;
        }

        HourLedgerEntity lastLedger = ledgers.stream()
                .max(Comparator.comparing(HourLedgerEntity::getId))
                .orElse(null);
        Long lastLedgerId = lastLedger == null ? null : lastLedger.getId();

        int expectedConsumed = PackageBalanceCalculator.expectedConsumedUnits(pkg, ledgers);
        int expectedRemaining = PackageBalanceCalculator.expectedRemainingUnits(pkg, ledgers);
        int packageConsumed = safeUnits(pkg.getConsumedUnits());
        int packageRemaining = safeUnits(pkg.getRemainingUnits());

        if (expectedConsumed == packageConsumed && expectedRemaining == packageRemaining) {
            // 余额一致，仅更新 snapshot（确保快照新鲜）
            upsertSnapshot(pkg, lastLedgerId);
            return false;
        }

        // 余额不一致，以权威口径进行校正（续费/赠课只影响总量，不消耗余额）
        log.warn("Balance discrepancy detected: packageId={}, expectedConsumed={}, packageConsumed={}, expectedRemaining={}, packageRemaining={}. Correcting.",
                pkg.getId(), expectedConsumed, packageConsumed, expectedRemaining, packageRemaining);

        pkg.setConsumedUnits(expectedConsumed);
        pkg.setRemainingUnits(expectedRemaining);
        coursePackageMapper.updateById(pkg);
        upsertSnapshot(pkg, lastLedgerId);
        return true;
    }

    private int safeUnits(Integer value) {
        return value == null ? 0 : value;
    }

    private void upsertSnapshot(CoursePackageEntity pkg, Long lastLedgerId) {
        PackageBalanceSnapshotEntity snapshot = snapshotMapper.selectById(pkg.getId());
        if (snapshot == null) {
            snapshot = new PackageBalanceSnapshotEntity();
            snapshot.setPackageId(pkg.getId());
            snapshot.setTotalUnits(pkg.getTotalUnits());
            snapshot.setConsumedUnits(pkg.getConsumedUnits());
            snapshot.setRemainingUnits(pkg.getRemainingUnits());
            snapshot.setLastLedgerId(lastLedgerId);
            snapshotMapper.insert(snapshot);
        } else {
            snapshot.setTotalUnits(pkg.getTotalUnits());
            snapshot.setConsumedUnits(pkg.getConsumedUnits());
            snapshot.setRemainingUnits(pkg.getRemainingUnits());
            snapshot.setLastLedgerId(lastLedgerId);
            snapshotMapper.updateById(snapshot);
        }
    }
}