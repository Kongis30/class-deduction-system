package com.classdeduction.system.modules.enrollment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.common.util.UnitConverter;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.dto.PackageTeacherAllocationRequest;
import com.classdeduction.system.modules.enrollment.dto.PackageTeacherAssignmentRequest;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.PackageTeacherAssignmentEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.service.PackageTeacherAssignmentService;
import com.classdeduction.system.modules.enrollment.vo.PackageTeacherAssignmentVO;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课时包老师分配服务实现。
 */
@Service
@RequiredArgsConstructor
public class PackageTeacherAssignmentServiceImpl implements PackageTeacherAssignmentService {

    private final PackageTeacherAssignmentMapper packageTeacherAssignmentMapper;
    private final CoursePackageMapper coursePackageMapper;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAssignment(PackageTeacherAssignmentRequest request) {
        // 1. Validate course package exists
        requireCoursePackage(request.getPackageId());

        // 2. Validate teacher exists
        requireTeacher(request.getTeacherId());

        // 3. If setting as primary, deactivate current primary
        if (Boolean.TRUE.equals(request.getIsPrimary())) {
            deactivateCurrentPrimary(request.getPackageId());
        }

        // 4. Create assignment
        PackageTeacherAssignmentEntity assignment = new PackageTeacherAssignmentEntity();
        assignment.setPackageId(request.getPackageId());
        assignment.setTeacherId(request.getTeacherId());
        assignment.setAllocatedUnits(request.getAllocatedHours() == null
                ? null
                : UnitConverter.toHalfHourUnits(request.getAllocatedHours()));
        assignment.setIsPrimary(Boolean.TRUE.equals(request.getIsPrimary()) ? 1 : 0);
        assignment.setEffectiveFrom(request.getEffectiveFrom() != null ? request.getEffectiveFrom() : AppTime.nowLocalDateTime());
        assignment.setEffectiveTo(request.getEffectiveTo());
        assignment.setAssignmentStatus("ACTIVE");
        assignment.setChangeReason(request.getChangeReason() != null ? request.getChangeReason() : "INITIAL_ASSIGNMENT");
        packageTeacherAssignmentMapper.insert(assignment);

        return assignment.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void endAssignment(Long id, String changeReason) {
        PackageTeacherAssignmentEntity assignment = requireAssignment(id);
        if (!"ACTIVE".equals(assignment.getAssignmentStatus())) {
            throw new BusinessException("ASSIGNMENT_ALREADY_ENDED", "This assignment has already ended.");
        }
        assignment.setAssignmentStatus("ENDED");
        assignment.setEffectiveTo(AppTime.nowLocalDateTime());
        assignment.setChangeReason(StringUtils.hasText(changeReason) ? changeReason : "MANUAL_END");
        packageTeacherAssignmentMapper.updateById(assignment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void switchPrimaryTeacher(Long packageId, Long newTeacherId, String changeReason) {
        // 1. Validate course package exists
        requireCoursePackage(packageId);

        // 2. Validate new teacher exists
        requireTeacher(newTeacherId);

        // 3. Deactivate current primary
        deactivateCurrentPrimary(packageId);

        // 4. Create new primary assignment
        PackageTeacherAssignmentEntity assignment = new PackageTeacherAssignmentEntity();
        assignment.setPackageId(packageId);
        assignment.setTeacherId(newTeacherId);
        assignment.setAllocatedUnits(null);
        assignment.setIsPrimary(1);
        assignment.setEffectiveFrom(AppTime.nowLocalDateTime());
        assignment.setAssignmentStatus("ACTIVE");
        assignment.setChangeReason(StringUtils.hasText(changeReason) ? changeReason : "TEACHER_CHANGE");
        packageTeacherAssignmentMapper.insert(assignment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceAllocations(Long packageId, PackageTeacherAllocationRequest request) {
        CoursePackageEntity coursePackage = requireCoursePackage(packageId);
        if (!"ONE_ON_ONE".equals(coursePackage.getTeachingMode())) {
            throw new BusinessException("TEACHER_ALLOCATION_NOT_SUPPORTED",
                    "Teacher-hour allocations are only available for 1-on-1 packages.");
        }

        Set<Long> teacherIds = new HashSet<>();
        int totalAllocatedUnits = 0;
        int primaryCount = 0;
        Map<Long, Integer> consumedUnitsByTeacher = loadConsumedUnitsByTeacher(packageId);
        for (PackageTeacherAllocationRequest.AllocationItem item : request.getAllocations()) {
            if (!teacherIds.add(item.getTeacherId())) {
                throw new BusinessException("DUPLICATE_TEACHER_ALLOCATION",
                        "Each teacher can only appear once in an allocation.");
            }
            requireTeacher(item.getTeacherId());
            int allocatedUnits = UnitConverter.toHalfHourUnits(item.getAllocatedHours());
            int consumedUnits = consumedUnitsByTeacher.getOrDefault(item.getTeacherId(), 0);
            if (allocatedUnits < consumedUnits) {
                throw new BusinessException("TEACHER_ALLOCATION_BELOW_CONSUMED",
                        "A teacher's allocation cannot be lower than their confirmed teaching hours.");
            }
            totalAllocatedUnits += allocatedUnits;
            if (Boolean.TRUE.equals(item.getPrimary())) {
                primaryCount++;
            }
        }
        if (totalAllocatedUnits > coursePackage.getTotalUnits()) {
            throw new BusinessException("TEACHER_ALLOCATION_EXCEEDS_PACKAGE",
                    "Allocated teacher hours cannot exceed the package's available total hours.");
        }
        if (primaryCount > 1) {
            throw new BusinessException("MULTIPLE_PRIMARY_TEACHERS", "Only one teacher can be primary.");
        }
        if (consumedUnitsByTeacher.keySet().stream().anyMatch(teacherId -> !teacherIds.contains(teacherId))) {
            throw new BusinessException("CONSUMED_TEACHER_ALLOCATION_REQUIRED",
                    "Teachers with confirmed teaching hours must remain in the allocation.");
        }

        LocalDateTime now = AppTime.nowLocalDateTime();
        List<PackageTeacherAssignmentEntity> currentAssignments = packageTeacherAssignmentMapper.selectList(
                new LambdaQueryWrapper<PackageTeacherAssignmentEntity>()
                        .eq(PackageTeacherAssignmentEntity::getPackageId, packageId)
                        .eq(PackageTeacherAssignmentEntity::getAssignmentStatus, "ACTIVE"));
        for (PackageTeacherAssignmentEntity current : currentAssignments) {
            current.setAssignmentStatus("ENDED");
            current.setEffectiveTo(now);
            current.setChangeReason(StringUtils.hasText(request.getReason())
                    ? request.getReason().trim()
                    : "ALLOCATION_ADJUSTMENT");
            packageTeacherAssignmentMapper.updateById(current);
        }

        boolean first = true;
        for (PackageTeacherAllocationRequest.AllocationItem item : request.getAllocations()) {
            PackageTeacherAssignmentEntity assignment = new PackageTeacherAssignmentEntity();
            assignment.setPackageId(packageId);
            assignment.setTeacherId(item.getTeacherId());
            assignment.setAllocatedUnits(UnitConverter.toHalfHourUnits(item.getAllocatedHours()));
            assignment.setSubjectName(StringUtils.hasText(item.getSubjectName()) ? item.getSubjectName().trim() : null);
            assignment.setHourlyRateSnapshot(item.getHourlyRateSnapshot());
            assignment.setIsPrimary(Boolean.TRUE.equals(item.getPrimary()) || (primaryCount == 0 && first) ? 1 : 0);
            assignment.setEffectiveFrom(now);
            assignment.setAssignmentStatus("ACTIVE");
            assignment.setChangeReason(StringUtils.hasText(request.getReason())
                    ? request.getReason().trim()
                    : "ALLOCATION_ADJUSTMENT");
            packageTeacherAssignmentMapper.insert(assignment);
            first = false;
        }
    }

    /**
     * 停用当前主老师。
     */
    private void deactivateCurrentPrimary(Long packageId) {
        List<PackageTeacherAssignmentEntity> currentPrimary = packageTeacherAssignmentMapper.selectList(
                new LambdaQueryWrapper<PackageTeacherAssignmentEntity>()
                        .eq(PackageTeacherAssignmentEntity::getPackageId, packageId)
                        .eq(PackageTeacherAssignmentEntity::getIsPrimary, 1)
                        .eq(PackageTeacherAssignmentEntity::getAssignmentStatus, "ACTIVE")
        );
        LocalDateTime now = AppTime.nowLocalDateTime();
        for (PackageTeacherAssignmentEntity assignment : currentPrimary) {
            assignment.setAssignmentStatus("ENDED");
            assignment.setEffectiveTo(now);
            assignment.setChangeReason("TEACHER_REPLACEMENT");
            packageTeacherAssignmentMapper.updateById(assignment);
        }
    }

    private PackageTeacherAssignmentEntity requireAssignment(Long id) {
        PackageTeacherAssignmentEntity assignment = packageTeacherAssignmentMapper.selectById(id);
        if (assignment == null) {
            throw new BusinessException("ASSIGNMENT_NOT_FOUND", "Teacher assignment not found.");
        }
        return assignment;
    }

    private CoursePackageEntity requireCoursePackage(Long id) {
        CoursePackageEntity packageEntity = coursePackageMapper.selectById(id);
        if (packageEntity == null) {
            throw new BusinessException("COURSE_PACKAGE_NOT_FOUND", "Course package not found.");
        }
        return packageEntity;
    }

    private TeacherEntity requireTeacher(Long id) {
        TeacherEntity teacher = teacherMapper.selectById(id);
        if (teacher == null) {
            throw new BusinessException("TEACHER_NOT_FOUND", "Teacher not found.");
        }
        return teacher;
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
                .hourlyRateSnapshot(item.getHourlyRateSnapshot())
                .primary(item.getIsPrimary() != null && item.getIsPrimary() == 1)
                .assignmentStatus(item.getAssignmentStatus())
                .effectiveFrom(item.getEffectiveFrom())
                .effectiveTo(item.getEffectiveTo())
                .changeReason(item.getChangeReason())
                .build();
    }
}
