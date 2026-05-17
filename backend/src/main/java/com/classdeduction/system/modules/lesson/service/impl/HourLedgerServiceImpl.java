package com.classdeduction.system.modules.lesson.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.common.util.UnitConverter;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.lesson.dto.HourLedgerPageQuery;
import com.classdeduction.system.modules.lesson.entity.HourLedgerEntity;
import com.classdeduction.system.modules.lesson.entity.PackageBalanceSnapshotEntity;
import com.classdeduction.system.modules.lesson.mapper.HourLedgerMapper;
import com.classdeduction.system.modules.lesson.mapper.PackageBalanceSnapshotMapper;
import com.classdeduction.system.modules.lesson.service.HourLedgerService;
import com.classdeduction.system.modules.lesson.vo.HourLedgerVO;
import com.classdeduction.system.modules.lesson.vo.PackageBalanceSnapshotVO;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 课时流水服务：负责流水查询和课包余额快照读取。
 */
@Service
@RequiredArgsConstructor
public class HourLedgerServiceImpl implements HourLedgerService {

    private final HourLedgerMapper hourLedgerMapper;
    private final CoursePackageMapper coursePackageMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final PackageBalanceSnapshotMapper packageBalanceSnapshotMapper;

    @Override
    public PageResponse<HourLedgerVO> pageLedger(HourLedgerPageQuery query) {
        LambdaQueryWrapper<HourLedgerEntity> wrapper = new LambdaQueryWrapper<HourLedgerEntity>()
                .eq(query.getPackageId() != null, HourLedgerEntity::getPackageId, query.getPackageId())
                .eq(query.getLedgerType() != null && !query.getLedgerType().isBlank(), HourLedgerEntity::getLedgerType, query.getLedgerType())
                .ge(query.getBusinessDateFrom() != null, HourLedgerEntity::getBusinessDate, query.getBusinessDateFrom())
                .le(query.getBusinessDateTo() != null, HourLedgerEntity::getBusinessDate, query.getBusinessDateTo())
                .orderByDesc(HourLedgerEntity::getOperatedAt)
                .orderByDesc(HourLedgerEntity::getId);
        IPage<HourLedgerEntity> page = hourLedgerMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        if (page.getRecords().isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }

        Map<Long, CoursePackageEntity> packageMap = listPackagesByIds(page.getRecords().stream().map(HourLedgerEntity::getPackageId).collect(Collectors.toSet()));
        Map<Long, LessonOccurrenceEntity> occurrenceMap = listOccurrencesByIds(page.getRecords().stream().map(HourLedgerEntity::getOccurrenceId).filter(Objects::nonNull).collect(Collectors.toSet()));

        return PageResponse.of(
                page.getRecords().stream()
                        .map(item -> toVO(item, packageMap.get(item.getPackageId()), occurrenceMap.get(item.getOccurrenceId())))
                        .toList(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize()
        );
    }

    @Override
    public PackageBalanceSnapshotVO getBalanceSnapshot(Long packageId) {
        CoursePackageEntity coursePackage = coursePackageMapper.selectById(packageId);
        if (coursePackage == null) {
            throw new BusinessException("COURSE_PACKAGE_NOT_FOUND", "Course package not found.");
        }
        PackageBalanceSnapshotEntity snapshot = packageBalanceSnapshotMapper.selectById(packageId);
        if (snapshot == null) {
            return PackageBalanceSnapshotVO.builder()
                    .packageId(coursePackage.getId())
                    .packageNo(coursePackage.getPackageNo())
                    .totalUnits(UnitConverter.toHours(coursePackage.getTotalUnits()))
                    .consumedUnits(UnitConverter.toHours(coursePackage.getConsumedUnits()))
                    .remainingUnits(UnitConverter.toHours(coursePackage.getRemainingUnits()))
                    .lastLedgerId(null)
                    .snapshotAt(null)
                    .build();
        }
        return PackageBalanceSnapshotVO.builder()
                .packageId(snapshot.getPackageId())
                .packageNo(coursePackage.getPackageNo())
                .totalUnits(UnitConverter.toHours(snapshot.getTotalUnits()))
                .consumedUnits(UnitConverter.toHours(snapshot.getConsumedUnits()))
                .remainingUnits(UnitConverter.toHours(snapshot.getRemainingUnits()))
                .lastLedgerId(snapshot.getLastLedgerId())
                .snapshotAt(snapshot.getSnapshotAt())
                .build();
    }

    private Map<Long, CoursePackageEntity> listPackagesByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return coursePackageMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(CoursePackageEntity::getId, item -> item));
    }

    private Map<Long, LessonOccurrenceEntity> listOccurrencesByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return lessonOccurrenceMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(LessonOccurrenceEntity::getId, item -> item));
    }

    private HourLedgerVO toVO(HourLedgerEntity entity, CoursePackageEntity coursePackage, LessonOccurrenceEntity occurrence) {
        return HourLedgerVO.builder()
                .id(entity.getId())
                .packageId(entity.getPackageId())
                .packageNo(coursePackage == null ? null : coursePackage.getPackageNo())
                .lessonRecordId(entity.getLessonRecordId())
                .occurrenceId(entity.getOccurrenceId())
                .lessonNo(occurrence == null ? null : occurrence.getLessonNo())
                .ledgerType(entity.getLedgerType())
                .changeUnits(UnitConverter.toHours(entity.getChangeUnits()))
                .balanceAfterUnits(UnitConverter.toHours(entity.getBalanceAfterUnits()))
                .businessDate(entity.getBusinessDate())
                .reasonCode(entity.getReasonCode())
                .reasonDesc(entity.getReasonDesc())
                .operatedBy(entity.getOperatedBy())
                .operatedAt(entity.getOperatedAt())
                .build();
    }
}
