package com.classdeduction.system.modules.enrollment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.util.UnitConverter;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.entity.PackageTeacherAssignmentEntity;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.service.PackageTeacherAssignmentService;
import com.classdeduction.system.modules.enrollment.vo.PackageTeacherAssignmentVO;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 课时包老师分配服务实现。
 */
@Service
@RequiredArgsConstructor
public class PackageTeacherAssignmentServiceImpl implements PackageTeacherAssignmentService {

    private final PackageTeacherAssignmentMapper packageTeacherAssignmentMapper;
    private final TeacherMapper teacherMapper;
    private final LessonRecordMapper lessonRecordMapper;

    @Override
    public List<PackageTeacherAssignmentVO> listByPackageId(Long packageId) {
        if (packageId == null) {
            return Collections.emptyList();
        }
        List<PackageTeacherAssignmentEntity> assignments = packageTeacherAssignmentMapper.selectList(
                new LambdaQueryWrapper<PackageTeacherAssignmentEntity>()
                        .eq(PackageTeacherAssignmentEntity::getPackageId, packageId)
                        .orderByDesc(PackageTeacherAssignmentEntity::getEffectiveFrom)
        );
        if (assignments.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, TeacherEntity> teacherMap = teacherMapper.selectBatchIds(
                        assignments.stream().map(PackageTeacherAssignmentEntity::getTeacherId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(TeacherEntity::getId, item -> item));
        Map<Long, Integer> consumedUnitsByTeacher = loadConsumedUnitsByTeacher(packageId);
        return assignments.stream()
                .map(item -> toVO(
                        item,
                        teacherMap.get(item.getTeacherId()),
                        consumedUnitsByTeacher.getOrDefault(item.getTeacherId(), 0)))
                .toList();
    }

    @Override
    public PackageTeacherAssignmentVO getAssignment(Long id) {
        PackageTeacherAssignmentEntity assignment = requireAssignment(id);
        TeacherEntity teacher = teacherMapper.selectById(assignment.getTeacherId());
        int consumedUnits = loadConsumedUnitsByTeacher(assignment.getPackageId())
                .getOrDefault(assignment.getTeacherId(), 0);
        return toVO(assignment, teacher, consumedUnits);
    }

    private PackageTeacherAssignmentEntity requireAssignment(Long id) {
        PackageTeacherAssignmentEntity assignment = packageTeacherAssignmentMapper.selectById(id);
        if (assignment == null) {
            throw new BusinessException("ASSIGNMENT_NOT_FOUND", "Teacher assignment not found.");
        }
        return assignment;
    }

    private Map<Long, Integer> loadConsumedUnitsByTeacher(Long packageId) {
        return lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                        .eq(LessonRecordEntity::getPackageId, packageId)
                        .eq(LessonRecordEntity::getConfirmStage, "CONFIRMED")
                        .eq(LessonRecordEntity::getIsDeleted, 0))
                .stream()
                .filter(item -> item.getActualTeacherId() != null)
                .collect(Collectors.groupingBy(
                        LessonRecordEntity::getActualTeacherId,
                        Collectors.summingInt(item -> item.getActualUnits() == null ? 0 : item.getActualUnits())));
    }

    private PackageTeacherAssignmentVO toVO(PackageTeacherAssignmentEntity item,
                                            TeacherEntity teacher,
                                            int consumedUnits) {
        Integer allocatedUnits = item.getAllocatedUnits();
        return PackageTeacherAssignmentVO.builder()
                .id(item.getId())
                .teacherId(item.getTeacherId())
                .teacherNo(teacher == null ? null : teacher.getTeacherNo())
                .teacherName(teacher == null ? null : teacher.getTeacherName())
                .subjectName(item.getSubjectName())
                .allocatedHours(allocatedUnits == null
                        ? null
                        : UnitConverter.toHoursDecimal(allocatedUnits))
                .consumedHours(UnitConverter.toHoursDecimal(consumedUnits))
                .remainingHours(allocatedUnits == null
                        ? null
                        : UnitConverter.toHoursDecimal(Math.max(allocatedUnits - consumedUnits, 0)))
                .primary(item.getIsPrimary() != null && item.getIsPrimary() == 1)
                .assignmentStatus(item.getAssignmentStatus())
                .effectiveFrom(item.getEffectiveFrom())
                .effectiveTo(item.getEffectiveTo())
                .changeReason(item.getChangeReason())
                .build();
    }
}
