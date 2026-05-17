package com.classdeduction.system.modules.enrollment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.util.UnitConverter;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.dto.CoursePackagePageQuery;
import com.classdeduction.system.modules.enrollment.dto.CoursePackageUpdateRequest;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.PackageTeacherAssignmentEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.service.CoursePackageService;
import com.classdeduction.system.modules.enrollment.vo.CoursePackageDetailVO;
import com.classdeduction.system.modules.enrollment.vo.CoursePackageListItemVO;
import com.classdeduction.system.modules.enrollment.vo.PackageTeacherAssignmentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课时包服务：负责课时包列表与详情查询。
 */
@Service
@RequiredArgsConstructor
public class CoursePackageServiceImpl implements CoursePackageService {

    private final CoursePackageMapper coursePackageMapper;
    private final PackageTeacherAssignmentMapper packageTeacherAssignmentMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final CourseProductMapper courseProductMapper;

    @Override
    public PageResponse<CoursePackageListItemVO> pagePackages(CoursePackagePageQuery query) {
        Page<CoursePackageEntity> page = new Page<>(query.getPageNum(), query.getPageSize());
        List<Long> matchedStudentIds = findMatchedStudentIds(query.getKeyword());
        List<Long> matchedPackageIds = findMatchedPrimaryPackageIds(query.getTeacherId());
        if (query.getTeacherId() != null && matchedPackageIds.isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }
        LambdaQueryWrapper<CoursePackageEntity> wrapper = new LambdaQueryWrapper<CoursePackageEntity>()
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(CoursePackageEntity::getPackageNo, query.getKeyword())
                        .or()
                        .like(CoursePackageEntity::getPackageName, query.getKeyword())
                        .or(!matchedStudentIds.isEmpty(), nested -> nested.in(CoursePackageEntity::getStudentId, matchedStudentIds)))
                .in(query.getTeacherId() != null, CoursePackageEntity::getId, matchedPackageIds)
                .eq(query.getStudentId() != null, CoursePackageEntity::getStudentId, query.getStudentId())
                .eq(StringUtils.hasText(query.getPackageStatus()), CoursePackageEntity::getPackageStatus, query.getPackageStatus())
                .orderByDesc(CoursePackageEntity::getCreatedAt);
        IPage<CoursePackageEntity> result = coursePackageMapper.selectPage(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }

        Map<Long, StudentEntity> studentMap = studentMapper.selectBatchIds(
                        result.getRecords().stream().map(CoursePackageEntity::getStudentId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(StudentEntity::getId, item -> item));
        Map<Long, String> primaryTeacherNameMap = buildPrimaryTeacherNameMap(result.getRecords().stream().map(CoursePackageEntity::getId).toList());

        List<CoursePackageListItemVO> records = result.getRecords().stream()
                .map(item -> toListItem(item, studentMap.get(item.getStudentId()), primaryTeacherNameMap.get(item.getId())))
                .toList();
        return PageResponse.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public CoursePackageDetailVO getPackage(Long id) {
        CoursePackageEntity coursePackage = coursePackageMapper.selectById(id);
        if (coursePackage == null) {
            throw new BusinessException("COURSE_PACKAGE_NOT_FOUND", "课时包不存在。");
        }
        StudentEntity student = studentMapper.selectById(coursePackage.getStudentId());
        CourseProductEntity courseProduct = courseProductMapper.selectById(coursePackage.getCourseProductId());
        List<PackageTeacherAssignmentEntity> assignments = packageTeacherAssignmentMapper.selectList(new LambdaQueryWrapper<PackageTeacherAssignmentEntity>()
                .eq(PackageTeacherAssignmentEntity::getPackageId, id)
                .orderByDesc(PackageTeacherAssignmentEntity::getEffectiveFrom));
        Map<Long, TeacherEntity> teacherMap = assignments.isEmpty()
                ? Collections.emptyMap()
                : teacherMapper.selectBatchIds(assignments.stream().map(PackageTeacherAssignmentEntity::getTeacherId).toList())
                .stream()
                .collect(Collectors.toMap(TeacherEntity::getId, item -> item));

        return CoursePackageDetailVO.builder()
                .id(coursePackage.getId())
                .packageNo(coursePackage.getPackageNo())
                .enrollmentOrderId(coursePackage.getEnrollmentOrderId())
                .studentId(coursePackage.getStudentId())
                .studentName(student == null ? null : student.getStudentName())
                .courseProductId(coursePackage.getCourseProductId())
                .courseProductName(courseProduct == null ? null : courseProduct.getProductName())
                .packageName(coursePackage.getPackageName())
                .subjectName(coursePackage.getSubjectName())
                .teachingMode(coursePackage.getTeachingMode())
                .totalUnits(UnitConverter.toHours(coursePackage.getTotalUnits()))
                .defaultLessonUnits(coursePackage.getDefaultLessonUnits())
                .consumedUnits(UnitConverter.toHours(coursePackage.getConsumedUnits()))
                .remainingUnits(UnitConverter.toHours(coursePackage.getRemainingUnits()))
                .originalAmount(coursePackage.getOriginalAmount())
                .discountAmount(coursePackage.getDiscountAmount())
                .receivedAmount(coursePackage.getReceivedAmount())
                .packageStatus(coursePackage.getPackageStatus())
                .startDate(coursePackage.getStartDate())
                .estimatedEndDate(coursePackage.getEstimatedEndDate())
                .remark(coursePackage.getRemark())
                .teacherAssignments(assignments.stream().map(item -> toAssignmentVO(item, teacherMap.get(item.getTeacherId()))).toList())
                .createdAt(coursePackage.getCreatedAt())
                .updatedAt(coursePackage.getUpdatedAt())
                .build();
    }

    private Map<Long, String> buildPrimaryTeacherNameMap(List<Long> packageIds) {
        if (packageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<PackageTeacherAssignmentEntity> wrapper = new LambdaQueryWrapper<PackageTeacherAssignmentEntity>()
                .in(PackageTeacherAssignmentEntity::getPackageId, packageIds)
                .eq(PackageTeacherAssignmentEntity::getIsPrimary, 1)
                .eq(PackageTeacherAssignmentEntity::getAssignmentStatus, "ACTIVE")
                .orderByDesc(PackageTeacherAssignmentEntity::getEffectiveFrom);
        List<PackageTeacherAssignmentEntity> assignments = packageTeacherAssignmentMapper.selectList(wrapper);
        if (assignments.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, TeacherEntity> teacherMap = teacherMapper.selectBatchIds(assignments.stream().map(PackageTeacherAssignmentEntity::getTeacherId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(TeacherEntity::getId, item -> item));
        Map<Long, String> result = new LinkedHashMap<>();
        for (PackageTeacherAssignmentEntity assignment : assignments) {
            if (result.containsKey(assignment.getPackageId())) {
                continue;
            }
            TeacherEntity teacher = teacherMap.get(assignment.getTeacherId());
            result.put(assignment.getPackageId(), teacher == null ? null : teacher.getTeacherName());
        }
        return result;
    }

    private List<Long> findMatchedPrimaryPackageIds(Long teacherId) {
        if (teacherId == null) {
            return Collections.emptyList();
        }
        return packageTeacherAssignmentMapper.selectList(new LambdaQueryWrapper<PackageTeacherAssignmentEntity>()
                        .eq(PackageTeacherAssignmentEntity::getTeacherId, teacherId)
                        .eq(PackageTeacherAssignmentEntity::getIsPrimary, 1)
                        .eq(PackageTeacherAssignmentEntity::getAssignmentStatus, "ACTIVE"))
                .stream()
                .map(PackageTeacherAssignmentEntity::getPackageId)
                .distinct()
                .toList();
    }

    private List<Long> findMatchedStudentIds(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        return studentMapper.selectList(new LambdaQueryWrapper<StudentEntity>()
                        .and(w -> w.like(StudentEntity::getStudentName, keyword)
                                .or()
                                .like(StudentEntity::getStudentNo, keyword)))
                .stream()
                .map(StudentEntity::getId)
                .toList();
    }

    private CoursePackageListItemVO toListItem(CoursePackageEntity item, StudentEntity student, String primaryTeacherName) {
        return CoursePackageListItemVO.builder()
                .id(item.getId())
                .packageNo(item.getPackageNo())
                .enrollmentOrderId(item.getEnrollmentOrderId())
                .studentId(item.getStudentId())
                .studentName(student == null ? null : student.getStudentName())
                .courseProductId(item.getCourseProductId())
                .packageName(item.getPackageName())
                .subjectName(item.getSubjectName())
                .teachingMode(item.getTeachingMode())
                .totalUnits(UnitConverter.toHours(item.getTotalUnits()))
                .consumedUnits(UnitConverter.toHours(item.getConsumedUnits()))
                .remainingUnits(UnitConverter.toHours(item.getRemainingUnits()))
                .receivedAmount(item.getReceivedAmount())
                .packageStatus(item.getPackageStatus())
                .startDate(item.getStartDate())
                .estimatedEndDate(item.getEstimatedEndDate())
                .primaryTeacherName(primaryTeacherName)
                .createdAt(item.getCreatedAt())
                .build();
    }

    private PackageTeacherAssignmentVO toAssignmentVO(PackageTeacherAssignmentEntity item, TeacherEntity teacher) {
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePackage(Long id, CoursePackageUpdateRequest request) {
        CoursePackageEntity coursePackage = requireCoursePackage(id);

        // Validate status transition
        if (StringUtils.hasText(request.getPackageStatus())) {
            validateStatusTransition(coursePackage.getPackageStatus(), request.getPackageStatus());
            coursePackage.setPackageStatus(request.getPackageStatus());
        }

        if (StringUtils.hasText(request.getPackageName())) {
            coursePackage.setPackageName(request.getPackageName());
        }

        if (request.getStartDate() != null) {
            coursePackage.setStartDate(request.getStartDate());
        }

        if (request.getEstimatedEndDate() != null) {
            coursePackage.setEstimatedEndDate(request.getEstimatedEndDate());
        }

        if (request.getRemark() != null) {
            coursePackage.setRemark(request.getRemark());
        }

        coursePackageMapper.updateById(coursePackage);
    }

    @Override
    public Integer getPackageBalance(Long id) {
        CoursePackageEntity coursePackage = requireCoursePackage(id);
        return coursePackage.getRemainingUnits();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closePackage(Long id) {
        CoursePackageEntity coursePackage = requireCoursePackage(id);

        // Validate that the package can be closed
        if ("CLOSED".equals(coursePackage.getPackageStatus())) {
            throw new BusinessException("PACKAGE_ALREADY_CLOSED", "课时包已经是关闭状态。");
        }

        // Mark as closed
        coursePackage.setPackageStatus("CLOSED");
        coursePackageMapper.updateById(coursePackage);
    }

    /**
     * Validate package status transition rules.
     */
    private void validateStatusTransition(String currentStatus, String newStatus) {
        // Define valid transitions: DRAFT -> ACTIVE -> COMPLETED -> CLOSED
        Map<String, Set<String>> validTransitions = Map.of(
                "DRAFT", Set.of("ACTIVE", "CLOSED"),
                "ACTIVE", Set.of("COMPLETED", "CLOSED"),
                "COMPLETED", Set.of("ACTIVE", "CLOSED"),
                "CLOSED", Set.of()
        );

        Set<String> allowed = validTransitions.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(newStatus)) {
            throw new BusinessException("INVALID_STATUS_TRANSITION",
                    String.format("不允许的状态转换: %s -> %s", currentStatus, newStatus));
        }
    }

    private CoursePackageEntity requireCoursePackage(Long id) {
        CoursePackageEntity packageEntity = coursePackageMapper.selectById(id);
        if (packageEntity == null) {
            throw new BusinessException("COURSE_PACKAGE_NOT_FOUND", "课时包不存在。");
        }
        return packageEntity;
    }
}
