package com.classdeduction.system.modules.systemconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.lesson.entity.HourLedgerEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.lesson.mapper.HourLedgerMapper;
import com.classdeduction.system.modules.lesson.entity.PackageBalanceSnapshotEntity;
import com.classdeduction.system.modules.lesson.mapper.PackageBalanceSnapshotMapper;
import com.classdeduction.system.modules.lesson.entity.HourLedgerEntity;
import com.classdeduction.system.modules.lesson.mapper.HourLedgerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
        // 从账本获取最后一笔记录的实际余额
        HourLedgerEntity lastLedger = hourLedgerMapper.selectOne(
                new LambdaQueryWrapper<HourLedgerEntity>()
                        .eq(HourLedgerEntity::getPackageId, pkg.getId())
                        .orderByDesc(HourLedgerEntity::getId)
                        .last("LIMIT 1"));

        if (lastLedger == null) {
            // 无账本记录，跳过（新建课包可能还没上过课）
            return false;
        }

        int ledgerBalance = lastLedger.getBalanceAfterUnits();
        int packageBalance = pkg.getRemainingUnits();

        if (ledgerBalance == packageBalance) {
            // 余额一致，仅更新 snapshot（确保快照新鲜）
            upsertSnapshot(pkg, lastLedger.getId());
            return false;
        }

        // 余额不一致，以账本为准进行校正
        log.warn("Balance discrepancy detected: packageId={}, ledgerBalance={}, packageBalance={}. Correcting to ledger value.",
                pkg.getId(), ledgerBalance, packageBalance);

        int consumed = pkg.getTotalUnits() - ledgerBalance;
        pkg.setConsumedUnits(consumed);
        pkg.setRemainingUnits(ledgerBalance);
        coursePackageMapper.updateById(pkg);
        upsertSnapshot(pkg, lastLedger.getId());
        return true;
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