package com.classdeduction.system.modules.enrollment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.classdeduction.system.modules.enrollment.dto.CoursePackageDeductedHoursRequest;
import com.classdeduction.system.modules.enrollment.dto.CoursePackageResumeRequest;
import com.classdeduction.system.modules.enrollment.dto.CoursePackageUpdateRequest;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupEntity;
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupMemberEntity;
import com.classdeduction.system.modules.enrollment.util.BusinessNoGenerator;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.enrollment.entity.PackageTeacherAssignmentEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMemberMapper;
import com.classdeduction.system.modules.enrollment.service.CoursePackageService;
import com.classdeduction.system.modules.enrollment.vo.CoursePackageDetailVO;
import com.classdeduction.system.modules.enrollment.vo.CoursePackageListItemVO;
import com.classdeduction.system.modules.enrollment.vo.GroupClassPackageProgressVO;
import com.classdeduction.system.modules.enrollment.vo.GroupClassStudentProgressVO;
import com.classdeduction.system.modules.enrollment.vo.PackageTeacherAssignmentVO;
import com.classdeduction.system.modules.enrollment.vo.PrivateLessonGroupParticipantVO;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.entity.HourLedgerEntity;
import com.classdeduction.system.modules.lesson.entity.PackageBalanceSnapshotEntity;
import com.classdeduction.system.modules.lesson.mapper.HourLedgerMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.lesson.mapper.PackageBalanceSnapshotMapper;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.service.RecurringScheduleRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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

    private static final String CONFIRM_STAGE_PENDING_ADMIN = "PENDING_ADMIN";

    private final CoursePackageMapper coursePackageMapper;
    private final EnrollmentOrderItemMapper enrollmentOrderItemMapper;
    private final PackageTeacherAssignmentMapper packageTeacherAssignmentMapper;
    private final PrivateLessonGroupMapper privateLessonGroupMapper;
    private final PrivateLessonGroupMemberMapper privateLessonGroupMemberMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final CourseProductMapper courseProductMapper;
    private final ClassGroupMapper classGroupMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final RecurringScheduleRuleMapper recurringScheduleRuleMapper;
    private final RecurringScheduleSlotMapper recurringScheduleSlotMapper;
    private final LessonRecordMapper lessonRecordMapper;
    private final HourLedgerMapper hourLedgerMapper;
    private final PackageBalanceSnapshotMapper packageBalanceSnapshotMapper;
    private final RecurringScheduleRuleService recurringScheduleRuleService;

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
        Set<Long> orderItemIds = result.getRecords().stream()
                .map(CoursePackageEntity::getEnrollmentOrderItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, EnrollmentOrderItemEntity> orderItemMap = orderItemIds.isEmpty()
                ? Collections.emptyMap()
                : enrollmentOrderItemMapper.selectBatchIds(orderItemIds).stream()
                        .collect(Collectors.toMap(EnrollmentOrderItemEntity::getId, item -> item));
        Map<Long, String> primaryTeacherNameMap = buildPrimaryTeacherNameMap(result.getRecords().stream().map(CoursePackageEntity::getId).toList());

        Map<Long, Integer> pendingDeductionByPackage = loadPendingDeductionUnits(
                result.getRecords().stream().map(CoursePackageEntity::getId).toList());
        List<CoursePackageListItemVO> records = result.getRecords().stream()
                .map(item -> toListItem(item, studentMap.get(item.getStudentId()), productMap.get(item.getCourseProductId()),
                        orderItemMap.get(item.getEnrollmentOrderItemId()), primaryTeacherNameMap.get(item.getId()),
                        pendingDeductionByPackage.getOrDefault(item.getId(), 0)))
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
        List<Long> activeGroupProductIds = listActiveGroupProductIds();
        if (activeGroupProductIds.isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }
        lambda.isNotNull(ClassGroupEntity::getProductId)
                .in(ClassGroupEntity::getProductId, activeGroupProductIds)
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
        Map<Long, Long> confirmedOccurrenceCounts = countConfirmedSessionDaysByClass(occurrenceMap);

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
        PrivateLessonGroupEntity privateLessonGroup = loadPrivateLessonGroupByPackageId(id);
        List<PrivateLessonGroupParticipantVO> privateLessonParticipants = loadPrivateLessonParticipants(privateLessonGroup);

        return CoursePackageDetailVO.builder()
                .id(coursePackage.getId())
                .packageNo(coursePackage.getPackageNo())
                .enrollmentOrderId(coursePackage.getEnrollmentOrderId())
                .studentId(coursePackage.getStudentId())
                .studentName(student == null ? null : student.getStudentName())
                .courseProductId(coursePackage.getCourseProductId())
                .courseProductName(courseProduct == null ? null : courseProduct.getProductName())
                .coursePriceOptionId(orderItem == null ? null : orderItem.getCoursePriceOptionId())
                .gradeBand(orderItem == null ? null : orderItem.getGradeBand())
                .packageName(coursePackage.getPackageName())
                .subjectName(coursePackage.getSubjectName())
                .teachingMode(coursePackage.getTeachingMode())
                .purchasedHours(resolvePurchasedHours(coursePackage, orderItem))
                .totalUnits(UnitConverter.toHoursDecimal(coursePackage.getTotalUnits()))
                .consumedUnits(UnitConverter.toHoursDecimal(coursePackage.getConsumedUnits()))
                .openingUsedHours(UnitConverter.toHoursDecimal(coursePackage.getOpeningUsedUnits()))
                .remainingUnits(UnitConverter.toHoursDecimal(coursePackage.getRemainingUnits()))
                .originalAmount(coursePackage.getOriginalAmount())
                .discountAmount(coursePackage.getDiscountAmount())
                .receivedAmount(coursePackage.getReceivedAmount())
                .packageStatus(coursePackage.getPackageStatus())
                .startDate(coursePackage.getStartDate())
                .estimatedEndDate(coursePackage.getEstimatedEndDate())
                .remark(coursePackage.getRemark())
                .privateLessonGroupId(privateLessonGroup == null ? null : privateLessonGroup.getId())
                .privateLessonParticipantCount(privateLessonGroup == null ? null : privateLessonGroup.getParticipantCount())
                .privateLessonParticipants(privateLessonParticipants)
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

    private PrivateLessonGroupEntity loadPrivateLessonGroupByPackageId(Long packageId) {
        PrivateLessonGroupMemberEntity member = privateLessonGroupMemberMapper.selectOne(
                new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                        .eq(PrivateLessonGroupMemberEntity::getPackageId, packageId)
                        .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                        .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0)
                        .last("LIMIT 1"));
        if (member == null) {
            return null;
        }
        return privateLessonGroupMapper.selectOne(new LambdaQueryWrapper<PrivateLessonGroupEntity>()
                .eq(PrivateLessonGroupEntity::getId, member.getPrivateLessonGroupId())
                .eq(PrivateLessonGroupEntity::getGroupStatus, "ACTIVE")
                .eq(PrivateLessonGroupEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
    }

    private List<PrivateLessonGroupParticipantVO> loadPrivateLessonParticipants(PrivateLessonGroupEntity group) {
        if (group == null) {
            return Collections.emptyList();
        }
        List<PrivateLessonGroupMemberEntity> members = privateLessonGroupMemberMapper.selectList(
                new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                        .eq(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId, group.getId())
                        .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                        .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0)
                        .orderByAsc(PrivateLessonGroupMemberEntity::getParticipantIndex));
        if (members.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, StudentEntity> students = studentMapper.selectBatchIds(members.stream()
                        .map(PrivateLessonGroupMemberEntity::getStudentId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(StudentEntity::getId, item -> item));
        return members.stream().map(member -> {
            StudentEntity student = students.get(member.getStudentId());
            return new PrivateLessonGroupParticipantVO(
                    member.getStudentId(),
                    student == null ? null : student.getStudentName(),
                    student == null ? null : student.getStudentNo(),
                    student == null ? null : student.getGrade(),
                    student == null ? null : student.getSchool(),
                    member.getPackageId(),
                    member.getParticipantIndex(),
                    member.getPriceMultiplier(),
                    member.getMemberStatus());
        }).toList();
    }

    private double resolvePurchasedHours(CoursePackageEntity coursePackage, EnrollmentOrderItemEntity orderItem) {
        if (orderItem != null && orderItem.getOneOnOneUnits() != null) {
            return UnitConverter.toHoursDecimal(orderItem.getOneOnOneUnits());
        }
        return UnitConverter.toHoursDecimal(coursePackage.getTotalUnits());
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
                        .eq(CourseProductEntity::getStatus, "ACTIVE")
                        .and(w -> w.like(CourseProductEntity::getProductName, keyword)
                                .or()
                                .like(CourseProductEntity::getSubjectName, keyword)
                                .or()
                                .like(CourseProductEntity::getProductCode, keyword)))
                .stream()
                .map(CourseProductEntity::getId)
                .toList();
    }

    /**
     * 读取当前可运营的班课产品 ID，供班课列表做实体关联过滤。
     * class_group 是历史上可能残留的独立实体，不能仅凭 product_id 非空判断其仍可展示。
     */
    private List<Long> listActiveGroupProductIds() {
        return courseProductMapper.selectList(new LambdaQueryWrapper<CourseProductEntity>()
                        .eq(CourseProductEntity::getTeachingMode, "GROUP")
                        .eq(CourseProductEntity::getStatus, "ACTIVE"))
                .stream()
                .map(CourseProductEntity::getId)
                .filter(Objects::nonNull)
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

    private Map<Long, Long> countConfirmedSessionDaysByClass(Map<Long, List<LessonOccurrenceEntity>> occurrenceMap) {
        List<LessonOccurrenceEntity> occurrences = occurrenceMap.values().stream()
                .flatMap(List::stream)
                .filter(occurrence -> occurrence.getPlannedStartAt() != null)
                .toList();
        if (occurrences.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, LessonOccurrenceEntity> occurrenceById = occurrences.stream()
                .collect(Collectors.toMap(LessonOccurrenceEntity::getId, item -> item, (left, right) -> left));
        List<LessonRecordEntity> records = lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                .in(LessonRecordEntity::getOccurrenceId, occurrenceById.keySet())
                .eq(LessonRecordEntity::getConfirmStage, "CONFIRMED")
                .eq(LessonRecordEntity::getIsDeleted, 0));
        // 班课 SESSION 按“上课日”统计：同一天多个连续/分段时段，只消耗 1 个 session。
        return records.stream()
                .map(record -> occurrenceById.get(record.getOccurrenceId()))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        LessonOccurrenceEntity::getClassGroupId,
                        Collectors.mapping(
                                occurrence -> occurrence.getPlannedStartAt().toLocalDate(),
                                Collectors.collectingAndThen(Collectors.toSet(), dates -> (long) dates.size()))));
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
                        .map(record -> occurrenceById.get(record.getOccurrenceId()))
                        .filter(occurrence -> isOccurrenceInEnrollmentWindow(occurrence, enrollment))
                        .map(occurrence -> occurrence.getPlannedStartAt().toLocalDate())
                        .distinct()
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
        return (int) occurrences.stream()
                .map(LessonOccurrenceEntity::getPlannedStartAt)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .distinct()
                .count();
    }

    private int resolveStudentTotalUnits(ClassEnrollmentEntity enrollment, List<LessonOccurrenceEntity> occurrences) {
        if ("PER_SESSION".equals(enrollment.getBillingType()) && enrollment.getPurchasedQuantity() != null) {
            return enrollment.getPurchasedQuantity();
        }
        return (int) occurrences.stream()
                .filter(occurrence -> isOccurrenceInEnrollmentWindow(occurrence, enrollment))
                .map(LessonOccurrenceEntity::getPlannedStartAt)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .distinct()
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

    private CoursePackageListItemVO toListItem(CoursePackageEntity item, StudentEntity student, CourseProductEntity product,
                                               EnrollmentOrderItemEntity orderItem, String primaryTeacherName,
                                               int pendingDeductionUnits) {
        return CoursePackageListItemVO.builder()
                .id(item.getId())
                .packageNo(item.getPackageNo())
                .enrollmentOrderId(item.getEnrollmentOrderId())
                .studentId(item.getStudentId())
                .studentName(student == null ? null : student.getStudentName())
                .courseProductId(item.getCourseProductId())
                .courseProductName(product == null ? null : product.getProductName())
                .gradeBand(orderItem == null ? null : orderItem.getGradeBand())
                .packageName(item.getPackageName())
                .subjectName(item.getSubjectName())
                .teachingMode(item.getTeachingMode())
                .totalUnits(UnitConverter.toHoursDecimal(item.getTotalUnits()))
                .consumedUnits(UnitConverter.toHoursDecimal(item.getConsumedUnits()))
                .openingUsedHours(UnitConverter.toHoursDecimal(item.getOpeningUsedUnits()))
                .pendingDeductionUnits(UnitConverter.toHoursDecimal(pendingDeductionUnits))
                .remainingUnits(UnitConverter.toHoursDecimal(item.getRemainingUnits()))
                .receivedAmount(item.getReceivedAmount())
                .packageStatus(item.getPackageStatus())
                .startDate(item.getStartDate())
                .estimatedEndDate(item.getEstimatedEndDate())
                .primaryTeacherName(primaryTeacherName)
                .createdAt(item.getCreatedAt())
                .build();
    }

    /** 批量统计等待管理员确认、且最终需要扣课的记录，供课包余额拆分展示使用。 */
    private Map<Long, Integer> loadPendingDeductionUnits(List<Long> packageIds) {
        if (packageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                        .select(LessonRecordEntity::getPackageId, LessonRecordEntity::getDeductedUnits)
                        .in(LessonRecordEntity::getPackageId, packageIds)
                        .eq(LessonRecordEntity::getConfirmStage, CONFIRM_STAGE_PENDING_ADMIN)
                        .eq(LessonRecordEntity::getShouldDeduct, 1)
                        .eq(LessonRecordEntity::getIsDeleted, 0))
                .stream()
                .filter(record -> record.getPackageId() != null)
                .collect(Collectors.groupingBy(LessonRecordEntity::getPackageId,
                        Collectors.summingInt(record -> safeUnits(record.getDeductedUnits()))));
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
        coursePackage.setTotalUnits(newTotalUnits);
        coursePackage.setRemainingUnits(newRemainingUnits);
        if (newRemainingUnits == 0) {
            // 课时被调整为 0 → 课时耗尽，即时转 COMPLETED（避免课包停留在 ACTIVE 而不在列表收敛）
            coursePackage.setPackageStatus("COMPLETED");
            coursePackage.setCompletedAt(AppTime.nowLocalDateTime());
        } else if (newRemainingUnits > 0 && "COMPLETED".equals(coursePackage.getPackageStatus())) {
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
        recurringScheduleRuleService.refreshPackageSchedule(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDeductedHours(Long id, CoursePackageDeductedHoursRequest request, Long operatorId) {
        CoursePackageEntity coursePackage = requireOneOnOnePackage(id);
        int requestedConsumedUnits = UnitConverter.toHalfHourUnits(request.getDeductedHours());
        int currentOpeningUsedUnits = safeUnits(coursePackage.getOpeningUsedUnits());
        int totalUnits = safeUnits(coursePackage.getTotalUnits());
        int currentConsumedUnits = safeUnits(coursePackage.getConsumedUnits());
        int trackedLessonConsumedUnits = Math.max(0, currentConsumedUnits - currentOpeningUsedUnits);
        if (requestedConsumedUnits == currentConsumedUnits) {
            return;
        }
        List<LessonRecordEntity> pendingRecords = lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                .eq(LessonRecordEntity::getPackageId, id)
                .eq(LessonRecordEntity::getConfirmStage, CONFIRM_STAGE_PENDING_ADMIN)
                .eq(LessonRecordEntity::getShouldDeduct, 1)
                .eq(LessonRecordEntity::getIsDeleted, 0));
        int pendingDeductionUnits = pendingRecords.stream()
                .mapToInt(record -> safeUnits(record.getDeductedUnits()))
                .sum();
        if (requestedConsumedUnits < trackedLessonConsumedUnits) {
            throw new BusinessException("DEDUCTED_HOURS_BELOW_CONFIRMED",
                    "Already deducted hours cannot be lower than confirmed lesson hours.");
        }
        if (requestedConsumedUnits + pendingDeductionUnits > totalUnits) {
            throw new BusinessException("DEDUCTED_HOURS_EXCEED_PACKAGE",
                    "Already deducted and pending deduction hours cannot exceed the package total.");
        }

        int nextOpeningUsedUnits = requestedConsumedUnits - trackedLessonConsumedUnits;
        int nextConsumedUnits = requestedConsumedUnits;

        int currentRemainingUnits = coursePackage.getRemainingUnits() == null
                ? Math.max(totalUnits - currentConsumedUnits, 0)
                : coursePackage.getRemainingUnits();
        int nextRemainingUnits = totalUnits - nextConsumedUnits;
        int balanceChangeUnits = nextRemainingUnits - currentRemainingUnits;

        coursePackage.setOpeningUsedUnits(nextOpeningUsedUnits);
        coursePackage.setConsumedUnits(nextConsumedUnits);
        coursePackage.setRemainingUnits(nextRemainingUnits);
        if (nextRemainingUnits == 0) {
            coursePackage.setPackageStatus("COMPLETED");
            coursePackage.setCompletedAt(AppTime.nowLocalDateTime());
        } else if ("COMPLETED".equals(coursePackage.getPackageStatus())) {
            coursePackage.setPackageStatus("ACTIVE");
            coursePackage.setCompletedAt(null);
        }
        if (coursePackageMapper.updateById(coursePackage) != 1) {
            throw new BusinessException("PACKAGE_CONCURRENT_UPDATE", "The package was updated by another request. Please retry.");
        }

        HourLedgerEntity ledger = new HourLedgerEntity();
        ledger.setPackageId(id);
        ledger.setLedgerType("MANUAL_ADJUST");
        ledger.setChangeUnits(balanceChangeUnits);
        ledger.setBalanceAfterUnits(nextRemainingUnits);
        ledger.setBusinessDate(AppTime.nowLocalDateTime().toLocalDate());
        ledger.setReasonCode("OPENING_USED_HOURS");
        ledger.setReasonDesc("Updated already deducted hours on the schedule page.");
        ledger.setOperatedBy(operatorId);
        ledger.setOperatedAt(AppTime.nowLocalDateTime());
        hourLedgerMapper.insert(ledger);
        upsertBalanceSnapshot(coursePackage, ledger.getId());

        // 余额变化后重算未来课次；排课服务会保留已确认和人工调整过的课次。
        recurringScheduleRuleService.refreshPackageSchedule(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pausePackage(Long id) {
        CoursePackageEntity coursePackage = requireOneOnOnePackage(id);
        if ("PAUSED".equals(coursePackage.getPackageStatus())) {
            throw new BusinessException("PACKAGE_ALREADY_PAUSED", "Course package is already paused.");
        }
        if (!List.of("ACTIVE", "COMPLETED").contains(coursePackage.getPackageStatus())) {
            throw new BusinessException("PACKAGE_PAUSE_NOT_ALLOWED", "Only active or completed 1-on-1 packages can be paused.");
        }

        coursePackage.setPackageStatus("PAUSED");
        coursePackageMapper.updateById(coursePackage);

        List<RecurringScheduleRuleEntity> rules = listPackageRules(id);
        if (!rules.isEmpty()) {
            recurringScheduleRuleMapper.update(null, new LambdaUpdateWrapper<RecurringScheduleRuleEntity>()
                    .eq(RecurringScheduleRuleEntity::getPackageId, id)
                    .eq(RecurringScheduleRuleEntity::getRuleStatus, "ACTIVE")
                    .set(RecurringScheduleRuleEntity::getRuleStatus, "PAUSED"));
        }
        cancelFutureUnrecordedOccurrences(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resumePackage(Long id, CoursePackageResumeRequest request) {
        CoursePackageEntity coursePackage = requireOneOnOnePackage(id);
        if (!"PAUSED".equals(coursePackage.getPackageStatus())) {
            throw new BusinessException("PACKAGE_NOT_PAUSED", "Only paused 1-on-1 packages can be resumed.");
        }
        if (coursePackage.getRemainingUnits() == null || coursePackage.getRemainingUnits() <= 0) {
            throw new BusinessException("PACKAGE_NO_REMAINING_HOURS", "This package has no remaining hours to schedule.");
        }

        List<RecurringScheduleRuleEntity> rules = listPackageRules(id);
        if (rules.isEmpty()) {
            throw new BusinessException("PACKAGE_SCHEDULE_NOT_FOUND", "No recurring schedule is available for this package.");
        }
        RecurringScheduleRuleEntity rule = rules.stream()
                .filter(item -> "PAUSED".equals(item.getRuleStatus()) || "ACTIVE".equals(item.getRuleStatus()))
                .max(Comparator.comparing(RecurringScheduleRuleEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(rules.get(0));

        List<RecurringScheduleSlotEntity> slots = recurringScheduleSlotMapper.selectList(new LambdaQueryWrapper<RecurringScheduleSlotEntity>()
                .eq(RecurringScheduleSlotEntity::getRuleId, rule.getId())
                .eq(RecurringScheduleSlotEntity::getSlotStatus, "ACTIVE")
                .orderByAsc(RecurringScheduleSlotEntity::getWeekday)
                .orderByAsc(RecurringScheduleSlotEntity::getStartTime));
        if (slots.isEmpty()) {
            throw new BusinessException("PACKAGE_SCHEDULE_SLOT_NOT_FOUND", "No active schedule slot is available for this package.");
        }

        cancelFutureUnrecordedOccurrences(id);

        coursePackage.setStartDate(request.getStartDate());
        coursePackage.setCompletedAt(null);
        coursePackage.setPackageStatus("ACTIVE");

        rule.setRuleStatus("ACTIVE");
        rule.setStartDate(request.getStartDate());
        rule.setEndDate(null);
        recurringScheduleRuleMapper.updateById(rule);

        List<LessonOccurrenceEntity> existingOccurrences = listActivePackageOccurrences(id);
        Set<LocalDateTime> existingStarts = existingOccurrences.stream()
                .map(LessonOccurrenceEntity::getPlannedStartAt)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<LessonOccurrenceEntity> generated = capResumeOccurrencesToPackageBalance(
                buildResumeOccurrences(rule, slots, coursePackage, existingOccurrences, existingStarts),
                coursePackage.getRemainingUnits());
        ensureNoConflicts(generated, coursePackage.getStudentId(), null, rule.getDefaultClassroomId(), rule.getId());
        generated.forEach(lessonOccurrenceMapper::insert);

        generated.stream()
                .map(LessonOccurrenceEntity::getPlannedEndAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .ifPresent(endAt -> coursePackage.setEstimatedEndDate(endAt.toLocalDate()));
        coursePackageMapper.updateById(coursePackage);
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
                "ACTIVE", Set.of("COMPLETED", "PAUSED", "CLOSED"),
                "PAUSED", Set.of("ACTIVE", "CLOSED"),
                "COMPLETED", Set.of("ACTIVE", "PAUSED", "CLOSED"),
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

    private CoursePackageEntity requireOneOnOnePackage(Long id) {
        CoursePackageEntity coursePackage = requireCoursePackage(id);
        if (!"ONE_ON_ONE".equals(coursePackage.getTeachingMode())) {
            throw new BusinessException("PACKAGE_PAUSE_NOT_SUPPORTED", "Pause and resume are only available for 1-on-1 packages.");
        }
        return coursePackage;
    }

    private int safeUnits(Integer units) {
        return units == null ? 0 : Math.max(0, units);
    }

    private List<RecurringScheduleRuleEntity> listPackageRules(Long packageId) {
        return recurringScheduleRuleMapper.selectList(new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                .eq(RecurringScheduleRuleEntity::getPackageId, packageId)
                .orderByDesc(RecurringScheduleRuleEntity::getCreatedAt));
    }

    private void cancelFutureUnrecordedOccurrences(Long packageId) {
        List<LessonOccurrenceEntity> futureOccurrences = lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getPackageId, packageId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .ge(LessonOccurrenceEntity::getPlannedStartAt, AppTime.nowLocalDateTime()));
        if (futureOccurrences.isEmpty()) {
            return;
        }
        Set<Long> occurrenceIdsWithRecords = lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                        .in(LessonRecordEntity::getOccurrenceId, futureOccurrences.stream().map(LessonOccurrenceEntity::getId).toList())
                        .ne(LessonRecordEntity::getConfirmStage, "REJECTED")
                        .eq(LessonRecordEntity::getIsDeleted, 0))
                .stream()
                .map(LessonRecordEntity::getOccurrenceId)
                .collect(Collectors.toSet());
        List<Long> cancellableIds = futureOccurrences.stream()
                .filter(item -> !occurrenceIdsWithRecords.contains(item.getId()))
                .map(LessonOccurrenceEntity::getId)
                .toList();
        if (cancellableIds.isEmpty()) {
            return;
        }
        lessonOccurrenceMapper.update(null, new LambdaUpdateWrapper<LessonOccurrenceEntity>()
                .in(LessonOccurrenceEntity::getId, cancellableIds)
                .set(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .set(LessonOccurrenceEntity::getNotes, "Cancelled when package was paused."));
    }

    private List<LessonOccurrenceEntity> listActivePackageOccurrences(Long packageId) {
        return lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getPackageId, packageId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED"));
    }

    /** 恢复课包时按每个时段尚未排完的额度继续生成，不再截断在固定天数内。 */
    private List<LessonOccurrenceEntity> buildResumeOccurrences(RecurringScheduleRuleEntity rule,
                                                                List<RecurringScheduleSlotEntity> slots,
                                                                CoursePackageEntity coursePackage,
                                                                List<LessonOccurrenceEntity> existingOccurrences,
                                                                Set<LocalDateTime> existingStarts) {
        LocalDate generationStart = Collections.max(List.of(rule.getStartDate(), AppTime.nowLocalDateTime().toLocalDate()));
        LocalDate generationEnd = rule.getEndDate();
        Map<Long, Integer> scheduledUnitsBySlot = existingOccurrences.stream()
                .filter(item -> item.getScheduleSlotId() != null)
                .collect(Collectors.groupingBy(
                        LessonOccurrenceEntity::getScheduleSlotId,
                        Collectors.summingInt(item -> item.getPlannedUnits() == null ? 0 : item.getPlannedUnits())));
        List<LessonOccurrenceEntity> occurrences = new ArrayList<>();
        for (RecurringScheduleSlotEntity slot : slots) {
            requireCompleteSlotAllocation(slot);
            int allocationRemaining = slot.getAllocatedUnits()
                    - scheduledUnitsBySlot.getOrDefault(slot.getId(), 0);
            if (allocationRemaining <= 0) {
                continue;
            }
            for (LocalDate date = generationStart;
                 allocationRemaining > 0 && (generationEnd == null || !date.isAfter(generationEnd));
                 date = date.plusDays(1)) {
                if (!Objects.equals(slot.getWeekday(), toWeekday(date.getDayOfWeek()))) {
                    continue;
                }
                LocalDateTime plannedStart = LocalDateTime.of(date, slot.getStartTime());
                if (existingStarts.contains(plannedStart)) {
                    continue;
                }
                int actualUnits = Math.min(slot.getPlannedUnits(), allocationRemaining);
                LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
                occurrence.setLessonNo(BusinessNoGenerator.generateLessonOccurrenceNo());
                occurrence.setPackageId(coursePackage.getId());
                occurrence.setStudentId(coursePackage.getStudentId());
                occurrence.setScheduleRuleId(rule.getId());
                occurrence.setScheduleSlotId(slot.getId());
                occurrence.setOccurrenceType("REGULAR");
                occurrence.setPlannedTeacherId(slot.getTeacherId());
                occurrence.setPlannedClassroomId(rule.getDefaultClassroomId());
                occurrence.setPlannedStartAt(plannedStart);
                occurrence.setPlannedUnits(actualUnits);
                occurrence.setPlannedEndAt(plannedStart.plusMinutes(actualUnits * 30L));
                occurrence.setPlanningStatus("SCHEDULED");
                occurrence.setConflictChecked(1);
                occurrences.add(occurrence);
                allocationRemaining -= actualUnits;
            }
        }
        occurrences.sort(Comparator.comparing(LessonOccurrenceEntity::getPlannedStartAt));
        return occurrences;
    }

    private void requireCompleteSlotAllocation(RecurringScheduleSlotEntity slot) {
        if (slot.getTeacherId() == null || !StringUtils.hasText(slot.getSubjectName())
                || slot.getAllocatedUnits() == null || slot.getAllocatedUnits() <= 0) {
            throw new BusinessException("INCOMPLETE_SLOT_ALLOCATION",
                    "Each schedule slot requires a teacher, course/subject, and allocated hours. Update this schedule before resuming the package.");
        }
    }

    private List<LessonOccurrenceEntity> capResumeOccurrencesToPackageBalance(
            List<LessonOccurrenceEntity> candidates, int remainingPackageUnits) {
        List<LessonOccurrenceEntity> result = new ArrayList<>();
        int remaining = remainingPackageUnits;
        for (LessonOccurrenceEntity candidate : candidates) {
            if (remaining <= 0) {
                break;
            }
            int plannedUnits = candidate.getPlannedUnits();
            if (plannedUnits <= remaining) {
                result.add(candidate);
                remaining -= plannedUnits;
                continue;
            }
            candidate.setPlannedUnits(remaining);
            candidate.setPlannedEndAt(candidate.getPlannedStartAt().plusMinutes(remaining * 30L));
            result.add(candidate);
            break;
        }
        return result;
    }

    private void ensureNoConflicts(List<LessonOccurrenceEntity> candidates,
                                   Long studentId,
                                   Long teacherId,
                                   Long classroomId,
                                   Long excludeRuleId) {
        if (candidates.isEmpty()) {
            return;
        }
        LocalDateTime windowStart = candidates.stream().map(LessonOccurrenceEntity::getPlannedStartAt).min(LocalDateTime::compareTo).orElseThrow();
        LocalDateTime windowEnd = candidates.stream().map(LessonOccurrenceEntity::getPlannedEndAt).max(LocalDateTime::compareTo).orElseThrow();
        List<LessonOccurrenceEntity> studentOccurrences = studentId == null ? Collections.emptyList() : lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getStudentId, studentId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .ne(excludeRuleId != null, LessonOccurrenceEntity::getScheduleRuleId, excludeRuleId)
                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));
        Set<Long> teacherIds = candidates.stream()
                .map(LessonOccurrenceEntity::getPlannedTeacherId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<LessonOccurrenceEntity> teacherOccurrences = teacherIds.isEmpty() ? Collections.emptyList() : lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .in(LessonOccurrenceEntity::getPlannedTeacherId, teacherIds)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .ne(excludeRuleId != null, LessonOccurrenceEntity::getScheduleRuleId, excludeRuleId)
                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));
        List<LessonOccurrenceEntity> classroomOccurrences = classroomId == null ? Collections.emptyList() : lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getPlannedClassroomId, classroomId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .ne(excludeRuleId != null, LessonOccurrenceEntity::getScheduleRuleId, excludeRuleId)
                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));

        List<String> conflicts = new ArrayList<>();
        for (LessonOccurrenceEntity candidate : candidates) {
            if (studentId != null && hasOverlap(candidate, studentOccurrences)) {
                conflicts.add("Student conflict at " + candidate.getPlannedStartAt());
            }
            Long candidateTeacherId = candidate.getPlannedTeacherId();
            if (candidateTeacherId != null && hasOverlap(candidate, teacherOccurrences.stream()
                    .filter(existing -> Objects.equals(existing.getPlannedTeacherId(), candidateTeacherId))
                    .toList())) {
                conflicts.add("Teacher conflict at " + candidate.getPlannedStartAt());
            }
            if (classroomId != null && hasOverlap(candidate, classroomOccurrences)) {
                conflicts.add("Classroom conflict at " + candidate.getPlannedStartAt());
            }
            if (conflicts.size() >= 3) {
                break;
            }
        }
        if (!conflicts.isEmpty()) {
            throw new BusinessException("SCHEDULE_CONFLICT", String.join("; ", conflicts));
        }
    }

    private boolean hasOverlap(LessonOccurrenceEntity candidate, List<LessonOccurrenceEntity> existingOccurrences) {
        return existingOccurrences.stream().anyMatch(existing ->
                candidate.getPlannedStartAt().isBefore(existing.getPlannedEndAt())
                        && candidate.getPlannedEndAt().isAfter(existing.getPlannedStartAt()));
    }

    private int toWeekday(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> 1;
            case TUESDAY -> 2;
            case WEDNESDAY -> 3;
            case THURSDAY -> 4;
            case FRIDAY -> 5;
            case SATURDAY -> 6;
            case SUNDAY -> 7;
        };
    }

}
