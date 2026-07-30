package com.classdeduction.system.modules.enrollment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.common.util.UnitConverter;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.CourseProductScheduleSlotEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductScheduleSlotMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.dto.ClassEnrollmentCreateRequest;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentCreateRequest;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderPageQuery;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderUpdateRequest;
import com.classdeduction.system.modules.enrollment.dto.RefundRequest;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.PackageTeacherAssignmentEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentMethodEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentRecordEntity;
import com.classdeduction.system.modules.enrollment.entity.ReceiptRecordEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentMethodMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentRecordMapper;
import com.classdeduction.system.modules.enrollment.mapper.ReceiptRecordMapper;
import com.classdeduction.system.modules.enrollment.service.EnrollmentOrderService;
import com.classdeduction.system.modules.enrollment.util.BusinessNoGenerator;
import com.classdeduction.system.modules.enrollment.vo.CoursePackageListItemVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentCreateResultVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentOrderDetailVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentOrderItemVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentOrderListItemVO;
import com.classdeduction.system.modules.enrollment.vo.PackageTeacherAssignmentVO;
import com.classdeduction.system.modules.enrollment.vo.PaymentRecordVO;
import com.classdeduction.system.modules.enrollment.vo.ReceiptRecordVO;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 报名服务：负责报名开单，并同步生成支付记录、课时包、主老师分配和收据。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EnrollmentOrderServiceImpl implements EnrollmentOrderService {

    private static final String DEFAULT_CURRENCY = "USD";
    private static final String DEFAULT_RECEIPT_TEMPLATE = "DEFAULT_RECEIPT";
    private static final String REFUND_RECEIPT_TEMPLATE = "REFUND_RECEIPT";
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final EnrollmentOrderMapper enrollmentOrderMapper;
    private final EnrollmentOrderItemMapper enrollmentOrderItemMapper;
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
    private final LessonRecordMapper lessonRecordMapper;
    private final ClassGroupMapper classGroupMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;
    private final CourseProductScheduleSlotMapper courseProductScheduleSlotMapper;
    private final PaymentMethodMapper paymentMethodMapper;

    /**
     * Initialize the business number sequence from the database to avoid
     * collisions after a server restart (in-memory AtomicInteger resets to 0).
     */
    @PostConstruct
    void initSequence() {
        String todayStr = LocalDate.now(AppTime.APP_ZONE_ID).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // Init enrollment order sequence (EO prefix)
        LambdaQueryWrapper<EnrollmentOrderEntity> eoWrapper = new LambdaQueryWrapper<EnrollmentOrderEntity>()
                .likeRight(EnrollmentOrderEntity::getEnrollmentNo, "EO" + todayStr)
                .orderByDesc(EnrollmentOrderEntity::getEnrollmentNo)
                .last("LIMIT 1");
        EnrollmentOrderEntity lastEO = enrollmentOrderMapper.selectOne(eoWrapper);
        if (lastEO != null) {
            String no = lastEO.getEnrollmentNo();
            int seq = Integer.parseInt(no.substring(no.length() - 4));
            BusinessNoGenerator.initSequence("EO", seq);
        }
        // Init course package sequence (PK prefix)
        LambdaQueryWrapper<CoursePackageEntity> pkWrapper = new LambdaQueryWrapper<CoursePackageEntity>()
                .likeRight(CoursePackageEntity::getPackageNo, "PK" + todayStr)
                .orderByDesc(CoursePackageEntity::getPackageNo)
                .last("LIMIT 1");
        CoursePackageEntity lastPK = coursePackageMapper.selectOne(pkWrapper);
        if (lastPK != null) {
            String no = lastPK.getPackageNo();
            int seq = Integer.parseInt(no.substring(no.length() - 4));
            BusinessNoGenerator.initSequence("PK", seq);
        }
        // Init payment record sequence (PMT prefix)
        LambdaQueryWrapper<PaymentRecordEntity> pmtWrapper = new LambdaQueryWrapper<PaymentRecordEntity>()
                .likeRight(PaymentRecordEntity::getPaymentNo, "PMT" + todayStr)
                .orderByDesc(PaymentRecordEntity::getPaymentNo)
                .last("LIMIT 1");
        PaymentRecordEntity lastPMT = paymentRecordMapper.selectOne(pmtWrapper);
        if (lastPMT != null) {
            String no = lastPMT.getPaymentNo();
            int seq = Integer.parseInt(no.substring(no.length() - 4));
            BusinessNoGenerator.initSequence("PMT", seq);
        }
        // Init receipt record sequence (RCP prefix)
        LambdaQueryWrapper<ReceiptRecordEntity> rcpWrapper = new LambdaQueryWrapper<ReceiptRecordEntity>()
                .likeRight(ReceiptRecordEntity::getReceiptNo, "RCP" + todayStr)
                .orderByDesc(ReceiptRecordEntity::getReceiptNo)
                .last("LIMIT 1");
        ReceiptRecordEntity lastRCP = receiptRecordMapper.selectOne(rcpWrapper);
        if (lastRCP != null) {
            String no = lastRCP.getReceiptNo();
            int seq = Integer.parseInt(no.substring(no.length() - 4));
            BusinessNoGenerator.initSequence("RCP", seq);
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
        List<Long> orderIds = result.getRecords().stream()
                .map(EnrollmentOrderEntity::getId)
                .toList();
        Map<Long, List<EnrollmentOrderItemVO>> itemsByOrderId = orderIds.isEmpty()
                ? Collections.emptyMap()
                : enrollmentOrderItemMapper.selectList(new LambdaQueryWrapper<EnrollmentOrderItemEntity>()
                        .in(EnrollmentOrderItemEntity::getEnrollmentOrderId, orderIds)
                        .orderByAsc(EnrollmentOrderItemEntity::getLineNo)
                        .orderByAsc(EnrollmentOrderItemEntity::getId))
                .stream()
                .collect(Collectors.groupingBy(EnrollmentOrderItemEntity::getEnrollmentOrderId,
                        Collectors.mapping(this::toOrderItemVO, Collectors.toList())));

        List<EnrollmentOrderListItemVO> list = result.getRecords().stream()
                .map(order -> {
                    StudentEntity student = studentMap.get(order.getStudentId());
                    List<EnrollmentOrderItemVO> items = itemsByOrderId.getOrDefault(order.getId(), Collections.emptyList());
                    if (order.getClassId() != null) {
                        // Class order: no course_package, show class name instead
                        ClassGroupEntity clz = classMap.get(order.getClassId());
                        return toClassOrderListItem(order, student, clz, items);
                    }
                    CoursePackageEntity firstPackage = firstPackageMap.get(order.getId());
                    CourseProductEntity product = firstPackage == null ? null : productMap.get(firstPackage.getCourseProductId());
                    return toOrderListItem(order, student, firstPackage, product, packageCountMap.getOrDefault(order.getId(), 0), items);
                })
                .toList();

        return PageResponse.of(list, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public EnrollmentOrderDetailVO getOrder(Long id) {
        EnrollmentOrderEntity order = requireOrder(id);
        StudentEntity student = requireStudent(order.getStudentId());
        List<EnrollmentOrderItemEntity> orderItems = listOrderItemsByOrderId(id);
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
                .campusId(order.getCampusId())
                .campusNameSnapshot(order.getCampusNameSnapshot())
                .orderStatus(order.getOrderStatus())
                .cashierUserId(order.getCashierUserId())
                .handledByName(order.getHandledByName())
                .signupDate(order.getSignupDate())
                .periodStartDate(order.getPeriodStartDate())
                .periodEndDate(order.getPeriodEndDate())
                .studentNameSnapshot(order.getStudentNameSnapshot())
                .gradeSnapshot(order.getGradeSnapshot())
                .schoolSnapshot(order.getSchoolSnapshot())
                .parentPhoneSnapshot(order.getParentPhoneSnapshot())
                .parentPhone2Snapshot(order.getParentPhone2Snapshot())
                .pickupRequiredSnapshot(Boolean.TRUE.equals(order.getPickupRequiredSnapshot()))
                .pickupAddressSnapshot(order.getPickupAddressSnapshot())
                .dropoffRequiredSnapshot(Boolean.TRUE.equals(order.getDropoffRequiredSnapshot()))
                .dropoffAddressSnapshot(order.getDropoffAddressSnapshot())
                .currencyCode(order.getCurrencyCode())
                .tuitionSubtotalAmount(order.getTuitionSubtotalAmount())
                .pickupFeeAmount(order.getPickupFeeAmount())
                .dropoffFeeAmount(order.getDropoffFeeAmount())
                .extraFeeAmount(order.getExtraFeeAmount())
                .extraFeeDetail(order.getExtraFeeDetail())
                .originalAmount(order.getOriginalAmount())
                .discountAmount(order.getDiscountAmount())
                .discountDetail(order.getDiscountDetail())
                .receivedAmount(order.getReceivedAmount())
                .amountDue(order.getAmountDue())
                .balanceAmount(order.getBalanceAmount())
                .paymentStatus(order.getPaymentStatus())
                .remark(order.getRemark())
                .items(orderItems.stream().map(this::toOrderItemVO).toList())
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
        validateOneOnOneProduct(product);
        validateOrderItemExists(request.getEnrollmentOrderItemId());
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
        applyAmountSnapshot(order, request.getOriginalAmount(), ZERO_AMOUNT, ZERO_AMOUNT,
                request.getDiscountAmount(), request.getReceivedAmount());
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

    private void validateOneOnOneProduct(CourseProductEntity product) {
        if (!"ACTIVE".equals(product.getStatus())) {
            throw new BusinessException("COURSE_PRODUCT_INACTIVE", "Course product is not active.");
        }
        if (!"ONE_ON_ONE".equals(product.getTeachingMode())) {
            throw new BusinessException("INVALID_COURSE_PRODUCT_TYPE", "Only 1:1 course products can be used for new enrollment.");
        }
    }

    private void validatePackageUnits(EnrollmentCreateRequest request) {
        if (request.getTotalUnits() != null && request.getTotalUnits() <= 0) {
            throw new BusinessException("INVALID_TOTAL_UNITS", "Total units must be greater than 0.");
        }
        if (request.getDefaultLessonUnits() != null && request.getDefaultLessonUnits() <= 0) {
            throw new BusinessException("INVALID_LESSON_UNITS", "Default lesson units must be greater than 0.");
        }
        if (request.getTotalUnits() != null
                && request.getDefaultLessonUnits() != null
                && request.getDefaultLessonUnits() > request.getTotalUnits()) {
            throw new BusinessException("LESSON_UNITS_EXCEED_TOTAL", "Default lesson units cannot exceed total units.");
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
            throw new BusinessException("INVALID_LESSON_UNITS", "Default lesson units must be greater than 0.");
        }
        if (totalUnits <= 0) {
            throw new BusinessException("INVALID_TOTAL_UNITS", "Total units must be greater than 0.");
        }
        // totalUnits 从前端传入的是小时数，内部存储统一转为半课时单位（1单位=0.5h）
        int halfHourUnits = totalUnits * 2;
        if (defaultLessonUnits > halfHourUnits) {
            throw new BusinessException("LESSON_UNITS_EXCEED_TOTAL", "Default lesson units cannot exceed total units.");
        }

        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setPackageNo(BusinessNoGenerator.generatePackageNo());
        coursePackage.setEnrollmentOrderId(order.getId());
        coursePackage.setEnrollmentOrderItemId(request.getEnrollmentOrderItemId());
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

        var studentOccurrences = studentId == null ? Collections.<LessonOccurrenceEntity>emptyList()
                : lessonOccurrenceMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LessonOccurrenceEntity>()
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
            if (studentId != null && hasOverlap(candidate, studentOccurrences)) {
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
            throw new BusinessException("ENROLLMENT_ORDER_NOT_FOUND", "Enrollment order not found.");
        }
        return order;
    }

    private StudentEntity requireStudent(Long studentId) {
        StudentEntity student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException("STUDENT_NOT_FOUND", "Student not found.");
        }
        return student;
    }

    private CourseProductEntity requireCourseProduct(Long courseProductId) {
        CourseProductEntity product = courseProductMapper.selectById(courseProductId);
        if (product == null) {
            throw new BusinessException("COURSE_PRODUCT_NOT_FOUND", "Course product not found.");
        }
        return product;
    }

    private TeacherEntity requireTeacher(Long teacherId) {
        TeacherEntity teacher = teacherMapper.selectById(teacherId);
        if (teacher == null) {
            throw new BusinessException("TEACHER_NOT_FOUND", "Teacher not found.");
        }
        return teacher;
    }

    private void validateOrderItemExists(Long orderItemId) {
        if (orderItemId == null) {
            return;
        }
        if (enrollmentOrderItemMapper.selectById(orderItemId) == null) {
            throw new BusinessException("ENROLLMENT_ORDER_ITEM_NOT_FOUND", "Enrollment order item not found.");
        }
    }

    private void applyAmountSnapshot(EnrollmentOrderEntity order,
                                     BigDecimal tuitionSubtotal,
                                     BigDecimal pickupFee,
                                     BigDecimal extraFee,
                                     BigDecimal discount,
                                     BigDecimal received) {
        BigDecimal subtotal = defaultAmount(tuitionSubtotal);
        BigDecimal pickup = defaultAmount(pickupFee);
        BigDecimal extra = defaultAmount(extraFee);
        BigDecimal discountAmount = defaultAmount(discount);
        BigDecimal receivedAmount = defaultAmount(received);
        BigDecimal amountDue = subtotal.add(pickup).add(extra).subtract(discountAmount);
        if (amountDue.compareTo(BigDecimal.ZERO) < 0) {
            amountDue = ZERO_AMOUNT;
        }
        order.setTuitionSubtotalAmount(subtotal);
        order.setPickupFeeAmount(pickup);
        order.setExtraFeeAmount(extra);
        order.setAmountDue(amountDue);
        order.setBalanceAmount(amountDue.subtract(receivedAmount));
    }

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount == null ? ZERO_AMOUNT : amount;
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

    private List<EnrollmentOrderItemEntity> listOrderItemsByOrderId(Long orderId) {
        return enrollmentOrderItemMapper.selectList(new LambdaQueryWrapper<EnrollmentOrderItemEntity>()
                .eq(EnrollmentOrderItemEntity::getEnrollmentOrderId, orderId)
                .orderByAsc(EnrollmentOrderItemEntity::getLineNo)
                .orderByAsc(EnrollmentOrderItemEntity::getId));
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

    private EnrollmentOrderListItemVO toOrderListItem(EnrollmentOrderEntity order,
                                                      StudentEntity student,
                                                      CoursePackageEntity firstPackage,
                                                      CourseProductEntity product,
                                                      int packageCount,
                                                      List<EnrollmentOrderItemVO> items) {
        return EnrollmentOrderListItemVO.builder()
                .id(order.getId())
                .enrollmentNo(order.getEnrollmentNo())
                .studentId(order.getStudentId())
                .studentName(student == null ? null : student.getStudentName())
                .courseProductId(firstPackage == null ? null : firstPackage.getCourseProductId())
                .courseProductName(product == null ? null : product.getProductName())
                .campusId(order.getCampusId())
                .campusNameSnapshot(order.getCampusNameSnapshot())
                .handledByName(order.getHandledByName())
                .signupDate(order.getSignupDate())
                .periodStartDate(order.getPeriodStartDate())
                .periodEndDate(order.getPeriodEndDate())
                .studentNameSnapshot(order.getStudentNameSnapshot())
                .gradeSnapshot(order.getGradeSnapshot())
                .schoolSnapshot(order.getSchoolSnapshot())
                .parentPhoneSnapshot(order.getParentPhoneSnapshot())
                .parentPhone2Snapshot(order.getParentPhone2Snapshot())
                .pickupRequiredSnapshot(Boolean.TRUE.equals(order.getPickupRequiredSnapshot()))
                .pickupAddressSnapshot(order.getPickupAddressSnapshot())
                .dropoffRequiredSnapshot(Boolean.TRUE.equals(order.getDropoffRequiredSnapshot()))
                .dropoffAddressSnapshot(order.getDropoffAddressSnapshot())
                .currencyCode(order.getCurrencyCode())
                .tuitionSubtotalAmount(order.getTuitionSubtotalAmount())
                .pickupFeeAmount(order.getPickupFeeAmount())
                .dropoffFeeAmount(order.getDropoffFeeAmount())
                .extraFeeAmount(order.getExtraFeeAmount())
                .originalAmount(order.getOriginalAmount())
                .discountAmount(order.getDiscountAmount())
                .receivedAmount(order.getReceivedAmount())
                .amountDue(order.getAmountDue())
                .balanceAmount(order.getBalanceAmount())
                .paymentStatus(order.getPaymentStatus())
                .orderStatus(order.getOrderStatus())
                .packageCount(packageCount)
                .items(items)
                .createdAt(order.getCreatedAt())
                .build();
    }

    private EnrollmentOrderListItemVO toClassOrderListItem(EnrollmentOrderEntity order,
                                                           StudentEntity student,
                                                           ClassGroupEntity clz,
                                                           List<EnrollmentOrderItemVO> items) {
        return EnrollmentOrderListItemVO.builder()
                .id(order.getId())
                .enrollmentNo(order.getEnrollmentNo())
                .studentId(order.getStudentId())
                .studentName(student == null ? null : student.getStudentName())
                .classId(order.getClassId())
                .className(clz == null ? null : clz.getClassName())
                .campusId(order.getCampusId())
                .campusNameSnapshot(order.getCampusNameSnapshot())
                .handledByName(order.getHandledByName())
                .signupDate(order.getSignupDate())
                .periodStartDate(order.getPeriodStartDate())
                .periodEndDate(order.getPeriodEndDate())
                .studentNameSnapshot(order.getStudentNameSnapshot())
                .gradeSnapshot(order.getGradeSnapshot())
                .schoolSnapshot(order.getSchoolSnapshot())
                .parentPhoneSnapshot(order.getParentPhoneSnapshot())
                .parentPhone2Snapshot(order.getParentPhone2Snapshot())
                .pickupRequiredSnapshot(Boolean.TRUE.equals(order.getPickupRequiredSnapshot()))
                .pickupAddressSnapshot(order.getPickupAddressSnapshot())
                .dropoffRequiredSnapshot(Boolean.TRUE.equals(order.getDropoffRequiredSnapshot()))
                .dropoffAddressSnapshot(order.getDropoffAddressSnapshot())
                .currencyCode(order.getCurrencyCode())
                .tuitionSubtotalAmount(order.getTuitionSubtotalAmount())
                .pickupFeeAmount(order.getPickupFeeAmount())
                .dropoffFeeAmount(order.getDropoffFeeAmount())
                .extraFeeAmount(order.getExtraFeeAmount())
                .originalAmount(order.getOriginalAmount())
                .discountAmount(order.getDiscountAmount())
                .receivedAmount(order.getReceivedAmount())
                .amountDue(order.getAmountDue())
                .balanceAmount(order.getBalanceAmount())
                .paymentStatus(order.getPaymentStatus())
                .orderStatus(order.getOrderStatus())
                .packageCount(0)
                .items(items)
                .createdAt(order.getCreatedAt())
                .build();
    }

    private EnrollmentOrderItemVO toOrderItemVO(EnrollmentOrderItemEntity item) {
        return EnrollmentOrderItemVO.builder()
                .id(item.getId())
                .enrollmentOrderId(item.getEnrollmentOrderId())
                .lineNo(item.getLineNo())
                .registerClass(item.getRegisterClass())
                .courseType(item.getCourseType())
                .courseProductId(item.getCourseProductId())
                .coursePriceOptionId(item.getCoursePriceOptionId())
                .classId(item.getClassId())
                .detail(item.getDetail())
                .gradeBand(item.getGradeBand())
                .periodLabel(item.getPeriodLabel())
                .timeSlotLabel(item.getTimeSlotLabel())
                .priceAmount(item.getPriceAmount())
                .originalPriceAmount(item.getOriginalPriceAmount())
                .manualPriceOverride(Boolean.TRUE.equals(item.getManualPriceOverride()))
                .oneOnOneHours(item.getOneOnOneHours())
                .oneOnOneUnits(item.getOneOnOneUnits())
                .initialLessonStartAt(item.getInitialLessonStartAt())
                .initialLessonEndAt(item.getInitialLessonEndAt())
                .teacherId(item.getTeacherId())
                .itemStatus(item.getItemStatus())
                .remark(item.getRemark())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private CoursePackageListItemVO toPackageListItem(CoursePackageEntity item, StudentEntity student, String primaryTeacherName, CourseProductEntity product) {
        return CoursePackageListItemVO.builder()
                .id(item.getId())
                .packageNo(item.getPackageNo())
                .enrollmentOrderId(item.getEnrollmentOrderId())
                .enrollmentOrderItemId(item.getEnrollmentOrderItemId())
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

    private PaymentRecordVO toPaymentVO(PaymentRecordEntity item) {
        return PaymentRecordVO.builder()
                .id(item.getId())
                .paymentNo(item.getPaymentNo())
                .paymentMethod(item.getPaymentMethod())
                .paymentType(item.getPaymentType())
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
                .receiptPayload(item.getReceiptPayload())
                .fileUrl(item.getFileUrl())
                .issuedAt(item.getIssuedAt())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(Long id, EnrollmentOrderUpdateRequest request) {
        EnrollmentOrderEntity order = requireOrder(id);

        // Order status must never be changed through the generic update:
        // cancellation goes through cancelOrder (paid-order guard, package close,
        // future-lesson cancel, class eligibility revoke) and refunds through refundOrder.
        if (StringUtils.hasText(request.getOrderStatus())
                && !request.getOrderStatus().equals(order.getOrderStatus())) {
            throw new BusinessException("ORDER_STATUS_UPDATE_NOT_ALLOWED",
                    "Order status cannot be changed here. Use cancel or refund instead.");
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

        if ("CANCELLED".equals(order.getOrderStatus())) {
            throw new BusinessException("ORDER_ALREADY_CANCELLED", "Order is already cancelled.");
        }
        if (defaultAmount(order.getReceivedAmount()).compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("PAID_ORDER_REQUIRES_REFUND",
                    "Orders with received payments cannot be cancelled directly. Use full refund or contact staff for manual review.");
        }

        cancelEnrollmentArtifacts(order);

        order.setOrderStatus("CANCELLED");
        int updated = enrollmentOrderMapper.updateById(order);
        if (updated != 1) {
            throw new BusinessException("ORDER_CONCURRENT_UPDATE",
                    "The order was updated by another request. Please retry.");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReceiptRecordVO refundOrder(Long id, RefundRequest request) {
        EnrollmentOrderEntity order = requireOrder(id);

        if ("REFUNDED".equals(order.getPaymentStatus())) {
            throw new BusinessException("ORDER_ALREADY_REFUNDED", "Order has already been refunded.");
        }
        BigDecimal received = order.getReceivedAmount() != null ? order.getReceivedAmount() : BigDecimal.ZERO;
        if (received.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("NO_PAYMENT_TO_REFUND", "There is no received payment to refund.");
        }
        BigDecimal refundAmount = defaultAmount(request.getRefundAmount());
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_REFUND_AMOUNT", "Refund amount must be greater than zero.");
        }
        if (refundAmount.compareTo(received) > 0) {
            throw new BusinessException("REFUND_AMOUNT_EXCEEDS_RECEIVED",
                    "Refund amount cannot exceed the currently received amount.");
        }
        requireActivePaymentMethod(request.getPaymentMethod());
        boolean fullRefund = refundAmount.compareTo(received) == 0;
        if (fullRefund) {
            validateFullRefundHasNoLessonRecords(order);
        }

        // Full refunds close enrollment artifacts. Partial refunds preserve courses and schedules.
        if (fullRefund && !"CANCELLED".equals(order.getOrderStatus())) {
            cancelEnrollmentArtifacts(order);
            order.setOrderStatus("CANCELLED");
        }

        // Refunds are immutable negative ledger entries; never overwrite payment history.
        PaymentRecordEntity refundRecord = new PaymentRecordEntity();
        refundRecord.setEnrollmentOrderId(order.getId());
        refundRecord.setPaymentNo(BusinessNoGenerator.generatePaymentNo());
        refundRecord.setPaymentMethod(request.getPaymentMethod());
        refundRecord.setPaymentType("REFUND");
        refundRecord.setPaymentStatus("REFUNDED");
        refundRecord.setAmount(refundAmount.negate());
        refundRecord.setPaidAt(AppTime.nowLocalDateTime());
        refundRecord.setHandledByUserId(request.getHandledByUserId());
        refundRecord.setHandledByName(request.getHandledByName());
        refundRecord.setRemark(request.getRemark());
        paymentRecordMapper.insert(refundRecord);

        BigDecimal remainingReceived = received.subtract(refundAmount);
        order.setReceivedAmount(remainingReceived);
        if (fullRefund) {
            // A fully refunded order is closed and has no collectible balance.
            order.setBalanceAmount(BigDecimal.ZERO);
            order.setPaymentStatus("REFUNDED");
        } else {
            BigDecimal amountDue = defaultAmount(order.getAmountDue());
            order.setBalanceAmount(amountDue.subtract(remainingReceived));
            order.setPaymentStatus(resolvePaymentStatus(
                    defaultAmount(order.getOriginalAmount()),
                    defaultAmount(order.getDiscountAmount()),
                    remainingReceived));
        }
        int updated = enrollmentOrderMapper.updateById(order);
        if (updated != 1) {
            throw new BusinessException("ORDER_CONCURRENT_UPDATE", "The order was updated by another request. Please retry.");
        }

        if (fullRefund) {
            List<CoursePackageEntity> packages = coursePackageMapper.selectList(
                    new LambdaQueryWrapper<CoursePackageEntity>()
                            .eq(CoursePackageEntity::getEnrollmentOrderId, order.getId()));
            for (CoursePackageEntity coursePackage : packages) {
                coursePackage.setReceivedAmount(BigDecimal.ZERO);
                coursePackageMapper.updateById(coursePackage);
            }
        }
        return createRefundReceipt(order, request, refundRecord, refundAmount);
    }

    private ReceiptRecordVO createRefundReceipt(EnrollmentOrderEntity order,
                                                RefundRequest request,
                                                PaymentRecordEntity refundRecord,
                                                BigDecimal refundAmount) {
        LocalDateTime issuedAt = AppTime.nowLocalDateTime();
        String receiptNo = BusinessNoGenerator.generateReceiptNo();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("receiptType", "REFUND");
        payload.put("receiptNo", receiptNo);
        payload.put("enrollmentOrderId", order.getId());
        payload.put("enrollmentNo", order.getEnrollmentNo());
        payload.put("studentId", order.getStudentId());
        payload.put("studentName", order.getStudentNameSnapshot());
        payload.put("currencyCode", order.getCurrencyCode());
        payload.put("issuedAt", issuedAt);
        payload.put("paymentAmount", refundAmount);
        payload.put("amountDue", order.getAmountDue());
        payload.put("receivedAmount", order.getReceivedAmount());
        payload.put("balanceAmount", order.getBalanceAmount());
        payload.put("paymentStatus", order.getPaymentStatus());
        Map<String, Object> refundPayment = new LinkedHashMap<>();
        refundPayment.put("paymentNo", refundRecord.getPaymentNo());
        refundPayment.put("paymentMethod", refundRecord.getPaymentMethod());
        refundPayment.put("amount", refundRecord.getAmount());
        refundPayment.put("paidAt", refundRecord.getPaidAt());
        refundPayment.put("handledByName", refundRecord.getHandledByName());
        refundPayment.put("remark", refundRecord.getRemark());
        payload.put("payments", List.of(refundPayment));

        ReceiptRecordEntity receipt = new ReceiptRecordEntity();
        receipt.setReceiptNo(receiptNo);
        receipt.setEnrollmentOrderId(order.getId());
        receipt.setStudentId(order.getStudentId());
        receipt.setTemplateCode(REFUND_RECEIPT_TEMPLATE);
        receipt.setReceiptPayload(jsonHelper.toJson(payload));
        receipt.setIssuedAt(issuedAt);
        receipt.setIssuedBy(request.getHandledByUserId());
        receiptRecordMapper.insert(receipt);

        return ReceiptRecordVO.builder()
                .id(receipt.getId())
                .receiptNo(receiptNo)
                .enrollmentOrderId(order.getId())
                .studentId(order.getStudentId())
                .studentName(order.getStudentNameSnapshot())
                .templateCode(REFUND_RECEIPT_TEMPLATE)
                .receiptPayload(receipt.getReceiptPayload())
                .issuedAt(issuedAt)
                .build();
    }

    private void requireActivePaymentMethod(String methodCode) {
        Long count = paymentMethodMapper.selectCount(new LambdaQueryWrapper<PaymentMethodEntity>()
                .eq(PaymentMethodEntity::getMethodCode, methodCode)
                .eq(PaymentMethodEntity::getStatus, "ACTIVE")
                .eq(PaymentMethodEntity::getIsDeleted, 0));
        if (count == null || count == 0) {
            throw new BusinessException("PAYMENT_METHOD_NOT_AVAILABLE",
                    "Payment method not found or inactive.");
        }
    }

    private void cancelEnrollmentArtifacts(EnrollmentOrderEntity order) {
        cancelOneOnOneEnrollment(order);
        cancelClassEnrollment(order);
    }

    private void cancelClassEnrollment(EnrollmentOrderEntity order) {
        List<ClassEnrollmentEntity> enrollments = new ArrayList<>();
        if (order.getClassId() != null) {
            // Legacy direct class orders do not have an order-item reference.
            enrollments.addAll(classEnrollmentMapper.selectList(
                    new LambdaQueryWrapper<ClassEnrollmentEntity>()
                            .eq(ClassEnrollmentEntity::getClassId, order.getClassId())
                            .eq(ClassEnrollmentEntity::getStudentId, order.getStudentId())
                            .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                            .eq(ClassEnrollmentEntity::getIsDeleted, 0)));
        }

        List<Long> orderItemIds = listOrderItemsByOrderId(order.getId()).stream()
                .map(EnrollmentOrderItemEntity::getId)
                .toList();
        if (!orderItemIds.isEmpty()) {
            enrollments.addAll(classEnrollmentMapper.selectList(
                    new LambdaQueryWrapper<ClassEnrollmentEntity>()
                            .in(ClassEnrollmentEntity::getEnrollmentOrderItemId, orderItemIds)
                            .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                            .eq(ClassEnrollmentEntity::getIsDeleted, 0)));
        }

        for (ClassEnrollmentEntity enrollment : enrollments.stream()
                .collect(Collectors.toMap(ClassEnrollmentEntity::getId, item -> item, (left, right) -> left))
                .values()) {
            classEnrollmentMapper.update(
                    new LambdaUpdateWrapper<ClassEnrollmentEntity>()
                            .eq(ClassEnrollmentEntity::getId, enrollment.getId())
                            .set(ClassEnrollmentEntity::getStatus, "WITHDRAWN")
                            .set(ClassEnrollmentEntity::getIsDeleted, 1)
            );
        }
    }

    private void cancelOneOnOneEnrollment(EnrollmentOrderEntity order) {
        List<CoursePackageEntity> packages = coursePackageMapper.selectList(
                new LambdaQueryWrapper<CoursePackageEntity>()
                        .eq(CoursePackageEntity::getEnrollmentOrderId, order.getId())
        );
        if (packages.isEmpty()) {
            return;
        }

        List<Long> packageIds = packages.stream().map(CoursePackageEntity::getId).toList();
        cancelFutureOccurrences(packageIds);
        deactivateRecurringSchedules(packageIds);

        // Closing the package prevents further deduction while preserving consumed lessons and payments.
        for (CoursePackageEntity coursePackage : packages) {
            if (!"CLOSED".equals(coursePackage.getPackageStatus())) {
                coursePackage.setPackageStatus("CLOSED");
                coursePackageMapper.updateById(coursePackage);
            }
        }
    }

    private void cancelFutureOccurrences(List<Long> packageIds) {
        lessonOccurrenceMapper.update(
                new UpdateWrapper<LessonOccurrenceEntity>()
                        .in("package_id", packageIds)
                        .in("planning_status", List.of("SCHEDULED", "ADJUSTED"))
                        .gt("planned_start_at", AppTime.nowLocalDateTime())
                        .set("planning_status", "CANCELLED")
        );
    }

    private void deactivateRecurringSchedules(List<Long> packageIds) {
        List<RecurringScheduleRuleEntity> rules = recurringScheduleRuleMapper.selectList(
                new QueryWrapper<RecurringScheduleRuleEntity>()
                        .in("package_id", packageIds)
                        .ne("rule_status", "INACTIVE")
        );
        if (rules.isEmpty()) {
            return;
        }

        List<Long> ruleIds = rules.stream().map(RecurringScheduleRuleEntity::getId).toList();
        recurringScheduleRuleMapper.update(
                new UpdateWrapper<RecurringScheduleRuleEntity>()
                        .in("id", ruleIds)
                        .set("rule_status", "INACTIVE")
        );
        recurringScheduleSlotMapper.update(
                new UpdateWrapper<RecurringScheduleSlotEntity>()
                        .in("rule_id", ruleIds)
                        .set("slot_status", "INACTIVE")
        );
    }

    private void validateFullRefundHasNoLessonRecords(EnrollmentOrderEntity order) {
        List<CoursePackageEntity> packages = coursePackageMapper.selectList(
                new LambdaQueryWrapper<CoursePackageEntity>()
                        .eq(CoursePackageEntity::getEnrollmentOrderId, order.getId()));
        List<Long> packageIds = packages.stream().map(CoursePackageEntity::getId).toList();
        if (!packageIds.isEmpty()) {
            Long lessonRecordCount = lessonRecordMapper.selectCount(new LambdaQueryWrapper<LessonRecordEntity>()
                    .in(LessonRecordEntity::getPackageId, packageIds));
            if (lessonRecordCount != null && lessonRecordCount > 0) {
                throw new BusinessException("FULL_REFUND_LESSON_RECORD_EXISTS",
                        "Full refund is unavailable after lessons are recorded. Contact staff for manual review.");
            }
        }

        List<Long> classIds = new ArrayList<>();
        if (order.getClassId() != null) {
            classIds.add(order.getClassId());
        }
        listOrderItemsByOrderId(order.getId()).stream()
                .map(EnrollmentOrderItemEntity::getClassId)
                .filter(Objects::nonNull)
                .forEach(classIds::add);
        if (classIds.isEmpty()) {
            return;
        }

        List<LessonOccurrenceEntity> occurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .select(LessonOccurrenceEntity::getId)
                        .in(LessonOccurrenceEntity::getClassGroupId, classIds.stream().distinct().toList()));
        if (occurrences.isEmpty()) {
            return;
        }
        Long lessonRecordCount = lessonRecordMapper.selectCount(new LambdaQueryWrapper<LessonRecordEntity>()
                .eq(LessonRecordEntity::getStudentId, order.getStudentId())
                .in(LessonRecordEntity::getOccurrenceId, occurrences.stream().map(LessonOccurrenceEntity::getId).toList()));
        if (lessonRecordCount != null && lessonRecordCount > 0) {
            throw new BusinessException("FULL_REFUND_LESSON_RECORD_EXISTS",
                    "Full refund is unavailable after lessons are recorded. Contact staff for manual review.");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrder(Long id) {
        EnrollmentOrderEntity order = requireOrder(id);
        validateOrderCanBeDeleted(order);

        // Deletion is reserved for mistaken unpaid orders. Release only future scheduling before hiding the order.
        if (!"CANCELLED".equals(order.getOrderStatus())) {
            if (order.getClassId() != null) {
                cancelClassEnrollment(order);
            } else {
                cancelOneOnOneEnrollment(order);
            }
        }
        enrollmentOrderMapper.deleteById(id);
    }

    private void validateOrderCanBeDeleted(EnrollmentOrderEntity order) {
        BigDecimal received = order.getReceivedAmount() != null ? order.getReceivedAmount() : BigDecimal.ZERO;
        if (received.compareTo(BigDecimal.ZERO) != 0 || !"UNPAID".equals(order.getPaymentStatus())) {
            throw new BusinessException("ORDER_DELETE_PAYMENT_EXISTS", "Only unpaid mistaken orders can be deleted. Use Refund for paid orders.");
        }

        Long paymentCount = paymentRecordMapper.selectCount(new LambdaQueryWrapper<PaymentRecordEntity>()
                .eq(PaymentRecordEntity::getEnrollmentOrderId, order.getId()));
        if (paymentCount != null && paymentCount > 0) {
            throw new BusinessException("ORDER_DELETE_PAYMENT_RECORD_EXISTS", "This order has payment records and cannot be deleted.");
        }

        Long receiptCount = receiptRecordMapper.selectCount(new LambdaQueryWrapper<ReceiptRecordEntity>()
                .eq(ReceiptRecordEntity::getEnrollmentOrderId, order.getId()));
        if (receiptCount != null && receiptCount > 0) {
            throw new BusinessException("ORDER_DELETE_RECEIPT_EXISTS", "This order has receipt records and cannot be deleted.");
        }

        if (order.getClassId() != null) {
            validateClassOrderHasNoLessonRecords(order);
            return;
        }
        validateOneOnOneOrderHasNoLessonActivity(order);
    }

    private void validateOneOnOneOrderHasNoLessonActivity(EnrollmentOrderEntity order) {
        List<CoursePackageEntity> packages = coursePackageMapper.selectList(
                new LambdaQueryWrapper<CoursePackageEntity>()
                        .eq(CoursePackageEntity::getEnrollmentOrderId, order.getId())
        );
        if (packages.isEmpty()) {
            return;
        }

        List<Long> packageIds = packages.stream().map(CoursePackageEntity::getId).toList();
        Long lessonRecordCount = lessonRecordMapper.selectCount(new LambdaQueryWrapper<LessonRecordEntity>()
                .in(LessonRecordEntity::getPackageId, packageIds));
        if (lessonRecordCount != null && lessonRecordCount > 0) {
            throw new BusinessException("ORDER_DELETE_LESSON_RECORD_EXISTS", "This order already has lesson records and cannot be deleted.");
        }

        Long pastOccurrenceCount = lessonOccurrenceMapper.selectCount(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .in(LessonOccurrenceEntity::getPackageId, packageIds)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .le(LessonOccurrenceEntity::getPlannedStartAt, AppTime.nowLocalDateTime()));
        if (pastOccurrenceCount != null && pastOccurrenceCount > 0) {
            throw new BusinessException("ORDER_DELETE_PAST_LESSON_EXISTS", "This order has past scheduled lessons and cannot be deleted.");
        }
    }

    private void validateClassOrderHasNoLessonRecords(EnrollmentOrderEntity order) {
        List<LessonOccurrenceEntity> occurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .select(LessonOccurrenceEntity::getId)
                        .eq(LessonOccurrenceEntity::getClassGroupId, order.getClassId())
        );
        if (occurrences.isEmpty()) {
            return;
        }

        Long lessonRecordCount = lessonRecordMapper.selectCount(new LambdaQueryWrapper<LessonRecordEntity>()
                .eq(LessonRecordEntity::getStudentId, order.getStudentId())
                .in(LessonRecordEntity::getOccurrenceId, occurrences.stream().map(LessonOccurrenceEntity::getId).toList()));
        if (lessonRecordCount != null && lessonRecordCount > 0) {
            throw new BusinessException("ORDER_DELETE_LESSON_RECORD_EXISTS", "This order already has lesson records and cannot be deleted.");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int renewActiveClassOccurrences() {
        List<ClassGroupEntity> activeGroups = classGroupMapper.selectList(
                new LambdaQueryWrapper<ClassGroupEntity>()
                        .eq(ClassGroupEntity::getStatus, "ACTIVE")
                        .isNotNull(ClassGroupEntity::getProductId));
        LocalDate today = LocalDate.now(AppTime.APP_ZONE_ID);
        int inserted = 0;
        for (ClassGroupEntity group : activeGroups) {
            try {
                CourseProductEntity product = courseProductMapper.selectById(group.getProductId());
                if (product == null || !"GROUP".equals(product.getTeachingMode()) || !"ACTIVE".equals(product.getStatus())) {
                    continue;
                }
                LocalDate[] window = resolveProductOccurrenceWindow(product, today);
                if (window == null) {
                    continue;
                }
                inserted += ensureClassOccurrencesThrough(group, product, window[0], window[1]);
            } catch (RuntimeException ex) {
                log.warn("Failed to renew occurrences for class group id={}: {}", group.getId(), ex.getMessage());
            }
        }
        return inserted;
    }

    /**
     * 计算班课产品的课次生成窗口 [max(产品起始, 今天), 产品自然终点]；配置不全返回 null。
     */
    private LocalDate[] resolveProductOccurrenceWindow(CourseProductEntity product, LocalDate today) {
        if ("PER_SESSION".equals(product.getBillingType())) {
            if (product.getStartDate() == null || product.getTotalSessions() == null || product.getTotalSessions() < 1) {
                return null;
            }
            List<CourseProductScheduleSlotEntity> slots = loadProductSlots(product.getId());
            if (slots.isEmpty()) {
                return null;
            }
            LocalDate start = product.getStartDate().isAfter(today) ? product.getStartDate() : today;
            LocalDate end = computeNthSessionDate(product.getStartDate(), slots, product.getTotalSessions());
            return end.isBefore(start) ? null : new LocalDate[]{start, end};
        }
        if ("MONTHLY".equals(product.getBillingType())) {
            if (product.getStartMonth() == null || product.getEndMonth() == null) {
                return null;
            }
            LocalDate productStartDay = YearMonth.from(product.getStartMonth()).atDay(1);
            LocalDate start = productStartDay.isAfter(today) ? productStartDay : today;
            LocalDate end = YearMonth.from(product.getEndMonth()).atEndOfMonth();
            return end.isBefore(start) ? null : new LocalDate[]{start, end};
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createClassEnrollmentOrder(ClassEnrollmentCreateRequest request) {
        // Validate student
        StudentEntity student = requireStudent(request.getStudentId());

        // Resolve class_group and its backing GROUP product
        ClassGroupEntity clz;
        Long resolvedClassId;
        CourseProductEntity product;
        if (request.getProductId() != null) {
            product = courseProductMapper.selectById(request.getProductId());
            if (product == null || !"GROUP".equals(product.getTeachingMode()) || !"ACTIVE".equals(product.getStatus())) {
                throw new BusinessException("PRODUCT_NOT_FOUND", "Group course product not found or not active.");
            }
            ClassGroupEntity existingGroup = classGroupMapper.selectOne(
                    new LambdaQueryWrapper<ClassGroupEntity>()
                            .eq(ClassGroupEntity::getProductId, request.getProductId())
                            .eq(ClassGroupEntity::getIsDeleted, 0)
                            .last("LIMIT 1"));
            if (existingGroup != null) {
                clz = existingGroup;
            } else {
                ClassGroupEntity newGroup = new ClassGroupEntity();
                newGroup.setClassName(product.getProductName());
                newGroup.setListPrice(product.getListPrice());
                newGroup.setProductId(request.getProductId());
                newGroup.setTeacherId(product.getDefaultTeacherId());
                newGroup.setClassroomId(product.getDefaultClassroomId());
                newGroup.setStatus("ACTIVE");
                classGroupMapper.insert(newGroup);
                clz = newGroup;
            }
            resolvedClassId = clz.getId();
        } else {
            if (request.getClassId() == null) {
                throw new BusinessException("CLASS_ID_REQUIRED", "Either classId or productId is required.");
            }
            clz = classGroupMapper.selectById(request.getClassId());
            if (clz == null || !"ACTIVE".equals(clz.getStatus())) {
                throw new BusinessException("CLASS_NOT_FOUND", "Class not found or not active.");
            }
            resolvedClassId = request.getClassId();
            product = clz.getProductId() != null ? courseProductMapper.selectById(clz.getProductId()) : null;
        }
        if (product == null || !"GROUP".equals(product.getTeachingMode())) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "Group course product not found for this class.");
        }

        // Compute pricing + enrollment window from the product's billing type (server-authoritative).
        ClassBillingResult billing = computeClassBilling(product, request);

        // Check duplicate active enrollment
        Long existingCount = classEnrollmentMapper.selectCount(
                new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getClassId, resolvedClassId)
                        .eq(ClassEnrollmentEntity::getStudentId, request.getStudentId())
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0)
        );
        if (existingCount > 0) {
            throw new BusinessException("ALREADY_ENROLLED", "Student is already enrolled in this class.");
        }

        // Validate discount/received against the computed original amount
        BigDecimal discountedAmount = billing.originalAmount.subtract(request.getDiscountAmount());
        if (discountedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("DISCOUNT_EXCEEDS_AMOUNT", "Discount cannot exceed original amount.");
        }
        if (request.getReceivedAmount().compareTo(discountedAmount) > 0) {
            throw new BusinessException("RECEIVED_EXCEEDS_AMOUNT", "Received amount cannot exceed amount due.");
        }

        // Generate shared class occurrences across the full product period (calendar completeness)
        ensureClassOccurrencesThrough(clz, product, billing.occurrenceWindowStart, billing.occurrenceWindowEnd);

        // Create enrollment_order
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setEnrollmentNo(BusinessNoGenerator.generateEnrollmentNo());
        order.setStudentId(student.getId());
        order.setClassId(resolvedClassId);
        order.setOrderStatus("CONFIRMED");
        order.setHandledByName(request.getHandledByName());
        order.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode() : DEFAULT_CURRENCY);
        order.setOriginalAmount(billing.originalAmount);
        order.setDiscountAmount(request.getDiscountAmount());
        order.setReceivedAmount(request.getReceivedAmount());
        // Persist the amount snapshot so later balance payments are accepted:
        // amountDue = original - discount; balance = amountDue - received.
        order.setAmountDue(discountedAmount);
        order.setBalanceAmount(discountedAmount.subtract(request.getReceivedAmount()));
        order.setPaymentStatus(resolvePaymentStatus(billing.originalAmount, request.getDiscountAmount(), request.getReceivedAmount()));
        order.setRemark(request.getRemark());
        enrollmentOrderMapper.insert(order);

        // Create class_enrollment (with billing snapshot)
        ClassEnrollmentEntity enrollment = new ClassEnrollmentEntity();
        enrollment.setClassId(resolvedClassId);
        enrollment.setStudentId(student.getId());
        enrollment.setEnrollmentDate(billing.enrollmentDate);
        enrollment.setExpiryDate(billing.expiryDate);
        enrollment.setBillingType(product.getBillingType());
        enrollment.setPurchasedQuantity(billing.purchasedQuantity);
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
     * 班课定价结果：服务端根据产品 billingType 与学生选择量计算原价与报名/到期窗口。
     * 注意：定价窗口（enrollmentDate/expiryDate，按学生所选）与日历课次生成窗口
     * （occurrenceWindowStart/occurrenceWindowEnd，按产品完整周期）是解耦的——
     * 班课课次是班级共享，应覆盖整个产品周期，与单个学生的购买量无关。
     */
    private static class ClassBillingResult {
        final BigDecimal originalAmount;
        final LocalDate enrollmentDate;
        final LocalDate expiryDate;
        final LocalDate occurrenceWindowStart;   // 课次生成起点 = max(产品起始, 今天)
        final LocalDate occurrenceWindowEnd;     // 课次生成终点 = 产品自然终点
        final Integer purchasedQuantity;

        ClassBillingResult(BigDecimal originalAmount, LocalDate enrollmentDate, LocalDate expiryDate,
                           LocalDate occurrenceWindowStart, LocalDate occurrenceWindowEnd, Integer purchasedQuantity) {
            this.originalAmount = originalAmount;
            this.enrollmentDate = enrollmentDate;
            this.expiryDate = expiryDate;
            this.occurrenceWindowStart = occurrenceWindowStart;
            this.occurrenceWindowEnd = occurrenceWindowEnd;
            this.purchasedQuantity = purchasedQuantity;
        }
    }

    /**
     * 按计费类型计算班课报名定价与时间窗口（按比例收费）。
     * 定价/到期按学生所选量；日历课次生成窗口按产品完整周期（起点 max(产品起始, 今天)）。
     * PER_SESSION: 原价 = listPrice × sessions / totalSessions；到期 = 第 sessions 次课日期；
     *              课次窗口 = [max(产品起始, 今天), 第 totalSessions 次课日期]。
     * MONTHLY:     原价 = listPrice × months / totalMonths；到期 = 所选结束月月末；
     *              课次窗口 = [max(产品起始月1号, 今天), 产品结束月月末]。
     */
    private ClassBillingResult computeClassBilling(CourseProductEntity product, ClassEnrollmentCreateRequest request) {
        String billingType = product.getBillingType();
        BigDecimal listPrice = product.getListPrice() != null ? product.getListPrice() : BigDecimal.ZERO;
        LocalDate today = LocalDate.now(AppTime.APP_ZONE_ID);

        if ("PER_SESSION".equals(billingType)) {
            Integer totalSessions = product.getTotalSessions();
            if (totalSessions == null || totalSessions < 1 || product.getStartDate() == null) {
                throw new BusinessException("PRODUCT_BILLING_INCOMPLETE", "Per-session product is missing start date or total sessions.");
            }
            Integer sessions = request.getSessions();
            if (sessions == null || sessions < 1) {
                throw new BusinessException("INVALID_SESSIONS", "Please select the number of sessions.");
            }
            if (sessions > totalSessions) {
                throw new BusinessException("SESSIONS_EXCEED_TOTAL", "Selected sessions exceed the product total sessions.");
            }
            BigDecimal original = listPrice.multiply(BigDecimal.valueOf(sessions))
                    .divide(BigDecimal.valueOf(totalSessions), 2, RoundingMode.HALF_UP);
            List<CourseProductScheduleSlotEntity> slots = loadProductSlots(product.getId());
            if (slots.isEmpty()) {
                throw new BusinessException("PRODUCT_NO_SLOTS", "Per-session product has no schedule slots.");
            }
            LocalDate expiry = computeNthSessionDate(product.getStartDate(), slots, sessions);
            // 课次覆盖产品完整周期：从产品起始日到第 totalSessions 次课日期。
            LocalDate windowStart = product.getStartDate().isAfter(today) ? product.getStartDate() : today;
            LocalDate windowEnd = computeNthSessionDate(product.getStartDate(), slots, totalSessions);
            return new ClassBillingResult(original, product.getStartDate(), expiry, windowStart, windowEnd, sessions);
        } else if ("MONTHLY".equals(billingType)) {
            if (product.getStartMonth() == null || product.getEndMonth() == null) {
                throw new BusinessException("PRODUCT_BILLING_INCOMPLETE", "Monthly product is missing start or end month.");
            }
            YearMonth productStart = YearMonth.from(product.getStartMonth());
            YearMonth productEnd = YearMonth.from(product.getEndMonth());
            YearMonth selStart = parseRequestMonth(request.getStartMonth(), "start month");
            YearMonth selEnd = parseRequestMonth(request.getEndMonth(), "end month");
            if (selEnd.isBefore(selStart)) {
                throw new BusinessException("INVALID_MONTH_RANGE", "End month cannot be earlier than start month.");
            }
            if (selStart.isBefore(productStart) || selEnd.isAfter(productEnd)) {
                throw new BusinessException("MONTH_OUT_OF_RANGE", "Selected months must be within the product month range.");
            }
            int totalMonths = (int) ChronoUnit.MONTHS.between(productStart, productEnd) + 1;
            int months = (int) ChronoUnit.MONTHS.between(selStart, selEnd) + 1;
            BigDecimal original = listPrice.multiply(BigDecimal.valueOf(months))
                    .divide(BigDecimal.valueOf(totalMonths), 2, RoundingMode.HALF_UP);
            LocalDate enrollmentDate = selStart.atDay(1);
            LocalDate expiry = selEnd.atEndOfMonth();
            // 课次覆盖产品完整周期：从产品起始月到产品结束月月末。
            LocalDate productStartDay = productStart.atDay(1);
            LocalDate windowStart = productStartDay.isAfter(today) ? productStartDay : today;
            LocalDate windowEnd = productEnd.atEndOfMonth();
            return new ClassBillingResult(original, enrollmentDate, expiry, windowStart, windowEnd, months);
        } else {
            throw new BusinessException("INVALID_BILLING_TYPE", "Unknown billing type for group product.");
        }
    }

    private List<CourseProductScheduleSlotEntity> loadProductSlots(Long productId) {
        return courseProductScheduleSlotMapper.selectList(
                new LambdaQueryWrapper<CourseProductScheduleSlotEntity>()
                        .eq(CourseProductScheduleSlotEntity::getProductId, productId)
                        .eq(CourseProductScheduleSlotEntity::getIsDeleted, 0));
    }

    private YearMonth parseRequestMonth(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("INVALID_MONTH", "Schedule " + fieldName + " is required.");
        }
        try {
            return YearMonth.parse(value);
        } catch (RuntimeException ex) {
            throw new BusinessException("INVALID_MONTH", "Schedule " + fieldName + " must use yyyy-MM format.");
        }
    }

    /**
     * 从起始日期开始，按每周固定 slot 顺序数到第 n 次课，返回该次课的日期。
     */
    private LocalDate computeNthSessionDate(LocalDate startDate, List<CourseProductScheduleSlotEntity> slots, int n) {
        Set<Integer> weekdays = slots.stream()
                .map(CourseProductScheduleSlotEntity::getWeekday)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        int count = 0;
        // 上限保护：最多扫描约 10 年，避免异常配置导致死循环。
        LocalDate limit = startDate.plusYears(10);
        for (LocalDate date = startDate; !date.isAfter(limit); date = date.plusDays(1)) {
            if (weekdays.contains(date.getDayOfWeek().getValue())) {
                count++;
                if (count >= n) {
                    return date;
                }
            }
        }
        throw new BusinessException("SESSION_DATE_UNRESOLVED", "Cannot resolve the date for the selected sessions.");
    }

    private int ensureClassOccurrencesThrough(ClassGroupEntity classGroup,
                                               CourseProductEntity product,
                                               LocalDate windowStart,
                                               LocalDate horizonEnd) {
        List<CourseProductScheduleSlotEntity> slots = loadProductSlots(product.getId());
        if (slots.isEmpty()) {
            return 0;
        }

        LocalDateTime windowStartDt = windowStart.atStartOfDay();
        LocalDateTime windowEnd = horizonEnd.plusDays(1).atStartOfDay();

        Set<LocalDateTime> existingStarts = lessonOccurrenceMapper.selectList(
                        new LambdaQueryWrapper<LessonOccurrenceEntity>()
                                .eq(LessonOccurrenceEntity::getClassGroupId, classGroup.getId())
                                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                                .ge(LessonOccurrenceEntity::getPlannedStartAt, windowStartDt)
                                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd))
                .stream()
                .map(LessonOccurrenceEntity::getPlannedStartAt)
                .collect(Collectors.toSet());

        List<LessonOccurrenceEntity> occurrences = new ArrayList<>();
        for (LocalDate date = windowStart; !date.isAfter(horizonEnd); date = date.plusDays(1)) {
            int weekday = date.getDayOfWeek().getValue(); // Monday=1, Sunday=7
            for (CourseProductScheduleSlotEntity slot : slots) {
                if (slot.getWeekday() != weekday || !slot.getEndTime().isAfter(slot.getStartTime())) {
                    continue;
                }
                LocalDateTime plannedStartAt = LocalDateTime.of(date, slot.getStartTime());
                if (existingStarts.contains(plannedStartAt)) {
                    continue;
                }
                long minutes = ChronoUnit.MINUTES.between(slot.getStartTime(), slot.getEndTime());
                LessonOccurrenceEntity occ = new LessonOccurrenceEntity();
                occ.setLessonNo(generateClassLessonNo());
                occ.setClassGroupId(classGroup.getId());
                occ.setOccurrenceType("REGULAR");
                occ.setPlannedTeacherId(product.getDefaultTeacherId());
                occ.setPlannedClassroomId(product.getDefaultClassroomId());
                occ.setPlannedStartAt(plannedStartAt);
                occ.setPlannedEndAt(LocalDateTime.of(date, slot.getEndTime()));
                occ.setPlannedUnits((int) (minutes / 30));
                occ.setPlanningStatus("SCHEDULED");
                occ.setConflictChecked(1);
                occurrences.add(occ);
            }
        }

        if (!occurrences.isEmpty()) {
            ensureNoConflicts(occurrences, null, product.getDefaultTeacherId(), product.getDefaultClassroomId());
            occurrences.forEach(lessonOccurrenceMapper::insert);
        }

        String scheduleDesc = buildScheduleDesc(slots);
        if (!scheduleDesc.equals(classGroup.getScheduleDesc())) {
            classGroup.setScheduleDesc(scheduleDesc);
            classGroupMapper.updateById(classGroup);
        }
        return occurrences.size();
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

    /**
     * 生成班课排课编号：CLS + yyyyMMddHHmmss + 4 位随机数
     */
    private String generateClassLessonNo() {
        return "CLS" + AppTime.nowLocalDateTime()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(1000, 10000));
    }

    /**
     * 从 course_product_schedule_slot 列表生成 scheduleDesc 文本，如 "Mon/Wed 09:00-10:30"。
     */
    private String buildScheduleDesc(List<CourseProductScheduleSlotEntity> slots) {
        // 按 (startTime, endTime) 分组，每组的星期合并
        Map<String, List<Integer>> timeToWeekdays = new LinkedHashMap<>();
        for (CourseProductScheduleSlotEntity slot : slots) {
            String key = slot.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm"))
                    + "-" + slot.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm"));
            timeToWeekdays.computeIfAbsent(key, k -> new ArrayList<>()).add(slot.getWeekday());
        }
        String[] weekdayAbbr = {"", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : timeToWeekdays.entrySet()) {
            String weekPart = entry.getValue().stream()
                    .sorted()
                    .map(w -> weekdayAbbr[w])
                    .collect(Collectors.joining("/"));
            lines.add(weekPart + " " + entry.getKey());
        }
        return String.join("\n", lines);
    }
}
