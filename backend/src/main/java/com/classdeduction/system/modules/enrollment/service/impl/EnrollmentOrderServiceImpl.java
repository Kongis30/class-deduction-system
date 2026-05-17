package com.classdeduction.system.modules.enrollment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.common.util.UnitConverter;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.dto.ClassEnrollmentCreateRequest;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentCreateRequest;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderPageQuery;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderUpdateRequest;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.PackageTeacherAssignmentEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentRecordEntity;
import com.classdeduction.system.modules.enrollment.entity.ReceiptRecordEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentRecordMapper;
import com.classdeduction.system.modules.enrollment.mapper.ReceiptRecordMapper;
import com.classdeduction.system.modules.enrollment.service.EnrollmentOrderService;
import com.classdeduction.system.modules.enrollment.util.BusinessNoGenerator;
import com.classdeduction.system.modules.enrollment.vo.CoursePackageListItemVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentCreateResultVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentOrderDetailVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentOrderListItemVO;
import com.classdeduction.system.modules.enrollment.vo.PackageTeacherAssignmentVO;
import com.classdeduction.system.modules.enrollment.vo.PaymentRecordVO;
import com.classdeduction.system.modules.enrollment.vo.ReceiptRecordVO;
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
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 报名服务：负责报名开单，并同步生成支付记录、课时包、主老师分配和收据。
 */
@Service
@RequiredArgsConstructor
public class EnrollmentOrderServiceImpl implements EnrollmentOrderService {

    private static final String DEFAULT_CURRENCY = "USD";
    private static final String DEFAULT_RECEIPT_TEMPLATE = "DEFAULT_RECEIPT";

    private final EnrollmentOrderMapper enrollmentOrderMapper;
    private final CoursePackageMapper coursePackageMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final ReceiptRecordMapper receiptRecordMapper;
    private final PackageTeacherAssignmentMapper packageTeacherAssignmentMapper;
    private final StudentMapper studentMapper;
    private final CourseProductMapper courseProductMapper;
    private final TeacherMapper teacherMapper;
    private final JsonHelper jsonHelper;
    private final RecurringScheduleRuleMapper recurringScheduleRuleMapper;
    private final RecurringScheduleSlotMapper recurringScheduleSlotMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final ClassGroupMapper classGroupMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;

    /**
     * Initialize the business number sequence from the database to avoid
     * collisions after a server restart (in-memory AtomicInteger resets to 0).
     */
    @PostConstruct
    void initSequence() {
        String todayStr = LocalDate.now(AppTime.APP_ZONE_ID).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        LambdaQueryWrapper<EnrollmentOrderEntity> wrapper = new LambdaQueryWrapper<EnrollmentOrderEntity>()
                .likeRight(EnrollmentOrderEntity::getEnrollmentNo, "EO" + todayStr)
                .orderByDesc(EnrollmentOrderEntity::getEnrollmentNo)
                .last("LIMIT 1");
        EnrollmentOrderEntity last = enrollmentOrderMapper.selectOne(wrapper);
        if (last != null) {
            String no = last.getEnrollmentNo();
            int seq = Integer.parseInt(no.substring(no.length() - 4));
            BusinessNoGenerator.initSequence(seq);
        }
    }

    @Override
    public PageResponse<EnrollmentOrderListItemVO> pageOrders(EnrollmentOrderPageQuery query) {
        Page<EnrollmentOrderEntity> page = new Page<>(query.getPageNum(), query.getPageSize());
        List<Long> matchedStudentIds = findMatchedStudentIds(query.getKeyword());
        LambdaQueryWrapper<EnrollmentOrderEntity> wrapper = new LambdaQueryWrapper<EnrollmentOrderEntity>()
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(EnrollmentOrderEntity::getEnrollmentNo, query.getKeyword())
                        .or(!matchedStudentIds.isEmpty(), nested -> nested.in(EnrollmentOrderEntity::getStudentId, matchedStudentIds)))
                .eq(query.getStudentId() != null, EnrollmentOrderEntity::getStudentId, query.getStudentId())
                .eq(query.getClassId() != null, EnrollmentOrderEntity::getClassId, query.getClassId())
                .isNull(query.isExcludeClassOrders(), EnrollmentOrderEntity::getClassId)
                .eq(StringUtils.hasText(query.getOrderStatus()), EnrollmentOrderEntity::getOrderStatus, query.getOrderStatus())
                .eq(StringUtils.hasText(query.getPaymentStatus()), EnrollmentOrderEntity::getPaymentStatus, query.getPaymentStatus())
                .orderByDesc(EnrollmentOrderEntity::getCreatedAt);
        IPage<EnrollmentOrderEntity> result = enrollmentOrderMapper.selectPage(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }

        Set<Long> studentIds = result.getRecords().stream().map(EnrollmentOrderEntity::getStudentId).collect(Collectors.toSet());
        Map<Long, StudentEntity> studentMap = listStudentsByIds(studentIds);

        // Separate 1v1 and class orders
        List<Long> oneOnOneOrderIds = result.getRecords().stream()
                .filter(o -> o.getClassId() == null)
                .map(EnrollmentOrderEntity::getId).toList();
        Set<Long> classIds = result.getRecords().stream()
                .map(EnrollmentOrderEntity::getClassId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        // Load packages for 1v1 orders
        List<CoursePackageEntity> allPackages = oneOnOneOrderIds.isEmpty()
                ? Collections.emptyList()
                : coursePackageMapper.selectList(new LambdaQueryWrapper<CoursePackageEntity>()
                        .in(CoursePackageEntity::getEnrollmentOrderId, oneOnOneOrderIds));
        Map<Long, Integer> packageCountMap = allPackages.stream()
                .collect(Collectors.groupingBy(CoursePackageEntity::getEnrollmentOrderId, Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        Map<Long, CoursePackageEntity> firstPackageMap = allPackages.stream()
                .collect(Collectors.groupingBy(CoursePackageEntity::getEnrollmentOrderId,
                        Collectors.collectingAndThen(Collectors.toList(), list -> list.isEmpty() ? null : list.get(0))));

        Set<Long> courseProductIds = allPackages.stream()
                .map(CoursePackageEntity::getCourseProductId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, CourseProductEntity> productMap = courseProductIds.isEmpty()
                ? Collections.emptyMap()
                : courseProductMapper.selectBatchIds(courseProductIds).stream()
                        .collect(Collectors.toMap(CourseProductEntity::getId, p -> p));

        // Load class names for class orders
        Map<Long, ClassGroupEntity> classMap = classIds.isEmpty()
                ? Collections.emptyMap()
                : classGroupMapper.selectBatchIds(classIds).stream()
                        .collect(Collectors.toMap(ClassGroupEntity::getId, c -> c));

        List<EnrollmentOrderListItemVO> list = result.getRecords().stream()
                .map(order -> {
                    StudentEntity student = studentMap.get(order.getStudentId());
                    if (order.getClassId() != null) {
                        // Class order: no course_package, show class name instead
                        ClassGroupEntity clz = classMap.get(order.getClassId());
                        return toClassOrderListItem(order, student, clz);
                    }
                    CoursePackageEntity firstPackage = firstPackageMap.get(order.getId());
                    CourseProductEntity product = firstPackage == null ? null : productMap.get(firstPackage.getCourseProductId());
                    return toOrderListItem(order, student, firstPackage, product, packageCountMap.getOrDefault(order.getId(), 0));
                })
                .toList();

        return PageResponse.of(list, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public EnrollmentOrderDetailVO getOrder(Long id) {
        EnrollmentOrderEntity order = requireOrder(id);
        StudentEntity student = requireStudent(order.getStudentId());
        List<PaymentRecordEntity> payments = listPaymentsByOrderId(id);
        List<ReceiptRecordEntity> receipts = listReceiptsByOrderIds(List.of(id));

        // Resolve class name for class orders
        String className = null;
        if (order.getClassId() != null) {
            ClassGroupEntity clz = classGroupMapper.selectById(order.getClassId());
            className = clz != null ? clz.getClassName() : null;
        }

        // Load packages only for 1v1 orders
        List<CoursePackageEntity> packages = order.getClassId() == null
                ? listPackagesByOrderIds(List.of(id))
                : Collections.emptyList();
        Map<Long, String> primaryTeacherNameMap = packages.isEmpty()
                ? Collections.emptyMap()
                : buildPrimaryTeacherNameMap(packages.stream().map(CoursePackageEntity::getId).toList());

        Set<Long> courseProductIds = packages.stream()
                .map(CoursePackageEntity::getCourseProductId)
                .filter(pid -> pid != null)
                .collect(Collectors.toSet());
        Map<Long, CourseProductEntity> productMap = courseProductIds.isEmpty()
                ? Collections.emptyMap()
                : courseProductMapper.selectBatchIds(courseProductIds).stream()
                        .collect(Collectors.toMap(CourseProductEntity::getId, p -> p));

        return EnrollmentOrderDetailVO.builder()
                .id(order.getId())
                .enrollmentNo(order.getEnrollmentNo())
                .studentId(student.getId())
                .studentNo(student.getStudentNo())
                .studentName(student.getStudentName())
                .classId(order.getClassId())
                .className(className)
                .orderStatus(order.getOrderStatus())
                .cashierUserId(order.getCashierUserId())
                .handledByName(order.getHandledByName())
                .currencyCode(order.getCurrencyCode())
                .originalAmount(order.getOriginalAmount())
                .discountAmount(order.getDiscountAmount())
                .receivedAmount(order.getReceivedAmount())
                .paymentStatus(order.getPaymentStatus())
                .remark(order.getRemark())
                .payments(payments.stream().map(this::toPaymentVO).toList())
                .packages(packages.stream().map(item -> toPackageListItem(item, student, primaryTeacherNameMap.get(item.getId()), productMap.get(item.getCourseProductId()))).toList())
                .receipts(receipts.stream().map(item -> toReceiptVO(item, student.getStudentName())).toList())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnrollmentCreateResultVO createOrder(EnrollmentCreateRequest request) {
        validateAmounts(request);
        StudentEntity student = requireStudent(request.getStudentId());
        CourseProductEntity product = requireCourseProduct(request.getCourseProductId());
        TeacherEntity primaryTeacher = request.getPrimaryTeacherId() == null ? null : requireTeacher(request.getPrimaryTeacherId());

        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setEnrollmentNo(BusinessNoGenerator.generateEnrollmentNo());
        order.setStudentId(student.getId());
        order.setOrderStatus("CONFIRMED");
        order.setCashierUserId(request.getCashierUserId());
        order.setHandledByName(request.getHandledByName());
        order.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode() : DEFAULT_CURRENCY);
        order.setOriginalAmount(request.getOriginalAmount());
        order.setDiscountAmount(request.getDiscountAmount());
        order.setReceivedAmount(request.getReceivedAmount());
        order.setPaymentStatus(resolvePaymentStatus(request.getOriginalAmount(), request.getDiscountAmount(), request.getReceivedAmount()));
        order.setRemark(request.getRemark());
        enrollmentOrderMapper.insert(order);

        CoursePackageEntity coursePackage = createCoursePackage(order, product, request, student);
        List<Long> paymentIds = new ArrayList<>();
        List<String> paymentNos = new ArrayList<>();
        List<Long> receiptIds = new ArrayList<>();
        List<String> receiptNos = new ArrayList<>();

        if (primaryTeacher != null) {
            createPrimaryTeacherAssignment(coursePackage.getId(), primaryTeacher.getId(), request);
        }

        // Auto-create schedule if slots are provided
        Long scheduleRuleId = null;
        Integer generatedOccurrenceCount = null;
        LocalDateTime firstLessonAt = null;
        LocalDateTime lastLessonAt = null;
        if (request.getSlots() != null && !request.getSlots().isEmpty() && request.getStartDate() != null) {
            var scheduleResult = createScheduleForPackage(coursePackage, student, request);
            scheduleRuleId = scheduleResult.ruleId();
            generatedOccurrenceCount = scheduleResult.occurrenceCount();
            firstLessonAt = scheduleResult.firstLessonAt();
            lastLessonAt = scheduleResult.lastLessonAt();
        }

        if (request.getReceivedAmount().compareTo(BigDecimal.ZERO) > 0) {
            PaymentRecordEntity paymentRecord = createPaymentRecord(order, request);
            paymentIds.add(paymentRecord.getId());
            paymentNos.add(paymentRecord.getPaymentNo());
        }
        if (!Boolean.FALSE.equals(request.getIssueReceipt())) {
            ReceiptRecordEntity receiptRecord = createReceipt(order, coursePackage, student, product, request);
            receiptIds.add(receiptRecord.getId());
            receiptNos.add(receiptRecord.getReceiptNo());
        }

        return EnrollmentCreateResultVO.builder()
                .enrollmentOrderId(order.getId())
                .enrollmentNo(order.getEnrollmentNo())
                .packageIds(List.of(coursePackage.getId()))
                .packageNos(List.of(coursePackage.getPackageNo()))
                .paymentIds(paymentIds)
                .paymentNos(paymentNos)
                .receiptIds(receiptIds)
                .receiptNos(receiptNos)
                .scheduleRuleId(scheduleRuleId)
                .generatedOccurrenceCount(generatedOccurrenceCount)
                .firstLessonAt(firstLessonAt)
                .lastLessonAt(lastLessonAt)
                .build();
    }

    private void validateAmounts(EnrollmentCreateRequest request) {
        BigDecimal discountedAmount = request.getOriginalAmount().subtract(request.getDiscountAmount());
        if (discountedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("DISCOUNT_EXCEEDS_AMOUNT", "Discount cannot exceed original amount.");
        }
        if (request.getReceivedAmount().compareTo(discountedAmount) > 0) {
            throw new BusinessException("RECEIVED_EXCEEDS_AMOUNT", "Received amount cannot exceed amount due.");
        }
        validatePackageUnits(request);
    }

    private void validatePackageUnits(EnrollmentCreateRequest request) {
        if (request.getTotalUnits() != null && request.getTotalUnits() <= 0) {
            throw new BusinessException("INVALID_TOTAL_UNITS", "总课时单位必须大于0。");
        }
        if (request.getDefaultLessonUnits() != null && request.getDefaultLessonUnits() <= 0) {
            throw new BusinessException("INVALID_LESSON_UNITS", "默认单节课时单位必须大于0。");
        }
        if (request.getTotalUnits() != null
                && request.getDefaultLessonUnits() != null
                && request.getDefaultLessonUnits() > request.getTotalUnits()) {
            throw new BusinessException("LESSON_UNITS_EXCEED_TOTAL", "默认单节课时单位不能大于总课时单位。");
        }
    }

    private CoursePackageEntity createCoursePackage(EnrollmentOrderEntity order,
                                                    CourseProductEntity product,
                                                    EnrollmentCreateRequest request,
                                                    StudentEntity student) {
        int totalUnits = request.getTotalUnits() != null ? request.getTotalUnits() : product.getDefaultTotalUnits();
        int defaultLessonUnits = request.getDefaultLessonUnits() != null
                ? request.getDefaultLessonUnits()
                : product.getDefaultLessonUnits();
        if (defaultLessonUnits <= 0) {
            throw new BusinessException("INVALID_LESSON_UNITS", "默认单节课时单位必须大于0。");
        }
        if (totalUnits <= 0) {
            throw new BusinessException("INVALID_TOTAL_UNITS", "总课时必须大于0。");
        }
        // totalUnits 从前端传入的是小时数，内部存储统一转为半课时单位（1单位=0.5h）
        int halfHourUnits = totalUnits * 2;
        if (defaultLessonUnits > halfHourUnits) {
            throw new BusinessException("LESSON_UNITS_EXCEED_TOTAL", "默认单节课时单位不能大于总课时单位。");
        }

        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setPackageNo(BusinessNoGenerator.generatePackageNo());
        coursePackage.setEnrollmentOrderId(order.getId());
        coursePackage.setStudentId(student.getId());
        coursePackage.setCourseProductId(product.getId());
        coursePackage.setPackageName(StringUtils.hasText(request.getPackageName()) ? request.getPackageName() : product.getProductName());
        coursePackage.setSubjectName(product.getSubjectName());
        coursePackage.setTeachingMode(product.getTeachingMode());
        coursePackage.setTotalUnits(halfHourUnits);
        coursePackage.setDefaultLessonUnits(defaultLessonUnits);
        coursePackage.setConsumedUnits(0);
        coursePackage.setRemainingUnits(halfHourUnits);
        coursePackage.setOriginalAmount(order.getOriginalAmount());
        coursePackage.setDiscountAmount(order.getDiscountAmount());
        coursePackage.setReceivedAmount(order.getReceivedAmount());
        coursePackage.setPackageStatus("ACTIVE");
        coursePackage.setStartDate(request.getStartDate());
        coursePackage.setEstimatedEndDate(request.getEstimatedEndDate());
        coursePackage.setRemark(request.getRemark());
        coursePackageMapper.insert(coursePackage);
        return coursePackage;
    }

    private void createPrimaryTeacherAssignment(Long packageId, Long teacherId, EnrollmentCreateRequest request) {
        PackageTeacherAssignmentEntity assignment = new PackageTeacherAssignmentEntity();
        assignment.setPackageId(packageId);
        assignment.setTeacherId(teacherId);
        assignment.setIsPrimary(1);
        assignment.setEffectiveFrom(request.getAssignmentEffectiveFrom() != null ? request.getAssignmentEffectiveFrom() : AppTime.nowLocalDateTime());
        assignment.setAssignmentStatus("ACTIVE");
        assignment.setChangeReason("INITIAL_ASSIGNMENT");
        packageTeacherAssignmentMapper.insert(assignment);
    }

    /**
     * Auto-create a recurring schedule rule, slots, and lesson occurrences
     * from the enrollment request's slot definitions.
     */
    private ScheduleResult createScheduleForPackage(CoursePackageEntity coursePackage,
                                                    StudentEntity student,
                                                    EnrollmentCreateRequest request) {
        List<EnrollmentCreateRequest.SlotDefinition> slotDefs = request.getSlots();
        int totalUnits = coursePackage.getTotalUnits();                     // 总课时（半课时单位，1单位=0.5h）

        // 按每周总半课时单位计算需要的周数
        int weeklyHalfHourUnits = slotDefs.stream().mapToInt(EnrollmentCreateRequest.SlotDefinition::getPlannedUnits).sum();
        int weeks = Math.max(1, (int) Math.ceil((double) totalUnits / weeklyHalfHourUnits));

        LocalDate startDate = request.getStartDate();
        LocalDate endDate = startDate.plusDays(weeks * 7L);

        // Also set the package estimatedEndDate
        coursePackage.setEstimatedEndDate(endDate);
        coursePackageMapper.updateById(coursePackage);

        // Build the rule entity
        RecurringScheduleRuleEntity rule = new RecurringScheduleRuleEntity();
        rule.setPackageId(coursePackage.getId());
        rule.setPrimaryTeacherId(request.getPrimaryTeacherId());
        rule.setDefaultClassroomId(request.getDefaultClassroomId());
        rule.setRuleStatus("ACTIVE");
        rule.setStartDate(startDate);
        rule.setEndDate(endDate);
        rule.setGenerationHorizonDays(weeks * 7);
        rule.setNotes("Auto-created from enrollment");
        recurringScheduleRuleMapper.insert(rule);

        // Build slot entities
        List<RecurringScheduleSlotEntity> slots = new ArrayList<>();
        for (var slotDef : slotDefs) {
            RecurringScheduleSlotEntity slot = new RecurringScheduleSlotEntity();
            slot.setRuleId(rule.getId());
            slot.setWeekday(slotDef.getWeekday());
            slot.setStartTime(LocalTime.parse(slotDef.getStartTime(), DateTimeFormatter.ofPattern("HH:mm")));
            slot.setEndTime(LocalTime.parse(slotDef.getEndTime(), DateTimeFormatter.ofPattern("HH:mm")));
            slot.setPlannedUnits(slotDef.getPlannedUnits());
            slot.setSlotStatus("ACTIVE");
            recurringScheduleSlotMapper.insert(slot);
            slots.add(slot);
        }

        // Build occurrences
        List<LessonOccurrenceEntity> occurrences = buildOccurrences(rule, slots, coursePackage, student.getId());
        if (!occurrences.isEmpty()) {
            ensureNoConflicts(occurrences, student.getId(), rule.getPrimaryTeacherId(), rule.getDefaultClassroomId());
            occurrences.forEach(lessonOccurrenceMapper::insert);
        }

        LocalDateTime firstStart = occurrences.stream()
                .map(LessonOccurrenceEntity::getPlannedStartAt).min(Comparator.naturalOrder()).orElse(null);
        LocalDateTime lastEnd = occurrences.stream()
                .map(LessonOccurrenceEntity::getPlannedEndAt).max(Comparator.naturalOrder()).orElse(null);

        return new ScheduleResult(rule.getId(), occurrences.size(), firstStart, lastEnd);
    }

    private record ScheduleResult(Long ruleId, int occurrenceCount, LocalDateTime firstLessonAt, LocalDateTime lastLessonAt) {}

    private List<LessonOccurrenceEntity> buildOccurrences(RecurringScheduleRuleEntity rule,
                                                          List<RecurringScheduleSlotEntity> slots,
                                                          CoursePackageEntity coursePackage,
                                                          Long studentId) {
        LocalDate generationStart = Collections.max(List.of(rule.getStartDate(), AppTime.nowLocalDateTime().toLocalDate()));
        LocalDate naturalWindowEnd = generationStart.plusDays(rule.getGenerationHorizonDays() - 1L);
        LocalDate generationEnd = rule.getEndDate() == null ? naturalWindowEnd
                : Collections.min(List.of(rule.getEndDate(), naturalWindowEnd));
        if (generationEnd.isBefore(generationStart)) {
            return Collections.emptyList();
        }

        int totalUnits = coursePackage.getTotalUnits();                     // 总课时（半课时单位）
        int accumulatedUnits = 0;

        List<LessonOccurrenceEntity> occurrences = new ArrayList<>();
        outer:
        for (LocalDate date = generationStart; !date.isAfter(generationEnd); date = date.plusDays(1)) {
            int weekday = toWeekday(date.getDayOfWeek());
            for (RecurringScheduleSlotEntity slot : slots) {
                if (!"ACTIVE".equals(slot.getSlotStatus()) || !java.util.Objects.equals(slot.getWeekday(), weekday)) {
                    continue;
                }

                int slotUnits = slot.getPlannedUnits();
                int remainingUnits = totalUnits - accumulatedUnits;
                if (remainingUnits <= 0) break outer;

                int actualUnits = Math.min(slotUnits, remainingUnits);

                LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
                occurrence.setLessonNo(generateLessonNo());
                occurrence.setPackageId(coursePackage.getId());
                occurrence.setStudentId(studentId);
                occurrence.setScheduleRuleId(rule.getId());
                occurrence.setScheduleSlotId(slot.getId());
                occurrence.setOccurrenceType("REGULAR");
                occurrence.setPlannedTeacherId(rule.getPrimaryTeacherId());
                occurrence.setPlannedClassroomId(rule.getDefaultClassroomId());
                occurrence.setPlannedStartAt(LocalDateTime.of(date, slot.getStartTime()));
                occurrence.setPlannedUnits(actualUnits);

                if (actualUnits < slotUnits) {
                    // 最后一次课：按剩余课时比例缩短 endTime
                    long slotDurationMinutes = java.time.Duration.between(slot.getStartTime(), slot.getEndTime()).toMinutes();
                    long actualDurationMinutes = Math.max(1, slotDurationMinutes * actualUnits / slotUnits);
                    occurrence.setPlannedEndAt(LocalDateTime.of(date, slot.getStartTime().plusMinutes(actualDurationMinutes)));
                } else {
                    occurrence.setPlannedEndAt(LocalDateTime.of(date, slot.getEndTime()));
                }

                occurrence.setPlanningStatus("SCHEDULED");
                occurrence.setConflictChecked(1);
                occurrences.add(occurrence);
                accumulatedUnits += actualUnits;
            }
        }
        return occurrences;
    }

    private void ensureNoConflicts(List<LessonOccurrenceEntity> generatedOccurrences,
                                   Long studentId,
                                   Long teacherId,
                                   Long classroomId) {
        if (generatedOccurrences.isEmpty()) {
            return;
        }
        LocalDateTime windowStart = generatedOccurrences.stream()
                .map(LessonOccurrenceEntity::getPlannedStartAt).min(Comparator.naturalOrder()).orElseThrow();
        LocalDateTime windowEnd = generatedOccurrences.stream()
                .map(LessonOccurrenceEntity::getPlannedEndAt).max(Comparator.naturalOrder()).orElseThrow();

        var studentOccurrences = lessonOccurrenceMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getStudentId, studentId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));
        var teacherOccurrences = teacherId == null ? Collections.<LessonOccurrenceEntity>emptyList()
                : lessonOccurrenceMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getPlannedTeacherId, teacherId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));
        var classroomOccurrences = classroomId == null ? Collections.<LessonOccurrenceEntity>emptyList()
                : lessonOccurrenceMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getPlannedClassroomId, classroomId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));

        List<String> conflicts = new ArrayList<>();
        for (LessonOccurrenceEntity candidate : generatedOccurrences) {
            if (hasOverlap(candidate, studentOccurrences)) {
                conflicts.add("Student conflict at " + candidate.getPlannedStartAt());
            }
            if (teacherId != null && hasOverlap(candidate, teacherOccurrences)) {
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

    private String generateLessonNo() {
        return "LS" + AppTime.nowLocalDateTime().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(1000, 10000));
    }

    private PaymentRecordEntity createPaymentRecord(EnrollmentOrderEntity order, EnrollmentCreateRequest request) {
        PaymentRecordEntity paymentRecord = new PaymentRecordEntity();
        paymentRecord.setEnrollmentOrderId(order.getId());
        paymentRecord.setPaymentNo(BusinessNoGenerator.generatePaymentNo());
        paymentRecord.setPaymentMethod("CASH");
        paymentRecord.setPaymentStatus(order.getPaymentStatus());
        paymentRecord.setAmount(request.getReceivedAmount());
        paymentRecord.setPaidAt(request.getPaidAt() != null ? request.getPaidAt() : AppTime.nowLocalDateTime());
        paymentRecord.setHandledByUserId(request.getCashierUserId());
        paymentRecord.setHandledByName(request.getHandledByName());
        paymentRecord.setRemark(request.getRemark());
        paymentRecordMapper.insert(paymentRecord);
        return paymentRecord;
    }

    private ReceiptRecordEntity createReceipt(EnrollmentOrderEntity order,
                                              CoursePackageEntity coursePackage,
                                              StudentEntity student,
                                              CourseProductEntity product,
                                              EnrollmentCreateRequest request) {
        ReceiptRecordEntity receiptRecord = new ReceiptRecordEntity();
        receiptRecord.setReceiptNo(BusinessNoGenerator.generateReceiptNo());
        receiptRecord.setEnrollmentOrderId(order.getId());
        receiptRecord.setStudentId(student.getId());
        receiptRecord.setTemplateCode(StringUtils.hasText(request.getTemplateCode()) ? request.getTemplateCode() : DEFAULT_RECEIPT_TEMPLATE);
        receiptRecord.setReceiptPayload(buildReceiptPayload(order, coursePackage, student, product, request));
        receiptRecord.setIssuedAt(AppTime.nowLocalDateTime());
        receiptRecord.setIssuedBy(request.getCashierUserId());
        receiptRecordMapper.insert(receiptRecord);
        return receiptRecord;
    }

    private String buildReceiptPayload(EnrollmentOrderEntity order,
                                       CoursePackageEntity coursePackage,
                                       StudentEntity student,
                                       CourseProductEntity product,
                                       EnrollmentCreateRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enrollmentNo", order.getEnrollmentNo());
        payload.put("packageNo", coursePackage.getPackageNo());
        payload.put("studentNo", student.getStudentNo());
        payload.put("studentName", student.getStudentName());
        payload.put("courseProductCode", product.getProductCode());
        payload.put("courseProductName", product.getProductName());
        payload.put("packageName", coursePackage.getPackageName());
        payload.put("handledByName", request.getHandledByName());
        payload.put("currencyCode", order.getCurrencyCode());
        payload.put("originalAmount", order.getOriginalAmount());
        payload.put("discountAmount", order.getDiscountAmount());
        payload.put("receivedAmount", order.getReceivedAmount());
        payload.put("totalUnits", coursePackage.getTotalUnits());
        payload.put("defaultLessonUnits", coursePackage.getDefaultLessonUnits());
        return jsonHelper.toJson(payload);
    }

    private String resolvePaymentStatus(BigDecimal originalAmount, BigDecimal discountAmount, BigDecimal receivedAmount) {
        BigDecimal dueAmount = originalAmount.subtract(discountAmount);
        if (dueAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return "PAID";
        }
        if (receivedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return "UNPAID";
        }
        if (receivedAmount.compareTo(dueAmount) >= 0) {
            return "PAID";
        }
        return "PARTIAL";
    }

    private EnrollmentOrderEntity requireOrder(Long id) {
        EnrollmentOrderEntity order = enrollmentOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("ENROLLMENT_ORDER_NOT_FOUND", "报名单不存在。");
        }
        return order;
    }

    private StudentEntity requireStudent(Long studentId) {
        StudentEntity student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException("STUDENT_NOT_FOUND", "学生不存在。");
        }
        return student;
    }

    private CourseProductEntity requireCourseProduct(Long courseProductId) {
        CourseProductEntity product = courseProductMapper.selectById(courseProductId);
        if (product == null) {
            throw new BusinessException("COURSE_PRODUCT_NOT_FOUND", "课程产品不存在。");
        }
        return product;
    }

    private TeacherEntity requireTeacher(Long teacherId) {
        TeacherEntity teacher = teacherMapper.selectById(teacherId);
        if (teacher == null) {
            throw new BusinessException("TEACHER_NOT_FOUND", "老师不存在。");
        }
        return teacher;
    }

    private Map<Long, StudentEntity> listStudentsByIds(Set<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return studentMapper.selectBatchIds(studentIds)
                .stream()
                .collect(Collectors.toMap(StudentEntity::getId, item -> item));
    }

    private List<CoursePackageEntity> listPackagesByOrderIds(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Collections.emptyList();
        }
        return coursePackageMapper.selectList(new LambdaQueryWrapper<CoursePackageEntity>()
                .in(CoursePackageEntity::getEnrollmentOrderId, orderIds)
                .orderByDesc(CoursePackageEntity::getCreatedAt));
    }

    private List<PaymentRecordEntity> listPaymentsByOrderId(Long orderId) {
        return paymentRecordMapper.selectList(new LambdaQueryWrapper<PaymentRecordEntity>()
                .eq(PaymentRecordEntity::getEnrollmentOrderId, orderId)
                .orderByDesc(PaymentRecordEntity::getPaidAt));
    }

    private List<ReceiptRecordEntity> listReceiptsByOrderIds(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Collections.emptyList();
        }
        return receiptRecordMapper.selectList(new LambdaQueryWrapper<ReceiptRecordEntity>()
                .in(ReceiptRecordEntity::getEnrollmentOrderId, orderIds)
                .orderByDesc(ReceiptRecordEntity::getIssuedAt));
    }

    private Map<Long, String> buildPrimaryTeacherNameMap(List<Long> packageIds) {
        if (packageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PackageTeacherAssignmentEntity> assignments = packageTeacherAssignmentMapper.selectList(new LambdaQueryWrapper<PackageTeacherAssignmentEntity>()
                .in(PackageTeacherAssignmentEntity::getPackageId, packageIds)
                .eq(PackageTeacherAssignmentEntity::getAssignmentStatus, "ACTIVE")
                .eq(PackageTeacherAssignmentEntity::getIsPrimary, 1)
                .orderByDesc(PackageTeacherAssignmentEntity::getEffectiveFrom));
        if (assignments.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, TeacherEntity> teacherMap = teacherMapper.selectBatchIds(assignments.stream().map(PackageTeacherAssignmentEntity::getTeacherId).toList())
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

    private EnrollmentOrderListItemVO toOrderListItem(EnrollmentOrderEntity order, StudentEntity student, CoursePackageEntity firstPackage, CourseProductEntity product, int packageCount) {
        return EnrollmentOrderListItemVO.builder()
                .id(order.getId())
                .enrollmentNo(order.getEnrollmentNo())
                .studentId(order.getStudentId())
                .studentName(student == null ? null : student.getStudentName())
                .courseProductId(firstPackage == null ? null : firstPackage.getCourseProductId())
                .courseProductName(product == null ? null : product.getProductName())
                .handledByName(order.getHandledByName())
                .currencyCode(order.getCurrencyCode())
                .originalAmount(order.getOriginalAmount())
                .discountAmount(order.getDiscountAmount())
                .receivedAmount(order.getReceivedAmount())
                .paymentStatus(order.getPaymentStatus())
                .orderStatus(order.getOrderStatus())
                .packageCount(packageCount)
                .createdAt(order.getCreatedAt())
                .build();
    }

    private EnrollmentOrderListItemVO toClassOrderListItem(EnrollmentOrderEntity order, StudentEntity student, ClassGroupEntity clz) {
        return EnrollmentOrderListItemVO.builder()
                .id(order.getId())
                .enrollmentNo(order.getEnrollmentNo())
                .studentId(order.getStudentId())
                .studentName(student == null ? null : student.getStudentName())
                .classId(order.getClassId())
                .className(clz == null ? null : clz.getClassName())
                .handledByName(order.getHandledByName())
                .currencyCode(order.getCurrencyCode())
                .originalAmount(order.getOriginalAmount())
                .discountAmount(order.getDiscountAmount())
                .receivedAmount(order.getReceivedAmount())
                .paymentStatus(order.getPaymentStatus())
                .orderStatus(order.getOrderStatus())
                .packageCount(0)
                .createdAt(order.getCreatedAt())
                .build();
    }

    private CoursePackageListItemVO toPackageListItem(CoursePackageEntity item, StudentEntity student, String primaryTeacherName, CourseProductEntity product) {
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

    private PaymentRecordVO toPaymentVO(PaymentRecordEntity item) {
        return PaymentRecordVO.builder()
                .id(item.getId())
                .paymentNo(item.getPaymentNo())
                .paymentMethod(item.getPaymentMethod())
                .paymentStatus(item.getPaymentStatus())
                .amount(item.getAmount())
                .paidAt(item.getPaidAt())
                .handledByName(item.getHandledByName())
                .remark(item.getRemark())
                .build();
    }

    private ReceiptRecordVO toReceiptVO(ReceiptRecordEntity item, String studentName) {
        return ReceiptRecordVO.builder()
                .id(item.getId())
                .receiptNo(item.getReceiptNo())
                .enrollmentOrderId(item.getEnrollmentOrderId())
                .studentId(item.getStudentId())
                .studentName(studentName)
                .templateCode(item.getTemplateCode())
                .fileUrl(item.getFileUrl())
                .issuedAt(item.getIssuedAt())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(Long id, EnrollmentOrderUpdateRequest request) {
        EnrollmentOrderEntity order = requireOrder(id);

        // Validate status transition
        if (StringUtils.hasText(request.getOrderStatus())) {
            validateStatusTransition(order.getOrderStatus(), request.getOrderStatus());
            order.setOrderStatus(request.getOrderStatus());
        }

        if (request.getCashierUserId() != null) {
            order.setCashierUserId(request.getCashierUserId());
        }

        if (request.getRemark() != null) {
            order.setRemark(request.getRemark());
        }

        enrollmentOrderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long id) {
        EnrollmentOrderEntity order = requireOrder(id);

        // Validate cancellation
        if ("CANCELLED".equals(order.getOrderStatus())) {
            throw new BusinessException("ORDER_ALREADY_CANCELLED", "Order is already cancelled.");
        }

        if (order.getClassId() != null) {
            // Class order: soft-delete class_enrollment so re-enrollment is possible
            ClassEnrollmentEntity enrollment = classEnrollmentMapper.selectOne(
                    new LambdaQueryWrapper<ClassEnrollmentEntity>()
                            .eq(ClassEnrollmentEntity::getClassId, order.getClassId())
                            .eq(ClassEnrollmentEntity::getStudentId, order.getStudentId())
                            .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                            .eq(ClassEnrollmentEntity::getIsDeleted, 0)
            );
            if (enrollment != null) {
                classEnrollmentMapper.update(
                        new LambdaUpdateWrapper<ClassEnrollmentEntity>()
                                .eq(ClassEnrollmentEntity::getId, enrollment.getId())
                                .set(ClassEnrollmentEntity::getStatus, "WITHDRAWN")
                                .set(ClassEnrollmentEntity::getIsDeleted, 1)
                );
            }
        } else {
            // 1v1 order: check for active course packages
            List<CoursePackageEntity> activePackages = coursePackageMapper.selectList(
                    new LambdaQueryWrapper<CoursePackageEntity>()
                            .eq(CoursePackageEntity::getEnrollmentOrderId, id)
                            .ne(CoursePackageEntity::getPackageStatus, "CLOSED")
            );
            if (!activePackages.isEmpty()) {
                throw new BusinessException("CANNOT_CANCEL_ORDER_WITH_ACTIVE_PACKAGES", "Cannot cancel order with active packages.");
            }
        }

        order.setOrderStatus("CANCELLED");
        enrollmentOrderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrder(Long id) {
        EnrollmentOrderEntity order = requireOrder(id);

        // Soft delete the order
        enrollmentOrderMapper.deleteById(id);

        // Note: Related course_packages, payment_records, and receipt_records
        // will be soft deleted via database cascade or should be handled explicitly
        // For now, they remain but orphaned. Consider adding explicit handling if needed.
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createClassEnrollmentOrder(ClassEnrollmentCreateRequest request) {
        // Validate student
        StudentEntity student = requireStudent(request.getStudentId());

        // Validate class
        ClassGroupEntity clz = classGroupMapper.selectById(request.getClassId());
        if (clz == null || !"ACTIVE".equals(clz.getStatus())) {
            throw new BusinessException("CLASS_NOT_FOUND", "Class not found or not active.");
        }

        // Check duplicate enrollment (only active enrollments, not withdrawn)
        Long existingCount = classEnrollmentMapper.selectCount(
                new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getClassId, request.getClassId())
                        .eq(ClassEnrollmentEntity::getStudentId, request.getStudentId())
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0)
        );
        if (existingCount > 0) {
            throw new BusinessException("ALREADY_ENROLLED", "Student is already enrolled in this class.");
        }

        // Validate amounts
        BigDecimal discountedAmount = request.getOriginalAmount().subtract(request.getDiscountAmount());
        if (discountedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("DISCOUNT_EXCEEDS_AMOUNT", "Discount cannot exceed original amount.");
        }
        if (request.getReceivedAmount().compareTo(discountedAmount) > 0) {
            throw new BusinessException("RECEIVED_EXCEEDS_AMOUNT", "Received amount cannot exceed amount due.");
        }

        // Create enrollment_order
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setEnrollmentNo(BusinessNoGenerator.generateEnrollmentNo());
        order.setStudentId(student.getId());
        order.setClassId(request.getClassId());
        order.setOrderStatus("CONFIRMED");
        order.setHandledByName(request.getHandledByName());
        order.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode() : DEFAULT_CURRENCY);
        order.setOriginalAmount(request.getOriginalAmount());
        order.setDiscountAmount(request.getDiscountAmount());
        order.setReceivedAmount(request.getReceivedAmount());
        order.setPaymentStatus(resolvePaymentStatus(request.getOriginalAmount(), request.getDiscountAmount(), request.getReceivedAmount()));
        order.setRemark(request.getRemark());
        enrollmentOrderMapper.insert(order);

        // Create class_enrollment
        ClassEnrollmentEntity enrollment = new ClassEnrollmentEntity();
        enrollment.setClassId(request.getClassId());
        enrollment.setStudentId(student.getId());
        enrollment.setEnrollmentDate(java.time.LocalDate.parse(request.getEnrollmentDate()));
        enrollment.setExpiryDate(java.time.LocalDate.parse(request.getExpiryDate()));
        enrollment.setStatus("ACTIVE");
        classEnrollmentMapper.insert(enrollment);

        // Create payment record if received amount > 0
        if (request.getReceivedAmount().compareTo(BigDecimal.ZERO) > 0) {
            PaymentRecordEntity paymentRecord = new PaymentRecordEntity();
            paymentRecord.setEnrollmentOrderId(order.getId());
            paymentRecord.setPaymentNo(BusinessNoGenerator.generatePaymentNo());
            paymentRecord.setPaymentMethod("CASH");
            paymentRecord.setPaymentStatus(order.getPaymentStatus());
            paymentRecord.setAmount(request.getReceivedAmount());
            paymentRecord.setPaidAt(AppTime.nowLocalDateTime());
            paymentRecord.setHandledByName(request.getHandledByName());
            paymentRecord.setRemark(request.getRemark());
            paymentRecordMapper.insert(paymentRecord);
        }
    }

    /**
     * Validate status transition rules.
     */
    private void validateStatusTransition(String currentStatus, String newStatus) {
        // Define valid transitions
        Map<String, Set<String>> validTransitions = Map.of(
                "DRAFT", Set.of("CONFIRMED", "CANCELLED"),
                "CONFIRMED", Set.of("CANCELLED"),
                "CANCELLED", Set.of()
        );

        Set<String> allowed = validTransitions.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(newStatus)) {
            throw new BusinessException("INVALID_STATUS_TRANSITION",
                    String.format("不允许的状态转换: %s -> %s", currentStatus, newStatus));
        }
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
}
