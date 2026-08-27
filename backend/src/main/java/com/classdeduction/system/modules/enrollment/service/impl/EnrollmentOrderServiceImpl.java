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
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentCreateRequest;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderPageQuery;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderCorrectionRequest;
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
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleSlotMapper;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.stream.Collectors;

/**
 * 报名服务：负责报名开单，并同步生成支付记录、课时包和收据。
 *
 * <p>1对1 的老师、课程和逐时段课时分配由排课服务负责，报名不再创建默认老师分配或自动排课。
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
    private final ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;
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
        List<Long> matchedCourseOrderIds = findMatchedCourseOrderIds(query.getKeyword());
        List<Long> matchedClassIds = findMatchedClassIds(query.getKeyword());
        LambdaQueryWrapper<EnrollmentOrderEntity> wrapper = new LambdaQueryWrapper<EnrollmentOrderEntity>()
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(EnrollmentOrderEntity::getEnrollmentNo, query.getKeyword())
                        .or(!matchedStudentIds.isEmpty(), nested -> nested.in(EnrollmentOrderEntity::getStudentId, matchedStudentIds))
                        .or(!matchedCourseOrderIds.isEmpty(), nested -> nested.in(EnrollmentOrderEntity::getId, matchedCourseOrderIds))
                        .or(!matchedClassIds.isEmpty(), nested -> nested.in(EnrollmentOrderEntity::getClassId, matchedClassIds)))
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
                .scheduleRuleId(null)
                .generatedOccurrenceCount(null)
                .firstLessonAt(null)
                .lastLessonAt(null)
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
    }

    private CoursePackageEntity createCoursePackage(EnrollmentOrderEntity order,
                                                    CourseProductEntity product,
                                                    EnrollmentCreateRequest request,
                                                    StudentEntity student) {
        int totalUnits = request.getTotalUnits();
        if (totalUnits <= 0) {
            throw new BusinessException("INVALID_TOTAL_UNITS", "Total units must be greater than 0.");
        }
        // totalUnits 从请求传入的是小时数，内部存储统一转为半课时单位（1单位=0.5h）。
        int halfHourUnits = totalUnits * 2;

        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setPackageNo(BusinessNoGenerator.generatePackageNo());
        coursePackage.setEnrollmentOrderId(order.getId());
        coursePackage.setEnrollmentOrderItemId(request.getEnrollmentOrderItemId());
        coursePackage.setStudentId(student.getId());
        coursePackage.setCourseProductId(product.getId());
        coursePackage.setPackageName(StringUtils.hasText(request.getPackageName()) ? request.getPackageName() : product.getProductName());
        coursePackage.setTeachingMode(product.getTeachingMode());
        coursePackage.setTotalUnits(halfHourUnits);
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
                .startDate(item.getStartDate())
                .priceAmount(item.getPriceAmount())
                .originalPriceAmount(item.getOriginalPriceAmount())
                .manualPriceOverride(Boolean.TRUE.equals(item.getManualPriceOverride()))
                .oneOnOneHours(item.getOneOnOneHours())
                .oneOnOneUnits(item.getOneOnOneUnits())
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
    public void correctOrder(Long id, EnrollmentOrderCorrectionRequest request) {
        EnrollmentOrderEntity order = requireOrder(id);
        if ("CANCELLED".equals(order.getOrderStatus())) {
            throw new BusinessException("ORDER_CANCELLED_NOT_EDITABLE",
                    "Cancelled orders cannot be edited.");
        }

        // Update order snapshot fields. Amounts, courses, and payments are untouched.
        if (StringUtils.hasText(request.getStudentName())) {
            order.setStudentNameSnapshot(request.getStudentName().trim());
        }
        if (request.getGrade() != null) {
            order.setGradeSnapshot(request.getGrade().trim());
        }
        if (request.getSchool() != null) {
            order.setSchoolSnapshot(request.getSchool().trim());
        }
        if (request.getGuardianPhone() != null) {
            order.setParentPhoneSnapshot(request.getGuardianPhone().trim());
        }
        if (request.getGuardianPhone2() != null) {
            order.setParentPhone2Snapshot(request.getGuardianPhone2().trim());
        }
        if (request.getPickupRequired() != null) {
            order.setPickupRequiredSnapshot(request.getPickupRequired());
        }
        if (request.getPickupAddress() != null) {
            order.setPickupAddressSnapshot(request.getPickupAddress().trim());
        }
        if (request.getDropoffRequired() != null) {
            order.setDropoffRequiredSnapshot(request.getDropoffRequired());
        }
        if (request.getDropoffAddress() != null) {
            order.setDropoffAddressSnapshot(request.getDropoffAddress().trim());
        }
        if (request.getSignupDate() != null) {
            order.setSignupDate(request.getSignupDate());
        }
        if (request.getHandledByName() != null) {
            order.setHandledByName(request.getHandledByName().trim());
        }
        if (request.getRemark() != null) {
            order.setRemark(request.getRemark());
        }
        enrollmentOrderMapper.updateById(order);

        // Keep the receipt snapshot(s) in sync so the printed receipt reflects the fix.
        syncReceiptSnapshots(order);
    }

    /**
     * Re-write the matching keys inside each stored receipt payload for this order so
     * corrected contact info is reflected on the saved/printable receipt.
     */
    private void syncReceiptSnapshots(EnrollmentOrderEntity order) {
        List<ReceiptRecordEntity> receipts = receiptRecordMapper.selectList(
                new LambdaQueryWrapper<ReceiptRecordEntity>()
                        .eq(ReceiptRecordEntity::getEnrollmentOrderId, order.getId()));
        for (ReceiptRecordEntity receipt : receipts) {
            Map<String, Object> payload = jsonHelper.fromJson(
                    receipt.getReceiptPayload(),
                    new TypeReference<LinkedHashMap<String, Object>>() {});
            if (payload == null) {
                continue;
            }
            payload.put("studentName", order.getStudentNameSnapshot());
            payload.put("grade", order.getGradeSnapshot());
            payload.put("school", order.getSchoolSnapshot());
            payload.put("guardianPhone", order.getParentPhoneSnapshot());
            payload.put("guardianPhone2", order.getParentPhone2Snapshot());
            payload.put("pickupRequired", order.getPickupRequiredSnapshot());
            payload.put("pickupAddress", order.getPickupAddressSnapshot());
            payload.put("dropoffRequired", order.getDropoffRequiredSnapshot());
            payload.put("dropoffAddress", order.getDropoffAddressSnapshot());
            payload.put("signupDate", order.getSignupDate());
            payload.put("handledByName", order.getHandledByName());
            receipt.setReceiptPayload(jsonHelper.toJson(payload));
            receiptRecordMapper.updateById(receipt);
        }
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void forceVoidOrder(Long id, String reason) {
        EnrollmentOrderEntity order = requireOrder(id);

        // 强制作废保留财务与消课历史，只关闭报名产物并软删订单，供清理历史脏数据。
        cancelEnrollmentArtifacts(order);

        if (!"CANCELLED".equals(order.getOrderStatus())) {
            order.setOrderStatus("CANCELLED");
        }
        String note = "[FORCE-VOID] " + (StringUtils.hasText(reason) ? reason.trim() : "Legacy record cleanup");
        String existingRemark = order.getRemark();
        order.setRemark(StringUtils.hasText(existingRemark) ? existingRemark + " | " + note : note);
        int updated = enrollmentOrderMapper.updateById(order);
        if (updated != 1) {
            throw new BusinessException("ORDER_CONCURRENT_UPDATE",
                    "The order was updated by another request. Please retry.");
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
                        .eq(ClassGroupEntity::getBillingType, "MONTHLY")
                        .isNotNull(ClassGroupEntity::getStartMonth)
                        .isNotNull(ClassGroupEntity::getEndMonth));
        LocalDate today = LocalDate.now(AppTime.APP_ZONE_ID);
        int inserted = 0;
        for (ClassGroupEntity group : activeGroups) {
            try {
                LocalDate[] window = resolveClassOccurrenceWindow(group, today);
                if (window == null) {
                    continue;
                }
                inserted += ensureClassOccurrencesThrough(group, window[0], window[1]);
            } catch (RuntimeException ex) {
                log.warn("Failed to renew occurrences for class group id={}: {}", group.getId(), ex.getMessage());
            }
        }
        return inserted;
    }

    private LocalDate[] resolveClassOccurrenceWindow(ClassGroupEntity group, LocalDate today) {
        if (group.getStartDate() != null && group.getEndDate() != null) {
            LocalDate start = group.getStartDate().isAfter(today) ? group.getStartDate() : today;
            return group.getEndDate().isBefore(start) ? null : new LocalDate[]{start, group.getEndDate()};
        }
        LocalDate classStartDay = group.getStartMonth();
        LocalDate classEndDay = group.getEndMonth();
        if (classStartDay == null || classEndDay == null) {
            return null;
        }
        LocalDate start = classStartDay.isAfter(today) ? classStartDay : today;
        LocalDate end = java.time.YearMonth.from(classEndDay).atEndOfMonth();
        return end.isBefore(start) ? null : new LocalDate[]{start, end};
    }

    private int ensureClassOccurrencesThrough(ClassGroupEntity classGroup,
                                               LocalDate windowStart,
                                               LocalDate horizonEnd) {
        List<ClassGroupScheduleSlotEntity> slots = loadClassSlots(classGroup.getId());
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
            for (ClassGroupScheduleSlotEntity slot : slots) {
                if (!Objects.equals(slot.getWeekday(), weekday) || !slot.getEndTime().isAfter(slot.getStartTime())) {
                    continue;
                }
                LocalDateTime plannedStartAt = LocalDateTime.of(date, slot.getStartTime());
                if (existingStarts.contains(plannedStartAt)) {
                    continue;
                }
                long minutes = ChronoUnit.MINUTES.between(slot.getStartTime(), slot.getEndTime());
                LessonOccurrenceEntity occ = new LessonOccurrenceEntity();
                occ.setLessonNo(BusinessNoGenerator.generateClassLessonOccurrenceNo());
                occ.setClassGroupId(classGroup.getId());
                occ.setOccurrenceType("REGULAR");
                occ.setPlannedTeacherId(slot.getTeacherId() == null ? classGroup.getTeacherId() : slot.getTeacherId());
                occ.setPlannedClassroomId(classGroup.getClassroomId());
                occ.setPlannedStartAt(plannedStartAt);
                occ.setPlannedEndAt(LocalDateTime.of(date, slot.getEndTime()));
                occ.setPlannedUnits((int) (minutes / 30));
                occ.setPlanningStatus("SCHEDULED");
                occ.setConflictChecked(1);
                occurrences.add(occ);
            }
        }

        if (!occurrences.isEmpty()) {
            ensureNoConflicts(occurrences, null, null, classGroup.getClassroomId());
            occurrences.stream()
                    .filter(item -> item.getPlannedTeacherId() != null)
                    .collect(Collectors.groupingBy(LessonOccurrenceEntity::getPlannedTeacherId))
                    .forEach((teacherId, teacherOccurrences) ->
                            ensureNoConflicts(teacherOccurrences, null, teacherId, null));
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

    private List<Long> findMatchedCourseOrderIds(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        List<Long> productIds = courseProductMapper.selectList(new LambdaQueryWrapper<CourseProductEntity>()
                        .and(w -> w.like(CourseProductEntity::getProductName, keyword)
                                .or()
                                .like(CourseProductEntity::getRegisterClass, keyword)))
                .stream()
                .map(CourseProductEntity::getId)
                .toList();
        if (productIds.isEmpty()) {
            return Collections.emptyList();
        }
        return coursePackageMapper.selectList(new LambdaQueryWrapper<CoursePackageEntity>()
                        .in(CoursePackageEntity::getCourseProductId, productIds))
                .stream()
                .map(CoursePackageEntity::getEnrollmentOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<Long> findMatchedClassIds(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        return classGroupMapper.selectList(new LambdaQueryWrapper<ClassGroupEntity>()
                        .like(ClassGroupEntity::getClassName, keyword))
                .stream()
                .map(ClassGroupEntity::getId)
                .toList();
    }

    private List<ClassGroupScheduleSlotEntity> loadClassSlots(Long classGroupId) {
        return classGroupScheduleSlotMapper.selectList(
                new LambdaQueryWrapper<ClassGroupScheduleSlotEntity>()
                        .eq(ClassGroupScheduleSlotEntity::getClassGroupId, classGroupId)
                        .eq(ClassGroupScheduleSlotEntity::getIsDeleted, 0));
    }

    private String buildScheduleDesc(List<ClassGroupScheduleSlotEntity> slots) {
        // 按 (startTime, endTime) 分组，每组的星期合并
        Map<String, List<Integer>> timeToWeekdays = new LinkedHashMap<>();
        for (ClassGroupScheduleSlotEntity slot : slots) {
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
