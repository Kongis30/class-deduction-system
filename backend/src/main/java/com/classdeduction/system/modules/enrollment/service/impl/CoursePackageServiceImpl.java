package com.classdeduction.system.modules.enrollment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.common.util.UnitConverter;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.dto.CoursePackagePageQuery;
import com.classdeduction.system.modules.enrollment.dto.CoursePackageHoursAdjustmentRequest;
import com.classdeduction.system.modules.enrollment.dto.CoursePackageUpdateRequest;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.enrollment.entity.PackageTeacherAssignmentEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.service.CoursePackageService;
import com.classdeduction.system.modules.enrollment.vo.CoursePackageDetailVO;
import com.classdeduction.system.modules.enrollment.vo.CoursePackageListItemVO;
import com.classdeduction.system.modules.enrollment.vo.GroupClassPackageProgressVO;
import com.classdeduction.system.modules.enrollment.vo.GroupClassStudentProgressVO;
import com.classdeduction.system.modules.enrollment.vo.PackageTeacherAssignmentVO;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.entity.HourLedgerEntity;
import com.classdeduction.system.modules.lesson.entity.PackageBalanceSnapshotEntity;
import com.classdeduction.system.modules.lesson.mapper.HourLedgerMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.lesson.mapper.PackageBalanceSnapshotMapper;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课时包服务：负责课时包列表与详情查询。
 */
@Service
@RequiredArgsConstructor
public class CoursePackageServiceImpl implements CoursePackageService {

    private final CoursePackageMapper coursePackageMapper;
    private final EnrollmentOrderItemMapper enrollmentOrderItemMapper;
    private final PackageTeacherAssignmentMapper packageTeacherAssignmentMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final CourseProductMapper courseProductMapper;
    private final ClassGroupMapper classGroupMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final LessonRecordMapper lessonRecordMapper;
    private final HourLedgerMapper hourLedgerMapper;
    private final PackageBalanceSnapshotMapper packageBalanceSnapshotMapper;

    @Override
    public PageResponse<CoursePackageListItemVO> pagePackages(CoursePackagePageQuery query) {
        Page<CoursePackageEntity> page = new Page<>(query.getPageNum(), query.getPageSize());
        List<Long> matchedStudentIds = findMatchedStudentIds(query.getKeyword());
        List<Long> matchedProductIds = findMatchedOneOnOneProductIds(query.getKeyword());
        List<Long> matchedPackageIds = findMatchedPrimaryPackageIds(query.getTeacherId());
        if (query.getTeacherId() != null && matchedPackageIds.isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }

        QueryWrapper<CoursePackageEntity> wrapper = new QueryWrapper<>();
        var lambda = wrapper.lambda();
        lambda.and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(CoursePackageEntity::getPackageNo, query.getKeyword())
                        .or()
                        .like(CoursePackageEntity::getPackageName, query.getKeyword())
                        .or(!matchedStudentIds.isEmpty(), nested -> nested.in(CoursePackageEntity::getStudentId, matchedStudentIds))
                        .or(!matchedProductIds.isEmpty(), nested -> nested.in(CoursePackageEntity::getCourseProductId, matchedProductIds)))
                .in(query.getTeacherId() != null, CoursePackageEntity::getId, matchedPackageIds)
                .eq(query.getStudentId() != null, CoursePackageEntity::getStudentId, query.getStudentId())
                .eq(query.getCourseProductId() != null, CoursePackageEntity::getCourseProductId, query.getCourseProductId())
                .eq(StringUtils.hasText(query.getTeachingMode()), CoursePackageEntity::getTeachingMode, query.getTeachingMode())
                .eq(StringUtils.hasText(query.getPackageStatus()), CoursePackageEntity::getPackageStatus, query.getPackageStatus());

        // Apply user-specified sort before default createdAt sort
        if (StringUtils.hasText(query.getSortBy())) {
            boolean isAsc = !"desc".equals(query.getSortOrder());
            if ("subjectName".equals(query.getSortBy())) {
                lambda.orderBy(true, isAsc, CoursePackageEntity::getSubjectName);
            }
        }
        lambda.orderByDesc(CoursePackageEntity::getCreatedAt);

        IPage<CoursePackageEntity> result = coursePackageMapper.selectPage(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }

        Map<Long, StudentEntity> studentMap = studentMapper.selectBatchIds(
                        result.getRecords().stream().map(CoursePackageEntity::getStudentId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(StudentEntity::getId, item -> item));
        Map<Long, CourseProductEntity> productMap = loadProducts(result.getRecords().stream()
                .map(CoursePackageEntity::getCourseProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<Long, String> primaryTeacherNameMap = buildPrimaryTeacherNameMap(result.getRecords().stream().map(CoursePackageEntity::getId).toList());

        List<CoursePackageListItemVO> records = result.getRecords().stream()
                .map(item -> toListItem(item, studentMap.get(item.getStudentId()), productMap.get(item.getCourseProductId()), primaryTeacherNameMap.get(item.getId())))
                .toList();
        return PageResponse.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public PageResponse<GroupClassPackageProgressVO> pageGroupClassPackages(CoursePackagePageQuery query) {
        Page<ClassGroupEntity> page = new Page<>(query.getPageNum(), query.getPageSize());
        List<Long> matchedClassIdsByStudent = findMatchedClassIdsByStudent(query.getStudentId());
        if (query.getStudentId() != null && matchedClassIdsByStudent.isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }
        List<Long> matchedProductIds = findMatchedGroupProductIds(query.getKeyword());

        QueryWrapper<ClassGroupEntity> wrapper = new QueryWrapper<>();
        var lambda = wrapper.lambda();
        lambda.isNotNull(ClassGroupEntity::getProductId)
                .in(query.getStudentId() != null, ClassGroupEntity::getId, matchedClassIdsByStudent)
                .eq(StringUtils.hasText(query.getPackageStatus()), ClassGroupEntity::getStatus, query.getPackageStatus())
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(ClassGroupEntity::getClassName, query.getKeyword())
                        .or(!matchedProductIds.isEmpty(), nested -> nested.in(ClassGroupEntity::getProductId, matchedProductIds)))
                .orderByDesc(ClassGroupEntity::getCreatedAt);

        IPage<ClassGroupEntity> result = classGroupMapper.selectPage(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }

        List<Long> classIds = result.getRecords().stream().map(ClassGroupEntity::getId).toList();
        Map<Long, CourseProductEntity> productMap = loadProducts(result.getRecords().stream()
                .map(ClassGroupEntity::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<Long, TeacherEntity> teacherMap = loadTeachers(result.getRecords().stream()
                .map(ClassGroupEntity::getTeacherId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<Long, Long> activeCounts = toLongMap(classEnrollmentMapper.countActiveByClassIds(classIds), "class_id", "cnt");
        Map<Long, List<LessonOccurrenceEntity>> occurrenceMap = loadClassOccurrenceMap(classIds);
        Map<Long, Long> confirmedOccurrenceCounts = countConfirmedOccurrencesByClass(occurrenceMap);

        List<GroupClassPackageProgressVO> records = result.getRecords().stream()
                .map(item -> toGroupClassProgress(item, productMap.get(item.getProductId()), teacherMap.get(item.getTeacherId()),
                        activeCounts.getOrDefault(item.getId(), 0L), occurrenceMap.getOrDefault(item.getId(), Collections.emptyList()),
                        confirmedOccurrenceCounts.getOrDefault(item.getId(), 0L)))
                .toList();
        return PageResponse.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public List<GroupClassStudentProgressVO> listGroupClassStudentProgress(Long classId) {
        ClassGroupEntity classGroup = classGroupMapper.selectById(classId);
        if (classGroup == null) {
            throw new BusinessException("CLASS_NOT_FOUND", "Class group not found.");
        }
        List<ClassEnrollmentEntity> enrollments = classEnrollmentMapper.selectAllStatusesByClassId(classId);
        if (enrollments.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, StudentEntity> studentMap = studentMapper.selectBatchIds(enrollments.stream()
                        .map(ClassEnrollmentEntity::getStudentId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(StudentEntity::getId, item -> item));
        List<LessonOccurrenceEntity> occurrences = lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getClassGroupId, classId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED"));
        Map<Long, LessonOccurrenceEntity> occurrenceById = occurrences.stream()
                .collect(Collectors.toMap(LessonOccurrenceEntity::getId, item -> item));

        return enrollments.stream()
                .map(enrollment -> toStudentProgress(enrollment, studentMap.get(enrollment.getStudentId()),
                        occurrences, occurrenceById))
                .toList();
    }

    @Override
    public CoursePackageDetailVO getPackage(Long id) {
        CoursePackageEntity coursePackage = coursePackageMapper.selectById(id);
        if (coursePackage == null) {
            throw new BusinessException("COURSE_PACKAGE_NOT_FOUND", "Course package not found.");
        }
        StudentEntity student = studentMapper.selectById(coursePackage.getStudentId());
        CourseProductEntity courseProduct = courseProductMapper.selectById(coursePackage.getCourseProductId());
        EnrollmentOrderItemEntity orderItem = coursePackage.getEnrollmentOrderItemId() == null
                ? null
                : enrollmentOrderItemMapper.selectById(coursePackage.getEnrollmentOrderItemId());
        List<PackageTeacherAssignmentEntity> assignments = packageTeacherAssignmentMapper.selectList(new LambdaQueryWrapper<PackageTeacherAssignmentEntity>()
                .eq(PackageTeacherAssignmentEntity::getPackageId, id)
                .orderByDesc(PackageTeacherAssignmentEntity::getEffectiveFrom));
        Map<Long, TeacherEntity> teacherMap = assignments.isEmpty()
                ? Collections.emptyMap()
                : teacherMapper.selectBatchIds(assignments.stream().map(PackageTeacherAssignmentEntity::getTeacherId).toList())
                .stream()
                .collect(Collectors.toMap(TeacherEntity::getId, item -> item));
        Map<Long, Integer> consumedUnitsByTeacher = loadConsumedUnitsByTeacher(id);

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
                .purchasedHours(resolvePurchasedHours(coursePackage, orderItem))
                .totalUnits(UnitConverter.toHoursDecimal(coursePackage.getTotalUnits()))
                .defaultLessonUnits(coursePackage.getDefaultLessonUnits())
                .consumedUnits(UnitConverter.toHoursDecimal(coursePackage.getConsumedUnits()))
                .remainingUnits(UnitConverter.toHoursDecimal(coursePackage.getRemainingUnits()))
                .allocatedTeacherHours(activeAllocatedHours(assignments))
                .unallocatedTeacherHours(Math.max(0.0, UnitConverter.toHoursDecimal(coursePackage.getTotalUnits()) - activeAllocatedHours(assignments)))
                .originalAmount(coursePackage.getOriginalAmount())
                .discountAmount(coursePackage.getDiscountAmount())
                .receivedAmount(coursePackage.getReceivedAmount())
                .packageStatus(coursePackage.getPackageStatus())
                .startDate(coursePackage.getStartDate())
                .estimatedEndDate(coursePackage.getEstimatedEndDate())
                .remark(coursePackage.getRemark())
                .teacherAssignments(assignments.stream()
                        .map(item -> toAssignmentVO(
                                item,
                                teacherMap.get(item.getTeacherId()),
                                consumedUnitsByTeacher.getOrDefault(item.getTeacherId(), 0)))
                        .toList())
                .createdAt(coursePackage.getCreatedAt())
                .updatedAt(coursePackage.getUpdatedAt())
                .build();
    }

    private double resolvePurchasedHours(CoursePackageEntity coursePackage, EnrollmentOrderItemEntity orderItem) {
        if (orderItem != null && orderItem.getOneOnOneUnits() != null) {
            return UnitConverter.toHoursDecimal(orderItem.getOneOnOneUnits());
        }
        return UnitConverter.toHoursDecimal(coursePackage.getTotalUnits());
    }

    private double activeAllocatedHours(List<PackageTeacherAssignmentEntity> assignments) {
        int allocatedUnits = assignments.stream()
                .filter(item -> "ACTIVE".equals(item.getAssignmentStatus()))
                .map(PackageTeacherAssignmentEntity::getAllocatedUnits)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        return UnitConverter.toHoursDecimal(allocatedUnits);
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

    private List<Long> findMatchedClassIdsByStudent(Long studentId) {
        if (studentId == null) {
            return Collections.emptyList();
        }
        return classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getStudentId, studentId)
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE"))
                .stream()
                .map(ClassEnrollmentEntity::getClassId)
                .distinct()
                .toList();
    }

    private List<Long> findMatchedGroupProductIds(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        return courseProductMapper.selectList(new LambdaQueryWrapper<CourseProductEntity>()
                        .eq(CourseProductEntity::getTeachingMode, "GROUP")
                        .and(w -> w.like(CourseProductEntity::getProductName, keyword)
                                .or()
                                .like(CourseProductEntity::getSubjectName, keyword)
                                .or()
                                .like(CourseProductEntity::getProductCode, keyword)))
                .stream()
                .map(CourseProductEntity::getId)
                .toList();
    }

    private List<Long> findMatchedOneOnOneProductIds(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        return courseProductMapper.selectList(new LambdaQueryWrapper<CourseProductEntity>()
                        .eq(CourseProductEntity::getTeachingMode, "ONE_ON_ONE")
                        .and(w -> w.like(CourseProductEntity::getProductName, keyword)
                                .or()
                                .like(CourseProductEntity::getSubjectName, keyword)
                                .or()
                                .like(CourseProductEntity::getProductCode, keyword)))
                .stream()
                .map(CourseProductEntity::getId)
                .toList();
    }

    private Map<Long, CourseProductEntity> loadProducts(Set<Long> productIds) {
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return courseProductMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(CourseProductEntity::getId, item -> item));
    }

    private Map<Long, TeacherEntity> loadTeachers(Set<Long> teacherIds) {
        if (teacherIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return teacherMapper.selectBatchIds(teacherIds).stream()
                .collect(Collectors.toMap(TeacherEntity::getId, item -> item));
    }

    private Map<Long, List<LessonOccurrenceEntity>> loadClassOccurrenceMap(List<Long> classIds) {
        if (classIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .in(LessonOccurrenceEntity::getClassGroupId, classIds)
                        .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED"))
                .stream()
                .collect(Collectors.groupingBy(LessonOccurrenceEntity::getClassGroupId));
    }

    private Map<Long, Long> countConfirmedOccurrencesByClass(Map<Long, List<LessonOccurrenceEntity>> occurrenceMap) {
        List<LessonOccurrenceEntity> occurrences = occurrenceMap.values().stream()
                .flatMap(List::stream)
                .toList();
        if (occurrences.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> occurrenceClassMap = occurrences.stream()
                .collect(Collectors.toMap(LessonOccurrenceEntity::getId, LessonOccurrenceEntity::getClassGroupId));
        List<LessonRecordEntity> records = lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                .in(LessonRecordEntity::getOccurrenceId, occurrenceClassMap.keySet())
                .eq(LessonRecordEntity::getConfirmStage, "CONFIRMED")
                .eq(LessonRecordEntity::getIsDeleted, 0));
        return records.stream()
                .map(LessonRecordEntity::getOccurrenceId)
                .filter(occurrenceClassMap::containsKey)
                .distinct()
                .collect(Collectors.groupingBy(occurrenceClassMap::get, Collectors.counting()));
    }

    private GroupClassPackageProgressVO toGroupClassProgress(ClassGroupEntity classGroup,
                                                            CourseProductEntity product,
                                                            TeacherEntity teacher,
                                                            Long activeStudentCount,
                                                            List<LessonOccurrenceEntity> occurrences,
                                                            Long confirmedOccurrenceCount) {
        int total = resolveClassTotalUnits(classGroup, occurrences);
        int consumed = Math.min(confirmedOccurrenceCount.intValue(), total);
        return GroupClassPackageProgressVO.builder()
                .classId(classGroup.getId())
                .productId(classGroup.getProductId())
                .className(classGroup.getClassName())
                .productName(product == null ? classGroup.getClassName() : product.getProductName())
                .subjectName(product == null ? null : product.getSubjectName())
                .billingType(classGroup.getBillingType())
                .scheduleDesc(classGroup.getScheduleDesc())
                .teacherName(teacher == null ? null : teacher.getTeacherName())
                .totalUnits(total)
                .consumedUnits(consumed)
                .remainingUnits(Math.max(total - consumed, 0))
                .activeStudentCount(activeStudentCount)
                .status(classGroup.getStatus())
                .startDate(resolveClassStartDate(classGroup, occurrences))
                .estimatedEndDate(resolveClassEndDate(classGroup, occurrences))
                .build();
    }

    private GroupClassStudentProgressVO toStudentProgress(ClassEnrollmentEntity enrollment,
                                                          StudentEntity student,
                                                          List<LessonOccurrenceEntity> occurrences,
                                                          Map<Long, LessonOccurrenceEntity> occurrenceById) {
        int total = resolveStudentTotalUnits(enrollment, occurrences);
        int consumed = occurrenceById.isEmpty()
                ? 0
                : (int) lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                                .eq(LessonRecordEntity::getStudentId, enrollment.getStudentId())
                                .in(LessonRecordEntity::getOccurrenceId, occurrenceById.keySet())
                                .eq(LessonRecordEntity::getConfirmStage, "CONFIRMED")
                                .eq(LessonRecordEntity::getIsDeleted, 0))
                        .stream()
                        .filter(record -> isOccurrenceInEnrollmentWindow(occurrenceById.get(record.getOccurrenceId()), enrollment))
                        .count();
        return GroupClassStudentProgressVO.builder()
                .enrollmentId(enrollment.getId())
                .classId(enrollment.getClassId())
                .studentId(enrollment.getStudentId())
                .studentName(student == null ? null : student.getStudentName())
                .billingType(enrollment.getBillingType())
                .totalUnits(total)
                .consumedUnits(Math.min(consumed, total))
                .remainingUnits(Math.max(total - consumed, 0))
                .enrollmentDate(enrollment.getEnrollmentDate())
                .expiryDate(enrollment.getExpiryDate())
                .enrollmentStatus(enrollment.getStatus())
                .build();
    }

    private int resolveClassTotalUnits(ClassGroupEntity classGroup, List<LessonOccurrenceEntity> occurrences) {
        if ("PER_SESSION".equals(classGroup.getBillingType()) && classGroup.getTotalSessions() != null) {
            return classGroup.getTotalSessions();
        }
        return occurrences.size();
    }

    private int resolveStudentTotalUnits(ClassEnrollmentEntity enrollment, List<LessonOccurrenceEntity> occurrences) {
        if ("PER_SESSION".equals(enrollment.getBillingType()) && enrollment.getPurchasedQuantity() != null) {
            return enrollment.getPurchasedQuantity();
        }
        return (int) occurrences.stream()
                .filter(occurrence -> isOccurrenceInEnrollmentWindow(occurrence, enrollment))
                .count();
    }

    private boolean isOccurrenceInEnrollmentWindow(LessonOccurrenceEntity occurrence, ClassEnrollmentEntity enrollment) {
        if (occurrence == null || occurrence.getPlannedStartAt() == null || enrollment.getEnrollmentDate() == null || enrollment.getExpiryDate() == null) {
            return false;
        }
        LocalDate lessonDate = occurrence.getPlannedStartAt().toLocalDate();
        return !lessonDate.isBefore(enrollment.getEnrollmentDate()) && !lessonDate.isAfter(enrollment.getExpiryDate());
    }

    private LocalDate resolveClassStartDate(ClassGroupEntity classGroup, List<LessonOccurrenceEntity> occurrences) {
        if (classGroup.getStartDate() != null) {
            return classGroup.getStartDate();
        }
        if (classGroup.getStartMonth() != null) {
            return classGroup.getStartMonth();
        }
        return occurrences.stream()
                .map(LessonOccurrenceEntity::getPlannedStartAt)
                .filter(Objects::nonNull)
                .map(item -> item.toLocalDate())
                .min(LocalDate::compareTo)
                .orElse(null);
    }

    private LocalDate resolveClassEndDate(ClassGroupEntity classGroup, List<LessonOccurrenceEntity> occurrences) {
        if (classGroup.getEndMonth() != null) {
            return classGroup.getEndMonth();
        }
        return occurrences.stream()
                .map(LessonOccurrenceEntity::getPlannedStartAt)
                .filter(Objects::nonNull)
                .map(item -> item.toLocalDate())
                .max(LocalDate::compareTo)
                .orElse(null);
    }

    private Map<Long, Long> toLongMap(List<Map<String, Object>> rows, String keyColumn, String valueColumn) {
        Map<Long, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object key = row.get(keyColumn);
            Object value = row.get(valueColumn);
            if (key instanceof Number && value instanceof Number) {
                result.put(((Number) key).longValue(), ((Number) value).longValue());
            }
        }
        return result;
    }

    private CoursePackageListItemVO toListItem(CoursePackageEntity item, StudentEntity student, CourseProductEntity product, String primaryTeacherName) {
        return CoursePackageListItemVO.builder()
                .id(item.getId())
                .packageNo(item.getPackageNo())
                .enrollmentOrderId(item.getEnrollmentOrderId())
                .studentId(item.getStudentId())
                .studentName(student == null ? null : student.getStudentName())
                .courseProductId(item.getCourseProductId())
                .courseProductName(product == null ? null : product.getProductName())
                .packageName(item.getPackageName())
                .subjectName(item.getSubjectName())
                .teachingMode(item.getTeachingMode())
                .totalUnits(UnitConverter.toHoursDecimal(item.getTotalUnits()))
                .consumedUnits(UnitConverter.toHoursDecimal(item.getConsumedUnits()))
                .remainingUnits(UnitConverter.toHoursDecimal(item.getRemainingUnits()))
                .receivedAmount(item.getReceivedAmount())
                .packageStatus(item.getPackageStatus())
                .startDate(item.getStartDate())
                .estimatedEndDate(item.getEstimatedEndDate())
                .primaryTeacherName(primaryTeacherName)
                .createdAt(item.getCreatedAt())
                .build();
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

    private PackageTeacherAssignmentVO toAssignmentVO(PackageTeacherAssignmentEntity item,
                                                       TeacherEntity teacher,
                                                       int consumedUnits) {
        Integer allocatedUnits = item.getAllocatedUnits();
        return PackageTeacherAssignmentVO.builder()
                .id(item.getId())
                .teacherId(item.getTeacherId())
                .teacherNo(teacher == null ? null : teacher.getTeacherNo())
                .teacherName(teacher == null ? null : teacher.getTeacherName())
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
    @Transactional(rollbackFor = Exception.class)
    public void adjustPackageHours(Long id, CoursePackageHoursAdjustmentRequest request, Long operatorId) {
        CoursePackageEntity coursePackage = requireCoursePackage(id);
        if (!"ONE_ON_ONE".equals(coursePackage.getTeachingMode())) {
            throw new BusinessException("PACKAGE_HOURS_NOT_SUPPORTED", "Hour adjustments are only available for 1-on-1 packages.");
        }
        int changeUnits = UnitConverter.toHalfHourUnits(request.getChangeHours());
        if (changeUnits == 0) {
            throw new BusinessException("ZERO_HOUR_ADJUSTMENT", "Hour change cannot be zero.");
        }
        if ("BONUS_HOURS".equals(request.getAdjustmentType()) && changeUnits < 0) {
            throw new BusinessException("INVALID_BONUS_HOURS", "Bonus hours must increase the available total.");
        }
        int newTotalUnits = coursePackage.getTotalUnits() + changeUnits;
        int newRemainingUnits = coursePackage.getRemainingUnits() + changeUnits;
        if (newTotalUnits < coursePackage.getConsumedUnits() || newRemainingUnits < 0) {
            throw new BusinessException("INSUFFICIENT_PACKAGE_HOURS", "The adjustment cannot reduce hours below the consumed balance.");
        }
        int activeAllocatedUnits = packageTeacherAssignmentMapper.selectList(
                        new LambdaQueryWrapper<PackageTeacherAssignmentEntity>()
                                .eq(PackageTeacherAssignmentEntity::getPackageId, id)
                                .eq(PackageTeacherAssignmentEntity::getAssignmentStatus, "ACTIVE"))
                .stream()
                .map(PackageTeacherAssignmentEntity::getAllocatedUnits)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        if (newTotalUnits < activeAllocatedUnits) {
            throw new BusinessException("HOURS_BELOW_TEACHER_ALLOCATION",
                    "Reduce teacher allocations before reducing package hours.");
        }

        coursePackage.setTotalUnits(newTotalUnits);
        coursePackage.setRemainingUnits(newRemainingUnits);
        if (newRemainingUnits > 0 && "COMPLETED".equals(coursePackage.getPackageStatus())) {
            coursePackage.setPackageStatus("ACTIVE");
            coursePackage.setCompletedAt(null);
        }
        if (coursePackageMapper.updateById(coursePackage) != 1) {
            throw new BusinessException("PACKAGE_CONCURRENT_UPDATE", "The package was updated by another request. Please retry.");
        }

        HourLedgerEntity ledger = new HourLedgerEntity();
        ledger.setPackageId(id);
        ledger.setLedgerType("MANUAL_ADJUST");
        ledger.setChangeUnits(changeUnits);
        ledger.setBalanceAfterUnits(newRemainingUnits);
        ledger.setBusinessDate(AppTime.nowLocalDateTime().toLocalDate());
        ledger.setReasonCode(request.getAdjustmentType());
        ledger.setReasonDesc(StringUtils.hasText(request.getReason()) ? request.getReason().trim() : null);
        ledger.setOperatedBy(operatorId);
        ledger.setOperatedAt(AppTime.nowLocalDateTime());
        hourLedgerMapper.insert(ledger);
        upsertBalanceSnapshot(coursePackage, ledger.getId());
    }

    private void upsertBalanceSnapshot(CoursePackageEntity coursePackage, Long lastLedgerId) {
        PackageBalanceSnapshotEntity snapshot = packageBalanceSnapshotMapper.selectById(coursePackage.getId());
        boolean insert = snapshot == null;
        if (insert) {
            snapshot = new PackageBalanceSnapshotEntity();
            snapshot.setPackageId(coursePackage.getId());
        }
        snapshot.setTotalUnits(coursePackage.getTotalUnits());
        snapshot.setConsumedUnits(coursePackage.getConsumedUnits());
        snapshot.setRemainingUnits(coursePackage.getRemainingUnits());
        snapshot.setLastLedgerId(lastLedgerId);
        snapshot.setSnapshotAt(AppTime.nowLocalDateTime());
        if (insert) {
            packageBalanceSnapshotMapper.insert(snapshot);
        } else {
            packageBalanceSnapshotMapper.updateById(snapshot);
        }
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
            throw new BusinessException("PACKAGE_ALREADY_CLOSED", "Course package is already closed.");
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
                    String.format("Status transition is not allowed: %s -> %s", currentStatus, newStatus));
        }
    }

    private CoursePackageEntity requireCoursePackage(Long id) {
        CoursePackageEntity packageEntity = coursePackageMapper.selectById(id);
        if (packageEntity == null) {
            throw new BusinessException("COURSE_PACKAGE_NOT_FOUND", "Course package not found.");
        }
        return packageEntity;
    }
}
