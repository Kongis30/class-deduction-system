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
import com.classdeduction.system.modules.enrollment.dto.CoursePackageUpdateRequest;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupEntity;
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupMemberEntity;
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
import com.classdeduction.system.modules.schedule.util.PrivateLessonAllocation;
import com.classdeduction.system.modules.schedule.service.RecurringScheduleRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
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
        if (query.getRenewalDueWithinDays() != null) {
            int dueWithinDays = Math.max(0, Math.min(query.getRenewalDueWithinDays(), 3650));
            lambda.eq(CoursePackageEntity::getPackageStatus, "ACTIVE")
                    .le(CoursePackageEntity::getEstimatedEndDate, AppTime.nowLocalDateTime().toLocalDate().plusDays(dueWithinDays));
        }
        if (StringUtils.hasText(query.getPackageStatuses())) {
            List<String> statuses = Arrays.stream(query.getPackageStatuses().split(","))
                    .map(String::trim).filter(StringUtils::hasText).distinct().toList();
            if (!statuses.isEmpty()) lambda.in(CoursePackageEntity::getPackageStatus, statuses);
        }

        if (Boolean.TRUE.equals(query.getPrioritizeLowBalance())) {
            // remaining_units 使用半小时为单位，10 小时等于 20 units；排序放在分页查询中保障低余额包位于列表前页。
            wrapper.last("ORDER BY CASE WHEN COALESCE(remaining_units, 0) <= 20 THEN 0 ELSE 1 END, "
                    + "CASE WHEN COALESCE(remaining_units, 0) <= 20 THEN COALESCE(remaining_units, 0) END ASC, created_at DESC");
        } else {
            // Apply user-specified sort before default createdAt sort
            if (StringUtils.hasText(query.getSortBy())) {
                boolean isAsc = !"desc".equals(query.getSortOrder());
                if ("subjectName".equals(query.getSortBy())) {
                    lambda.orderBy(true, isAsc, CoursePackageEntity::getSubjectName);
                }
            }
            lambda.orderByDesc(CoursePackageEntity::getCreatedAt);
        }

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
        Map<Long, PackageScheduleSummary> scheduleSummaryMap = Boolean.TRUE.equals(query.getIncludeScheduleSummary())
                ? loadPackageScheduleSummaries(result.getRecords())
                : Collections.emptyMap();

        Map<Long, Integer> pendingAdminDeductionByPackage = loadPendingAdminDeductionUnits(
                result.getRecords().stream().map(CoursePackageEntity::getId).toList());
        Map<Long, Integer> unresolvedPastByPackage = loadUnresolvedPastUnits(
                result.getRecords().stream().map(CoursePackageEntity::getId).toList());
        Map<Long, Integer> pendingProcessingByPackage = loadPendingProcessingUnits(
                result.getRecords().stream().map(CoursePackageEntity::getId).toList());
        List<CoursePackageListItemVO> records = result.getRecords().stream()
                .map(item -> toListItem(item, studentMap.get(item.getStudentId()), productMap.get(item.getCourseProductId()),
                        orderItemMap.get(item.getEnrollmentOrderItemId()), primaryTeacherNameMap.get(item.getId()),
                        pendingProcessingByPackage.getOrDefault(item.getId(), 0),
                        pendingAdminDeductionByPackage.getOrDefault(item.getId(), 0),
                        unresolvedPastByPackage.getOrDefault(item.getId(), 0), scheduleSummaryMap.get(item.getId())))
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
                                               int pendingProcessingUnits, int pendingAdminDeductionUnits,
                                               int unresolvedPastUnits,
                                               PackageScheduleSummary scheduleSummary) {
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
                .startingHours(UnitConverter.toHoursDecimal(Math.max(0,
                        safeUnits(item.getTotalUnits()) - safeUnits(item.getOpeningUsedUnits()))))
                .consumedUnits(UnitConverter.toHoursDecimal(item.getConsumedUnits()))
                .openingUsedHours(UnitConverter.toHoursDecimal(item.getOpeningUsedUnits()))
                .pendingDeductionUnits(UnitConverter.toHoursDecimal(pendingProcessingUnits))
                .pendingAdminDeductionUnits(UnitConverter.toHoursDecimal(pendingAdminDeductionUnits))
                .remainingUnits(UnitConverter.toHoursDecimal(item.getRemainingUnits()))
                .unresolvedPastHours(UnitConverter.toHoursDecimal(unresolvedPastUnits))
                .receivedAmount(item.getReceivedAmount())
                .packageStatus(item.getPackageStatus())
                .scheduleStatus(scheduleSummary == null ? "Needs setup" : scheduleSummary.status())
                .nextLessonStartAt(scheduleSummary == null ? null : scheduleSummary.nextLessonStartAt())
                .startDate(item.getStartDate())
                .estimatedEndDate(item.getEstimatedEndDate())
                .primaryTeacherName(primaryTeacherName)
                .createdAt(item.getCreatedAt())
                .build();
    }

    /**
     * 批量计算当前页 1-on-1 课包的排课状态与下一节课，避免列表逐行请求规则和详情。
     * 页面通过 pagePackages 调用此方法，结果直接随课包列表返回。
     */
    private Map<Long, PackageScheduleSummary> loadPackageScheduleSummaries(List<CoursePackageEntity> packages) {
        List<CoursePackageEntity> oneOnOnePackages = packages.stream()
                .filter(item -> "ONE_ON_ONE".equals(item.getTeachingMode()))
                .toList();
        if (oneOnOnePackages.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> packageIds = oneOnOnePackages.stream().map(CoursePackageEntity::getId).toList();
        List<RecurringScheduleRuleEntity> rules = recurringScheduleRuleMapper.selectList(
                new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                        .in(RecurringScheduleRuleEntity::getPackageId, packageIds)
                        .orderByDesc(RecurringScheduleRuleEntity::getCreatedAt));
        if (rules.isEmpty()) {
            return oneOnOnePackages.stream().collect(Collectors.toMap(
                    CoursePackageEntity::getId,
                    item -> new PackageScheduleSummary("Needs setup", null)));
        }

        List<Long> ruleIds = rules.stream().map(RecurringScheduleRuleEntity::getId).toList();
        Map<Long, List<RecurringScheduleSlotEntity>> slotsByRuleId = recurringScheduleSlotMapper.selectList(
                        new LambdaQueryWrapper<RecurringScheduleSlotEntity>()
                                .in(RecurringScheduleSlotEntity::getRuleId, ruleIds))
                .stream()
                .collect(Collectors.groupingBy(RecurringScheduleSlotEntity::getRuleId));

        LocalDateTime now = AppTime.nowLocalDateTime();
        Map<Long, LocalDateTime> nextLessonByRuleId = lessonOccurrenceMapper.selectList(
                        new LambdaQueryWrapper<LessonOccurrenceEntity>()
                                .select(LessonOccurrenceEntity::getScheduleRuleId, LessonOccurrenceEntity::getPlannedStartAt,
                                        LessonOccurrenceEntity::getPlanningStatus)
                                .in(LessonOccurrenceEntity::getScheduleRuleId, ruleIds)
                                .ge(LessonOccurrenceEntity::getPlannedStartAt, now)
                                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                                .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt))
                .stream()
                .filter(item -> item.getScheduleRuleId() != null && item.getPlannedStartAt() != null)
                .collect(Collectors.toMap(LessonOccurrenceEntity::getScheduleRuleId,
                        LessonOccurrenceEntity::getPlannedStartAt, (first, ignored) -> first));

        Map<Long, List<RecurringScheduleRuleEntity>> rulesByPackageId = rules.stream()
                .filter(rule -> rule.getPackageId() != null)
                .collect(Collectors.groupingBy(RecurringScheduleRuleEntity::getPackageId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, PackageScheduleSummary> result = new LinkedHashMap<>();
        for (CoursePackageEntity coursePackage : oneOnOnePackages) {
            List<RecurringScheduleRuleEntity> packageRules = rulesByPackageId.getOrDefault(coursePackage.getId(), Collections.emptyList());
            // Keep the list's existing rule choice: the schedule page selected the newest rule first.
            RecurringScheduleRuleEntity rule = packageRules.isEmpty() ? null : packageRules.get(0);
            if (rule == null) {
                result.put(coursePackage.getId(), new PackageScheduleSummary("Needs setup", null));
                continue;
            }

            boolean paused = "PAUSED".equals(coursePackage.getPackageStatus()) || "PAUSED".equals(rule.getRuleStatus());
            boolean incomplete = slotsByRuleId.getOrDefault(rule.getId(), Collections.emptyList()).stream()
                    .anyMatch(slot -> slot.getTeacherId() == null
                            || !StringUtils.hasText(slot.getSubjectName())
                            || slot.getAllocatedUnits() == null
                            || slot.getAllocatedUnits() <= 0);
            String status = paused ? "Paused" : incomplete ? "Incomplete" : "Scheduled";
            result.put(coursePackage.getId(), new PackageScheduleSummary(status, nextLessonByRuleId.get(rule.getId())));
        }
        return result;
    }

    private record PackageScheduleSummary(String status, LocalDateTime nextLessonStartAt) {
    }

    /** 批量统计等待管理员确认、且最终需要扣课的记录，供课包余额拆分展示使用。 */
    private Map<Long, Integer> loadPendingAdminDeductionUnits(List<Long> packageIds) {
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

    /** 排课页展示已结束但未最终处理的课次；与待管理员扣课预留额分开，避免改变排课校验。 */
    private Map<Long, Integer> loadPendingProcessingUnits(List<Long> packageIds) {
        if (packageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PrivateLessonGroupMemberEntity> members = privateLessonGroupMemberMapper.selectList(
                new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                        .in(PrivateLessonGroupMemberEntity::getPackageId, packageIds)
                        .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                        .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0));
        Map<Long, List<Long>> packageIdsByGroup = members.stream()
                .filter(member -> member.getPrivateLessonGroupId() != null)
                .collect(Collectors.groupingBy(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId,
                        Collectors.mapping(PrivateLessonGroupMemberEntity::getPackageId, Collectors.toList())));
        Set<Long> groupIds = packageIdsByGroup.keySet();
        LocalDateTime now = AppTime.nowLocalDateTime();
        List<LessonOccurrenceEntity> occurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .and(scope -> scope.in(LessonOccurrenceEntity::getPackageId, packageIds)
                                .or(!groupIds.isEmpty(), group -> group.in(
                                        LessonOccurrenceEntity::getPrivateLessonGroupId, groupIds)))
                        .le(LessonOccurrenceEntity::getPlannedEndAt, now));
        if (occurrences.isEmpty()) {
            return Collections.emptyMap();
        }
        List<LessonRecordEntity> records = lessonRecordMapper.selectList(
                new LambdaQueryWrapper<LessonRecordEntity>()
                        .in(LessonRecordEntity::getOccurrenceId, occurrences.stream()
                                .map(LessonOccurrenceEntity::getId).toList())
                        .eq(LessonRecordEntity::getIsDeleted, 0));
        records.sort(Comparator.comparing(LessonRecordEntity::getUpdatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(LessonRecordEntity::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed());
        Map<Long, Map<Long, LessonRecordEntity>> recordsByPackage = new LinkedHashMap<>();
        Map<Long, LessonRecordEntity> legacyRecords = new LinkedHashMap<>();
        for (LessonRecordEntity record : records) {
            if (record.getPackageId() == null) {
                legacyRecords.putIfAbsent(record.getOccurrenceId(), record);
            } else {
                recordsByPackage.computeIfAbsent(record.getPackageId(), key -> new LinkedHashMap<>())
                        .putIfAbsent(record.getOccurrenceId(), record);
            }
        }
        Map<Long, Integer> result = new LinkedHashMap<>();
        Map<Long, Set<Long>> seenByPackage = new LinkedHashMap<>();
        for (LessonOccurrenceEntity occurrence : occurrences) {
            List<Long> ownerPackageIds = occurrence.getPrivateLessonGroupId() == null
                    ? Collections.singletonList(occurrence.getPackageId())
                    : packageIdsByGroup.getOrDefault(occurrence.getPrivateLessonGroupId(), Collections.emptyList());
            for (Long packageId : ownerPackageIds) {
                if (packageId == null || !seenByPackage.computeIfAbsent(packageId, key -> new HashSet<>())
                        .add(occurrence.getId())) {
                    continue;
                }
                LessonRecordEntity record = recordsByPackage.getOrDefault(packageId, Collections.emptyMap())
                        .get(occurrence.getId());
                if (record == null && occurrence.getPrivateLessonGroupId() == null) {
                    record = legacyRecords.get(occurrence.getId());
                }
                if (PrivateLessonAllocation.isPendingProcessing(occurrence, record, now)) {
                    result.merge(packageId, Math.max(0, safeUnits(occurrence.getPlannedUnits())), Integer::sum);
                }
            }
        }
        return result;
    }

    /** 课包列表额外返回过去未处理课次的预留额；不改动账本 remaining_units。 */
    private Map<Long, Integer> loadUnresolvedPastUnits(List<Long> packageIds) {
        if (packageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LocalDateTime now = AppTime.nowLocalDateTime();
        List<LessonOccurrenceEntity> occurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .in(LessonOccurrenceEntity::getPackageId, packageIds)
                        .lt(LessonOccurrenceEntity::getPlannedStartAt, now)
                        .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED"));
        if (occurrences.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, LessonRecordEntity> records = new LinkedHashMap<>();
        for (LessonRecordEntity record : lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                .in(LessonRecordEntity::getOccurrenceId, occurrences.stream()
                        .map(LessonOccurrenceEntity::getId).toList())
                .eq(LessonRecordEntity::getIsDeleted, 0)
                .orderByDesc(LessonRecordEntity::getUpdatedAt)
                .orderByDesc(LessonRecordEntity::getId))) {
            records.putIfAbsent(record.getOccurrenceId(), record);
        }
        Map<Long, Integer> reserved = new LinkedHashMap<>();
        for (Map.Entry<Long, List<LessonOccurrenceEntity>> entry : occurrences.stream()
                .collect(Collectors.groupingBy(LessonOccurrenceEntity::getPackageId)).entrySet()) {
            reserved.put(entry.getKey(), PrivateLessonAllocation.unresolvedPastUnits(
                    entry.getValue(), records, now));
        }
        return reserved;
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
        if ("BONUS_HOURS".equals(request.getAdjustmentType())) {
            coursePackage.setBonusUnits(safeUnits(coursePackage.getBonusUnits()) + changeUnits);
        }
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

        // 此入口编辑的是周期起始值；明确保存输入值，再重算未来课次。
        recurringScheduleRuleService.setPackageStartingHours(id, totalUnits - nextOpeningUsedUnits);
        // 重算只更新未来课次，不得覆盖 Starting Hours 快照。
        recurringScheduleRuleService.refreshPackageSchedule(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pausePackage(Long id) {
        List<CoursePackageEntity> packages = loadPrivateLessonPackages(id);
        if (packages.stream().allMatch(item -> "PAUSED".equals(item.getPackageStatus()))) {
            throw new BusinessException("PACKAGE_ALREADY_PAUSED", "Course package is already paused.");
        }
        if (packages.stream().anyMatch(item -> !List.of("ACTIVE", "COMPLETED", "PAUSED").contains(item.getPackageStatus()))) {
            throw new BusinessException("PACKAGE_PAUSE_NOT_ALLOWED", "Only active or completed 1-on-1 packages can be paused.");
        }

        // 共享课程的成员课包必须同进同退；课次可能挂在主课包，也可能来自历史成员课包。
        for (CoursePackageEntity coursePackage : packages) {
            if (!"PAUSED".equals(coursePackage.getPackageStatus())) {
                coursePackage.setPackageStatus("PAUSED");
                coursePackageMapper.updateById(coursePackage);
            }
            recurringScheduleRuleMapper.update(null, new LambdaUpdateWrapper<RecurringScheduleRuleEntity>()
                    .eq(RecurringScheduleRuleEntity::getPackageId, coursePackage.getId())
                    .eq(RecurringScheduleRuleEntity::getRuleStatus, "ACTIVE")
                    .set(RecurringScheduleRuleEntity::getRuleStatus, "PAUSED"));
            cancelFutureOccurrencesWhenPaused(coursePackage.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resumePackage(Long id) {
        List<CoursePackageEntity> packages = loadPrivateLessonPackages(id);
        if (packages.stream().noneMatch(item -> "PAUSED".equals(item.getPackageStatus()))) {
            throw new BusinessException("PACKAGE_NOT_PAUSED", "Only paused 1-on-1 packages can be resumed.");
        }
        if (packages.stream().anyMatch(item -> !List.of("PAUSED", "ACTIVE").contains(item.getPackageStatus()))) {
            throw new BusinessException("PACKAGE_RESUME_NOT_ALLOWED", "Only paused or active 1-on-1 packages can be resumed.");
        }
        if (packages.stream().anyMatch(item -> item.getRemainingUnits() == null || item.getRemainingUnits() <= 0)) {
            throw new BusinessException("PACKAGE_NO_REMAINING_HOURS", "This package has no remaining hours to schedule.");
        }

        // 恢复只重新开放成员课包；旧规则继续暂停，避免自动按过期时段生成课次。
        for (CoursePackageEntity coursePackage : packages) {
            coursePackage.setCompletedAt(null);
            coursePackage.setEstimatedEndDate(null);
            coursePackage.setPackageStatus("ACTIVE");
            coursePackageMapper.updateById(coursePackage);
        }
    }

    /** 找出同一共享课程的所有有效成员课包；独立课程仅返回其自身。 */
    private List<CoursePackageEntity> loadPrivateLessonPackages(Long packageId) {
        CoursePackageEntity selected = requireOneOnOnePackage(packageId);
        PrivateLessonGroupMemberEntity membership = privateLessonGroupMemberMapper.selectOne(
                new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                        .eq(PrivateLessonGroupMemberEntity::getPackageId, packageId)
                        .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                        .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0)
                        .last("LIMIT 1"));
        if (membership == null) return List.of(selected);
        List<PrivateLessonGroupMemberEntity> members = privateLessonGroupMemberMapper.selectList(
                new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                        .eq(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId, membership.getPrivateLessonGroupId())
                        .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                        .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0)
                        .orderByAsc(PrivateLessonGroupMemberEntity::getParticipantIndex));
        if (members.isEmpty()) {
            throw new BusinessException("PRIVATE_GROUP_EMPTY", "This private lesson group has no active members.");
        }
        List<CoursePackageEntity> packages = new ArrayList<>();
        for (PrivateLessonGroupMemberEntity member : members) {
            packages.add(member.getPackageId().equals(packageId)
                    ? selected : requireOneOnOnePackage(member.getPackageId()));
        }
        return packages;
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

    /** 停课释放所有未来课次的老师和教室占用；待审核消课同时退回，已确认记录保留供追溯。 */
    private void cancelFutureOccurrencesWhenPaused(Long packageId) {
        List<LessonOccurrenceEntity> futureOccurrences = lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getPackageId, packageId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .ge(LessonOccurrenceEntity::getPlannedStartAt, AppTime.nowLocalDateTime()));
        if (futureOccurrences.isEmpty()) {
            return;
        }
        List<Long> occurrenceIds = futureOccurrences.stream().map(LessonOccurrenceEntity::getId).toList();
        lessonRecordMapper.update(null, new LambdaUpdateWrapper<LessonRecordEntity>()
                .in(LessonRecordEntity::getOccurrenceId, occurrenceIds)
                .eq(LessonRecordEntity::getConfirmStage, CONFIRM_STAGE_PENDING_ADMIN)
                .eq(LessonRecordEntity::getIsDeleted, 0)
                .set(LessonRecordEntity::getConfirmStage, "REJECTED")
                .set(LessonRecordEntity::getRejectReason, "Package paused before the planned lesson.")
                .set(LessonRecordEntity::getRejectedAt, AppTime.nowLocalDateTime()));
        lessonOccurrenceMapper.update(null, new LambdaUpdateWrapper<LessonOccurrenceEntity>()
                .in(LessonOccurrenceEntity::getId, occurrenceIds)
                .set(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .set(LessonOccurrenceEntity::getNotes, "Cancelled when package was paused."));
    }

}
