package com.classdeduction.system.modules.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.base.dto.CoursePriceOptionPageQuery;
import com.classdeduction.system.modules.base.dto.CoursePriceOptionSaveRequest;
import com.classdeduction.system.modules.base.entity.CoursePriceOptionEntity;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.mapper.CoursePriceOptionMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.service.CoursePriceOptionService;
import com.classdeduction.system.modules.base.vo.CoursePriceOptionVO;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Query service for registration course selector price options.
 */
@Service
@RequiredArgsConstructor
public class CoursePriceOptionServiceImpl implements CoursePriceOptionService {

    private static final String DEFAULT_CURRENCY = "USD";
    private static final String DEFAULT_STATUS = "ACTIVE";
    private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "INACTIVE");

    private final CoursePriceOptionMapper coursePriceOptionMapper;
    private final CourseProductMapper courseProductMapper;
    private final EnrollmentOrderItemMapper enrollmentOrderItemMapper;
    private final ClassGroupMapper classGroupMapper;
    private final ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final LessonRecordMapper lessonRecordMapper;

    @Override
    public List<CoursePriceOptionVO> listActiveOptions(String courseType) {
        return coursePriceOptionMapper.selectList(baseWrapper()
                        .eq(CoursePriceOptionEntity::getStatus, "ACTIVE")
                        .eq(StringUtils.hasText(courseType), CoursePriceOptionEntity::getCourseType, courseType)
                        .orderByAsc(CoursePriceOptionEntity::getCourseType)
                        .orderByAsc(CoursePriceOptionEntity::getSortOrder)
                        .orderByAsc(CoursePriceOptionEntity::getId))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public PageResponse<CoursePriceOptionVO> pageOptions(CoursePriceOptionPageQuery query) {
        Page<CoursePriceOptionEntity> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<CoursePriceOptionEntity> wrapper = baseWrapper()
                .eq(StringUtils.hasText(query.getCourseType()), CoursePriceOptionEntity::getCourseType, query.getCourseType())
                .eq(StringUtils.hasText(query.getStatus()), CoursePriceOptionEntity::getStatus, query.getStatus())
                .eq(query.getCourseProductId() != null, CoursePriceOptionEntity::getCourseProductId, query.getCourseProductId())
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(CoursePriceOptionEntity::getRegisterClass, query.getKeyword())
                        .or()
                        .like(CoursePriceOptionEntity::getDetailText, query.getKeyword())
                        .or()
                        .like(CoursePriceOptionEntity::getGradeBand, query.getKeyword())
                        .or()
                        .like(CoursePriceOptionEntity::getPeriodLabel, query.getKeyword())
                        .or()
                        .like(CoursePriceOptionEntity::getTimeSlotLabel, query.getKeyword()))
                .orderByAsc(CoursePriceOptionEntity::getCourseType)
                .orderByAsc(CoursePriceOptionEntity::getSortOrder)
                .orderByAsc(CoursePriceOptionEntity::getId);
        IPage<CoursePriceOptionEntity> result = coursePriceOptionMapper.selectPage(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }
        return PageResponse.of(result.getRecords().stream().map(this::toVO).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOption(CoursePriceOptionSaveRequest request) {
        validateRequest(request);
        CoursePriceOptionEntity entity = new CoursePriceOptionEntity();
        applySaveRequest(entity, request);
        if (!"ONE_ON_ONE".equals(entity.getCourseType()) && "ACTIVE".equals(entity.getStatus())) {
            ensureGroupClass(entity, request);
        }
        if (!StringUtils.hasText(entity.getOptionCode())) {
            entity.setOptionCode(generateOptionCode(request));
        }
        ensureOptionCodeUnique(null, entity.getOptionCode());
        coursePriceOptionMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOption(Long id, CoursePriceOptionSaveRequest request) {
        validateRequest(request);
        CoursePriceOptionEntity entity = requireEntity(id);
        protectReferencedOptionCriticalFields(entity, request);
        String existingOptionCode = entity.getOptionCode();
        applySaveRequest(entity, request);
        if (!"ONE_ON_ONE".equals(entity.getCourseType()) && "ACTIVE".equals(entity.getStatus())) {
            ensureGroupClass(entity, request);
        }
        if (!StringUtils.hasText(entity.getOptionCode())) {
            entity.setOptionCode(existingOptionCode);
        }
        ensureOptionCodeUnique(id, entity.getOptionCode());
        coursePriceOptionMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOption(Long id) {
        CoursePriceOptionEntity entity = requireEntity(id);
        if (isOptionReferenced(id)) {
            throw new BusinessException("COURSE_PRICE_OPTION_REFERENCED",
                    "Course price option has been used by enrollment orders. Set it to INACTIVE instead.");
        }
        coursePriceOptionMapper.deleteById(entity.getId());
    }

    private LambdaQueryWrapper<CoursePriceOptionEntity> baseWrapper() {
        return new LambdaQueryWrapper<CoursePriceOptionEntity>()
                .eq(CoursePriceOptionEntity::getIsDeleted, 0);
    }

    private CoursePriceOptionEntity requireEntity(Long id) {
        CoursePriceOptionEntity entity = coursePriceOptionMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("COURSE_PRICE_OPTION_NOT_FOUND", "Course price option not found.");
        }
        return entity;
    }

    private void validateRequest(CoursePriceOptionSaveRequest request) {
        String status = StringUtils.hasText(request.getStatus()) ? request.getStatus() : DEFAULT_STATUS;
        if (!VALID_STATUSES.contains(status)) {
            throw new BusinessException("INVALID_COURSE_PRICE_OPTION_STATUS",
                    "Course price option status must be ACTIVE or INACTIVE.");
        }
        if (request.getCourseProductId() == null) {
            throw new BusinessException("COURSE_PRODUCT_REQUIRED",
                    "Course price option must link to a course product.");
        }
        CourseProductEntity product = courseProductMapper.selectById(request.getCourseProductId());
        if (product == null || ("ACTIVE".equals(status) && !"ACTIVE".equals(product.getStatus()))) {
            throw new BusinessException("COURSE_PRODUCT_INVALID",
                    "Active course price option must link to an active course product.");
        }
        validateProductCompatibility(request, product);
        if ("ACTIVE".equals(status)) {
            validateMatrixRow(request, product);
        }
    }

    private void validateProductCompatibility(CoursePriceOptionSaveRequest request, CourseProductEntity product) {
        String expectedMode = "ONE_ON_ONE".equals(request.getCourseType()) ? "ONE_ON_ONE" : "GROUP";
        if (!expectedMode.equals(product.getTeachingMode())) {
            throw new BusinessException("COURSE_PRODUCT_MODE_MISMATCH",
                    "Course price option teaching mode does not match the linked course product.");
        }
        if (StringUtils.hasText(product.getCourseType())
                && !request.getCourseType().equals(product.getCourseType())) {
            throw new BusinessException("COURSE_PRODUCT_TYPE_MISMATCH",
                    "Course price option type does not match the linked course product.");
        }
    }

    private void validateMatrixRow(CoursePriceOptionSaveRequest request, CourseProductEntity product) {
        if (request.getPriceAmount() == null || request.getPriceAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("COURSE_PRICE_REQUIRED", "Active course price option requires a non-negative price.");
        }
        if ("ONE_ON_ONE".equals(product.getTeachingMode())) {
            if (request.getOneOnOneHours() == null || request.getOneOnOneHours().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("ONE_ON_ONE_HOURS_REQUIRED",
                        "One-on-one price option requires purchased hours.");
            }
            if (request.getOneOnOneHours().compareTo(new BigDecimal("400")) > 0) {
                throw new BusinessException("ONE_ON_ONE_HOURS_TOO_LARGE",
                        "One-on-one purchased hours cannot exceed 400 hours.");
            }
            if (request.getOneOnOneHours().multiply(new BigDecimal("2")).stripTrailingZeros().scale() > 0) {
                throw new BusinessException("ONE_ON_ONE_HOURS_INCREMENT_INVALID",
                        "One-on-one purchased hours must use 0.5-hour increments.");
            }
            return;
        }
        requireText(request.getGradeBand(), "Grade band");
        // A grade class can be created before the school has finalized its
        // teacher, room, billing window, or weekly timetable. Registration
        // remains blocked until those operational details are completed.
    }

    private void requireText(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("COURSE_PRICE_MATRIX_FIELD_REQUIRED",
                    label + " is required for active group course price options.");
        }
    }

    private void protectReferencedOptionCriticalFields(CoursePriceOptionEntity existing,
                                                       CoursePriceOptionSaveRequest request) {
        if (!isOptionReferenced(existing.getId())) {
            return;
        }
        boolean changed = !safeEquals(existing.getCourseProductId(), request.getCourseProductId())
                || !safeEquals(existing.getCourseType(), request.getCourseType())
                || !safeEquals(existing.getRegisterClass(), request.getRegisterClass())
                || !safeEquals(existing.getGradeBand(), normalizeBlank(request.getGradeBand()))
                || !safeEquals(existing.getPeriodLabel(), normalizeBlank(request.getPeriodLabel()))
                || !safeEquals(existing.getDurationLabel(), normalizeBlank(request.getDurationLabel()))
                || !safeEquals(existing.getTimeSlotLabel(), normalizeBlank(request.getTimeSlotLabel()))
                || !safeEquals(existing.getDetailText(), request.getDetailText())
                || !sameAmount(existing.getOneOnOneHours(), request.getOneOnOneHours())
                || !sameAmount(existing.getPriceAmount(), request.getPriceAmount())
                || !safeEquals(existing.getCurrencyCode(), StringUtils.hasText(request.getCurrencyCode())
                        ? request.getCurrencyCode()
                        : DEFAULT_CURRENCY);
        if (changed) {
            throw new BusinessException("COURSE_PRICE_OPTION_REFERENCED",
                    "This price row is already used. Create a new price row to change structure or price.");
        }
    }

    private boolean isOptionReferenced(Long optionId) {
        return countOptionReferences(optionId) > 0;
    }

    private long countOptionReferences(Long optionId) {
        Long count = enrollmentOrderItemMapper.selectCount(new LambdaQueryWrapper<EnrollmentOrderItemEntity>()
                .eq(EnrollmentOrderItemEntity::getCoursePriceOptionId, optionId)
                .eq(EnrollmentOrderItemEntity::getIsDeleted, 0));
        return count == null ? 0L : count;
    }

    private List<String> resolveOptionReferenceSources(long enrollmentLineCount) {
        List<String> sources = new ArrayList<>();
        if (enrollmentLineCount > 0) {
            sources.add("Enrollment order items");
        }
        return sources;
    }

    private boolean safeEquals(Object first, Object second) {
        return java.util.Objects.equals(first, second);
    }

    private boolean sameAmount(BigDecimal first, BigDecimal second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.compareTo(second) == 0;
    }

    private void applySaveRequest(CoursePriceOptionEntity entity, CoursePriceOptionSaveRequest request) {
        boolean oneOnOne = "ONE_ON_ONE".equals(request.getCourseType());
        entity.setOptionCode(normalizeBlank(request.getOptionCode()));
        entity.setCourseType(request.getCourseType());
        entity.setRegisterClass(request.getRegisterClass());
        entity.setGradeBand(oneOnOne ? normalizeBlank(request.getGradeBand()) : null);
        entity.setPeriodLabel(oneOnOne ? normalizeBlank(request.getPeriodLabel()) : null);
        entity.setDurationLabel(oneOnOne ? normalizeBlank(request.getDurationLabel()) : null);
        entity.setTimeSlotLabel(null);
        entity.setDetailText(request.getDetailText());
        entity.setOneOnOneHours(request.getOneOnOneHours());
        entity.setPriceAmount(request.getPriceAmount());
        entity.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode() : DEFAULT_CURRENCY);
        entity.setCourseProductId(request.getCourseProductId());
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : DEFAULT_STATUS);
        entity.setNotes(normalizeBlank(request.getNotes()));
    }

    /**
     * A group price row belongs to exactly one course-and-grade class. Students
     * select the price row during registration and inherit this class schedule.
     */
    private void ensureGroupClass(CoursePriceOptionEntity option, CoursePriceOptionSaveRequest request) {
        if ("ONE_ON_ONE".equals(option.getCourseType()) || option.getCourseProductId() == null) {
            option.setClassId(null);
            return;
        }
        if (option.getClassId() != null) {
            ClassGroupEntity classGroup = classGroupMapper.selectById(option.getClassId());
            if (classGroup == null) {
                throw new BusinessException("CLASS_GROUP_NOT_FOUND",
                        "The grade class linked to this price row no longer exists.");
            }
            applyGroupSchedule(classGroup, request);
            return;
        }
        CourseProductEntity product = courseProductMapper.selectById(option.getCourseProductId());
        if (product == null) {
            throw new BusinessException("COURSE_PRODUCT_NOT_FOUND", "Course product not found.");
        }
        String gradeBand = StringUtils.hasText(request.getGradeBand()) ? request.getGradeBand().trim() : "General";
        ClassGroupEntity existing = classGroupMapper.selectOne(new LambdaQueryWrapper<ClassGroupEntity>()
                .eq(ClassGroupEntity::getProductId, product.getId())
                .eq(ClassGroupEntity::getGradeBand, gradeBand)
                .eq(ClassGroupEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (existing != null) {
            option.setClassId(existing.getId());
            applyGroupSchedule(existing, request);
            return;
        }

        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setProductId(product.getId());
        classGroup.setGradeBand(gradeBand);
        classGroup.setClassName(product.getProductName() + " · " + gradeBand);
        classGroup.setListPrice(BigDecimal.ZERO);
        classGroup.setStatus("ACTIVE");
        classGroupMapper.insert(classGroup);
        option.setClassId(classGroup.getId());
        applyGroupSchedule(classGroup, request);
    }

    private void applyGroupSchedule(ClassGroupEntity classGroup, CoursePriceOptionSaveRequest request) {
        classGroup.setTeacherId(request.getGroupTeacherId());
        classGroup.setClassroomId(request.getGroupClassroomId());
        classGroup.setBillingType(request.getGroupBillingType());
        classGroup.setStartDate(request.getGroupStartDate());
        classGroup.setTotalSessions(request.getGroupTotalSessions());
        classGroup.setStartMonth(parseMonth(request.getGroupStartMonth(), "start month"));
        classGroup.setEndMonth(parseMonth(request.getGroupEndMonth(), "end month"));
        classGroupMapper.updateById(classGroup);
        if (request.getGroupScheduleSlots() == null) {
            return;
        }
        classGroupScheduleSlotMapper.delete(new LambdaQueryWrapper<ClassGroupScheduleSlotEntity>()
                .eq(ClassGroupScheduleSlotEntity::getClassGroupId, classGroup.getId()));
        List<ClassGroupScheduleSlotEntity> slots = new ArrayList<>();
        for (CoursePriceOptionSaveRequest.GroupScheduleSlotDTO slot : request.getGroupScheduleSlots()) {
            if (slot.getWeekday() == null || slot.getWeekday() < 1 || slot.getWeekday() > 7) {
                throw new BusinessException("INVALID_GROUP_SCHEDULE", "Weekday must be between 1 and 7.");
            }
            LocalTime start = LocalTime.parse(slot.getStartTime());
            LocalTime end = LocalTime.parse(slot.getEndTime());
            if (!end.isAfter(start)) {
                throw new BusinessException("INVALID_GROUP_SCHEDULE", "Schedule end time must be after start time.");
            }
            ClassGroupScheduleSlotEntity entity = new ClassGroupScheduleSlotEntity();
            entity.setClassGroupId(classGroup.getId());
            entity.setWeekday(slot.getWeekday());
            entity.setStartTime(start);
            entity.setEndTime(end);
            classGroupScheduleSlotMapper.insert(entity);
            slots.add(entity);
        }
        rebuildFutureClassOccurrences(classGroup, slots);
    }

    /**
     * Grade classes should show their planned lessons before students enroll.
     * Preserve recorded lessons, but keep future unrecorded rows aligned with
     * the latest weekly schedule from Price Matrix.
     */
    private void rebuildFutureClassOccurrences(ClassGroupEntity classGroup, List<ClassGroupScheduleSlotEntity> slots) {
        removeFutureUnrecordedClassOccurrences(classGroup.getId());
        if (slots.isEmpty()) {
            return;
        }
        Set<String> existingKeys = futureClassOccurrenceKeys(classGroup.getId());
        LocalDate startDate = resolveOccurrenceStartDate(classGroup);
        LocalDate endDate = resolveOccurrenceEndDate(classGroup, startDate);
        if (endDate.isBefore(startDate)) {
            return;
        }
        int remainingSessions = "PER_SESSION".equals(classGroup.getBillingType())
                && classGroup.getTotalSessions() != null
                ? Math.max(classGroup.getTotalSessions() - existingKeys.size(), 0)
                : Integer.MAX_VALUE;
        for (LocalDate date = startDate; !date.isAfter(endDate) && remainingSessions > 0; date = date.plusDays(1)) {
            int weekday = date.getDayOfWeek().getValue();
            for (ClassGroupScheduleSlotEntity slot : slots) {
                if (!slot.getWeekday().equals(weekday) || remainingSessions <= 0) {
                    continue;
                }
                LocalDateTime startAt = LocalDateTime.of(date, slot.getStartTime());
                LocalDateTime endAt = LocalDateTime.of(date, slot.getEndTime());
                String key = occurrenceKey(startAt, endAt);
                if (existingKeys.contains(key)) {
                    continue;
                }
                LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
                occurrence.setLessonNo(generateClassLessonNo());
                occurrence.setClassGroupId(classGroup.getId());
                occurrence.setScheduleSlotId(slot.getId());
                occurrence.setOccurrenceType("REGULAR");
                occurrence.setPlannedTeacherId(classGroup.getTeacherId());
                occurrence.setPlannedClassroomId(classGroup.getClassroomId());
                occurrence.setPlannedStartAt(startAt);
                occurrence.setPlannedEndAt(endAt);
                occurrence.setPlannedUnits((int) (ChronoUnit.MINUTES.between(slot.getStartTime(), slot.getEndTime()) / 30));
                occurrence.setPlanningStatus("SCHEDULED");
                occurrence.setConflictChecked(1);
                lessonOccurrenceMapper.insert(occurrence);
                existingKeys.add(key);
                remainingSessions--;
            }
        }
    }

    private void removeFutureUnrecordedClassOccurrences(Long classId) {
        LocalDateTime now = AppTime.nowLocalDateTime();
        List<LessonOccurrenceEntity> futureOccurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getClassGroupId, classId)
                        .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                        .ge(LessonOccurrenceEntity::getPlannedStartAt, now));
        if (futureOccurrences.isEmpty()) {
            return;
        }
        Set<Long> occurrenceIds = futureOccurrences.stream()
                .map(LessonOccurrenceEntity::getId)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> recordedIds = lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                        .in(LessonRecordEntity::getOccurrenceId, occurrenceIds)
                        .eq(LessonRecordEntity::getIsDeleted, 0))
                .stream()
                .map(LessonRecordEntity::getOccurrenceId)
                .collect(java.util.stream.Collectors.toSet());
        List<Long> removableIds = futureOccurrences.stream()
                .map(LessonOccurrenceEntity::getId)
                .filter(id -> !recordedIds.contains(id))
                .toList();
        if (!removableIds.isEmpty()) {
            lessonOccurrenceMapper.delete(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                    .in(LessonOccurrenceEntity::getId, removableIds));
        }
    }

    private Set<String> futureClassOccurrenceKeys(Long classId) {
        LocalDateTime now = AppTime.nowLocalDateTime();
        Set<String> keys = new HashSet<>();
        lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getClassGroupId, classId)
                        .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                        .ge(LessonOccurrenceEntity::getPlannedStartAt, now))
                .forEach(item -> keys.add(occurrenceKey(item.getPlannedStartAt(), item.getPlannedEndAt())));
        return keys;
    }

    private LocalDate resolveOccurrenceStartDate(ClassGroupEntity classGroup) {
        LocalDate today = AppTime.nowLocalDateTime().toLocalDate();
        LocalDate configuredStart = "MONTHLY".equals(classGroup.getBillingType())
                ? classGroup.getStartMonth()
                : classGroup.getStartDate();
        if (configuredStart == null || configuredStart.isBefore(today)) {
            return today;
        }
        return configuredStart;
    }

    private LocalDate resolveOccurrenceEndDate(ClassGroupEntity classGroup, LocalDate startDate) {
        if ("PER_SESSION".equals(classGroup.getBillingType()) && classGroup.getTotalSessions() != null) {
            return startDate.plusDays(370);
        }
        if ("MONTHLY".equals(classGroup.getBillingType()) && classGroup.getEndMonth() != null) {
            return YearMonth.from(classGroup.getEndMonth()).atEndOfMonth();
        }
        return startDate.plusDays(60);
    }

    private String occurrenceKey(LocalDateTime startAt, LocalDateTime endAt) {
        return startAt + "|" + endAt;
    }

    private String generateClassLessonNo() {
        return "CLS-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private void validateGroupBilling(CoursePriceOptionSaveRequest request) {
        if (!Set.of("PER_SESSION", "MONTHLY").contains(request.getGroupBillingType())) {
            throw new BusinessException("INVALID_GROUP_BILLING_TYPE",
                    "Grade class billing type must be PER_SESSION or MONTHLY.");
        }
        if ("PER_SESSION".equals(request.getGroupBillingType())) {
            if (request.getGroupStartDate() == null || request.getGroupTotalSessions() == null
                    || request.getGroupTotalSessions() < 1) {
                throw new BusinessException("GROUP_BILLING_REQUIRED",
                        "Per-session grade classes need a start date and total sessions.");
            }
            if (StringUtils.hasText(request.getGroupStartMonth()) || StringUtils.hasText(request.getGroupEndMonth())) {
                throw new BusinessException("INVALID_GROUP_BILLING",
                        "Per-session grade classes cannot have a month range.");
            }
            return;
        }
        LocalDate startMonth = parseMonth(request.getGroupStartMonth(), "start month");
        LocalDate endMonth = parseMonth(request.getGroupEndMonth(), "end month");
        if (startMonth == null || endMonth == null) {
            throw new BusinessException("GROUP_BILLING_REQUIRED",
                    "Monthly grade classes need a start and end month.");
        }
        if (endMonth.isBefore(startMonth)) {
            throw new BusinessException("INVALID_GROUP_BILLING", "End month cannot be earlier than start month.");
        }
        if (request.getGroupStartDate() != null || request.getGroupTotalSessions() != null) {
            throw new BusinessException("INVALID_GROUP_BILLING",
                    "Monthly grade classes cannot have a start date or total sessions.");
        }
    }

    private LocalDate parseMonth(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return YearMonth.parse(value).atDay(1);
        } catch (RuntimeException ex) {
            throw new BusinessException("INVALID_GROUP_BILLING",
                    "Grade class " + fieldName + " must use yyyy-MM format.");
        }
    }

    private void ensureOptionCodeUnique(Long currentId, String optionCode) {
        Long count = coursePriceOptionMapper.selectCount(baseWrapper()
                .eq(CoursePriceOptionEntity::getOptionCode, optionCode)
                .ne(currentId != null, CoursePriceOptionEntity::getId, currentId));
        if (count != null && count > 0) {
            throw new BusinessException("COURSE_PRICE_OPTION_CODE_EXISTS",
                    "Course price option code already exists: " + optionCode);
        }
    }

    private String generateOptionCode(CoursePriceOptionSaveRequest request) {
        String base = slug(String.join("_",
                request.getCourseType(),
                request.getRegisterClass(),
                nullToEmpty(request.getGradeBand()),
                nullToEmpty(request.getPeriodLabel()),
                nullToEmpty(request.getDurationLabel()),
                nullToEmpty(request.getTimeSlotLabel())));
        if (!StringUtils.hasText(base)) {
            base = "COURSE_PRICE_OPTION";
        }
        base = truncateCode(base, 64);
        String code = base;
        int suffix = 2;
        while (optionCodeExists(code)) {
            String suffixText = "_" + suffix;
            code = truncateCode(base, 64 - suffixText.length()) + suffixText;
            suffix++;
        }
        return code;
    }

    private boolean optionCodeExists(String optionCode) {
        Long count = coursePriceOptionMapper.selectCount(baseWrapper()
                .eq(CoursePriceOptionEntity::getOptionCode, optionCode));
        return count != null && count > 0;
    }

    private String slug(String value) {
        return value.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private String truncateCode(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).replaceAll("_+$", "");
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private CoursePriceOptionVO toVO(CoursePriceOptionEntity entity) {
        long referenceCount = countOptionReferences(entity.getId());
        ClassGroupEntity classGroup = entity.getClassId() == null ? null : classGroupMapper.selectById(entity.getClassId());
        List<CoursePriceOptionVO.GroupScheduleSlotVO> groupScheduleSlots = classGroup == null
                ? List.of()
                : classGroupScheduleSlotMapper.selectList(new LambdaQueryWrapper<ClassGroupScheduleSlotEntity>()
                                .eq(ClassGroupScheduleSlotEntity::getClassGroupId, classGroup.getId())
                                .eq(ClassGroupScheduleSlotEntity::getIsDeleted, 0)
                                .orderByAsc(ClassGroupScheduleSlotEntity::getWeekday)
                                .orderByAsc(ClassGroupScheduleSlotEntity::getStartTime))
                        .stream()
                        .map(slot -> new CoursePriceOptionVO.GroupScheduleSlotVO(
                                slot.getWeekday(), slot.getStartTime().toString(), slot.getEndTime().toString()))
                        .toList();
        return CoursePriceOptionVO.builder()
                .id(entity.getId())
                .optionCode(entity.getOptionCode())
                .courseType(entity.getCourseType())
                .registerClass(entity.getRegisterClass())
                .gradeBand(classGroup == null ? entity.getGradeBand() : classGroup.getGradeBand())
                .periodLabel(entity.getPeriodLabel())
                .durationLabel(entity.getDurationLabel())
                .timeSlotLabel(entity.getTimeSlotLabel())
                .detailText(entity.getDetailText())
                .oneOnOneHours(entity.getOneOnOneHours())
                .priceAmount(entity.getPriceAmount())
                .currencyCode(entity.getCurrencyCode())
                .courseProductId(entity.getCourseProductId())
                .classId(entity.getClassId())
                .groupTeacherId(classGroup == null ? null : classGroup.getTeacherId())
                .groupClassroomId(classGroup == null ? null : classGroup.getClassroomId())
                .groupScheduleSlots(groupScheduleSlots)
                .groupBillingType(classGroup == null ? null : classGroup.getBillingType())
                .groupStartDate(classGroup == null || classGroup.getStartDate() == null
                        ? null : classGroup.getStartDate().toString())
                .groupTotalSessions(classGroup == null ? null : classGroup.getTotalSessions())
                .groupStartMonth(classGroup == null || classGroup.getStartMonth() == null
                        ? null : YearMonth.from(classGroup.getStartMonth()).toString())
                .groupEndMonth(classGroup == null || classGroup.getEndMonth() == null
                        ? null : YearMonth.from(classGroup.getEndMonth()).toString())
                .sortOrder(entity.getSortOrder())
                .status(entity.getStatus())
                .notes(entity.getNotes())
                .referenced(referenceCount > 0)
                .referenceCount(referenceCount)
                .referenceSources(resolveOptionReferenceSources(referenceCount))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
