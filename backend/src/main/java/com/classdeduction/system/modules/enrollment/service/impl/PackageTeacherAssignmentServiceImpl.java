package com.classdeduction.system.modules.enrollment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.dto.PackageTeacherAssignmentRequest;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.PackageTeacherAssignmentEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.service.PackageTeacherAssignmentService;
import com.classdeduction.system.modules.enrollment.vo.PackageTeacherAssignmentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
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
    private final CoursePackageMapper coursePackageMapper;
    private final TeacherMapper teacherMapper;

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
        return assignments.stream().map(item -> toVO(item, teacherMap.get(item.getTeacherId()))).toList();
    }

    @Override
    public PackageTeacherAssignmentVO getAssignment(Long id) {
        PackageTeacherAssignmentEntity assignment = requireAssignment(id);
        TeacherEntity teacher = teacherMapper.selectById(assignment.getTeacherId());
        return toVO(assignment, teacher);
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
        assignment.setIsPrimary(1);
        assignment.setEffectiveFrom(AppTime.nowLocalDateTime());
        assignment.setAssignmentStatus("ACTIVE");
        assignment.setChangeReason(StringUtils.hasText(changeReason) ? changeReason : "TEACHER_CHANGE");
        packageTeacherAssignmentMapper.insert(assignment);
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

    private PackageTeacherAssignmentVO toVO(PackageTeacherAssignmentEntity item, TeacherEntity teacher) {
        return PackageTeacherAssignmentVO.builder()
                .id(item.getId())
                .teacherId(item.getTeacherId())
                .teacherNo(teacher == null ? null : teacher.getTeacherNo())
                .teacherName(teacher == null ? null : teacher.getTeacherName())
                .primary(item.getIsPrimary() != null && item.getIsPrimary() == 1)
                .assignmentStatus(item.getAssignmentStatus())
                .effectiveFrom(item.getEffectiveFrom())
                .effectiveTo(item.getEffectiveTo())
                .changeReason(item.getChangeReason())
                .build();
    }
}
