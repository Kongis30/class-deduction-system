package com.classdeduction.system.modules.systemconfig.service.impl;

import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.lesson.entity.HourLedgerEntity;
import com.classdeduction.system.modules.lesson.mapper.HourLedgerMapper;
import com.classdeduction.system.modules.lesson.mapper.PackageBalanceSnapshotMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 余额同步任务测试：续费未写账本导致最后一笔 balance_after 过期时，
 * 任务必须以 total_units 为基准重新推导余额，而不是回退到过期账本值。
 */
@ExtendWith(MockitoExtension.class)
class PackageBalanceSyncSchedulerTest {

    @Mock
    private CoursePackageMapper coursePackageMapper;
    @Mock
    private HourLedgerMapper hourLedgerMapper;
    @Mock
    private PackageBalanceSnapshotMapper snapshotMapper;

    @Test
    void correctsStaleBalanceToTotalMinusConsumed() {
        CoursePackageEntity pkg = new CoursePackageEntity();
        pkg.setId(1L);
        pkg.setTotalUnits(324);
        pkg.setOpeningUsedUnits(20);
        pkg.setConsumedUnits(104);
        pkg.setRemainingUnits(220);

        HourLedgerEntity opening = new HourLedgerEntity();
        opening.setId(10L);
        opening.setPackageId(1L);
        opening.setLedgerType("MANUAL_ADJUST");
        opening.setChangeUnits(-20);

        when(coursePackageMapper.selectList(any())).thenReturn(List.of(pkg));
        when(hourLedgerMapper.selectList(any())).thenReturn(List.of(opening));
        when(snapshotMapper.selectById(1L)).thenReturn(null);

        PackageBalanceSyncScheduler scheduler =
                new PackageBalanceSyncScheduler(coursePackageMapper, hourLedgerMapper, snapshotMapper);
        scheduler.syncPackageBalances();

        assertThat(pkg.getConsumedUnits()).isEqualTo(20);
        assertThat(pkg.getRemainingUnits()).isEqualTo(304);
        verify(coursePackageMapper).updateById(pkg);
    }

    @Test
    void skipsPackagesWithoutLedger() {
        CoursePackageEntity pkg = new CoursePackageEntity();
        pkg.setId(2L);
        pkg.setTotalUnits(240);
        pkg.setConsumedUnits(0);
        pkg.setRemainingUnits(240);

        when(coursePackageMapper.selectList(any())).thenReturn(List.of(pkg));
        when(hourLedgerMapper.selectList(any())).thenReturn(List.of());

        PackageBalanceSyncScheduler scheduler =
                new PackageBalanceSyncScheduler(coursePackageMapper, hourLedgerMapper, snapshotMapper);
        scheduler.syncPackageBalances();

        verify(coursePackageMapper, never()).updateById(any(CoursePackageEntity.class));
    }
}
