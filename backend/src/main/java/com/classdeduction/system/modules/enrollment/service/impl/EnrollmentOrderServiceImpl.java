package com.classdeduction.system.modules.enrollment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.dto.CurrentUserResponse;
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
import com.classdeduction.system.modules.enrollment.dto.RegistrationIntakeRequest;
import com.classdeduction.system.modules.enrollment.dto.RefundRequest;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderParticipantEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderCourseRefundStateEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentAllocationEntity;
import com.classdeduction.system.modules.enrollment.entity.PackageTeacherAssignmentEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentMethodEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentRecordEntity;
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupMemberEntity;
import com.classdeduction.system.modules.enrollment.entity.ReceiptRecordEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderParticipantMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderCourseRefundStateMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentMethodMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentRecordMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentAllocationMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMemberMapper;
import com.classdeduction.system.modules.enrollment.mapper.ReceiptRecordMapper;
import com.classdeduction.system.modules.enrollment.service.ClassBillingService;
import com.classdeduction.system.modules.schedule.service.ClassHolidayService;
import com.classdeduction.system.modules.schedule.vo.ClassHolidayVO;
import com.classdeduction.system.modules.enrollment.service.EnrollmentOrderService;
import com.classdeduction.system.modules.enrollment.service.allocation.PaymentAllocationService;
import com.classdeduction.system.modules.enrollment.util.BusinessNoGenerator;
import com.classdeduction.system.modules.enrollment.vo.CoursePackageListItemVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentCreateResultVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentOrderDetailVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentOrderItemVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentOrderListItemVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentOrderParticipantVO;
import com.classdeduction.system.modules.enrollment.vo.PackageTeacherAssignmentVO;
import com.classdeduction.system.modules.enrollment.vo.PaymentRecordVO;
import com.classdeduction.system.modules.enrollment.vo.ReceiptRecordVO;
import com.classdeduction.system.modules.enrollment.vo.RefundCoursePreviewVO;
import com.classdeduction.system.modules.finance.service.StudentCreditService;
import com.classdeduction.system.modules.enrollment.util.WholeDollarBilling;
import com.classdeduction.system.modules.lesson.entity.HourLedgerEntity;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.entity.PackageBalanceSnapshotEntity;
import com.classdeduction.system.modules.lesson.mapper.HourLedgerMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.lesson.mapper.PackageBalanceSnapshotMapper;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.entity.LessonAdjustmentLogEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.util.ClassSlotRotation;
import com.classdeduction.system.modules.schedule.util.ScheduleSlotTime;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonAdjustmentLogMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleSlotMapper;
import com.classdeduction.system.modules.systemconfig.entity.NotificationLogEntity;
import com.classdeduction.system.modules.systemconfig.mapper.NotificationLogMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 报名服务：负责报名开单，并同步生成支付记录、课时包和收据。
 *
 * <p>1对1 的老师、课程和逐时段课时分配由排课服务负责，报名不再创建默认老师分配或自动排课。
 */
@Service
@Slf4j
@RequiredArgsConstructor
@DependsOn("privateLessonSchemaCompatibilityInitializer")
public class EnrollmentOrderServiceImpl implements EnrollmentOrderService {

    private static final String DEFAULT_CURRENCY = "USD";
    private static final String DEFAULT_RECEIPT_TEMPLATE = "DEFAULT_RECEIPT";
    private static final String REFUND_RECEIPT_TEMPLATE = "REFUND_RECEIPT";
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final EnrollmentOrderMapper enrollmentOrderMapper;
    private final EnrollmentOrderItemMapper enrollmentOrderItemMapper;
    private final EnrollmentOrderParticipantMapper enrollmentOrderParticipantMapper;
    private final EnrollmentOrderCourseRefundStateMapper enrollmentOrderCourseRefundStateMapper;
    private final CoursePackageMapper coursePackageMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final PaymentAllocationMapper paymentAllocationMapper;
    private final PaymentAllocationService paymentAllocationService;
    private final PrivateLessonGroupMemberMapper privateLessonGroupMemberMapper;
    private final ReceiptRecordMapper receiptRecordMapper;
    private final ClassBillingService classBillingService;
    private final PackageTeacherAssignmentMapper packageTeacherAssignmentMapper;
    private final StudentMapper studentMapper;
    private final CourseProductMapper courseProductMapper;
    private final TeacherMapper teacherMapper;
    private final JsonHelper jsonHelper;
    @Autowired
    private StudentCreditService studentCreditService;
    private final RecurringScheduleRuleMapper recurringScheduleRuleMapper;
    private final RecurringScheduleSlotMapper recurringScheduleSlotMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final LessonRecordMapper lessonRecordMapper;
    private final HourLedgerMapper hourLedgerMapper;
    private final PackageBalanceSnapshotMapper packageBalanceSnapshotMapper;
    private final LessonAdjustmentLogMapper lessonAdjustmentLogMapper;
    private final NotificationLogMapper notificationLogMapper;
    private final ClassGroupMapper classGroupMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;
    private final ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;
    private final PaymentMethodMapper paymentMethodMapper;
    private final ClassHolidayService classHolidayService;

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
        // 统一清理关键词后再查学生、课程和班课，避免输入框中的首尾空格让筛选看起来失效。
        String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;
        List<Long> matchedStudentIds = findMatchedStudentIds(keyword);
        List<Long> matchedCourseOrderIds = findMatchedCourseOrderIds(keyword);
        List<Long> matchedClassIds = findMatchedClassIds(keyword);
        String teachingMode = resolveTeachingMode(query);
        boolean oneOnOneView = "ONE_ON_ONE".equals(teachingMode);
        boolean groupView = "GROUP".equals(teachingMode);
        boolean courseItemView = oneOnOneView || groupView || query.getClassId() != null;
        LambdaQueryWrapper<EnrollmentOrderEntity> wrapper = new LambdaQueryWrapper<EnrollmentOrderEntity>()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(EnrollmentOrderEntity::getEnrollmentNo, keyword)
                        // 学生改名或历史学生资料被归档后，仍可通过报名单保存的姓名快照检索。
                        .or().like(EnrollmentOrderEntity::getStudentNameSnapshot, keyword)
                        .or().apply("EXISTS (SELECT 1 FROM enrollment_order_participant eop "
                                + "WHERE eop.enrollment_order_id = enrollment_order.id "
                                + "AND eop.student_name_snapshot LIKE CONCAT('%', {0}, '%') "
                                + "AND eop.is_deleted = 0)", keyword)
                        .or(!matchedStudentIds.isEmpty(), nested -> nested.in(EnrollmentOrderEntity::getStudentId, matchedStudentIds))
                        .or(!matchedCourseOrderIds.isEmpty(), nested -> nested.in(EnrollmentOrderEntity::getId, matchedCourseOrderIds))
                        .or(!matchedClassIds.isEmpty(), nested -> nested.in(EnrollmentOrderEntity::getClassId, matchedClassIds)))
                .eq(query.getStudentId() != null, EnrollmentOrderEntity::getStudentId, query.getStudentId())
                .eq(StringUtils.hasText(query.getOrderStatus()), EnrollmentOrderEntity::getOrderStatus, query.getOrderStatus())
                .eq(!oneOnOneView && !groupView && query.getClassId() == null && StringUtils.hasText(query.getPaymentStatus()),
                        EnrollmentOrderEntity::getPaymentStatus, query.getPaymentStatus())
                .orderByDesc(EnrollmentOrderEntity::getCreatedAt);
        // 课程归属存在于 order item 中，不能再用订单级 class_id 判断混合报名单。
        if (query.getClassId() != null) {
            wrapper.and(w -> w.eq(EnrollmentOrderEntity::getClassId, query.getClassId())
                    .or().apply("EXISTS (SELECT 1 FROM enrollment_order_item eoi "
                                    + "WHERE eoi.enrollment_order_id = enrollment_order.id "
                                    + "AND eoi.class_id = {0} AND eoi.is_deleted = 0)", query.getClassId()));
        }
        if (oneOnOneView) {
            // 旧版 1-on-1 订单可能没有 order_item，但一定已经生成 course_package；纯 Credit 充值没有课时包，不能走这个兼容兜底。
            wrapper.apply("(EXISTS (SELECT 1 FROM enrollment_order_item eoi "
                            + "WHERE eoi.enrollment_order_id = enrollment_order.id "
                            + "AND eoi.course_type = 'ONE_ON_ONE' AND eoi.is_deleted = 0) "
                            + "OR (enrollment_order.class_id IS NULL AND NOT EXISTS "
                            + "(SELECT 1 FROM enrollment_order_item eoi2 "
                            + "WHERE eoi2.enrollment_order_id = enrollment_order.id AND eoi2.is_deleted = 0) "
                            + "AND EXISTS (SELECT 1 FROM course_package cp "
                            + "WHERE cp.enrollment_order_id = enrollment_order.id AND cp.is_deleted = 0)))");
        } else if (groupView) {
            wrapper.apply("(enrollment_order.class_id IS NOT NULL OR EXISTS "
                    + "(SELECT 1 FROM enrollment_order_item eoi "
                    + "WHERE eoi.enrollment_order_id = enrollment_order.id "
                    + "AND (eoi.course_type <> 'ONE_ON_ONE' OR eoi.course_type IS NULL) "
                    + "AND eoi.is_deleted = 0))");
        }
        if ((oneOnOneView || groupView || query.getClassId() != null) && StringUtils.hasText(query.getPaymentStatus())) {
            String paymentCondition = paymentStatusItemCondition(query.getPaymentStatus());
            if (paymentCondition != null) {
                wrapper.apply("EXISTS (SELECT 1 FROM enrollment_order_item eoi "
                        + "WHERE eoi.enrollment_order_id = enrollment_order.id "
                        + "AND eoi.is_deleted = 0 AND " + paymentCondition + ")");
            } else if ("REFUNDED".equalsIgnoreCase(query.getPaymentStatus().trim())) {
                wrapper.eq(EnrollmentOrderEntity::getPaymentStatus, "REFUNDED");
            }
        }
        List<EnrollmentOrderEntity> orderRecords;
        IPage<EnrollmentOrderEntity> result = null;
        if (courseItemView) {
            // 课程视图需要先展开课程项再分页，避免同一报名单的多门课挤在一行或让 total 失真。
            orderRecords = enrollmentOrderMapper.selectList(wrapper);
        } else {
            Page<EnrollmentOrderEntity> page = new Page<>(query.getPageNum(), query.getPageSize());
            result = enrollmentOrderMapper.selectPage(page, wrapper);
            orderRecords = result.getRecords();
        }
        if (orderRecords.isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }

        Set<Long> studentIds = orderRecords.stream().map(EnrollmentOrderEntity::getStudentId).collect(Collectors.toSet());
        Map<Long, StudentEntity> studentMap = listStudentsByIds(studentIds);

        List<Long> orderIds = orderRecords.stream()
                .map(EnrollmentOrderEntity::getId)
                .toList();
        List<EnrollmentOrderItemEntity> orderItemEntities = enrollmentOrderItemMapper.selectList(
                new LambdaQueryWrapper<EnrollmentOrderItemEntity>()
                        .in(EnrollmentOrderItemEntity::getEnrollmentOrderId, orderIds)
                        .orderByAsc(EnrollmentOrderItemEntity::getLineNo)
                        .orderByAsc(EnrollmentOrderItemEntity::getId));
        Map<Long, List<EnrollmentOrderItemEntity>> itemEntitiesByOrderId = orderItemEntities.stream()
                .collect(Collectors.groupingBy(EnrollmentOrderItemEntity::getEnrollmentOrderId));
        Map<Long, List<EnrollmentOrderItemVO>> itemsByOrderId = orderItemEntities.stream()
                .collect(Collectors.groupingBy(EnrollmentOrderItemEntity::getEnrollmentOrderId,
                        Collectors.mapping(this::toOrderItemVO, Collectors.toList())));

        List<CoursePackageEntity> allPackages = coursePackageMapper.selectList(
                new LambdaQueryWrapper<CoursePackageEntity>()
                        .in(CoursePackageEntity::getEnrollmentOrderId, orderIds));
        Map<Long, Integer> packageCountMap = allPackages.stream()
                .collect(Collectors.groupingBy(CoursePackageEntity::getEnrollmentOrderId, Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        Map<Long, CoursePackageEntity> firstPackageMap = allPackages.stream()
                .collect(Collectors.groupingBy(CoursePackageEntity::getEnrollmentOrderId,
                        Collectors.collectingAndThen(Collectors.toList(), list -> list.isEmpty() ? null : list.get(0))));

        Set<Long> courseProductIds = orderItemEntities.stream()
                .map(EnrollmentOrderItemEntity::getCourseProductId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        courseProductIds.addAll(allPackages.stream()
                .map(CoursePackageEntity::getCourseProductId)
                .filter(id -> id != null)
                .collect(Collectors.toSet()));
        Map<Long, CourseProductEntity> productMap = courseProductIds.isEmpty()
                ? Collections.emptyMap()
                : courseProductMapper.selectBatchIds(courseProductIds).stream()
                        .collect(Collectors.toMap(CourseProductEntity::getId, p -> p));

        Set<Long> classIds = orderItemEntities.stream()
                .map(EnrollmentOrderItemEntity::getClassId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        orderRecords.stream().map(EnrollmentOrderEntity::getClassId)
                .filter(id -> id != null).forEach(classIds::add);
        Map<Long, ClassGroupEntity> classMap = classIds.isEmpty()
                ? Collections.emptyMap()
                : classGroupMapper.selectBatchIds(classIds).stream()
                        .collect(Collectors.toMap(ClassGroupEntity::getId, c -> c));
        List<EnrollmentOrderParticipantEntity> orderParticipants = orderIds.isEmpty()
                ? Collections.emptyList()
                : enrollmentOrderParticipantMapper.selectList(new LambdaQueryWrapper<EnrollmentOrderParticipantEntity>()
                        .in(EnrollmentOrderParticipantEntity::getEnrollmentOrderId, orderIds)
                        .eq(EnrollmentOrderParticipantEntity::getIsDeleted, 0)
                        .orderByAsc(EnrollmentOrderParticipantEntity::getParticipantIndex)
                        .orderByAsc(EnrollmentOrderParticipantEntity::getId));
        Map<Long, List<EnrollmentOrderParticipantVO>> participantsByOrderId = orderParticipants.stream()
                .collect(Collectors.groupingBy(EnrollmentOrderParticipantEntity::getEnrollmentOrderId,
                        Collectors.mapping(this::toParticipantVO, Collectors.toList())));

        Map<Long, CoursePackageEntity> packageByItemId = allPackages.stream()
                .filter(item -> item.getEnrollmentOrderItemId() != null)
                .collect(Collectors.toMap(CoursePackageEntity::getEnrollmentOrderItemId, item -> item, (left, right) -> left));
        List<EnrollmentOrderListItemVO> allRows = orderRecords.stream()
                .flatMap(order -> {
                    StudentEntity student = studentMap.get(order.getStudentId());
                    List<EnrollmentOrderItemVO> items = itemsByOrderId.getOrDefault(order.getId(), Collections.emptyList());
                    List<EnrollmentOrderParticipantVO> participants = participantsByOrderId.getOrDefault(
                            order.getId(), Collections.emptyList());
                    if (courseItemView) {
                        List<EnrollmentOrderItemEntity> matchingItems = itemEntitiesByOrderId
                                .getOrDefault(order.getId(), Collections.emptyList()).stream()
                                .filter(item -> matchesCourseView(item, order, query, oneOnOneView, groupView))
                                .toList();
                        if (!matchingItems.isEmpty()) {
                            return matchingItems.stream().map(item -> {
                                CoursePackageEntity coursePackage = packageByItemId.get(item.getId());
                                CourseProductEntity product = productMap.get(item.getCourseProductId() != null
                                        ? item.getCourseProductId()
                                        : coursePackage == null ? null : coursePackage.getCourseProductId());
                                if (isOneOnOneItem(item)) {
                                    return toOrderListItem(order, student, coursePackage, product,
                                            packageCountMap.getOrDefault(order.getId(), 0), items, participants, item);
                                }
                                ClassGroupEntity clz = classMap.get(item.getClassId() != null ? item.getClassId() : order.getClassId());
                                return toClassOrderListItem(order, student, clz, items, participants, item);
                            });
                        }
                        // 兼容早期没有 order item 快照的历史订单。
                        if (order.getClassId() != null && groupView) {
                            ClassGroupEntity clz = classMap.get(order.getClassId());
                            return java.util.stream.Stream.of(toClassOrderListItem(order, student, clz, items, participants, null));
                        }
                        if (order.getClassId() == null && oneOnOneView) {
                            CoursePackageEntity firstPackage = firstPackageMap.get(order.getId());
                            CourseProductEntity product = firstPackage == null ? null : productMap.get(firstPackage.getCourseProductId());
                            return java.util.stream.Stream.of(toOrderListItem(order, student, firstPackage, product,
                                    packageCountMap.getOrDefault(order.getId(), 0), items, participants, null));
                        }
                        return java.util.stream.Stream.<EnrollmentOrderListItemVO>empty();
                    }
                    CoursePackageEntity firstPackage = firstPackageMap.get(order.getId());
                    CourseProductEntity product = firstPackage == null ? null : productMap.get(firstPackage.getCourseProductId());
                    return java.util.stream.Stream.of(toOrderListItem(order, student, firstPackage, product,
                            packageCountMap.getOrDefault(order.getId(), 0), items, participants, null));
                })
                .toList();

        if (!courseItemView) {
            return PageResponse.of(allRows, result.getTotal(), result.getCurrent(), result.getSize());
        }
        long fromIndex = Math.max(0, (query.getPageNum() - 1) * query.getPageSize());
        int from = fromIndex >= allRows.size() ? allRows.size() : (int) fromIndex;
        int to = Math.min(allRows.size(), from + Math.toIntExact(query.getPageSize()));
        List<EnrollmentOrderListItemVO> pageRows = allRows.subList(from, to);
        return PageResponse.of(pageRows, allRows.size(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public EnrollmentOrderDetailVO getOrder(Long id) {
        EnrollmentOrderEntity order = requireOrder(id);
        StudentEntity student = requireStudent(order.getStudentId());
        List<EnrollmentOrderItemEntity> orderItems = listOrderItemsByOrderId(id);
        List<Long> sessionItemIds = orderItems.stream()
                .filter(item -> "SESSION".equalsIgnoreCase(item.getPackageUnit()))
                .map(EnrollmentOrderItemEntity::getId).toList();
        Map<Long, Integer> selectedSessionsByItemId = sessionItemIds.isEmpty() ? Map.of()
                : classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .in(ClassEnrollmentEntity::getEnrollmentOrderItemId, sessionItemIds)
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0)
                        .in(ClassEnrollmentEntity::getStatus, "ACTIVE", "EXPIRED")
                        .orderByDesc(ClassEnrollmentEntity::getId)).stream()
                        .filter(enrollment -> enrollment.getPurchasedQuantity() != null)
                        .collect(Collectors.toMap(ClassEnrollmentEntity::getEnrollmentOrderItemId,
                                ClassEnrollmentEntity::getPurchasedQuantity, (newer, older) -> newer));
        List<PaymentRecordEntity> payments = listPaymentsByOrderId(id);
        List<ReceiptRecordEntity> receipts = listReceiptsByOrderIds(List.of(id));
        List<EnrollmentOrderParticipantEntity> participants = enrollmentOrderParticipantMapper.selectList(
                new LambdaQueryWrapper<EnrollmentOrderParticipantEntity>()
                        .eq(EnrollmentOrderParticipantEntity::getEnrollmentOrderId, id)
                        .eq(EnrollmentOrderParticipantEntity::getIsDeleted, 0)
                        .orderByAsc(EnrollmentOrderParticipantEntity::getParticipantIndex));

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
                .studentClassSnapshot(order.getStudentClassSnapshot())
                .gradeSnapshot(order.getGradeSnapshot())
                .schoolSnapshot(order.getSchoolSnapshot())
                .parentPhoneSnapshot(order.getParentPhoneSnapshot())
                .parentPhone2Snapshot(order.getParentPhone2Snapshot())
                .pickupRequiredSnapshot(Boolean.TRUE.equals(order.getPickupRequiredSnapshot()))
                .pickupAddressSnapshot(order.getPickupAddressSnapshot())
                .dropoffRequiredSnapshot(Boolean.TRUE.equals(order.getDropoffRequiredSnapshot()))
                .dropoffAddressSnapshot(order.getDropoffAddressSnapshot())
                .currencyCode(order.getCurrencyCode())
                .billingPolicy(order.getBillingPolicy())
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
                .items(orderItems.stream().map(item -> toOrderItemVO(item,
                        selectedSessionsByItemId.get(item.getId()))).toList())
                .participants(participants.stream().map(this::toParticipantVO).toList())
                .payments(payments.stream().map(this::toPaymentVO).toList())
                .packages(packages.stream().map(item -> toPackageListItem(item, student, primaryTeacherNameMap.get(item.getId()), productMap.get(item.getCourseProductId()))).toList())
                .receipts(receipts.stream().map(item -> toReceiptVO(item, student.getStudentName())).toList())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private EnrollmentOrderParticipantVO toParticipantVO(EnrollmentOrderParticipantEntity item) {
        return new EnrollmentOrderParticipantVO(
                item.getId(), item.getStudentId(), item.getParticipantIndex(),
                item.getStudentNameSnapshot(), item.getStudentNoSnapshot(), item.getGradeSnapshot(),
                item.getSchoolSnapshot(), item.getStudentClassSnapshot(), item.getGuardianNameSnapshot(),
                item.getGuardianPhoneSnapshot(), item.getGuardianPhone2Snapshot(), item.getGuardianEmailSnapshot(),
                item.getPickupRequiredSnapshot(), item.getPickupAddressSnapshot(),
                item.getDropoffRequiredSnapshot(), item.getDropoffAddressSnapshot(),
                item.getGrossAmount(), item.getDiscountAmount(), item.getDiscountDetail(),
                item.getAmountDue(), item.getPaidAmount(), item.getBalanceAmount());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnrollmentCreateResultVO createOrder(EnrollmentCreateRequest request) {
        return createOrder(request, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnrollmentCreateResultVO createOrder(EnrollmentCreateRequest request, CurrentUserResponse operator) {
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
        if (operator != null) {
            order.setRegistrationOperatorUserId(operator.userId());
            order.setRegistrationOperatorName(operator.displayName());
        }
        order.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode() : DEFAULT_CURRENCY);
        order.setBillingPolicy(WholeDollarBilling.POLICY);
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
            // 课程级付款分摊（单课程订单全额计入唯一课程项）
            paymentAllocationService.replayAllocations(order);
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
        WholeDollarBilling.requireInteger(request.getOriginalAmount(), "Course price");
        WholeDollarBilling.requireInteger(request.getDiscountAmount(), "Discount");
        WholeDollarBilling.requireInteger(request.getReceivedAmount(), "Received amount");
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

    private String resolveTeachingMode(EnrollmentOrderPageQuery query) {
        if (StringUtils.hasText(query.getTeachingMode())) {
            return query.getTeachingMode().trim().toUpperCase();
        }
        return query.isExcludeClassOrders() ? "ONE_ON_ONE" : null;
    }

    private boolean matchesCourseView(EnrollmentOrderItemEntity item,
                                      EnrollmentOrderEntity order,
                                      EnrollmentOrderPageQuery query,
                                      boolean oneOnOneView,
                                      boolean groupView) {
        if (query.getClassId() != null) {
            return Objects.equals(item.getClassId(), query.getClassId());
        }
        if (oneOnOneView) {
            return isOneOnOneItem(item);
        }
        if (groupView) {
            return !isOneOnOneItem(item);
        }
        return order.getClassId() != null ? Objects.equals(item.getClassId(), order.getClassId()) : isOneOnOneItem(item);
    }

    private boolean isOneOnOneItem(EnrollmentOrderItemEntity item) {
        return item != null && "ONE_ON_ONE".equalsIgnoreCase(item.getCourseType());
    }

    private String paymentStatusItemCondition(String paymentStatus) {
        return switch (paymentStatus.trim().toUpperCase()) {
            case "PAID" -> "COALESCE(eoi.amount_due, 0) <= COALESCE(eoi.paid_amount, 0)";
            case "UNPAID" -> "COALESCE(eoi.amount_due, 0) > 0 AND COALESCE(eoi.paid_amount, 0) <= 0";
            case "PARTIAL" -> "COALESCE(eoi.paid_amount, 0) > 0 AND COALESCE(eoi.paid_amount, 0) < COALESCE(eoi.amount_due, 0)";
            default -> null;
        };
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
                                                      List<EnrollmentOrderItemVO> items,
                                                      List<EnrollmentOrderParticipantVO> participants,
                                                      EnrollmentOrderItemEntity courseItem) {
        BigDecimal originalAmount = courseItem == null
                ? order.getOriginalAmount()
                : defaultAmount(courseItem.getOriginalPriceAmount() == null
                ? courseItem.getPriceAmount() : courseItem.getOriginalPriceAmount());
        BigDecimal discountAmount = courseItem == null ? order.getDiscountAmount() : defaultAmount(courseItem.getDiscountAmount());
        BigDecimal receivedAmount = courseItem == null ? order.getReceivedAmount() : defaultAmount(courseItem.getPaidAmount());
        BigDecimal amountDue = courseItem == null ? order.getAmountDue() : defaultAmount(courseItem.getAmountDue());
        BigDecimal balanceAmount = courseItem == null ? order.getBalanceAmount() : defaultAmount(courseItem.getBalanceAmount());
        Long courseProductId = courseItem != null && courseItem.getCourseProductId() != null
                ? courseItem.getCourseProductId() : firstPackage == null ? null : firstPackage.getCourseProductId();
        String courseProductName = product == null ? null : product.getProductName();
        return EnrollmentOrderListItemVO.builder()
                .id(order.getId())
                .orderItemId(courseItem == null ? null : courseItem.getId())
                .enrollmentNo(order.getEnrollmentNo())
                .studentId(order.getStudentId())
                .studentName(student == null ? null : student.getStudentName())
                .courseProductId(courseProductId)
                .courseProductName(courseProductName)
                .courseType(courseItem == null ? null : courseItem.getCourseType())
                .campusId(order.getCampusId())
                .campusNameSnapshot(order.getCampusNameSnapshot())
                .handledByName(order.getHandledByName())
                .signupDate(order.getSignupDate())
                .periodStartDate(order.getPeriodStartDate())
                .periodEndDate(order.getPeriodEndDate())
                .studentNameSnapshot(order.getStudentNameSnapshot())
                .studentClassSnapshot(order.getStudentClassSnapshot())
                .gradeSnapshot(order.getGradeSnapshot())
                .schoolSnapshot(order.getSchoolSnapshot())
                .parentPhoneSnapshot(order.getParentPhoneSnapshot())
                .parentPhone2Snapshot(order.getParentPhone2Snapshot())
                .pickupRequiredSnapshot(Boolean.TRUE.equals(order.getPickupRequiredSnapshot()))
                .pickupAddressSnapshot(order.getPickupAddressSnapshot())
                .dropoffRequiredSnapshot(Boolean.TRUE.equals(order.getDropoffRequiredSnapshot()))
                .dropoffAddressSnapshot(order.getDropoffAddressSnapshot())
                .currencyCode(order.getCurrencyCode())
                .billingPolicy(order.getBillingPolicy())
                .tuitionSubtotalAmount(order.getTuitionSubtotalAmount())
                .pickupFeeAmount(order.getPickupFeeAmount())
                .dropoffFeeAmount(order.getDropoffFeeAmount())
                .extraFeeAmount(order.getExtraFeeAmount())
                .originalAmount(originalAmount)
                .discountAmount(discountAmount)
                .receivedAmount(receivedAmount)
                .amountDue(amountDue)
                .balanceAmount(balanceAmount)
                .paymentStatus(courseItem == null ? order.getPaymentStatus() : resolvePaymentStatus(amountDue, ZERO_AMOUNT, receivedAmount))
                .orderStatus(order.getOrderStatus())
                .packageCount(packageCount)
                .items(items)
                .participants(participants)
                .createdAt(order.getCreatedAt())
                .build();
    }

    private EnrollmentOrderListItemVO toClassOrderListItem(EnrollmentOrderEntity order,
                                                           StudentEntity student,
                                                           ClassGroupEntity clz,
                                                           List<EnrollmentOrderItemVO> items,
                                                           List<EnrollmentOrderParticipantVO> participants,
                                                           EnrollmentOrderItemEntity courseItem) {
        BigDecimal originalAmount = courseItem == null
                ? order.getOriginalAmount()
                : defaultAmount(courseItem.getOriginalPriceAmount() == null
                ? courseItem.getPriceAmount() : courseItem.getOriginalPriceAmount());
        BigDecimal discountAmount = courseItem == null ? order.getDiscountAmount() : defaultAmount(courseItem.getDiscountAmount());
        BigDecimal receivedAmount = courseItem == null ? order.getReceivedAmount() : defaultAmount(courseItem.getPaidAmount());
        BigDecimal amountDue = courseItem == null ? order.getAmountDue() : defaultAmount(courseItem.getAmountDue());
        BigDecimal balanceAmount = courseItem == null ? order.getBalanceAmount() : defaultAmount(courseItem.getBalanceAmount());
        return EnrollmentOrderListItemVO.builder()
                .id(order.getId())
                .orderItemId(courseItem == null ? null : courseItem.getId())
                .enrollmentNo(order.getEnrollmentNo())
                .studentId(order.getStudentId())
                .studentName(student == null ? null : student.getStudentName())
                .classId(courseItem != null && courseItem.getClassId() != null ? courseItem.getClassId() : order.getClassId())
                .className(clz == null ? null : clz.getClassName())
                .courseProductId(courseItem == null ? null : courseItem.getCourseProductId())
                .courseType(courseItem == null ? null : courseItem.getCourseType())
                .campusId(order.getCampusId())
                .campusNameSnapshot(order.getCampusNameSnapshot())
                .handledByName(order.getHandledByName())
                .signupDate(order.getSignupDate())
                .periodStartDate(order.getPeriodStartDate())
                .periodEndDate(order.getPeriodEndDate())
                .studentNameSnapshot(order.getStudentNameSnapshot())
                .studentClassSnapshot(order.getStudentClassSnapshot())
                .gradeSnapshot(order.getGradeSnapshot())
                .schoolSnapshot(order.getSchoolSnapshot())
                .parentPhoneSnapshot(order.getParentPhoneSnapshot())
                .parentPhone2Snapshot(order.getParentPhone2Snapshot())
                .pickupRequiredSnapshot(Boolean.TRUE.equals(order.getPickupRequiredSnapshot()))
                .pickupAddressSnapshot(order.getPickupAddressSnapshot())
                .dropoffRequiredSnapshot(Boolean.TRUE.equals(order.getDropoffRequiredSnapshot()))
                .dropoffAddressSnapshot(order.getDropoffAddressSnapshot())
                .currencyCode(order.getCurrencyCode())
                .billingPolicy(order.getBillingPolicy())
                .tuitionSubtotalAmount(order.getTuitionSubtotalAmount())
                .pickupFeeAmount(order.getPickupFeeAmount())
                .dropoffFeeAmount(order.getDropoffFeeAmount())
                .extraFeeAmount(order.getExtraFeeAmount())
                .originalAmount(originalAmount)
                .discountAmount(discountAmount)
                .receivedAmount(receivedAmount)
                .amountDue(amountDue)
                .balanceAmount(balanceAmount)
                .paymentStatus(courseItem == null ? order.getPaymentStatus() : resolvePaymentStatus(amountDue, ZERO_AMOUNT, receivedAmount))
                .orderStatus(order.getOrderStatus())
                .packageCount(0)
                .items(items)
                .participants(participants)
                .createdAt(order.getCreatedAt())
                .build();
    }

    private EnrollmentOrderItemVO toOrderItemVO(EnrollmentOrderItemEntity item) {
        return toOrderItemVO(item, null);
    }

    private EnrollmentOrderItemVO toOrderItemVO(EnrollmentOrderItemEntity item, Integer selectedSessions) {
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
                .sessions(selectedSessions)
                .priceAmount(item.getPriceAmount())
                .originalPriceAmount(item.getOriginalPriceAmount())
                .manualPriceOverride(Boolean.TRUE.equals(item.getManualPriceOverride()))
                .discountAmount(item.getDiscountAmount())
                .amountDue(item.getAmountDue())
                .paidAmount(item.getPaidAmount())
                .balanceAmount(item.getBalanceAmount())
                .oneOnOneHours(item.getOneOnOneHours())
                .oneOnOneUnits(item.getOneOnOneUnits())
                .pickupRequiredSnapshot(Boolean.TRUE.equals(item.getPickupRequiredSnapshot()))
                .pickupAddressSnapshot(item.getPickupAddressSnapshot())
                .dropoffRequiredSnapshot(Boolean.TRUE.equals(item.getDropoffRequiredSnapshot()))
                .dropoffAddressSnapshot(item.getDropoffAddressSnapshot())
                .pickupFeeAmount(item.getPickupFeeAmount())
                .dropoffFeeAmount(item.getDropoffFeeAmount())
                .extraFeeAmount(item.getExtraFeeAmount())
                .extraFeeDetail(item.getExtraFeeDetail())
                .itemStatus(item.getItemStatus())
                .stoppedConsumedAmount(item.getStoppedConsumedAmount())
                .stoppedSessionCount(item.getStoppedSessionCount())
                .stoppedAt(item.getStoppedAt())
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
                .participantIndex(item.getParticipantIndex())
                .paymentMethod(item.getPaymentMethod())
                .paymentType(item.getPaymentType())
                .paymentStatus(item.getPaymentStatus())
                .amount(item.getAmount())
                .paidAt(item.getPaidAt())
                .handledByName(item.getHandledByName())
                .remark(item.getRemark())
                .allocations(paymentAllocationMapper.selectList(new LambdaQueryWrapper<PaymentAllocationEntity>()
                                .eq(PaymentAllocationEntity::getPaymentRecordId, item.getId()))
                        .stream()
                        .map(allocation -> new PaymentRecordVO.CourseAllocationVO(
                                allocation.getEnrollmentOrderItemId(), allocation.getAmount()))
                        .toList())
                .build();
    }

    private ReceiptRecordVO toReceiptVO(ReceiptRecordEntity item, String studentName) {
        return ReceiptRecordVO.builder()
                .id(item.getId())
                .receiptNo(item.getReceiptNo())
                .enrollmentOrderId(item.getEnrollmentOrderId())
                .studentId(item.getStudentId())
                .paymentRecordId(item.getPaymentRecordId())
                .enrollmentOrderActive(true)
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
        if (WholeDollarBilling.appliesTo(order)) {
            validateWholeDollarCorrection(request);
        }
        if ("CANCELLED".equals(order.getOrderStatus())) {
            throw new BusinessException("ORDER_CANCELLED_NOT_EDITABLE",
                    "Cancelled orders cannot be edited.");
        }

        StudentEntity student = requireStudent(order.getStudentId());
        if (StringUtils.hasText(request.getStudentName())) {
            order.setStudentNameSnapshot(request.getStudentName().trim());
            student.setStudentName(request.getStudentName().trim());
        }
        if (request.getGrade() != null) {
            order.setGradeSnapshot(request.getGrade().trim());
            student.setGrade(request.getGrade().trim());
        }
        if (request.getStudentClass() != null) {
            order.setStudentClassSnapshot(request.getStudentClass().trim());
            student.setStudentClass(request.getStudentClass().trim());
        }
        if (request.getSchool() != null) {
            order.setSchoolSnapshot(request.getSchool().trim());
            student.setSchool(request.getSchool().trim());
        }
        if (request.getGuardianPhone() != null) {
            order.setParentPhoneSnapshot(request.getGuardianPhone().trim());
            student.setGuardianPhone(request.getGuardianPhone().trim());
        }
        if (request.getGuardianPhone2() != null) {
            order.setParentPhone2Snapshot(request.getGuardianPhone2().trim());
            student.setGuardianPhone2(request.getGuardianPhone2().trim());
        }
        boolean campusChanged = request.getCampusName() != null
                && (!Objects.equals(order.getCampusNameSnapshot(), request.getCampusName().trim())
                    || (order.getCampusId() != null && request.getCampusId() != null
                        && !Objects.equals(order.getCampusId(), request.getCampusId())));
        if (request.getCampusName() != null) {
            order.setCampusNameSnapshot(request.getCampusName().trim());
            order.setCampusId(request.getCampusId());
        }
        if (request.getPickupRequired() != null) {
            order.setPickupRequiredSnapshot(request.getPickupRequired());
            student.setPickupRequired(request.getPickupRequired());
        }
        if (request.getPickupAddress() != null) {
            order.setPickupAddressSnapshot(request.getPickupAddress().trim());
            student.setPickupAddress(request.getPickupAddress().trim());
        }
        if (request.getDropoffRequired() != null) {
            order.setDropoffRequiredSnapshot(request.getDropoffRequired());
            student.setDropoffRequired(request.getDropoffRequired());
        }
        if (request.getDropoffAddress() != null) {
            order.setDropoffAddressSnapshot(request.getDropoffAddress().trim());
            student.setDropoffAddress(request.getDropoffAddress().trim());
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

        // 课程、价格、课包和班级报名必须跟原订单行一起更正，不能重新创建报名单。
        if (request.getParticipants() != null && !request.getParticipants().isEmpty()) {
            applyCorrectedParticipants(order, request.getParticipants());
        }
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            Long targetCampusId = request.getCampusId() != null ? request.getCampusId() : order.getCampusId();
            applyCorrectedItems(order, student, request.getItems(), targetCampusId, campusChanged);
        }
        if (request.getParticipantBillings() != null && !request.getParticipantBillings().isEmpty()) {
            reconcileCorrectedParticipantPayments(order, request.getParticipantBillings());
        } else if (request.getPayments() != null) {
            reconcileCorrectedPayments(order, request.getPayments());
        }
        boolean moneyChanged = request.getItems() != null || request.getPayments() != null
                || request.getParticipantBillings() != null
                || request.getExtraFees() != null || request.getDiscounts() != null
                || request.getPickupFeeAmount() != null || request.getDropoffFeeAmount() != null;
        if (moneyChanged) {
            applyCorrectedMoney(order, request);
        }
        studentMapper.updateById(student);
        enrollmentOrderMapper.updateById(order);
        // 更正金额相关内容后重放课程级分摊：价格/附加费/折扣变化需重算课程项应收，
        // 仅付款金额变化也需重放（课程分摊随付款金额演进）。
        if (moneyChanged) {
            boolean priceChanged = request.getItems() != null || request.getExtraFees() != null
                    || request.getDiscounts() != null
                    || request.getPickupFeeAmount() != null || request.getDropoffFeeAmount() != null;
            if (priceChanged) {
                invalidateItemAmountDue(order.getId());
            }
            Long allocationParticipantCount = enrollmentOrderParticipantMapper.selectCount(
                    new LambdaQueryWrapper<EnrollmentOrderParticipantEntity>()
                            .eq(EnrollmentOrderParticipantEntity::getEnrollmentOrderId, order.getId())
                            .eq(EnrollmentOrderParticipantEntity::getIsDeleted, 0));
            if (allocationParticipantCount == null || allocationParticipantCount <= 1) {
                paymentAllocationService.replayAllocations(order);
            }
        }
        if (request.getParticipantBillings() != null && !request.getParticipantBillings().isEmpty()) {
            // 先完成订单级金额重算，再写参与人快照，确保课程级接送/服务费使用本次更正后的值。
            applyCorrectedParticipantBillingSnapshots(order, request.getParticipantBillings());
        }
        // updateById skips null fields, so a campus switch to a no-id campus (e.g. Online)
        // would keep the stale campus_id. Apply the campus snapshot explicitly so it can be nulled.
        if (campusChanged) {
            enrollmentOrderMapper.update(null, new LambdaUpdateWrapper<EnrollmentOrderEntity>()
                    .eq(EnrollmentOrderEntity::getId, order.getId())
                    .set(EnrollmentOrderEntity::getCampusNameSnapshot, order.getCampusNameSnapshot())
                    .set(EnrollmentOrderEntity::getCampusId, order.getCampusId()));
        }

        // Keep every saved receipt snapshot in sync so print/preview and the order detail agree.
        syncReceiptSnapshots(order, request.getCampusAddress(), request.getCampusPhone(), request.getTaxId(), request);
    }

    /** 新订单更正仍遵守原订单的整美元计价契约；旧订单不受此校验影响。 */
    private void validateWholeDollarCorrection(EnrollmentOrderCorrectionRequest request) {
        WholeDollarBilling.requireInteger(request.getPickupFeeAmount(), "Pickup fee");
        WholeDollarBilling.requireInteger(request.getDropoffFeeAmount(), "Drop-off fee");
        WholeDollarBilling.requireInteger(request.getExtraFeeAmount(), "Extra fee");
        WholeDollarBilling.requireInteger(request.getDiscountAmount(), "Discount");
        validateWholeDollarLines(request.getExtraFees());
        validateWholeDollarLines(request.getDiscounts());
        validateWholeDollarPayments(request.getPayments());
        if (request.getItems() != null) {
            for (RegistrationIntakeRequest.CourseItemPayload item : request.getItems()) {
                if (item == null) continue;
                WholeDollarBilling.requireInteger(item.getPriceAmount(), "Course price");
                WholeDollarBilling.requireInteger(item.getDiscountAmount(), "Course discount");
                WholeDollarBilling.requireInteger(item.getPickupFeeAmount(), "Course pickup fee");
                WholeDollarBilling.requireInteger(item.getDropoffFeeAmount(), "Course drop-off fee");
                validateWholeDollarLines(item.getServiceFees());
            }
        }
        if (request.getParticipantBillings() != null) {
            for (RegistrationIntakeRequest.ParticipantBillingPayload billing : request.getParticipantBillings()) {
                if (billing == null) continue;
                WholeDollarBilling.requireInteger(billing.getDiscountAmount(), "Participant discount");
                validateWholeDollarLines(billing.getDiscounts());
                validateWholeDollarPayments(billing.getPayments());
            }
        }
    }

    private void validateWholeDollarLines(List<RegistrationIntakeRequest.FeeLinePayload> lines) {
        if (lines == null) return;
        for (RegistrationIntakeRequest.FeeLinePayload line : lines) {
            if (line != null) WholeDollarBilling.requireInteger(line.getAmount(), "Fee or discount");
        }
    }

    private void validateWholeDollarPayments(List<RegistrationIntakeRequest.PaymentPayload> payments) {
        if (payments == null) return;
        for (RegistrationIntakeRequest.PaymentPayload payment : payments) {
            if (payment == null) continue;
            WholeDollarBilling.requireInteger(payment.getAmount(), "Payment");
            if (payment.getAllocations() != null) {
                for (RegistrationIntakeRequest.CoursePaymentAllocationPayload allocation : payment.getAllocations()) {
                    if (allocation != null) WholeDollarBilling.requireInteger(allocation.getAmount(), "Course payment allocation");
                }
            }
        }
    }

    /**
     * 更新共享私教的独立学生资料。这里不改变成员关系或课包归属，避免编辑收据时意外创建第二个学生。
     */
    private void applyCorrectedParticipants(EnrollmentOrderEntity order,
                                            List<RegistrationIntakeRequest.StudentPayload> payloads) {
        List<EnrollmentOrderParticipantEntity> stored = enrollmentOrderParticipantMapper.selectList(
                new LambdaQueryWrapper<EnrollmentOrderParticipantEntity>()
                        .eq(EnrollmentOrderParticipantEntity::getEnrollmentOrderId, order.getId())
                        .eq(EnrollmentOrderParticipantEntity::getIsDeleted, 0)
                        .orderByAsc(EnrollmentOrderParticipantEntity::getParticipantIndex));
        if (stored.isEmpty() || stored.size() != payloads.size()) {
            throw new BusinessException("PRIVATE_LESSON_PARTICIPANTS_MISMATCH",
                    "The saved private lesson participants cannot be changed from this receipt.");
        }
        for (int index = 0; index < payloads.size(); index++) {
            RegistrationIntakeRequest.StudentPayload payload = payloads.get(index);
            EnrollmentOrderParticipantEntity participant = stored.get(index);
            if (payload == null || !Objects.equals(payload.getStudentId(), participant.getStudentId())) {
                throw new BusinessException("PRIVATE_LESSON_PARTICIPANT_ID_MISMATCH",
                        "A receipt edit cannot replace a private lesson participant.");
            }
            StudentEntity student = requireStudent(participant.getStudentId());
            applyCorrectedStudentProfile(student, payload);
            studentMapper.updateById(student);
            copyParticipantSnapshot(participant, student);
            enrollmentOrderParticipantMapper.updateById(participant);
            if (index == 0) {
                order.setStudentNameSnapshot(student.getStudentName());
                order.setStudentClassSnapshot(student.getStudentClass());
                order.setGradeSnapshot(student.getGrade());
                order.setSchoolSnapshot(student.getSchool());
                order.setParentPhoneSnapshot(student.getGuardianPhone());
                order.setParentPhone2Snapshot(student.getGuardianPhone2());
                order.setPickupRequiredSnapshot(student.getPickupRequired());
                order.setPickupAddressSnapshot(student.getPickupAddress());
                order.setDropoffRequiredSnapshot(student.getDropoffRequired());
                order.setDropoffAddressSnapshot(student.getDropoffAddress());
            }
        }
    }

    private void applyCorrectedStudentProfile(StudentEntity student,
                                              RegistrationIntakeRequest.StudentPayload payload) {
        if (payload.getStudentName() != null) student.setStudentName(payload.getStudentName().trim());
        if (payload.getEnglishName() != null) student.setEnglishName(payload.getEnglishName());
        if (payload.getGender() != null) student.setGender(payload.getGender());
        if (payload.getBirthDate() != null) student.setBirthDate(payload.getBirthDate());
        if (payload.getGrade() != null) student.setGrade(payload.getGrade());
        if (payload.getStudentClass() != null) student.setStudentClass(payload.getStudentClass());
        if (payload.getSchool() != null) student.setSchool(payload.getSchool());
        if (payload.getGuardianName() != null) student.setGuardianName(payload.getGuardianName());
        if (payload.getGuardianPhone() != null) student.setGuardianPhone(payload.getGuardianPhone());
        if (payload.getGuardianPhone2() != null) student.setGuardianPhone2(payload.getGuardianPhone2());
        if (payload.getGuardianEmail() != null) student.setGuardianEmail(payload.getGuardianEmail());
        if (payload.getGuardianRelationType() != null) student.setGuardianRelationType(payload.getGuardianRelationType());
        if (payload.getPickupRequired() != null) student.setPickupRequired(payload.getPickupRequired());
        if (payload.getPickupAddress() != null) student.setPickupAddress(payload.getPickupAddress());
        if (payload.getDropoffRequired() != null) student.setDropoffRequired(payload.getDropoffRequired());
        if (payload.getDropoffAddress() != null) student.setDropoffAddress(payload.getDropoffAddress());
    }

    private void copyParticipantSnapshot(EnrollmentOrderParticipantEntity target, StudentEntity student) {
        target.setStudentNameSnapshot(student.getStudentName());
        target.setStudentNoSnapshot(student.getStudentNo());
        target.setGradeSnapshot(student.getGrade());
        target.setSchoolSnapshot(student.getSchool());
        target.setStudentClassSnapshot(student.getStudentClass());
        target.setGuardianNameSnapshot(student.getGuardianName());
        target.setGuardianPhoneSnapshot(student.getGuardianPhone());
        target.setGuardianPhone2Snapshot(student.getGuardianPhone2());
        target.setGuardianEmailSnapshot(student.getGuardianEmail());
        target.setPickupRequiredSnapshot(student.getPickupRequired());
        target.setPickupAddressSnapshot(student.getPickupAddress());
        target.setDropoffRequiredSnapshot(student.getDropoffRequired());
        target.setDropoffAddressSnapshot(student.getDropoffAddress());
    }

    private void reconcileCorrectedParticipantPayments(EnrollmentOrderEntity order,
                                                       List<RegistrationIntakeRequest.ParticipantBillingPayload> billings) {
        List<PaymentRecordEntity> existing = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecordEntity>()
                        .eq(PaymentRecordEntity::getEnrollmentOrderId, order.getId())
                        .eq(PaymentRecordEntity::getPaymentType, "PAYMENT")
                        .ne(PaymentRecordEntity::getPaymentStatus, "VOIDED")
                        .orderByAsc(PaymentRecordEntity::getPaidAt)
                        .orderByAsc(PaymentRecordEntity::getId));
        for (RegistrationIntakeRequest.ParticipantBillingPayload billing : billings) {
            if (billing == null || billing.getParticipantIndex() == null) continue;
            List<PaymentRecordEntity> participantPayments = existing.stream()
                    .filter(item -> Objects.equals(item.getParticipantIndex(), billing.getParticipantIndex()))
                    .toList();
            List<RegistrationIntakeRequest.PaymentPayload> payloads = billing.getPayments() == null
                    ? Collections.emptyList() : billing.getPayments();
            for (int i = 0; i < Math.max(participantPayments.size(), payloads.size()); i++) {
                if (i < payloads.size()) {
                    RegistrationIntakeRequest.PaymentPayload payload = payloads.get(i);
                    PaymentRecordEntity payment = i < participantPayments.size()
                            ? participantPayments.get(i) : new PaymentRecordEntity();
                    if (payment.getId() == null) {
                        payment.setEnrollmentOrderId(order.getId());
                        payment.setPaymentNo(BusinessNoGenerator.generatePaymentNo());
                        payment.setPaymentType("PAYMENT");
                    }
                    payment.setParticipantIndex(billing.getParticipantIndex());
                    payment.setPaymentMethod(payload.getPaymentMethod());
                    payment.setPaymentStatus("PAID");
                    payment.setAmount(defaultAmount(payload.getAmount()));
                    payment.setPaidAt(payload.getPaidAt() == null ? AppTime.nowLocalDateTime() : payload.getPaidAt());
                    payment.setHandledByUserId(payload.getHandledByUserId());
                    payment.setHandledByName(payload.getHandledByName());
                    payment.setRemark(payload.getRemark());
                    if (payment.getId() == null) paymentRecordMapper.insert(payment);
                    else paymentRecordMapper.updateById(payment);
                } else {
                    PaymentRecordEntity payment = participantPayments.get(i);
                    payment.setPaymentStatus("VOIDED");
                    payment.setAmount(ZERO_AMOUNT);
                    paymentRecordMapper.updateById(payment);
                }
            }
        }
    }

    private void applyCorrectedParticipantBillingSnapshots(
            EnrollmentOrderEntity order,
            List<RegistrationIntakeRequest.ParticipantBillingPayload> billings) {
        List<EnrollmentOrderParticipantEntity> participants = enrollmentOrderParticipantMapper.selectList(
                new LambdaQueryWrapper<EnrollmentOrderParticipantEntity>()
                        .eq(EnrollmentOrderParticipantEntity::getEnrollmentOrderId, order.getId())
                        .eq(EnrollmentOrderParticipantEntity::getIsDeleted, 0));
        BigDecimal sharedFees = defaultAmount(order.getPickupFeeAmount())
                .add(defaultAmount(order.getDropoffFeeAmount()))
                .add(defaultAmount(order.getExtraFeeAmount()));
        for (EnrollmentOrderParticipantEntity participant : participants) {
            RegistrationIntakeRequest.ParticipantBillingPayload billing = billings.stream()
                    .filter(item -> item != null && Objects.equals(item.getParticipantIndex(), participant.getParticipantIndex()))
                    .findFirst().orElse(null);
            if (billing == null) continue;
            BigDecimal discount = defaultAmount(billing.getDiscountAmount() == null
                    ? sumFeeLines(billing.getDiscounts()) : billing.getDiscountAmount());
            BigDecimal requestedPaid = billing.getPayments() == null ? ZERO_AMOUNT : billing.getPayments().stream()
                    .filter(Objects::nonNull)
                    .map(RegistrationIntakeRequest.PaymentPayload::getAmount)
                    .map(this::defaultAmount)
                    .filter(amount -> amount.compareTo(ZERO_AMOUNT) > 0)
                    .reduce(ZERO_AMOUNT, BigDecimal::add);
            BigDecimal paid = requestedPaid.add(sumActiveRefunds(order.getId(), participant.getParticipantIndex()))
                    .max(ZERO_AMOUNT);
            BigDecimal fees = Objects.equals(participant.getParticipantIndex(), 1) ? sharedFees : ZERO_AMOUNT;
            BigDecimal due = defaultAmount(participant.getGrossAmount()).add(fees).subtract(discount)
                    .max(ZERO_AMOUNT).setScale(2, RoundingMode.HALF_UP);
            participant.setDiscountAmount(discount);
            participant.setDiscountDetail(jsonHelper.toJson(billing.getDiscounts()));
            participant.setAmountDue(due);
            participant.setPaidAmount(paid);
            participant.setBalanceAmount(due.subtract(paid).setScale(2, RoundingMode.HALF_UP));
            enrollmentOrderParticipantMapper.updateById(participant);
        }
    }

    /**
     * 将原订单项更新到新的课程/班级，并迁移班课在册关系。历史课次不改写，未来课次通过新的
     * class_enrollment 关系自然进入新班级；1 对 1 课包只调整总量，已消耗课时永远保留。
     */
    private void applyCorrectedItems(EnrollmentOrderEntity order,
                                     StudentEntity student,
                                     List<RegistrationIntakeRequest.CourseItemPayload> payloads,
                                     Long campusId,
                                     boolean validateCampus) {
        List<EnrollmentOrderItemEntity> existingItems = listOrderItemsByOrderId(order.getId());
        Map<Long, EnrollmentOrderItemEntity> itemMap = existingItems.stream()
                .collect(Collectors.toMap(EnrollmentOrderItemEntity::getId, item -> item));
        for (RegistrationIntakeRequest.CourseItemPayload payload : payloads) {
            if (payload.getOrderItemId() == null) {
                throw new BusinessException("CORRECTION_ORDER_ITEM_REQUIRED", "Each edited course row must identify its original order item.");
            }
            EnrollmentOrderItemEntity item = itemMap.get(payload.getOrderItemId());
            if (item == null) {
                throw new BusinessException("CORRECTION_ORDER_ITEM_NOT_FOUND", "Edited course row does not belong to this order.");
            }
            ClassItemBefore previous = new ClassItemBefore(item.getLineNo(), item.getClassId(), item.getStartDate(),
                    item.getPackageUnit(), item.getPackageQuantity(), item.getCourseProductId(), item.getGradeBand());
            Long oldClassId = item.getClassId();
            if (isGroupItem(item) && payload.getClassId() == null) {
                throw new BusinessException("GRADE_CLASS_REQUIRED",
                        "Select a class for each group-class course before saving the receipt.");
            }
            boolean classChanged = !Objects.equals(oldClassId, payload.getClassId());
            if (payload.getClassId() != null && (validateCampus || (classChanged && campusId != null))) {
                ClassGroupEntity selectedClass = classGroupMapper.selectById(payload.getClassId());
                if (validateCampus && (campusId == null || selectedClass == null || selectedClass.getCampusId() == null
                        || !Objects.equals(campusId, selectedClass.getCampusId()))) {
                    throw new BusinessException("CLASS_CAMPUS_MISMATCH",
                            "Selected class does not belong to the selected registration campus. Choose a class for that campus.");
                }
            }
            copyCorrectionItem(item, payload);
            enrollmentOrderItemMapper.updateById(item);
            persistCorrectedItemTransportSnapshot(item, payload);
            if (isGroupItem(item)) {
                migrateCorrectedClassEnrollment(order, student, item, previous,
                        payload.getSessions(), payload.getPurchasedMonths());
            } else {
                updateCorrectedOneOnOnePackage(order, item);
            }
        }
        order.setClassId(resolveCorrectedOrderClassId(existingItems));
    }

    private void copyCorrectionItem(EnrollmentOrderItemEntity item,
                                     RegistrationIntakeRequest.CourseItemPayload payload) {
        item.setLineNo(payload.getLineNo() == null ? item.getLineNo() : payload.getLineNo());
        item.setRegisterClass(payload.getRegisterClass());
        item.setCourseType(payload.getCourseType());
        item.setCourseProductId(payload.getCourseProductId());
        item.setCoursePriceOptionId(payload.getCoursePriceOptionId());
        item.setClassId(payload.getClassId());
        item.setDetail(payload.getDetail());
        item.setGradeBand(payload.getGradeBand());
        item.setPeriodLabel(payload.getPeriodLabel());
        item.setTimeSlotLabel(payload.getTimeSlotLabel());
        item.setStartDate(payload.getStartDate());
        item.setPackageUnit(payload.getPackageUnit());
        item.setPackageQuantity(payload.getPackageQuantity());
        item.setPackageTotalPrice(payload.getPackageTotalPrice());
        item.setAttendanceOption(payload.getAttendanceOption());
        item.setAttendanceScheduleJson(payload.getAttendanceScheduleJson());
        item.setPriceAmount(defaultAmount(payload.getPriceAmount()));
        item.setOriginalPriceAmount(defaultAmount(payload.getOriginalPriceAmount()));
        item.setManualPriceOverride(payload.getManualPriceOverride());
        if (payload.getDiscountAmount() != null) {
            item.setDiscountAmount(defaultAmount(payload.getDiscountAmount()));
        }
        item.setOneOnOneHours(payload.getOneOnOneHours());
        item.setOpeningUsedHours(payload.getOpeningUsedHours());
        item.setOneOnOneUnits(payload.getOneOnOneUnits());
        item.setPickupRequiredSnapshot(Boolean.TRUE.equals(payload.getPickupRequired()));
        item.setPickupAddressSnapshot(Boolean.TRUE.equals(payload.getPickupRequired())
                ? normalizedAddress(payload.getPickupAddress()) : null);
        item.setDropoffRequiredSnapshot(Boolean.TRUE.equals(payload.getDropoffRequired()));
        item.setDropoffAddressSnapshot(Boolean.TRUE.equals(payload.getDropoffRequired())
                ? normalizedAddress(payload.getDropoffAddress()) : null);
        item.setPickupFeeAmount(Boolean.TRUE.equals(payload.getPickupRequired())
                ? defaultAmount(payload.getPickupFeeAmount()) : ZERO_AMOUNT);
        item.setDropoffFeeAmount(Boolean.TRUE.equals(payload.getDropoffRequired())
                ? defaultAmount(payload.getDropoffFeeAmount()) : ZERO_AMOUNT);
        item.setExtraFeeAmount(sumFeeLines(payload.getServiceFees()));
        item.setExtraFeeDetail(jsonHelper.toJson(payload.getServiceFees()));
        item.setRemark(payload.getRemark());
    }

    /** 由课程行更正流程调用，确保已取消的接送标记与地址能真正写入数据库。 */
    private void persistCorrectedItemTransportSnapshot(EnrollmentOrderItemEntity item,
                                                       RegistrationIntakeRequest.CourseItemPayload payload) {
        boolean pickupRequired = Boolean.TRUE.equals(payload.getPickupRequired());
        boolean dropoffRequired = Boolean.TRUE.equals(payload.getDropoffRequired());
        enrollmentOrderItemMapper.updateTransportSnapshot(
                item.getId(),
                pickupRequired,
                pickupRequired ? normalizedAddress(payload.getPickupAddress()) : null,
                dropoffRequired,
                dropoffRequired ? normalizedAddress(payload.getDropoffAddress()) : null);
    }

    private String normalizedAddress(String address) {
        return StringUtils.hasText(address) ? address.trim() : null;
    }

    private boolean isGroupItem(EnrollmentOrderItemEntity item) {
        return !"ONE_ON_ONE".equalsIgnoreCase(item.getCourseType());
    }

    private record ClassItemBefore(Integer lineNo, Long classId, LocalDate startDate, String packageUnit,
                                   BigDecimal packageQuantity, Long courseProductId, String gradeBand) {
    }

    private void migrateCorrectedClassEnrollment(EnrollmentOrderEntity order,
                                                 StudentEntity student,
                                                 EnrollmentOrderItemEntity item,
                                                 ClassItemBefore previous,
                                                 Integer selectedSessions,
                                                 Integer selectedMonths) {
        ClassGroupEntity targetClass = item.getClassId() == null ? null : classGroupMapper.selectById(item.getClassId());
        List<ClassEnrollmentEntity> enrollments = classEnrollmentMapper.selectList(
                new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getEnrollmentOrderItemId, item.getId())
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0));
        ClassEnrollmentEntity current = enrollments.stream()
                .filter(enrollment -> "ACTIVE".equals(enrollment.getStatus())
                        || "EXPIRED".equals(enrollment.getStatus()))
                .findFirst().orElse(null);
        if (current == null) {
            // 续报会把在册记录转挂到新订单项；已停课的记录也不应因编辑旧收据而重新入班。
            Integer oldSessions = "SESSION".equalsIgnoreCase(previous.packageUnit())
                    ? originalReceiptSessions(order.getId(), previous.lineNo()) : null;
            Integer storedSessions = oldSessions == null ? wholeNumber(previous.packageQuantity()) : oldSessions;
            boolean sessionCountChanged = selectedSessions != null && "SESSION".equalsIgnoreCase(item.getPackageUnit())
                    && !Objects.equals(selectedSessions, storedSessions);
            Integer oldMonths = "MONTH".equalsIgnoreCase(previous.packageUnit())
                    ? originalReceiptPurchasedMonths(order.getId(), previous.lineNo()) : null;
            Integer storedMonths = oldMonths == null ? wholeNumber(previous.packageQuantity()) : oldMonths;
            boolean monthCountChanged = selectedMonths != null && "MONTH".equalsIgnoreCase(item.getPackageUnit())
                    && !Objects.equals(selectedMonths, storedMonths);
            if (!Objects.equals(previous.classId(), item.getClassId())
                    || !Objects.equals(previous.startDate(), item.getStartDate())
                    || !Objects.equals(previous.packageUnit(), item.getPackageUnit())
                    || !sameQuantity(previous.packageQuantity(), item.getPackageQuantity())
                    || !Objects.equals(previous.courseProductId(), item.getCourseProductId())
                    || !Objects.equals(previous.gradeBand(), item.getGradeBand())
                    || sessionCountChanged || monthCountChanged) {
                throw new BusinessException("CORRECTION_ENROLLMENT_DETACHED",
                        "This receipt no longer owns a class enrollment. Edit the current enrollment before changing its schedule or package duration.");
            }
            return;
        }
        Integer originalSessions = "SESSION".equalsIgnoreCase(previous.packageUnit())
                ? originalReceiptSessions(order.getId(), previous.lineNo()) : null;
        Integer originalMonths = "MONTH".equalsIgnoreCase(previous.packageUnit())
                ? originalReceiptPurchasedMonths(order.getId(), previous.lineNo()) : null;
        boolean cumulativeSessionEnrollment = "SESSION".equalsIgnoreCase(previous.packageUnit())
                && (originalSessions != null && current.getPurchasedQuantity() != null
                    && current.getPurchasedQuantity() > originalSessions
                    || originalSessions == null && current.getEnrollmentDate() != null
                    && previous.startDate() != null && current.getEnrollmentDate().isBefore(previous.startDate()));
        boolean cumulativeMonthEnrollment = "MONTH".equalsIgnoreCase(previous.packageUnit())
                && (originalMonths != null && current.getPurchasedQuantity() != null
                    && current.getPurchasedQuantity() > originalMonths
                    || originalMonths == null && current.getEnrollmentDate() != null
                    && previous.startDate() != null && current.getEnrollmentDate().isBefore(previous.startDate()));
        if (Objects.equals(previous.classId(), item.getClassId())) {
            LocalDate baseline = item.getStartDate() == null ? current.getEnrollmentDate() : item.getStartDate();
            LocalDate previousStart = previous.startDate() == null ? current.getEnrollmentDate() : previous.startDate();
            boolean quantityChanged = "SESSION".equalsIgnoreCase(item.getPackageUnit())
                    ? selectedSessions != null && !Objects.equals(selectedSessions,
                        originalSessions == null ? current.getPurchasedQuantity() : originalSessions)
                    : "MONTH".equalsIgnoreCase(item.getPackageUnit())
                        ? selectedMonths != null && !Objects.equals(selectedMonths,
                            originalMonths == null ? wholeNumber(current.getPurchasedQuantity()) : originalMonths)
                        : !sameQuantity(previous.packageQuantity(), item.getPackageQuantity());
            if (Objects.equals(previousStart, baseline)
                    && Objects.equals(previous.packageUnit(), item.getPackageUnit())
                    && Objects.equals(previous.courseProductId(), item.getCourseProductId())
                    && Objects.equals(previous.gradeBand(), item.getGradeBand()) && !quantityChanged) {
                // 金额或收据信息更正不改变既有在册有效期，也不追溯重校验后来新增的假期。
                if (!Objects.equals(current.getAttendanceOption(), item.getAttendanceOption())) {
                    current.setAttendanceOption(item.getAttendanceOption());
                    classEnrollmentMapper.updateById(current);
                }
                return;
            }
            if (cumulativeSessionEnrollment || cumulativeMonthEnrollment) {
                throw new BusinessException("CORRECTION_RENEWAL_SESSIONS_UNSUPPORTED",
                        "This enrollment includes earlier purchases. Change its package duration from the current enrollment, not an old receipt.");
            }
            BigDecimal billingQuantity = correctedBillingQuantity(item, previous.packageUnit(), current,
                    selectedSessions, selectedMonths);
            validateCorrectedClassTarget(targetClass, item);
            ClassBillingService.ClassBilling billing = computeCorrectedClassBilling(
                    targetClass, baseline, item, billingQuantity);
            current.setEnrollmentDate(billing.enrollmentDate());
            current.setExpiryDate(billing.expiryDate());
            current.setBillingType(item.getPackageUnit());
            current.setPurchasedQuantity(billing.purchasedQuantity());
            current.setAttendanceOption(item.getAttendanceOption());
            classEnrollmentMapper.updateById(current);
            return;
        }
        if (cumulativeSessionEnrollment || cumulativeMonthEnrollment) {
            throw new BusinessException("CORRECTION_RENEWAL_SESSIONS_UNSUPPORTED",
                    "This enrollment includes earlier purchases. Change its package duration from the current enrollment, not an old receipt.");
        }
        if (targetClass != null) {
            validateCorrectedClassTarget(targetClass, item);
        }
        BigDecimal billingQuantity = correctedBillingQuantity(item, previous.packageUnit(), current,
                selectedSessions, selectedMonths);
        for (ClassEnrollmentEntity enrollment : enrollments) {
            if ("ACTIVE".equals(enrollment.getStatus())) {
                enrollment.setStatus("CANCELLED");
                classEnrollmentMapper.updateById(enrollment);
            }
        }
        if (targetClass == null) {
            return;
        }
        ClassEnrollmentEntity replacement = new ClassEnrollmentEntity();
        // 换班/换年级：按新班级同一套计费规则重建在册记录，expiry_date 取自计算结果而非订单/班级的冗余字段。
        ClassBillingService.ClassBilling billing = computeCorrectedClassBilling(targetClass,
                item.getStartDate() == null ? order.getSignupDate() : item.getStartDate(), item, billingQuantity);
        replacement.setClassId(targetClass.getId());
        replacement.setStudentId(student.getId());
        replacement.setEnrollmentOrderItemId(item.getId());
        replacement.setEnrollmentDate(billing.enrollmentDate());
        replacement.setExpiryDate(billing.expiryDate());
        replacement.setBillingType(item.getPackageUnit());
        replacement.setPurchasedQuantity(billing.purchasedQuantity());
        replacement.setAttendanceOption(item.getAttendanceOption());
        replacement.setStatus("ACTIVE");
        classEnrollmentMapper.insert(replacement);
    }

    /** 班级关系真正变化时才校验当前班级状态；旧收据金额更正不受后续停用影响。 */
    private void validateCorrectedClassTarget(ClassGroupEntity targetClass, EnrollmentOrderItemEntity item) {
        if (targetClass == null || Integer.valueOf(1).equals(targetClass.getIsDeleted())
                || !"ACTIVE".equals(targetClass.getStatus())) {
            throw new BusinessException("CORRECTION_CLASS_INVALID", "The selected class is no longer active.");
        }
        if (item.getCourseProductId() != null
                && !Objects.equals(targetClass.getProductId(), item.getCourseProductId())) {
            throw new BusinessException("CORRECTION_CLASS_PRODUCT_MISMATCH",
                    "The selected class does not belong to the selected course.");
        }
        if (StringUtils.hasText(item.getGradeBand()) && StringUtils.hasText(targetClass.getGradeBand())
                && !item.getGradeBand().trim().equalsIgnoreCase(targetClass.getGradeBand().trim())) {
            throw new BusinessException("CORRECTION_CLASS_GRADE_MISMATCH",
                    "The selected class does not belong to the selected grade.");
        }
    }

    /**
     * 更正后的班课在册有效期：与报名新增链路共用同一套计费规则（ClassBillingService），
     * 保证 expiry_date 口径一致且永不为空。
     *
     * @param targetClass 新班级
     * @param baseline 起始日期：item 更正后的 start；为空时由调用方回退
     * @param item 更正后的订单课程项
     * @param billingQuantity 实际购买数量；SESSION 使用所选次数，不使用定价选项的最大次数
     */
    private ClassBillingService.ClassBilling computeCorrectedClassBilling(ClassGroupEntity targetClass,
                                                                          LocalDate baseline,
                                                                          EnrollmentOrderItemEntity item,
                                                                          BigDecimal billingQuantity) {
        return classBillingService.compute(targetClass, baseline, null,
                item.getPackageUnit(), billingQuantity);
    }

    /** 收据编辑的 SESSION 数量来自学生选购次数，价格选项的 packageQuantity 只是上限。 */
    private BigDecimal correctedBillingQuantity(EnrollmentOrderItemEntity item, String oldPackageUnit,
                                                ClassEnrollmentEntity current, Integer selectedSessions,
                                                Integer selectedMonths) {
        if ("MONTH".equalsIgnoreCase(item.getPackageUnit())) {
            Integer actualMonths = selectedMonths;
            if (actualMonths == null && "MONTH".equalsIgnoreCase(oldPackageUnit) && current != null) {
                actualMonths = wholeNumber(current.getPurchasedQuantity());
            }
            if (actualMonths == null || actualMonths < 1 || actualMonths > 36 || (item.getPackageQuantity() != null
                    && BigDecimal.valueOf(actualMonths).compareTo(item.getPackageQuantity()) > 0)) {
                throw new BusinessException("REGISTRATION_DURATION_INVALID",
                        "Selected months must be between 1 and the package month limit.");
            }
            return BigDecimal.valueOf(actualMonths);
        }
        if (!"SESSION".equalsIgnoreCase(item.getPackageUnit())) {
            return item.getPackageQuantity();
        }
        Integer actualSessions = selectedSessions;
        if (actualSessions == null && "SESSION".equalsIgnoreCase(oldPackageUnit) && current != null) {
            actualSessions = current.getPurchasedQuantity();
        }
        if (actualSessions == null && item.getPackageQuantity() != null) {
            try {
                actualSessions = item.getPackageQuantity().intValueExact();
            } catch (ArithmeticException ex) {
                throw new BusinessException("REGISTRATION_DURATION_INVALID",
                        "Selected sessions must be a whole number.");
            }
        }
        if (actualSessions == null || actualSessions < 1 || (item.getPackageQuantity() != null
                && BigDecimal.valueOf(actualSessions).compareTo(item.getPackageQuantity()) > 0)) {
            throw new BusinessException("REGISTRATION_DURATION_INVALID",
                    "Selected sessions must be between 1 and the package session limit.");
        }
        return BigDecimal.valueOf(actualSessions);
    }

    private boolean sameQuantity(BigDecimal left, BigDecimal right) {
        return left == null || right == null ? left == right : left.compareTo(right) == 0;
    }

    /** 续报后的旧订单不再拥有 class_enrollment，原购买次数从其保存的收据快照读取。 */
    private Integer originalReceiptSessions(Long orderId, Integer lineNo) {
        List<ReceiptRecordEntity> receipts = receiptRecordMapper.selectList(new LambdaQueryWrapper<ReceiptRecordEntity>()
                .eq(ReceiptRecordEntity::getEnrollmentOrderId, orderId)
                .orderByDesc(ReceiptRecordEntity::getId));
        for (ReceiptRecordEntity receipt : receipts) {
            Map<String, Object> snapshot = jsonHelper.fromJson(receipt.getReceiptPayload(),
                    new TypeReference<LinkedHashMap<String, Object>>() {});
            if (snapshot == null || !(snapshot.get("items") instanceof List<?> rows)) continue;
            for (Object row : rows) {
                if (!(row instanceof Map<?, ?> values)) continue;
                Integer rowLineNo = wholeNumber(values.get("lineNo"));
                if ((lineNo != null && !lineNo.equals(rowLineNo)) || (lineNo == null && rows.size() != 1)) continue;
                Integer sessions = wholeNumber(values.get("sessions"));
                if (sessions != null) return sessions;
            }
        }
        return null;
    }

    /** 收据快照保存学生实际购买月数；订单项 package_quantity 表示定价选项的月数上限。 */
    private Integer originalReceiptPurchasedMonths(Long orderId, Integer lineNo) {
        List<ReceiptRecordEntity> receipts = receiptRecordMapper.selectList(new LambdaQueryWrapper<ReceiptRecordEntity>()
                .eq(ReceiptRecordEntity::getEnrollmentOrderId, orderId)
                .orderByDesc(ReceiptRecordEntity::getId));
        for (ReceiptRecordEntity receipt : receipts) {
            Map<String, Object> snapshot = jsonHelper.fromJson(receipt.getReceiptPayload(),
                    new TypeReference<LinkedHashMap<String, Object>>() {});
            if (snapshot == null || !(snapshot.get("items") instanceof List<?> rows)) continue;
            for (Object row : rows) {
                if (!(row instanceof Map<?, ?> values)) continue;
                Integer rowLineNo = wholeNumber(values.get("lineNo"));
                if ((lineNo != null && !lineNo.equals(rowLineNo)) || (lineNo == null && rows.size() != 1)) continue;
                Integer months = wholeNumber(values.get("purchasedMonths"));
                if (months != null) return months;
            }
        }
        return null;
    }

    private Integer wholeNumber(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value.toString()).intValueExact();
        } catch (NumberFormatException | ArithmeticException ex) {
            return null;
        }
    }

    private void updateCorrectedOneOnOnePackage(EnrollmentOrderEntity order, EnrollmentOrderItemEntity item) {
        CoursePackageEntity coursePackage = coursePackageMapper.selectOne(new LambdaQueryWrapper<CoursePackageEntity>()
                .eq(CoursePackageEntity::getEnrollmentOrderItemId, item.getId())
                .eq(CoursePackageEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (coursePackage == null) {
            return;
        }
        int consumed = safeInt(coursePackage.getConsumedUnits());
        int requested = item.getOneOnOneUnits() == null
                ? Math.max(1, item.getOneOnOneHours() == null ? coursePackage.getTotalUnits() : item.getOneOnOneHours().multiply(BigDecimal.valueOf(2)).intValue())
                : item.getOneOnOneUnits();
        if (requested < consumed) {
            throw new BusinessException("CORRECTION_HOURS_BELOW_CONSUMED", "Package hours cannot be lower than hours already used.");
        }
        coursePackage.setCourseProductId(item.getCourseProductId());
        coursePackage.setPackageName(StringUtils.hasText(item.getDetail()) ? item.getDetail() : coursePackage.getPackageName());
        coursePackage.setTotalUnits(requested);
        coursePackage.setRemainingUnits(requested - consumed);
        coursePackage.setOriginalAmount(defaultAmount(item.getPriceAmount()));
        coursePackage.setStartDate(item.getStartDate() == null ? coursePackage.getStartDate() : item.getStartDate());
        coursePackageMapper.updateById(coursePackage);
    }

    private Long resolveCorrectedOrderClassId(List<EnrollmentOrderItemEntity> items) {
        List<Long> classIds = items.stream().map(EnrollmentOrderItemEntity::getClassId)
                .filter(Objects::nonNull).distinct().toList();
        return classIds.size() == 1 ? classIds.get(0) : null;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private void reconcileCorrectedPayments(EnrollmentOrderEntity order,
                                            List<RegistrationIntakeRequest.PaymentPayload> payloads) {
        Map<Integer, EnrollmentOrderItemEntity> itemsByLineNo = listOrderItemsByOrderId(order.getId()).stream()
                .collect(Collectors.toMap(EnrollmentOrderItemEntity::getLineNo, item -> item,
                        (left, right) -> left));
        List<PaymentRecordEntity> existing = paymentRecordMapper.selectList(new LambdaQueryWrapper<PaymentRecordEntity>()
                .eq(PaymentRecordEntity::getEnrollmentOrderId, order.getId())
                .eq(PaymentRecordEntity::getPaymentType, "PAYMENT")
                .ne(PaymentRecordEntity::getPaymentStatus, "VOIDED")
                .orderByAsc(PaymentRecordEntity::getPaidAt)
                .orderByAsc(PaymentRecordEntity::getId));
        for (int i = 0; i < Math.max(existing.size(), payloads.size()); i++) {
            if (i < payloads.size()) {
                RegistrationIntakeRequest.PaymentPayload payload = payloads.get(i);
                PaymentRecordEntity payment = i < existing.size() ? existing.get(i) : new PaymentRecordEntity();
                if (payment.getId() == null) {
                    payment.setEnrollmentOrderId(order.getId());
                    payment.setPaymentNo(BusinessNoGenerator.generatePaymentNo());
                    payment.setPaymentType("PAYMENT");
                }
                payment.setPaymentMethod(payload.getPaymentMethod());
                payment.setPaymentStatus("PAID");
                payment.setAmount(defaultAmount(payload.getAmount()));
                payment.setPaidAt(payload.getPaidAt() == null ? AppTime.nowLocalDateTime() : payload.getPaidAt());
                payment.setHandledByUserId(payload.getHandledByUserId());
                payment.setHandledByName(payload.getHandledByName());
                payment.setRemark(payload.getRemark());
                if (payment.getId() == null) paymentRecordMapper.insert(payment); else paymentRecordMapper.updateById(payment);
                // allocations 是收据编辑中的当前值：空数组也代表用户已清空旧分摊，
                // 不能继续保留原 allocation，否则后续重放会把旧金额带回当前付款。
                if (payload.getAllocations() != null) {
                    Map<Long, BigDecimal> allocations = new LinkedHashMap<>();
                    for (RegistrationIntakeRequest.CoursePaymentAllocationPayload allocation : payload.getAllocations()) {
                        EnrollmentOrderItemEntity item = itemsByLineNo.get(allocation.getOrderItemLineNo());
                        if (item == null || allocation.getAmount() == null) {
                            throw new BusinessException("COURSE_PAYMENT_ALLOCATION_INVALID",
                                    "A corrected payment references a course that is not in this order.");
                        }
                        allocations.merge(item.getId(), allocation.getAmount(), BigDecimal::add);
                    }
                    paymentAllocationService.replaceExplicitAllocations(payment, order.getId(), allocations);
                }
            } else {
                PaymentRecordEntity payment = existing.get(i);
                payment.setPaymentStatus("VOIDED");
                payment.setAmount(ZERO_AMOUNT);
                paymentRecordMapper.updateById(payment);
            }
        }
    }

    private void applyCorrectedMoney(EnrollmentOrderEntity order, EnrollmentOrderCorrectionRequest request) {
        BigDecimal tuition = request.getItems() == null ? defaultAmount(order.getTuitionSubtotalAmount()) : request.getItems().stream()
                .map(item -> defaultAmount(item.getPriceAmount())).reduce(ZERO_AMOUNT, BigDecimal::add);
        boolean hasCourseServiceFees = hasCorrectedCourseServiceFees(request);
        BigDecimal pickup = hasCourseServiceFees
                ? sumCorrectedCoursePickupFees(request.getItems())
                : resolveCorrectedRegistrationFee(request.getPickupFeeCharged(), request.getPickupFeeAmount(), order.getPickupFeeAmount());
        BigDecimal dropoff = hasCourseServiceFees
                ? sumCorrectedCourseDropoffFees(request.getItems())
                : resolveCorrectedRegistrationFee(request.getDropoffFeeCharged(), request.getDropoffFeeAmount(), order.getDropoffFeeAmount());
        BigDecimal extra = hasCourseServiceFees
                ? sumCorrectedCourseServiceFees(request)
                : request.getExtraFeeAmount() != null
                ? defaultAmount(request.getExtraFeeAmount())
                : request.getExtraFees() != null
                ? sumFeeLines(request.getExtraFees())
                : defaultAmount(order.getExtraFeeAmount());
        BigDecimal discount = request.getParticipantBillings() != null && !request.getParticipantBillings().isEmpty()
                ? request.getParticipantBillings().stream()
                .filter(Objects::nonNull)
                .map(item -> item.getDiscountAmount() == null
                        ? sumFeeLines(item.getDiscounts()) : defaultAmount(item.getDiscountAmount()))
                .reduce(ZERO_AMOUNT, BigDecimal::add)
                : request.getDiscountAmount() != null ? defaultAmount(request.getDiscountAmount()) : sumFeeLines(request.getDiscounts());
        BigDecimal original = tuition.add(pickup).add(dropoff).add(extra).setScale(2, RoundingMode.HALF_UP);
        if (discount.compareTo(original) > 0) throw new BusinessException("DISCOUNT_EXCEEDS_AMOUNT", "Discount cannot exceed original amount.");
        boolean hasCorrectedPaymentPayload = (request.getParticipantBillings() != null
                && !request.getParticipantBillings().isEmpty()) || request.getPayments() != null;
        BigDecimal requestedPaid = request.getParticipantBillings() != null && !request.getParticipantBillings().isEmpty()
                ? request.getParticipantBillings().stream()
                .filter(Objects::nonNull)
                .flatMap(item -> item.getPayments() == null ? java.util.stream.Stream.empty() : item.getPayments().stream())
                .filter(Objects::nonNull)
                .map(RegistrationIntakeRequest.PaymentPayload::getAmount)
                .map(this::defaultAmount)
                .filter(amount -> amount.compareTo(ZERO_AMOUNT) > 0)
                .reduce(ZERO_AMOUNT, BigDecimal::add)
                : request.getPayments() == null ? defaultAmount(order.getReceivedAmount()) : request.getPayments().stream()
                .filter(Objects::nonNull)
                .map(RegistrationIntakeRequest.PaymentPayload::getAmount)
                .map(this::defaultAmount)
                .filter(amount -> amount.compareTo(ZERO_AMOUNT) > 0)
                .reduce(ZERO_AMOUNT, BigDecimal::add);
        // 退款流水是不可变的负数记录，编辑时不能只按正向支付重算，否则会把退款前金额恢复回来。
        // 未提交付款字段时沿用订单当前净额，避免把历史退款重复扣除。
        BigDecimal received = hasCorrectedPaymentPayload
                ? requestedPaid.add(sumActiveRefunds(order.getId(), null)).max(ZERO_AMOUNT)
                : defaultAmount(order.getReceivedAmount());
        BigDecimal due = original.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        if (received.compareTo(due) > 0) {
            throw new BusinessException("PAYMENT_EXCEEDS_AMOUNT_DUE", "Paid amount cannot exceed amount due.");
        }
        order.setTuitionSubtotalAmount(tuition);
        order.setPickupFeeAmount(pickup);
        order.setDropoffFeeAmount(dropoff);
        order.setExtraFeeAmount(extra);
        order.setExtraFeeDetail(jsonHelper.toJson(request.getExtraFees()));
        order.setOriginalAmount(original);
        order.setDiscountAmount(discount);
        order.setDiscountDetail(jsonHelper.toJson(request.getDiscounts()));
        order.setReceivedAmount(received);
        order.setAmountDue(due);
        order.setBalanceAmount(due.subtract(received));
        order.setPaymentStatus(resolvePaymentStatus(original, discount, received));
    }

    /** 更正收据时沿用首次报名的课程级费用口径。 */
    private boolean hasCorrectedCourseServiceFees(EnrollmentOrderCorrectionRequest request) {
        boolean itemFees = request.getItems() != null && request.getItems().stream()
                .filter(Objects::nonNull)
                .anyMatch(item -> Boolean.TRUE.equals(item.getPickupRequired())
                        || Boolean.TRUE.equals(item.getDropoffRequired())
                        || Boolean.TRUE.equals(item.getPickupFeeCharged())
                        || Boolean.TRUE.equals(item.getDropoffFeeCharged())
                        || defaultAmount(item.getPickupFeeAmount()).compareTo(ZERO_AMOUNT) > 0
                        || defaultAmount(item.getDropoffFeeAmount()).compareTo(ZERO_AMOUNT) > 0
                        || (item.getServiceFees() != null && !item.getServiceFees().isEmpty()));
        boolean assignedExtraFees = request.getExtraFees() != null && request.getExtraFees().stream()
                .filter(Objects::nonNull)
                .anyMatch(item -> item.getOrderItemLineNo() != null);
        return itemFees || assignedExtraFees;
    }

    private BigDecimal sumCorrectedCoursePickupFees(List<RegistrationIntakeRequest.CourseItemPayload> items) {
        return items == null ? ZERO_AMOUNT : items.stream().filter(Objects::nonNull)
                .map(item -> Boolean.TRUE.equals(item.getPickupRequired())
                        ? defaultAmount(item.getPickupFeeAmount()) : ZERO_AMOUNT)
                .reduce(ZERO_AMOUNT, BigDecimal::add);
    }

    private BigDecimal sumCorrectedCourseDropoffFees(List<RegistrationIntakeRequest.CourseItemPayload> items) {
        return items == null ? ZERO_AMOUNT : items.stream().filter(Objects::nonNull)
                .map(item -> Boolean.TRUE.equals(item.getDropoffRequired())
                        ? defaultAmount(item.getDropoffFeeAmount()) : ZERO_AMOUNT)
                .reduce(ZERO_AMOUNT, BigDecimal::add);
    }

    private BigDecimal sumCorrectedCourseServiceFees(EnrollmentOrderCorrectionRequest request) {
        BigDecimal itemFees = request.getItems() == null ? ZERO_AMOUNT : request.getItems().stream()
                .filter(Objects::nonNull)
                .map(item -> sumFeeLines(item.getServiceFees()))
                .reduce(ZERO_AMOUNT, BigDecimal::add);
        BigDecimal assignedExtraFees = request.getExtraFees() == null ? ZERO_AMOUNT : request.getExtraFees().stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getOrderItemLineNo() != null)
                .map(RegistrationIntakeRequest.FeeLinePayload::getAmount)
                .map(this::defaultAmount)
                .reduce(ZERO_AMOUNT, BigDecimal::add);
        return itemFees.add(assignedExtraFees);
    }

    private BigDecimal resolveCorrectedRegistrationFee(Boolean charged,
                                                       BigDecimal requestedAmount,
                                                       BigDecimal existingAmount) {
        if (charged != null || requestedAmount != null) {
            return Boolean.TRUE.equals(charged) ? defaultAmount(requestedAmount) : ZERO_AMOUNT;
        }
        return defaultAmount(existingAmount);
    }

    /** 汇总本订单仍有效的退款记录；REFUND 金额在账本中以负数保存。 */
    private BigDecimal sumActiveRefunds(Long orderId, Integer participantIndex) {
        LambdaQueryWrapper<PaymentRecordEntity> query = new LambdaQueryWrapper<PaymentRecordEntity>()
                .eq(PaymentRecordEntity::getEnrollmentOrderId, orderId)
                .eq(PaymentRecordEntity::getPaymentType, "REFUND")
                .ne(PaymentRecordEntity::getPaymentStatus, "VOIDED");
        if (participantIndex != null) {
            query.eq(PaymentRecordEntity::getParticipantIndex, participantIndex);
        }
        List<PaymentRecordEntity> refunds = paymentRecordMapper.selectList(query);
        if (refunds == null) {
            return ZERO_AMOUNT;
        }
        return refunds.stream()
                .filter(Objects::nonNull)
                .map(PaymentRecordEntity::getAmount)
                .map(this::defaultAmount)
                .filter(amount -> amount.compareTo(ZERO_AMOUNT) < 0)
                .reduce(ZERO_AMOUNT, BigDecimal::add);
    }

    private BigDecimal sumFeeLines(List<RegistrationIntakeRequest.FeeLinePayload> lines) {
        return lines == null ? ZERO_AMOUNT : lines.stream().map(RegistrationIntakeRequest.FeeLinePayload::getAmount)
                .map(this::defaultAmount).reduce(ZERO_AMOUNT, BigDecimal::add);
    }

    /**
     * Re-write the matching keys inside each stored receipt payload for this order so
     * corrected contact info is reflected on the saved/printable receipt.
     */
    private void syncReceiptSnapshots(EnrollmentOrderEntity order,
                                      String campusAddress,
                                      String campusPhone,
                                      String taxId,
                                      EnrollmentOrderCorrectionRequest request) {
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
            payload.put("studentClass", order.getStudentClassSnapshot());
            payload.put("grade", order.getGradeSnapshot());
            payload.put("school", order.getSchoolSnapshot());
            payload.put("guardianPhone", order.getParentPhoneSnapshot());
            payload.put("guardianPhone2", order.getParentPhone2Snapshot());
            payload.put("campusId", order.getCampusId());
            payload.put("campusName", order.getCampusNameSnapshot());
            if (campusAddress != null) {
                payload.put("campusAddress", campusAddress);
            }
            if (campusPhone != null) {
                payload.put("campusPhone", campusPhone);
            }
            if (taxId != null) {
                payload.put("taxId", taxId);
            }
            payload.put("pickupRequired", order.getPickupRequiredSnapshot());
            payload.put("pickupAddress", order.getPickupAddressSnapshot());
            payload.put("dropoffRequired", order.getDropoffRequiredSnapshot());
            payload.put("dropoffAddress", order.getDropoffAddressSnapshot());
            payload.put("signupDate", order.getSignupDate());
            payload.put("handledByName", order.getHandledByName());
            payload.put("registrationRemark", order.getRemark());
            if (request.getItems() != null) {
                payload.put("items", request.getItems());
            }
            if (request.getExtraFees() != null) {
                payload.put("extraFees", request.getExtraFees());
            }
            if (request.getDiscounts() != null) {
                payload.put("discounts", request.getDiscounts());
            }
            if (request.getPayments() != null) {
                payload.put("payments", request.getPayments());
            }
            payload.put("participants", receiptParticipantPayloads(order.getId()));
            payload.put("payments", receiptPaymentPayloads(order.getId()));
            payload.put("pickupFeeCharged", request.getPickupFeeCharged());
            payload.put("pickupFeeAmount", order.getPickupFeeAmount());
            payload.put("dropoffFeeCharged", request.getDropoffFeeCharged());
            payload.put("dropoffFeeAmount", order.getDropoffFeeAmount());
            payload.put("tuitionSubtotalAmount", order.getTuitionSubtotalAmount());
            payload.put("extraFeeAmount", order.getExtraFeeAmount());
            payload.put("originalAmount", order.getOriginalAmount());
            payload.put("discountAmount", order.getDiscountAmount());
            BigDecimal receiptCreditTopUp = receiptCreditTopUpAmount(payload);
            BigDecimal receiptAmountDue = defaultAmount(order.getAmountDue()).add(receiptCreditTopUp);
            BigDecimal receiptPaidAmount = defaultAmount(order.getReceivedAmount()).add(receiptCreditTopUp);
            payload.put("amountDue", receiptAmountDue);
            payload.put("paidAmount", receiptPaidAmount);
            payload.put("balanceAmount", receiptAmountDue.subtract(receiptPaidAmount));
            payload.put("paymentStatus", order.getPaymentStatus());
            if (request.getReceiptOptions() != null) {
                payload.put("showTaxId", request.getReceiptOptions().getShowTaxId());
                payload.put("taxId", request.getReceiptOptions().getTaxId());
                payload.put("campusAddress", request.getReceiptOptions().getCampusAddress());
                payload.put("campusPhone", request.getReceiptOptions().getCampusPhone());
                payload.put("brandName", request.getReceiptOptions().getBrandName());
                payload.put("logoUrl", request.getReceiptOptions().getLogoUrl());
                payload.put("zelleQrCodeUrl", request.getReceiptOptions().getZelleQrCodeUrl());
                payload.put("policyText", request.getReceiptOptions().getPolicyText());
            }
            receipt.setReceiptPayload(jsonHelper.toJson(payload));
            receiptRecordMapper.updateById(receipt);
        }
    }

    private List<Map<String, Object>> receiptParticipantPayloads(Long orderId) {
        List<EnrollmentOrderParticipantEntity> participants = enrollmentOrderParticipantMapper.selectList(
                        new LambdaQueryWrapper<EnrollmentOrderParticipantEntity>()
                                .eq(EnrollmentOrderParticipantEntity::getEnrollmentOrderId, orderId)
                                .eq(EnrollmentOrderParticipantEntity::getIsDeleted, 0)
                                .orderByAsc(EnrollmentOrderParticipantEntity::getParticipantIndex));
        if (participants == null) {
            participants = Collections.emptyList();
        }
        return participants.stream().map(item -> {
                    Map<String, Object> participant = new LinkedHashMap<>();
                    participant.put("studentId", item.getStudentId());
                    participant.put("studentNo", item.getStudentNoSnapshot());
                    participant.put("studentName", item.getStudentNameSnapshot());
                    participant.put("participantIndex", item.getParticipantIndex());
                    participant.put("grade", item.getGradeSnapshot());
                    participant.put("school", item.getSchoolSnapshot());
                    participant.put("studentClass", item.getStudentClassSnapshot());
                    participant.put("guardianName", item.getGuardianNameSnapshot());
                    participant.put("guardianPhone", item.getGuardianPhoneSnapshot());
                    participant.put("guardianPhone2", item.getGuardianPhone2Snapshot());
                    participant.put("guardianEmail", item.getGuardianEmailSnapshot());
                    participant.put("pickupRequired", item.getPickupRequiredSnapshot());
                    participant.put("pickupAddress", item.getPickupAddressSnapshot());
                    participant.put("dropoffRequired", item.getDropoffRequiredSnapshot());
                    participant.put("dropoffAddress", item.getDropoffAddressSnapshot());
                    participant.put("grossAmount", item.getGrossAmount());
                    participant.put("priceAmount", item.getGrossAmount());
                    participant.put("discountAmount", item.getDiscountAmount());
                    participant.put("discounts", item.getDiscountDetail() == null ? Collections.emptyList()
                            : jsonHelper.fromJson(item.getDiscountDetail(), new TypeReference<List<Map<String, Object>>>() {}));
                    participant.put("amountDue", item.getAmountDue());
                    participant.put("paidAmount", item.getPaidAmount());
                    participant.put("balanceAmount", item.getBalanceAmount());
                    return participant;
                }).toList();
    }

    private List<Map<String, Object>> receiptPaymentPayloads(Long orderId) {
        List<PaymentRecordEntity> payments = paymentRecordMapper.selectList(new LambdaQueryWrapper<PaymentRecordEntity>()
                        .eq(PaymentRecordEntity::getEnrollmentOrderId, orderId)
                        .orderByAsc(PaymentRecordEntity::getPaidAt)
                        .orderByAsc(PaymentRecordEntity::getId));
        if (payments == null) {
            payments = Collections.emptyList();
        }
        return payments.stream()
                .filter(item -> item.getAmount() != null && item.getAmount().compareTo(ZERO_AMOUNT) != 0
                        && !"VOIDED".equals(item.getPaymentStatus()))
                .map(item -> {
                    Map<String, Object> payment = new LinkedHashMap<>();
                    payment.put("paymentId", item.getId());
                    payment.put("participantIndex", item.getParticipantIndex());
                    payment.put("paymentNo", item.getPaymentNo());
                    payment.put("paymentMethod", item.getPaymentMethod());
                    payment.put("paymentType", item.getPaymentType());
                    payment.put("paymentStatus", item.getPaymentStatus());
                    payment.put("amount", item.getAmount());
                    payment.put("paidAt", item.getPaidAt());
                    payment.put("handledByName", item.getHandledByName());
                    payment.put("remark", item.getRemark());
                    return payment;
                }).toList();
    }

    /** 混合报名收据的 Credit 充值属于独立订单，但仍需保留在本张收据的展示金额中。 */
    private BigDecimal receiptCreditTopUpAmount(Map<String, Object> payload) {
        Object rawAmount = payload.get("creditTopUpAmount");
        if (!(rawAmount instanceof Number) && !(rawAmount instanceof String)) {
            return ZERO_AMOUNT;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(String.valueOf(rawAmount));
        } catch (NumberFormatException ex) {
            return ZERO_AMOUNT;
        }
        Object rawItems = payload.get("items");
        if (!(rawItems instanceof List<?>) || ((List<?>) rawItems).isEmpty()) {
            // 纯 Credit 充值订单的 amountDue 已经包含充值金额，不能重复相加。
            return ZERO_AMOUNT;
        }
        return amount.max(ZERO_AMOUNT).setScale(2, RoundingMode.HALF_UP);
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
        Integer participantIndex = request.getParticipantIndex();
        if (participantIndex != null && participantIndex < 1) {
            throw new BusinessException("INVALID_PARTICIPANT_INDEX", "The selected student is invalid.");
        }
        EnrollmentOrderParticipantEntity targetParticipant = participantIndex == null
                ? null
                : findOrderParticipant(order.getId(), participantIndex);
        if (participantIndex != null && targetParticipant == null) {
            throw new BusinessException("PAYMENT_PARTICIPANT_NOT_FOUND",
                    "The selected student is not part of this enrollment order.");
        }
        List<EnrollmentOrderParticipantEntity> participants = listOrderParticipants(order.getId());
        Long participantCount = enrollmentOrderParticipantMapper.selectCount(
                new LambdaQueryWrapper<EnrollmentOrderParticipantEntity>()
                        .eq(EnrollmentOrderParticipantEntity::getEnrollmentOrderId, order.getId())
                        .eq(EnrollmentOrderParticipantEntity::getIsDeleted, 0));
        boolean singleParticipantOrder = participantCount == null || participantCount <= 1;
        Long orderItemId = request.getOrderItemId();
        if (orderItemId != null && targetParticipant == null && participants.size() == 1) {
            targetParticipant = participants.get(0);
            participantIndex = targetParticipant.getParticipantIndex();
        } else if (orderItemId != null && targetParticipant == null && singleParticipantOrder
                && order.getStudentId() != null) {
            targetParticipant = legacyPrimaryParticipant(order);
            participantIndex = targetParticipant.getParticipantIndex();
        }
        if (orderItemId != null && !singleParticipantOrder && targetParticipant == null) {
            throw new BusinessException("REFUND_PARTICIPANT_REQUIRED",
                    "Select the student whose course payment should be refunded.");
        }
        EnrollmentOrderItemEntity targetItem = null;
        BigDecimal targetItemPaidBeforeRefund = ZERO_AMOUNT;
        ClassUsage targetUsage = ClassUsage.empty();
        Map<Long, BigDecimal> coursePaidBeforeRefund = Collections.emptyMap();
        if (orderItemId != null) {
            targetItem = enrollmentOrderItemMapper.selectOne(new LambdaQueryWrapper<EnrollmentOrderItemEntity>()
                    .eq(EnrollmentOrderItemEntity::getId, orderItemId)
                    .eq(EnrollmentOrderItemEntity::getEnrollmentOrderId, order.getId())
                    .eq(EnrollmentOrderItemEntity::getIsDeleted, 0));
            if (targetItem == null) {
                throw new BusinessException("REFUND_COURSE_NOT_FOUND", "The selected course is not on this receipt.");
            }
            // 多人收据不能重放未明确归属学生的历史分摊；这类金额必须先人工核对。
            if (singleParticipantOrder || targetParticipant == null) {
                paymentAllocationService.replayAllocations(order);
            }
            targetItem = enrollmentOrderItemMapper.selectById(orderItemId);
            BigDecimal itemPaid = targetParticipant == null
                    ? resolveItemPaidAmount(targetItem)
                    : resolveParticipantCoursePaidAmount(order, targetItem, targetParticipant, singleParticipantOrder);
            targetItemPaidBeforeRefund = itemPaid;
            if (itemPaid.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("NO_PAYMENT_TO_REFUND", "The selected course has no refundable payment.");
            }
            if (defaultAmount(request.getRefundAmount()).compareTo(itemPaid) > 0) {
                throw new BusinessException("REFUND_AMOUNT_EXCEEDS_COURSE_PAID",
                        "Refund amount cannot exceed the selected course's currently paid amount.");
            }
            targetUsage = calculateParticipantCourseUsage(order, targetItem, targetParticipant,
                    listOrderParticipants(order.getId()).size());
        } else if (singleParticipantOrder) {
            // 为旧订单先补齐课程已付快照，后续只停掉被本次退款实际影响的班课项。
            paymentAllocationService.replayAllocations(order);
            coursePaidBeforeRefund = snapshotCoursePaidAmounts(order);
        }
        BigDecimal participantReceived = targetItem != null && targetParticipant != null
                ? targetItemPaidBeforeRefund
                : targetParticipant == null
                    ? received
                    : resolveParticipantReceivedAmount(order.getId(), targetParticipant);
        if (participantReceived.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("NO_PAYMENT_TO_REFUND", "There is no received payment to refund.");
        }
        BigDecimal refundAmount = defaultAmount(request.getRefundAmount());
        if (WholeDollarBilling.appliesTo(order)) {
            WholeDollarBilling.requireInteger(request.getRefundAmount(), "Refund");
        }
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_REFUND_AMOUNT", "Refund amount must be greater than zero.");
        }
        if (refundAmount.compareTo(participantReceived) > 0) {
            throw new BusinessException("REFUND_AMOUNT_EXCEEDS_RECEIVED",
                    targetParticipant == null
                            ? "Refund amount cannot exceed the currently received amount."
                    : "Refund amount cannot exceed this student's received amount.");
        }
        if (orderItemId == null) {
            long activeItemCount = enrollmentOrderItemMapper.selectCount(new LambdaQueryWrapper<EnrollmentOrderItemEntity>()
                    .eq(EnrollmentOrderItemEntity::getEnrollmentOrderId, order.getId())
                    .eq(EnrollmentOrderItemEntity::getIsDeleted, 0));
            if (activeItemCount > 1 && refundAmount.compareTo(participantReceived) < 0) {
                throw new BusinessException("REFUND_COURSE_REQUIRED",
                        "For a partial refund on a multi-course receipt, select a course. Refund the full current amount to cover all courses.");
            }
        }
        if (targetParticipant == null && participants.size() > 1
                && refundAmount.compareTo(received) != 0) {
            throw new BusinessException("REFUND_PARTICIPANT_REQUIRED",
                    "Select the student for a partial refund on a multi-student enrollment.");
        }
        if (targetParticipant != null && refundAmount.compareTo(received) > 0) {
            throw new BusinessException("REFUND_AMOUNT_EXCEEDS_RECEIVED",
                    "Refund amount cannot exceed the order's currently received amount.");
        }
        boolean convertToCredit = "CREDIT".equalsIgnoreCase(request.getDisposition());
        if (!convertToCredit) {
            requireActivePaymentMethod(request.getPaymentMethod());
        }
        boolean fullParticipantRefund = orderItemId == null && targetParticipant != null
                && refundAmount.compareTo(participantReceived) == 0;
        boolean hasOtherParticipants = false;
        if (targetParticipant != null) {
            for (EnrollmentOrderParticipantEntity item : participants) {
                if (!Objects.equals(item.getParticipantIndex(), targetParticipant.getParticipantIndex())) {
                    hasOtherParticipants = true;
                    break;
                }
            }
        }
        boolean fullOrderRefund = orderItemId == null && (targetParticipant == null
                ? refundAmount.compareTo(received) == 0
                : fullParticipantRefund && !hasOtherParticipants);
        if (orderItemId != null && !targetUsage.automaticStopSupported()
                && targetParticipant == null && refundAmount.compareTo(received) == 0) {
            long activeItemCount = enrollmentOrderItemMapper.selectCount(new LambdaQueryWrapper<EnrollmentOrderItemEntity>()
                    .eq(EnrollmentOrderItemEntity::getEnrollmentOrderId, order.getId())
                    .eq(EnrollmentOrderItemEntity::getIsDeleted, 0));
            fullOrderRefund = activeItemCount <= 1;
        }
        if (fullOrderRefund) {
            validateFullRefundHasNoLessonRecords(order);
        }

        // 退款只取消 1-on-1 排课；班课课次属于共享排课，不随单个学生退款而撤销。
        if (fullOrderRefund && !"CANCELLED".equals(order.getOrderStatus())) {
            cancelOneOnOneEnrollment(order);
            cancelClassEnrollment(order);
            order.setOrderStatus("CANCELLED");
        }

        // Refunds are immutable negative ledger entries; never overwrite payment history.
        PaymentRecordEntity refundRecord = new PaymentRecordEntity();
        refundRecord.setEnrollmentOrderId(order.getId());
        refundRecord.setParticipantIndex(participantIndex);
        refundRecord.setPaymentNo(BusinessNoGenerator.generatePaymentNo());
        refundRecord.setPaymentMethod(convertToCredit
                ? StudentCreditService.PAYMENT_METHOD_CODE : request.getPaymentMethod());
        refundRecord.setPaymentType("REFUND");
        refundRecord.setPaymentStatus("REFUNDED");
        refundRecord.setAmount(refundAmount.negate());
        refundRecord.setPaidAt(AppTime.nowLocalDateTime());
        refundRecord.setHandledByUserId(request.getHandledByUserId());
        refundRecord.setHandledByName(request.getHandledByName());
        refundRecord.setRemark(request.getRemark());
        paymentRecordMapper.insert(refundRecord);
        if (targetItem != null) {
            paymentAllocationService.recordRefundAllocation(refundRecord, order.getId(), targetItem.getId(), refundAmount);
            BigDecimal remainingCoursePaid = targetItemPaidBeforeRefund.subtract(refundAmount);
            if (targetUsage.automaticStopSupported()
                    && !isParticipantCourseStopped(targetItem, targetParticipant)
                    && remainingCoursePaid.compareTo(targetUsage.consumedAmount()) <= 0) {
                stopParticipantCourse(order, targetItem, targetParticipant, targetUsage,
                        remainingCoursePaid.max(ZERO_AMOUNT));
            }
        } else if (fullParticipantRefund && participants.size() > 1) {
            allocateFullParticipantRefund(order, targetParticipant, refundRecord, refundAmount);
        }
        if (convertToCredit) {
            Long creditStudentId = targetParticipant == null ? order.getStudentId() : targetParticipant.getStudentId();
            studentCreditService.creditFromRefund(creditStudentId, refundAmount, order.getId(), refundRecord.getId(),
                    request.getHandledByUserId(), request.getHandledByName(), request.getRemark());
        }

        // 非共享订单：课程级分摊按已付比例回冲（退款为负数分摊行）
        if (singleParticipantOrder) {
            paymentAllocationService.replayAllocations(order);
            if (orderItemId == null && !fullOrderRefund
                    && stopCoursesAtConsumedThreshold(order, coursePaidBeforeRefund)) {
                paymentAllocationService.replayAllocations(order);
            }
        }

        BigDecimal remainingReceived = received.subtract(refundAmount);
        order.setReceivedAmount(remainingReceived);
        if (fullOrderRefund) {
            // A fully refunded order is closed and has no collectible balance.
            order.setBalanceAmount(BigDecimal.ZERO);
            order.setPaymentStatus("REFUNDED");
        } else {
            BigDecimal amountDue = defaultAmount(order.getAmountDue());
            order.setBalanceAmount(amountDue.subtract(remainingReceived));
            order.setPaymentStatus(resolvePaymentStatus(
                    amountDue,
                    ZERO_AMOUNT,
                    remainingReceived));
        }
        int updated = enrollmentOrderMapper.updateById(order);
        if (updated != 1) {
            throw new BusinessException("ORDER_CONCURRENT_UPDATE", "The order was updated by another request. Please retry.");
        }

        syncParticipantPaymentSnapshots(order.getId());
        if (fullOrderRefund && participants.size() > 1) {
            resetParticipantPaymentSnapshots(participants);
        }
        if (fullParticipantRefund && !fullOrderRefund) {
            stopParticipantCoursesForFullRefund(order, targetParticipant, participants.size());
        }
        if (fullOrderRefund) {
            List<CoursePackageEntity> packages = coursePackageMapper.selectList(
                    new LambdaQueryWrapper<CoursePackageEntity>()
                            .eq(CoursePackageEntity::getEnrollmentOrderId, order.getId()));
            if (packages == null) {
                packages = Collections.emptyList();
            }
            for (CoursePackageEntity coursePackage : packages) {
                coursePackage.setReceivedAmount(BigDecimal.ZERO);
                coursePackageMapper.updateById(coursePackage);
            }
        }
        // 原报名收据保持不变；本次退款单独生成凭证并关联负向流水。
        return createRefundReceipt(order, request, refundRecord, targetItem, targetParticipant,
                convertToCredit);
    }

    @Override
    public RefundCoursePreviewVO previewRefund(Long id, Integer participantIndex) {
        EnrollmentOrderEntity order = requireOrder(id);
        List<EnrollmentOrderParticipantEntity> participants = listOrderParticipants(order.getId());
        EnrollmentOrderParticipantEntity participant = participantIndex == null ? null
                : findOrderParticipant(order.getId(), participantIndex);
        if (participantIndex != null && participant == null) {
            throw new BusinessException("PAYMENT_PARTICIPANT_NOT_FOUND",
                    "The selected student is not part of this enrollment order.");
        }
        if (participant == null && participants.size() == 1) {
            participant = participants.get(0);
        }
        int participantCount = participants.size();
        EnrollmentOrderParticipantEntity selectedParticipant = participant;
        List<RefundCoursePreviewVO.CourseItem> items = enrollmentOrderItemMapper.selectList(
                        new LambdaQueryWrapper<EnrollmentOrderItemEntity>()
                                .eq(EnrollmentOrderItemEntity::getEnrollmentOrderId, order.getId())
                                .eq(EnrollmentOrderItemEntity::getIsDeleted, 0)
                                .orderByAsc(EnrollmentOrderItemEntity::getLineNo))
                .stream()
                .map(item -> {
                    ClassUsage usage = calculateRefundPreviewUsage(order, item, selectedParticipant, participants);
                    BigDecimal paidAmount = selectedParticipant == null
                            ? resolveItemPaidAmount(item)
                            : resolveParticipantCoursePaidAmount(order, item, selectedParticipant, participantCount <= 1);
                    return new RefundCoursePreviewVO.CourseItem(
                            item.getId(), item.getLineNo(), item.getDetail(), item.getCourseType(), item.getClassId(),
                            paidAmount, usage.consumedLessonCount(), usage.consumedAmount(),
                            usage.automaticStopSupported(), selectedParticipant == null
                                    ? order.getStudentNameSnapshot() : selectedParticipant.getStudentNameSnapshot());
                })
                .toList();
        return new RefundCoursePreviewVO(items);
    }

    /** 课程退款金额以支付流水上的学生序号和课程分摊的交集为准，不能按收据总额猜测分配。 */
    private BigDecimal resolveParticipantCoursePaidAmount(EnrollmentOrderEntity order,
                                                          EnrollmentOrderItemEntity item,
                                                          EnrollmentOrderParticipantEntity participant,
                                                          boolean singleParticipantOrder) {
        List<PaymentAllocationEntity> allocations = paymentAllocationMapper.selectList(
                new LambdaQueryWrapper<PaymentAllocationEntity>()
                        .eq(PaymentAllocationEntity::getEnrollmentOrderId, order.getId())
                        .eq(PaymentAllocationEntity::getEnrollmentOrderItemId, item.getId()));
        if (allocations == null || allocations.isEmpty()) {
            return singleParticipantOrder ? resolveItemPaidAmount(item) : ZERO_AMOUNT;
        }
        Map<Long, PaymentRecordEntity> recordsById = paymentRecordMapper.selectBatchIds(allocations.stream()
                        .map(PaymentAllocationEntity::getPaymentRecordId)
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(PaymentRecordEntity::getId, Function.identity(), (left, right) -> left));
        BigDecimal amount = ZERO_AMOUNT;
        for (PaymentAllocationEntity allocation : allocations) {
            PaymentRecordEntity record = recordsById.get(allocation.getPaymentRecordId());
            if (record == null || "VOIDED".equalsIgnoreCase(record.getPaymentStatus())) {
                continue;
            }
            boolean participantMatches = Objects.equals(record.getParticipantIndex(), participant.getParticipantIndex())
                    || (singleParticipantOrder && record.getParticipantIndex() == null);
            if (participantMatches) {
                amount = amount.add(defaultAmount(allocation.getAmount()));
            }
        }
        return amount.max(ZERO_AMOUNT).setScale(2, RoundingMode.HALF_UP);
    }

    private EnrollmentOrderParticipantEntity legacyPrimaryParticipant(EnrollmentOrderEntity order) {
        EnrollmentOrderParticipantEntity participant = new EnrollmentOrderParticipantEntity();
        participant.setEnrollmentOrderId(order.getId());
        participant.setStudentId(order.getStudentId());
        participant.setParticipantIndex(1);
        participant.setStudentNameSnapshot(order.getStudentNameSnapshot());
        return participant;
    }

    /** 整个参与学生退款时，必须把负向流水明确分摊到该学生每门课，确保其他参与人金额不变。 */
    private void allocateFullParticipantRefund(EnrollmentOrderEntity order,
                                               EnrollmentOrderParticipantEntity participant,
                                               PaymentRecordEntity refundRecord,
                                               BigDecimal refundAmount) {
        List<EnrollmentOrderItemEntity> items = enrollmentOrderItemMapper.selectList(
                new LambdaQueryWrapper<EnrollmentOrderItemEntity>()
                        .eq(EnrollmentOrderItemEntity::getEnrollmentOrderId, order.getId())
                        .eq(EnrollmentOrderItemEntity::getIsDeleted, 0));
        BigDecimal allocated = ZERO_AMOUNT;
        for (EnrollmentOrderItemEntity item : items) {
            BigDecimal paid = resolveParticipantCoursePaidAmount(order, item, participant, false);
            if (paid.compareTo(ZERO_AMOUNT) > 0) {
                paymentAllocationService.recordRefundAllocation(refundRecord, order.getId(), item.getId(), paid);
                allocated = allocated.add(paid);
            }
        }
        if (allocated.compareTo(refundAmount) != 0) {
            throw new BusinessException("REFUND_PARTICIPANT_ALLOCATION_REQUIRED",
                    "This student's historical payments are not fully assigned to courses. Review the receipt allocations before refunding.");
        }
    }

    /** 整位学生退出收据时逐课程记录消耗快照，并只关闭该学生的课程关联。 */
    private void stopParticipantCoursesForFullRefund(EnrollmentOrderEntity order,
                                                      EnrollmentOrderParticipantEntity participant,
                                                      int participantCount) {
        List<EnrollmentOrderItemEntity> items = enrollmentOrderItemMapper.selectList(
                new LambdaQueryWrapper<EnrollmentOrderItemEntity>()
                        .eq(EnrollmentOrderItemEntity::getEnrollmentOrderId, order.getId())
                        .eq(EnrollmentOrderItemEntity::getIsDeleted, 0));
        for (EnrollmentOrderItemEntity item : items) {
            ClassUsage usage = calculateParticipantCourseUsage(order, item, participant, participantCount);
            if (item.getClassId() != null) {
                saveParticipantCourseRefundState(order, item, participant, usage);
                for (ClassEnrollmentEntity enrollment : listClassEnrollments(item, order)) {
                    if (!Objects.equals(enrollment.getStudentId(), participant.getStudentId())
                            || !"ACTIVE".equalsIgnoreCase(enrollment.getStatus())) {
                        continue;
                    }
                    enrollment.setStatus("WITHDRAWN");
                    classEnrollmentMapper.updateById(enrollment);
                }
            } else {
                stopParticipantCourse(order, item, participant, usage, ZERO_AMOUNT);
            }
        }
    }

    /** 计算目标学生在目标课程上的已消耗金额，避免把同收据其他学生的课次混入停课判断。 */
    private ClassUsage calculateParticipantCourseUsage(EnrollmentOrderEntity order,
                                                       EnrollmentOrderItemEntity item,
                                                       EnrollmentOrderParticipantEntity participant,
                                                       int participantCount) {
        List<ClassEnrollmentEntity> enrollments = listClassEnrollments(item, order);
        if (participant != null) {
            enrollments = enrollments.stream()
                    .filter(enrollment -> Objects.equals(enrollment.getStudentId(), participant.getStudentId()))
                    .toList();
        }
        if (item.getClassId() != null) {
            return calculateClassUsage(order, item, enrollments, Math.max(1, participantCount), participant);
        }
        if (participant == null || participant.getStudentId() == null) {
            return ClassUsage.empty();
        }
        List<CoursePackageEntity> packages = coursePackageMapper.selectList(
                new LambdaQueryWrapper<CoursePackageEntity>()
                        .eq(CoursePackageEntity::getEnrollmentOrderId, order.getId())
                        .eq(CoursePackageEntity::getEnrollmentOrderItemId, item.getId())
                        .eq(CoursePackageEntity::getStudentId, participant.getStudentId())
                        .eq(CoursePackageEntity::getIsDeleted, 0));
        if (packages == null || packages.isEmpty()) {
            return ClassUsage.empty();
        }
        CoursePackageEntity coursePackage = packages.get(0);
        List<LessonRecordEntity> records = lessonRecordMapper.selectList(
                new LambdaQueryWrapper<LessonRecordEntity>()
                        .eq(LessonRecordEntity::getPackageId, coursePackage.getId())
                        .eq(LessonRecordEntity::getConfirmStage, "CONFIRMED")
                        .eq(LessonRecordEntity::getIsDeleted, 0));
        int usedUnits = records.stream()
                .filter(record -> record.getPlannedStartAt() == null || order.getCreatedAt() == null
                        || !record.getPlannedStartAt().isBefore(order.getCreatedAt()))
                .mapToInt(record -> record.getDeductedUnits() == null ? 0 : Math.max(0, record.getDeductedUnits()))
                .sum();
        BigDecimal openingHours = defaultAmount(item.getOpeningUsedHours());
        int openingUnits = openingHours.multiply(BigDecimal.valueOf(2)).setScale(0, RoundingMode.HALF_UP).intValue();
        usedUnits += Math.max(0, openingUnits);
        BigDecimal packageUnits = item.getOneOnOneUnits() == null ? BigDecimal.ZERO
                : BigDecimal.valueOf(item.getOneOnOneUnits());
        if (packageUnits.compareTo(BigDecimal.ZERO) <= 0) {
            return ClassUsage.empty();
        }
        if (usedUnits <= 0) {
            return new ClassUsage(0, ZERO_AMOUNT, null, null, true);
        }
        BigDecimal studentGross = participantCourseGross(order, item, participant, participantCount)
                .subtract(participantCourseDiscount(order, item, participant, participantCount))
                .max(ZERO_AMOUNT);
        BigDecimal consumed = studentGross.multiply(BigDecimal.valueOf(usedUnits))
                .divide(packageUnits, WholeDollarBilling.appliesTo(order) ? 0 : 2,
                        WholeDollarBilling.appliesTo(order) ? RoundingMode.CEILING : RoundingMode.HALF_UP);
        LocalDate first = records.stream()
                .filter(record -> record.getDeductedUnits() != null && record.getDeductedUnits() > 0)
                .filter(record -> record.getPlannedStartAt() != null)
                .filter(record -> order.getCreatedAt() == null || !record.getPlannedStartAt().isBefore(order.getCreatedAt()))
                .map(record -> record.getPlannedStartAt().toLocalDate()).min(Comparator.naturalOrder()).orElse(null);
        LocalDate last = records.stream()
                .filter(record -> record.getDeductedUnits() != null && record.getDeductedUnits() > 0)
                .filter(record -> record.getPlannedStartAt() != null)
                .filter(record -> order.getCreatedAt() == null || !record.getPlannedStartAt().isBefore(order.getCreatedAt()))
                .map(record -> record.getPlannedStartAt().toLocalDate()).max(Comparator.naturalOrder()).orElse(null);
        return new ClassUsage(usedUnits, consumed, first, last, true);
    }

    /** 退款窗口没有选定学生时，展示整张收据的已消耗合计；行级退款使用上面的单学生计算。 */
    private ClassUsage calculateRefundPreviewUsage(EnrollmentOrderEntity order,
                                                   EnrollmentOrderItemEntity item,
                                                   EnrollmentOrderParticipantEntity participant,
                                                   List<EnrollmentOrderParticipantEntity> participants) {
        if (participant != null) {
            return calculateParticipantCourseUsage(order, item, participant, participants.size());
        }
        if (participants == null || participants.isEmpty()) {
            return item.getClassId() == null ? ClassUsage.empty()
                    : calculateClassUsage(order, item, listClassEnrollments(item, order), 1);
        }
        int count = 0;
        BigDecimal amount = ZERO_AMOUNT;
        LocalDate first = null;
        LocalDate last = null;
        for (EnrollmentOrderParticipantEntity row : participants) {
            ClassUsage usage = calculateParticipantCourseUsage(order, item, row, participants.size());
            count += usage.consumedLessonCount();
            amount = amount.add(usage.consumedAmount());
            if (usage.firstUsedDate() != null && (first == null || usage.firstUsedDate().isBefore(first))) {
                first = usage.firstUsedDate();
            }
            if (usage.lastUsedDate() != null && (last == null || usage.lastUsedDate().isAfter(last))) {
                last = usage.lastUsedDate();
            }
        }
        return new ClassUsage(count, amount.setScale(2, RoundingMode.HALF_UP), first, last, count > 0);
    }

    /** 退款达到消耗线时只停止这个学生的这门课；共享私教/班课的其他成员和课次保留。 */
    private void stopParticipantCourse(EnrollmentOrderEntity order,
                                       EnrollmentOrderItemEntity item,
                                       EnrollmentOrderParticipantEntity participant,
                                       ClassUsage usage,
                                       BigDecimal remainingPaidAmount) {
        if (participant == null) {
            return;
        }
        if (isParticipantCourseStopped(item, participant)) {
            return;
        }
        reduceStoppedCourseDue(order, item, participant, usage, remainingPaidAmount);
        saveParticipantCourseRefundState(order, item, participant, usage);
        if (item.getClassId() != null) {
            for (ClassEnrollmentEntity enrollment : listClassEnrollments(item, order)) {
                if (!Objects.equals(enrollment.getStudentId(), participant.getStudentId())
                        || !"ACTIVE".equalsIgnoreCase(enrollment.getStatus())) {
                    continue;
                }
                enrollment.setStatus("WITHDRAWN");
                if (usage.lastUsedDate() != null) {
                    enrollment.setExpiryDate(usage.lastUsedDate());
                }
                classEnrollmentMapper.updateById(enrollment);
            }
            return;
        }
        List<CoursePackageEntity> packages = coursePackageMapper.selectList(
                new LambdaQueryWrapper<CoursePackageEntity>()
                        .eq(CoursePackageEntity::getEnrollmentOrderId, order.getId())
                        .eq(CoursePackageEntity::getEnrollmentOrderItemId, item.getId())
                        .eq(CoursePackageEntity::getStudentId, participant.getStudentId())
                        .eq(CoursePackageEntity::getIsDeleted, 0));
        boolean sharedOrder = listOrderParticipants(order.getId()).size() > 1;
        for (CoursePackageEntity coursePackage : packages) {
            int lineUnits = Math.max(0, item.getOneOnOneUnits() == null ? 0 : item.getOneOnOneUnits());
            int consumedUnits = Math.min(lineUnits, Math.max(0, usage.consumedLessonCount()));
            int unconsumedUnits = Math.max(0, lineUnits - consumedUnits);
            int newTotalUnits = Math.max(0, safeInt(coursePackage.getTotalUnits()) - unconsumedUnits);
            int newRemainingUnits = Math.min(newTotalUnits, Math.max(0,
                    safeInt(coursePackage.getRemainingUnits()) - unconsumedUnits));
            coursePackage.setTotalUnits(newTotalUnits);
            coursePackage.setRemainingUnits(newRemainingUnits);
            coursePackage.setConsumedUnits(Math.max(0, newTotalUnits - newRemainingUnits));
            coursePackage.setReceivedAmount(defaultAmount(remainingPaidAmount));
            boolean packageStillHasHours = newTotalUnits > 0 && newRemainingUnits > 0;
            coursePackage.setPackageStatus(packageStillHasHours ? "ACTIVE" : "CLOSED");
            if (!packageStillHasHours) {
                coursePackage.setCompletedAt(AppTime.nowLocalDateTime());
            }
            coursePackageMapper.updateById(coursePackage);
            PrivateLessonGroupMemberEntity member = privateLessonGroupMemberMapper.selectOne(
                    new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                            .eq(PrivateLessonGroupMemberEntity::getPackageId, coursePackage.getId())
                            .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                            .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0));
            if (member != null && !packageStillHasHours) {
                member.setMemberStatus("INACTIVE");
                privateLessonGroupMemberMapper.updateById(member);
            }
            if (!packageStillHasHours && member != null
                    && !hasActivePrivateLessonGroupMembers(member.getPrivateLessonGroupId())) {
                cancelFutureOccurrencesForPrivateGroup(member.getPrivateLessonGroupId());
                deactivateRecurringSchedulesForPrivateGroup(member.getPrivateLessonGroupId());
            } else if (!packageStillHasHours && member == null && !sharedOrder) {
                cancelFutureOccurrences(List.of(coursePackage.getId()));
                deactivateRecurringSchedules(List.of(coursePackage.getId()));
            }
            // 旧共享包缺少成员映射时，无法证明课次只属于该学生，因此保留共享课表。
        }
    }

    /** 停课后只将目标学生该课程未消费的应收额从个人和订单汇总中扣除一次。 */
    private void reduceStoppedCourseDue(EnrollmentOrderEntity order,
                                        EnrollmentOrderItemEntity item,
                                        EnrollmentOrderParticipantEntity participant,
                                        ClassUsage usage,
                                        BigDecimal remainingPaidAmount) {
        List<EnrollmentOrderParticipantEntity> participants = listOrderParticipants(order.getId());
        int participantCount = Math.max(1, participants.size());
        BigDecimal originalCourseDue = participantCount <= 1
                ? defaultAmount(item.getAmountDue())
                : participantCourseGross(order, item, participant, participantCount)
                        .add(participantCourseFee(item, participant))
                        .subtract(participantCourseDiscount(order, item, participant, participantCount))
                        .max(ZERO_AMOUNT).setScale(2, RoundingMode.HALF_UP);
        BigDecimal stoppedCourseDue = usage.consumedAmount().min(originalCourseDue)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal reduction = originalCourseDue.subtract(stoppedCourseDue).max(ZERO_AMOUNT);
        if (reduction.compareTo(ZERO_AMOUNT) <= 0) {
            return;
        }

        if (participantCount <= 1) {
            item.setAmountDue(stoppedCourseDue);
            item.setItemStatus("STOPPED");
            item.setPaidAmount(defaultAmount(remainingPaidAmount));
            item.setBalanceAmount(stoppedCourseDue.subtract(defaultAmount(remainingPaidAmount)));
            enrollmentOrderItemMapper.updateById(item);
        } else {
            participant.setAmountDue(defaultAmount(participant.getAmountDue()).subtract(reduction).max(ZERO_AMOUNT));
            participant.setBalanceAmount(defaultAmount(participant.getAmountDue())
                    .subtract(defaultAmount(participant.getPaidAmount())));
            enrollmentOrderParticipantMapper.updateById(participant);
        }
        order.setAmountDue(defaultAmount(order.getAmountDue()).subtract(reduction).max(ZERO_AMOUNT));
        order.setBalanceAmount(defaultAmount(order.getAmountDue()).subtract(defaultAmount(order.getReceivedAmount())));
        order.setPaymentStatus(resolvePaymentStatus(order.getAmountDue(), ZERO_AMOUNT, order.getReceivedAmount()));
        enrollmentOrderMapper.updateById(order);
    }

    private BigDecimal participantCourseFee(EnrollmentOrderItemEntity item,
                                            EnrollmentOrderParticipantEntity participant) {
        if (participant.getParticipantIndex() == null || participant.getParticipantIndex() != 1) {
            return ZERO_AMOUNT;
        }
        return defaultAmount(item.getAmountDue()).subtract(defaultAmount(item.getPriceAmount()))
                .add(defaultAmount(item.getDiscountAmount())).max(ZERO_AMOUNT);
    }

    private boolean hasActivePrivateLessonGroupMembers(Long groupId) {
        if (groupId == null) {
            return false;
        }
        return privateLessonGroupMemberMapper.selectCount(new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                .eq(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId, groupId)
                .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0)) > 0;
    }

    private void cancelFutureOccurrencesForPrivateGroup(Long groupId) {
        if (groupId == null) {
            return;
        }
        lessonOccurrenceMapper.update(new LambdaUpdateWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getPrivateLessonGroupId, groupId)
                .in(LessonOccurrenceEntity::getPlanningStatus, List.of("SCHEDULED", "ADJUSTED"))
                .gt(LessonOccurrenceEntity::getPlannedStartAt, AppTime.nowLocalDateTime())
                .set(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED"));
    }

    private void deactivateRecurringSchedulesForPrivateGroup(Long groupId) {
        if (groupId == null) {
            return;
        }
        List<RecurringScheduleRuleEntity> rules = recurringScheduleRuleMapper.selectList(
                new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                        .eq(RecurringScheduleRuleEntity::getPrivateLessonGroupId, groupId)
                        .ne(RecurringScheduleRuleEntity::getRuleStatus, "INACTIVE"));
        if (rules.isEmpty()) {
            return;
        }
        List<Long> ruleIds = rules.stream().map(RecurringScheduleRuleEntity::getId).toList();
        recurringScheduleRuleMapper.update(new LambdaUpdateWrapper<RecurringScheduleRuleEntity>()
                .in(RecurringScheduleRuleEntity::getId, ruleIds)
                .set(RecurringScheduleRuleEntity::getRuleStatus, "INACTIVE"));
        recurringScheduleSlotMapper.update(new LambdaUpdateWrapper<RecurringScheduleSlotEntity>()
                .in(RecurringScheduleSlotEntity::getRuleId, ruleIds)
                .set(RecurringScheduleSlotEntity::getSlotStatus, "INACTIVE"));
    }

    /** 停课状态按收据课程和参与学生精确存档，不使用共享订单项的全局状态。 */
    private void saveParticipantCourseRefundState(EnrollmentOrderEntity order,
                                                  EnrollmentOrderItemEntity item,
                                                  EnrollmentOrderParticipantEntity participant,
                                                  ClassUsage usage) {
        EnrollmentOrderCourseRefundStateEntity state = enrollmentOrderCourseRefundStateMapper.selectOne(
                new LambdaQueryWrapper<EnrollmentOrderCourseRefundStateEntity>()
                        .eq(EnrollmentOrderCourseRefundStateEntity::getEnrollmentOrderItemId, item.getId())
                        .eq(EnrollmentOrderCourseRefundStateEntity::getStudentId, participant.getStudentId())
                        .eq(EnrollmentOrderCourseRefundStateEntity::getIsDeleted, 0));
        if (state == null) {
            state = new EnrollmentOrderCourseRefundStateEntity();
            state.setEnrollmentOrderId(order.getId());
            state.setEnrollmentOrderItemId(item.getId());
            state.setStudentId(participant.getStudentId());
            state.setParticipantIndex(participant.getParticipantIndex());
        }
        state.setItemStatus("STOPPED");
        state.setStoppedConsumedAmount(usage.consumedAmount());
        state.setStoppedSessionCount(usage.consumedLessonCount());
        state.setStoppedPeriodStartDate(usage.firstUsedDate());
        state.setStoppedPeriodEndDate(usage.lastUsedDate());
        state.setStoppedAt(AppTime.nowLocalDateTime());
        if (state.getId() == null) {
            enrollmentOrderCourseRefundStateMapper.insert(state);
        } else {
            enrollmentOrderCourseRefundStateMapper.updateById(state);
        }
    }

    /** 查询订单项关联的班课报名；旧数据缺少 order_item_id 时按班级与学生兼容查找。 */
    private List<ClassEnrollmentEntity> listClassEnrollments(EnrollmentOrderItemEntity item, EnrollmentOrderEntity order) {
        if (item.getClassId() == null) {
            return Collections.emptyList();
        }
        List<ClassEnrollmentEntity> enrollments = classEnrollmentMapper.selectList(
                new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getEnrollmentOrderItemId, item.getId())
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0));
        if (enrollments != null && !enrollments.isEmpty()) {
            return enrollments;
        }
        Set<Long> studentIds = listOrderParticipants(order.getId()).stream()
                .map(EnrollmentOrderParticipantEntity::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (order.getStudentId() != null) {
            studentIds.add(order.getStudentId());
        }
        if (studentIds.isEmpty()) {
            return Collections.emptyList();
        }
        return classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                .eq(ClassEnrollmentEntity::getClassId, item.getClassId())
                .in(ClassEnrollmentEntity::getStudentId, studentIds)
                .eq(ClassEnrollmentEntity::getIsDeleted, 0)
                .eq(ClassEnrollmentEntity::getStatus, "ACTIVE"))
                .stream()
                .filter(enrollment -> order.getSignupDate() == null
                        || enrollment.getEnrollmentDate() == null
                        || !order.getSignupDate().isBefore(enrollment.getEnrollmentDate()))
                .toList();
    }

    /** 已确认且可计费的班课记录按学生与上课日期去重，金额使用报名时课程价和优惠快照。 */
    private ClassUsage calculateClassUsage(EnrollmentOrderEntity order, EnrollmentOrderItemEntity item,
                                           List<ClassEnrollmentEntity> enrollments) {
        return calculateClassUsage(order, item, enrollments, Math.max(1, enrollments.size()));
    }

    private ClassUsage calculateClassUsage(EnrollmentOrderEntity order, EnrollmentOrderItemEntity item,
                                           List<ClassEnrollmentEntity> enrollments,
                                           int participantCount) {
        return calculateClassUsage(order, item, enrollments, participantCount, null);
    }

    private ClassUsage calculateClassUsage(EnrollmentOrderEntity order, EnrollmentOrderItemEntity item,
                                           List<ClassEnrollmentEntity> enrollments,
                                           int participantCount,
                                           EnrollmentOrderParticipantEntity participant) {
        if (item.getClassId() == null || enrollments == null || enrollments.isEmpty()) {
            return ClassUsage.empty();
        }
        boolean sessionBilled = "SESSION".equalsIgnoreCase(item.getPackageUnit())
                || "PER_SESSION".equalsIgnoreCase(item.getPackageUnit())
                || enrollments.stream().anyMatch(enrollment -> "PER_SESSION".equalsIgnoreCase(enrollment.getBillingType()));
        if (!sessionBilled) {
            return ClassUsage.empty();
        }
        List<LessonOccurrenceEntity> occurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .select(LessonOccurrenceEntity::getId, LessonOccurrenceEntity::getPlannedStartAt)
                        .eq(LessonOccurrenceEntity::getClassGroupId, item.getClassId())
                        .eq(LessonOccurrenceEntity::getIsDeleted, 0));
        Map<Long, LocalDate> dateByOccurrenceId = occurrences.stream()
                .filter(occurrence -> occurrence.getPlannedStartAt() != null)
                .collect(Collectors.toMap(LessonOccurrenceEntity::getId,
                        occurrence -> occurrence.getPlannedStartAt().toLocalDate(), (left, right) -> left));
        if (dateByOccurrenceId.isEmpty()) {
            return new ClassUsage(0, ZERO_AMOUNT, null, null, true);
        }

        BigDecimal tuition = participant == null
                ? defaultAmount(item.getPriceAmount()).subtract(defaultAmount(item.getDiscountAmount()))
                .max(ZERO_AMOUNT)
                : participantCourseGross(order, item, participant, participantCount)
                .subtract(participantCourseDiscount(order, item, participant, participantCount)).max(ZERO_AMOUNT);
        BigDecimal sessionCount = item.getPackageQuantity();
        if (sessionCount == null || sessionCount.compareTo(BigDecimal.ZERO) <= 0) {
            sessionCount = enrollments.stream().map(ClassEnrollmentEntity::getPurchasedQuantity)
                    .filter(Objects::nonNull).map(BigDecimal::valueOf).findFirst().orElse(BigDecimal.ZERO);
        }
        if (sessionCount.compareTo(BigDecimal.ZERO) <= 0) {
            return new ClassUsage(0, ZERO_AMOUNT, null, null, true);
        }
        BigDecimal billedSessions = participant == null
                ? sessionCount.multiply(BigDecimal.valueOf(Math.max(1, participantCount))) : sessionCount;
        BigDecimal amountPerStudentSession = tuition.divide(billedSessions, 4, RoundingMode.HALF_UP);

        Map<Long, Set<LocalDate>> datesByStudent = new LinkedHashMap<>();
        List<LessonRecordEntity> records = lessonRecordMapper.selectList(
                new LambdaQueryWrapper<LessonRecordEntity>()
                        .in(LessonRecordEntity::getOccurrenceId, dateByOccurrenceId.keySet())
                        .eq(LessonRecordEntity::getConfirmStage, "CONFIRMED")
                        .eq(LessonRecordEntity::getIsDeleted, 0));
        for (ClassEnrollmentEntity enrollment : enrollments) {
            Set<LocalDate> dates = records.stream()
                    .filter(record -> Objects.equals(record.getStudentId(), enrollment.getStudentId()))
                    .filter(record -> record.getResultStatus() != null
                            && Set.of("COMPLETED", "CANCELLED").contains(record.getResultStatus()))
                    .map(record -> dateByOccurrenceId.get(record.getOccurrenceId()))
                    .filter(Objects::nonNull)
                    .filter(date -> enrollment.getEnrollmentDate() == null || !date.isBefore(enrollment.getEnrollmentDate()))
                    .filter(date -> enrollment.getExpiryDate() == null || !date.isAfter(enrollment.getExpiryDate()))
                    .collect(Collectors.toSet());
            datesByStudent.computeIfAbsent(enrollment.getStudentId(), ignored -> new java.util.HashSet<>())
                    .addAll(dates);
        }
        int count = datesByStudent.values().stream().mapToInt(Set::size).sum();
        LocalDate firstUsedDate = datesByStudent.values().stream().flatMap(Set::stream)
                .min(Comparator.naturalOrder()).orElse(null);
        LocalDate lastUsedDate = datesByStudent.values().stream().flatMap(Set::stream)
                .max(Comparator.naturalOrder()).orElse(null);
        BigDecimal consumedAmount = WholeDollarBilling.appliesTo(order)
                ? tuition.multiply(BigDecimal.valueOf(count)).divide(billedSessions, 0, RoundingMode.CEILING).setScale(2)
                : amountPerStudentSession.multiply(BigDecimal.valueOf(count)).setScale(2, RoundingMode.HALF_UP);
        return new ClassUsage(count, consumedAmount, firstUsedDate, lastUsedDate, true);
    }

    /** 还原报名时某个学生在单个课程项上的实际原价份额。 */
    private BigDecimal participantCourseGross(EnrollmentOrderEntity order, EnrollmentOrderItemEntity item,
                                             EnrollmentOrderParticipantEntity participant,
                                             int participantCount) {
        BigDecimal total = defaultAmount(item.getPriceAmount());
        if (participant == null || participantCount <= 1) {
            return total;
        }
        BigDecimal share = total.divide(BigDecimal.valueOf(participantCount),
                WholeDollarBilling.appliesTo(order) ? 0 : 2, RoundingMode.DOWN).setScale(2);
        return participant.getParticipantIndex() != null && participant.getParticipantIndex() == participantCount
                ? total.subtract(share.multiply(BigDecimal.valueOf(participantCount - 1))).setScale(2, RoundingMode.HALF_UP)
                : share;
    }

    /** 优先用学生收据快照里的课程折扣；旧数据没有课程归属时沿用报名时的按价格比例分摊。 */
    private BigDecimal participantCourseDiscount(EnrollmentOrderEntity order, EnrollmentOrderItemEntity item,
                                                 EnrollmentOrderParticipantEntity participant,
                                                 int participantCount) {
        if (participant == null || participantCount <= 1) {
            return defaultAmount(item.getDiscountAmount());
        }
        List<Map<String, Object>> discounts = StringUtils.hasText(participant.getDiscountDetail())
                ? jsonHelper.fromJson(participant.getDiscountDetail(), new TypeReference<List<Map<String, Object>>>() {})
                : Collections.emptyList();
        boolean hasCourseAssignments = discounts != null && discounts.stream().filter(Objects::nonNull)
                .anyMatch(line -> line.get("orderItemId") != null || line.get("orderItemLineNo") != null || line.get("lineNo") != null);
        if (hasCourseAssignments) {
            return discounts.stream().filter(Objects::nonNull)
                    .filter(line -> belongsToOrderItem(line, item))
                    .map(line -> toBigDecimal(line.get("amount")))
                    .reduce(ZERO_AMOUNT, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        List<EnrollmentOrderItemEntity> orderItems = enrollmentOrderItemMapper.selectList(
                new LambdaQueryWrapper<EnrollmentOrderItemEntity>()
                        .eq(EnrollmentOrderItemEntity::getEnrollmentOrderId, item.getEnrollmentOrderId())
                        .eq(EnrollmentOrderItemEntity::getIsDeleted, 0)
                        .orderByAsc(EnrollmentOrderItemEntity::getLineNo));
        if (orderItems == null || orderItems.size() <= 1) {
            return defaultAmount(participant.getDiscountAmount()).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal totalDiscount = defaultAmount(participant.getDiscountAmount());
        BigDecimal totalWeight = orderItems.stream().map(course -> defaultAmount(course.getPriceAmount()))
                .reduce(ZERO_AMOUNT, BigDecimal::add);
        BigDecimal assigned = ZERO_AMOUNT;
        for (int index = 0; index < orderItems.size(); index++) {
            EnrollmentOrderItemEntity course = orderItems.get(index);
            BigDecimal share = index == orderItems.size() - 1
                    ? totalDiscount.subtract(assigned)
                    : totalWeight.compareTo(ZERO_AMOUNT) <= 0
                    ? totalDiscount.divide(BigDecimal.valueOf(orderItems.size()),
                            WholeDollarBilling.appliesTo(order) ? 0 : 2, RoundingMode.DOWN)
                    : totalDiscount.multiply(defaultAmount(course.getPriceAmount()))
                    .divide(totalWeight, WholeDollarBilling.appliesTo(order) ? 0 : 2, RoundingMode.DOWN);
            if (Objects.equals(course.getId(), item.getId())) {
                return share.setScale(2, RoundingMode.HALF_UP);
            }
            assigned = assigned.add(share);
        }
        return ZERO_AMOUNT;
    }

    /** 班课达到已消耗金额后移出学生名单并归档报名明细，保留共享班级的课次安排。 */
    private void markClassCourseStopped(EnrollmentOrderEntity order,
                                       EnrollmentOrderItemEntity item,
                                       ClassUsage usage) {
        for (ClassEnrollmentEntity enrollment : listClassEnrollments(item, order)) {
            if (!"ACTIVE".equalsIgnoreCase(enrollment.getStatus())) {
                continue;
            }
            enrollment.setStatus("WITHDRAWN");
            if (usage.lastUsedDate() != null) {
                enrollment.setExpiryDate(usage.lastUsedDate());
            }
            classEnrollmentMapper.updateById(enrollment);
        }
        item.setItemStatus("STOPPED");
        BigDecimal previousDue = defaultAmount(item.getAmountDue());
        BigDecimal stoppedDue = usage.consumedAmount().min(previousDue);
        item.setStoppedConsumedAmount(usage.consumedAmount());
        item.setStoppedSessionCount(usage.consumedLessonCount());
        item.setStoppedPeriodStartDate(usage.firstUsedDate());
        item.setStoppedPeriodEndDate(usage.lastUsedDate());
        item.setStoppedAt(AppTime.nowLocalDateTime());
        item.setAmountDue(stoppedDue);
        item.setBalanceAmount(stoppedDue.subtract(defaultAmount(item.getPaidAmount())));
        order.setAmountDue(defaultAmount(order.getAmountDue()).subtract(previousDue).add(stoppedDue)
                .max(ZERO_AMOUNT));
        enrollmentOrderItemMapper.updateById(item);
    }

    /** 整張收據退款時，退款按舊有課程比例分攤；逐項檢查哪些班課已退到已消耗金額。 */
    private boolean stopCoursesAtConsumedThreshold(EnrollmentOrderEntity order,
                                                   Map<Long, BigDecimal> paidBeforeRefund) {
        List<EnrollmentOrderItemEntity> items = enrollmentOrderItemMapper.selectList(
                new LambdaQueryWrapper<EnrollmentOrderItemEntity>()
                        .eq(EnrollmentOrderItemEntity::getEnrollmentOrderId, order.getId())
                        .eq(EnrollmentOrderItemEntity::getIsDeleted, 0));
        boolean stoppedAny = false;
        for (EnrollmentOrderItemEntity item : items) {
            if ("STOPPED".equalsIgnoreCase(item.getItemStatus())) {
                continue;
            }
            List<ClassEnrollmentEntity> enrollments = listClassEnrollments(item, order);
            if (enrollments.stream().noneMatch(enrollment -> "ACTIVE".equalsIgnoreCase(enrollment.getStatus()))) {
                continue;
            }
            ClassUsage usage = calculateClassUsage(order, item, enrollments);
            BigDecimal previousPaid = paidBeforeRefund.get(item.getId());
            BigDecimal currentPaid = resolveItemPaidAmount(item);
            if (usage.automaticStopSupported() && previousPaid != null
                    && currentPaid.compareTo(previousPaid) < 0
                    && currentPaid.compareTo(usage.consumedAmount()) <= 0) {
                markClassCourseStopped(order, item, usage);
                stoppedAny = true;
            }
        }
        return stoppedAny;
    }

    private Map<Long, BigDecimal> snapshotCoursePaidAmounts(EnrollmentOrderEntity order) {
        Map<Long, BigDecimal> amounts = new LinkedHashMap<>();
        enrollmentOrderItemMapper.selectList(new LambdaQueryWrapper<EnrollmentOrderItemEntity>()
                        .eq(EnrollmentOrderItemEntity::getEnrollmentOrderId, order.getId())
                        .eq(EnrollmentOrderItemEntity::getIsDeleted, 0))
                .forEach(item -> amounts.put(item.getId(), resolveItemPaidAmount(item)));
        return amounts;
    }

    private BigDecimal resolveItemPaidAmount(EnrollmentOrderItemEntity item) {
        List<PaymentAllocationEntity> allocations = paymentAllocationMapper.selectList(
                new LambdaQueryWrapper<PaymentAllocationEntity>()
                        .eq(PaymentAllocationEntity::getEnrollmentOrderItemId, item.getId()));
        if (allocations == null || allocations.isEmpty()) {
            return defaultAmount(item.getPaidAmount());
        }
        return allocations.stream().map(PaymentAllocationEntity::getAmount)
                .map(this::defaultAmount).reduce(ZERO_AMOUNT, BigDecimal::add).max(ZERO_AMOUNT);
    }

    private record ClassUsage(int consumedLessonCount,
                              BigDecimal consumedAmount,
                              LocalDate firstUsedDate,
                              LocalDate lastUsedDate,
                              boolean automaticStopSupported) {
        private static ClassUsage empty() {
            return new ClassUsage(0, ZERO_AMOUNT, null, null, false);
        }
    }

    private EnrollmentOrderParticipantEntity findOrderParticipant(Long orderId, Integer participantIndex) {
        return enrollmentOrderParticipantMapper.selectOne(
                new LambdaQueryWrapper<EnrollmentOrderParticipantEntity>()
                        .eq(EnrollmentOrderParticipantEntity::getEnrollmentOrderId, orderId)
                        .eq(EnrollmentOrderParticipantEntity::getParticipantIndex, participantIndex)
                        .eq(EnrollmentOrderParticipantEntity::getIsDeleted, 0));
    }

    private List<EnrollmentOrderParticipantEntity> listOrderParticipants(Long orderId) {
        List<EnrollmentOrderParticipantEntity> participants = enrollmentOrderParticipantMapper.selectList(
                new LambdaQueryWrapper<EnrollmentOrderParticipantEntity>()
                        .eq(EnrollmentOrderParticipantEntity::getEnrollmentOrderId, orderId)
                        .eq(EnrollmentOrderParticipantEntity::getIsDeleted, 0)
                        .orderByAsc(EnrollmentOrderParticipantEntity::getParticipantIndex));
        return participants == null ? Collections.emptyList() : participants;
    }

    /**
     * 参与学生的退款额度以带 participant_index 的支付流水为准，兼容历史主学生未带序号的流水。
     */
    private BigDecimal resolveParticipantReceivedAmount(Long orderId,
                                                        EnrollmentOrderParticipantEntity participant) {
        List<PaymentRecordEntity> payments = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecordEntity>()
                        .eq(PaymentRecordEntity::getEnrollmentOrderId, orderId));
        if (payments == null) {
            payments = Collections.emptyList();
        }
        BigDecimal scoped = payments.stream()
                .filter(item -> !"VOIDED".equals(item.getPaymentStatus()))
                .filter(item -> Objects.equals(item.getParticipantIndex(), participant.getParticipantIndex()))
                .map(item -> defaultAmount(item.getAmount()))
                .reduce(ZERO_AMOUNT, BigDecimal::add);
        if (Objects.equals(participant.getParticipantIndex(), 1)) {
            return scoped.add(payments.stream()
                    .filter(item -> item.getParticipantIndex() == null)
                    .filter(item -> !"VOIDED".equals(item.getPaymentStatus()))
                    .map(item -> defaultAmount(item.getAmount()))
                    .reduce(ZERO_AMOUNT, BigDecimal::add))
                    .max(ZERO_AMOUNT);
        }
        return scoped.compareTo(ZERO_AMOUNT) != 0
                ? scoped.max(ZERO_AMOUNT)
                : defaultAmount(participant.getPaidAmount()).max(ZERO_AMOUNT);
    }

    private void syncParticipantPaymentSnapshots(Long orderId) {
        List<EnrollmentOrderParticipantEntity> participants = listOrderParticipants(orderId);
        if (participants.isEmpty()) {
            return;
        }
        List<PaymentRecordEntity> payments = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecordEntity>()
                        .eq(PaymentRecordEntity::getEnrollmentOrderId, orderId));
        if (payments == null) {
            payments = Collections.emptyList();
        }
        for (EnrollmentOrderParticipantEntity participant : participants) {
            BigDecimal paid = payments.stream()
                    .filter(item -> !"VOIDED".equals(item.getPaymentStatus()))
                    .filter(item -> Objects.equals(item.getParticipantIndex(), participant.getParticipantIndex())
                            || (participant.getParticipantIndex() == 1 && item.getParticipantIndex() == null))
                    .map(item -> defaultAmount(item.getAmount()))
                    .reduce(ZERO_AMOUNT, BigDecimal::add);
            participant.setPaidAmount(paid);
            participant.setBalanceAmount(defaultAmount(participant.getAmountDue()).subtract(paid));
            enrollmentOrderParticipantMapper.updateById(participant);
        }
    }

    private void resetParticipantPaymentSnapshots(List<EnrollmentOrderParticipantEntity> participants) {
        for (EnrollmentOrderParticipantEntity participant : participants) {
            participant.setPaidAmount(ZERO_AMOUNT);
            participant.setBalanceAmount(defaultAmount(participant.getAmountDue()));
            enrollmentOrderParticipantMapper.updateById(participant);
        }
    }

    private boolean isParticipantCourseStopped(EnrollmentOrderItemEntity item,
                                               EnrollmentOrderParticipantEntity participant) {
        if (item == null || participant == null) {
            return false;
        }
        EnrollmentOrderCourseRefundStateEntity state = enrollmentOrderCourseRefundStateMapper.selectOne(
                new LambdaQueryWrapper<EnrollmentOrderCourseRefundStateEntity>()
                        .eq(EnrollmentOrderCourseRefundStateEntity::getEnrollmentOrderItemId, item.getId())
                        .eq(EnrollmentOrderCourseRefundStateEntity::getStudentId, participant.getStudentId())
                        .eq(EnrollmentOrderCourseRefundStateEntity::getIsDeleted, 0));
        return state != null && "STOPPED".equalsIgnoreCase(state.getItemStatus());
    }

    /**
     * 为每笔退款生成独立收据快照。
     *
     * <p>报名收据记录报名时点事实，退款收据记录本次负向流水；两者不能互相覆盖，
     * 否则 Receipt 页面无法按凭证类型追溯退款。</p>
     */
    private ReceiptRecordVO createRefundReceipt(EnrollmentOrderEntity order,
                                                RefundRequest request,
                                                PaymentRecordEntity refundRecord,
                                                EnrollmentOrderItemEntity targetItem,
                                                EnrollmentOrderParticipantEntity targetParticipant,
                                                boolean convertToCredit) {
        Long receiptStudentId = targetParticipant == null ? order.getStudentId() : targetParticipant.getStudentId();
        String receiptStudentName = targetParticipant == null
                ? order.getStudentNameSnapshot()
                : targetParticipant.getStudentNameSnapshot();
        if (!StringUtils.hasText(receiptStudentName)) {
            StudentEntity student = studentMapper.selectById(receiptStudentId);
            receiptStudentName = student == null ? order.getStudentNameSnapshot() : student.getStudentName();
        }

        LocalDateTime issuedAt = AppTime.nowLocalDateTime();
        String receiptNo = BusinessNoGenerator.generateReceiptNo();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("receiptType", "REFUND");
        payload.put("receiptNo", receiptNo);
        payload.put("enrollmentOrderId", order.getId());
        payload.put("enrollmentNo", order.getEnrollmentNo());
        payload.put("studentId", receiptStudentId);
        payload.put("studentName", receiptStudentName);
        payload.put("currencyCode", order.getCurrencyCode());
        payload.put("issuedAt", issuedAt);
        payload.put("paymentAmount", refundRecord.getAmount());
        payload.put("amountDue", order.getAmountDue());
        payload.put("receivedAmount", order.getReceivedAmount());
        payload.put("paidAmount", refundRecord.getAmount());
        payload.put("balanceAmount", order.getBalanceAmount());
        payload.put("paymentStatus", order.getPaymentStatus());
        payload.put("paymentType", "REFUND");
        payload.put("refundDisposition", convertToCredit ? "CREDIT" : "REFUND");
        payload.put("participantIndex", refundRecord.getParticipantIndex());
        payload.put("orderItemId", targetItem == null ? null : targetItem.getId());

        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("paymentId", refundRecord.getId());
        payment.put("paymentNo", refundRecord.getPaymentNo());
        payment.put("paymentMethod", refundRecord.getPaymentMethod());
        payment.put("paymentType", refundRecord.getPaymentType());
        payment.put("paymentStatus", refundRecord.getPaymentStatus());
        payment.put("amount", refundRecord.getAmount());
        payment.put("paidAt", refundRecord.getPaidAt());
        payment.put("handledByUserId", refundRecord.getHandledByUserId());
        payment.put("handledByName", refundRecord.getHandledByName());
        payment.put("remark", refundRecord.getRemark());
        payment.put("participantIndex", refundRecord.getParticipantIndex());
        payment.put("orderItemId", targetItem == null ? null : targetItem.getId());
        payload.put("payments", List.of(payment));

        if (targetItem != null) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", targetItem.getId());
            item.put("lineNo", targetItem.getLineNo());
            item.put("detail", targetItem.getDetail());
            item.put("courseType", targetItem.getCourseType());
            item.put("amountDue", targetItem.getAmountDue());
            item.put("paidAmount", targetItem.getPaidAmount());
            item.put("balanceAmount", targetItem.getBalanceAmount());
            payload.put("items", List.of(item));
        }

        ReceiptRecordEntity receipt = new ReceiptRecordEntity();
        receipt.setReceiptNo(receiptNo);
        receipt.setEnrollmentOrderId(order.getId());
        receipt.setStudentId(receiptStudentId);
        receipt.setPaymentRecordId(refundRecord.getId());
        receipt.setTemplateCode(REFUND_RECEIPT_TEMPLATE);
        receipt.setReceiptPayload(jsonHelper.toJson(payload));
        receipt.setIssuedAt(issuedAt);
        receipt.setIssuedBy(request.getHandledByUserId());
        receiptRecordMapper.insert(receipt);
        return toReceiptVO(receipt, receiptStudentName);
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
        List<Long> orderItemIds = listOrderItemsByOrderId(order.getId()).stream()
                .map(EnrollmentOrderItemEntity::getId)
                .toList();
        markClassEnrollmentsWithdrawn(collectClassEnrollments(order, orderItemIds, true));
    }

    /**
     * 收集本单关联的班课在册记录：兼容 legacy class_id 直连与 enrollment_order_item 模型，按 id 去重。
     *
     * @param onlyActive 仅收集 ACTIVE 在册（Cancel / ForceVoid 流程沿用旧行为）
     */
    private List<ClassEnrollmentEntity> collectClassEnrollments(EnrollmentOrderEntity order,
                                                                List<Long> orderItemIds,
                                                                boolean onlyActive) {
        Map<Long, ClassEnrollmentEntity> byId = new LinkedHashMap<>();
        if (order.getClassId() != null) {
            // Legacy direct class orders do not have an order-item reference.
            LambdaQueryWrapper<ClassEnrollmentEntity> legacy = new LambdaQueryWrapper<ClassEnrollmentEntity>()
                    .eq(ClassEnrollmentEntity::getClassId, order.getClassId())
                    .eq(ClassEnrollmentEntity::getStudentId, order.getStudentId());
            if (onlyActive) {
                legacy.eq(ClassEnrollmentEntity::getStatus, "ACTIVE");
            }
            legacy.eq(ClassEnrollmentEntity::getIsDeleted, 0);
            for (ClassEnrollmentEntity enrollment : classEnrollmentMapper.selectList(legacy)) {
                byId.put(enrollment.getId(), enrollment);
            }
        }

        if (!orderItemIds.isEmpty()) {
            LambdaQueryWrapper<ClassEnrollmentEntity> itemBased = new LambdaQueryWrapper<ClassEnrollmentEntity>()
                    .in(ClassEnrollmentEntity::getEnrollmentOrderItemId, orderItemIds);
            if (onlyActive) {
                itemBased.eq(ClassEnrollmentEntity::getStatus, "ACTIVE");
            }
            itemBased.eq(ClassEnrollmentEntity::getIsDeleted, 0);
            for (ClassEnrollmentEntity enrollment : classEnrollmentMapper.selectList(itemBased)) {
                byId.put(enrollment.getId(), enrollment);
            }
        }
        return new ArrayList<>(byId.values());
    }

    private void markClassEnrollmentsWithdrawn(List<ClassEnrollmentEntity> enrollments) {
        for (ClassEnrollmentEntity enrollment : enrollments) {
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
        if (defaultAmount(order.getReceivedAmount()).compareTo(ZERO_AMOUNT) != 0) {
            throw new BusinessException("COURSE_REFUND_REQUIRED",
                    "Refund the order's payments before deleting its receipt.");
        }
        List<EnrollmentOrderItemEntity> items = listOrderItemsByOrderId(id);
        if (items.stream().anyMatch(item -> defaultAmount(item.getPaidAmount()).compareTo(ZERO_AMOUNT) != 0)) {
            throw new BusinessException("COURSE_REFUND_REQUIRED",
                    "Refund each course's allocated payments before deleting the receipt.");
        }
        List<Long> itemIds = items.stream()
                .map(EnrollmentOrderItemEntity::getId)
                .toList();

        // 混合报名的班级保存在课程项上；不能仅看订单主表 class_id。
        boolean hasClassCourse = order.getClassId() != null || items.stream()
                .anyMatch(item -> item.getClassId() != null);
        if (hasClassCourse) {
            deleteClassOrderCascade(order, itemIds);
        } else {
            deleteOneOnOneOrderCascade(order, itemIds);
        }
        renameEnrollmentNoBeforeSoftDelete(order.getId());
        enrollmentOrderMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrderItem(Long orderId, Long itemId) {
        EnrollmentOrderEntity order = requireOrder(orderId);
        List<EnrollmentOrderItemEntity> items = listOrderItemsByOrderId(orderId);
        EnrollmentOrderItemEntity target = items.stream()
                .filter(item -> Objects.equals(item.getId(), itemId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("ORDER_ITEM_NOT_FOUND", "Course is not part of this receipt."));
        List<PaymentAllocationEntity> allocations = paymentAllocationMapper.selectList(
                new LambdaQueryWrapper<PaymentAllocationEntity>()
                        .eq(PaymentAllocationEntity::getEnrollmentOrderItemId, itemId));
        if (allocations == null) {
            allocations = Collections.emptyList();
        }
        BigDecimal allocated = allocations.stream().map(PaymentAllocationEntity::getAmount)
                .map(this::defaultAmount).reduce(ZERO_AMOUNT, BigDecimal::add);
        // 删除课程不能隐式吞掉已收款；先退款至课程净收款为零，保留其他课程付款。
        if (allocated.compareTo(ZERO_AMOUNT) != 0 || defaultAmount(target.getPaidAmount()).compareTo(ZERO_AMOUNT) != 0) {
            throw new BusinessException("COURSE_REFUND_REQUIRED",
                    "Refund this course's allocated payments before deleting it.");
        }
        if (items.size() == 1) {
            if (defaultAmount(order.getReceivedAmount()).compareTo(ZERO_AMOUNT) != 0) {
                throw new BusinessException("COURSE_REFUND_REQUIRED",
                        "Refund the payment before deleting this receipt.");
            }
            deleteOrder(orderId);
            return;
        }
        if (allocations.isEmpty() && defaultAmount(order.getReceivedAmount()).compareTo(ZERO_AMOUNT) > 0) {
            BigDecimal otherCoursePaid = items.stream()
                    .filter(item -> !Objects.equals(item.getId(), itemId))
                    .map(EnrollmentOrderItemEntity::getPaidAmount)
                    .map(this::defaultAmount).reduce(ZERO_AMOUNT, BigDecimal::add);
            if (otherCoursePaid.compareTo(defaultAmount(order.getReceivedAmount())) != 0) {
                throw new BusinessException("COURSE_PAYMENT_ALLOCATION_REQUIRED",
                        "This receipt has payments without reliable course allocations. Review its payments before deleting this course.");
            }
        }
        List<EnrollmentOrderParticipantEntity> participants = listOrderParticipants(orderId);
        if (participants.size() > 1) {
            throw new BusinessException("MULTI_STUDENT_COURSE_DELETE_UNSUPPORTED",
                    "This receipt has multiple students. Edit the receipt to remove this course.");
        }

        BigDecimal removedDue = target.getAmountDue() == null
                ? defaultAmount(target.getPriceAmount()).add(defaultAmount(target.getPickupFeeAmount()))
                        .add(defaultAmount(target.getDropoffFeeAmount())).add(defaultAmount(target.getExtraFeeAmount()))
                        .subtract(defaultAmount(target.getDiscountAmount()))
                : target.getAmountDue();
        BigDecimal newDue = defaultAmount(order.getAmountDue()).subtract(removedDue);
        BigDecimal received = defaultAmount(order.getReceivedAmount());
        if (newDue.compareTo(ZERO_AMOUNT) < 0 || received.compareTo(newDue) > 0) {
            throw new BusinessException("COURSE_DELETE_BALANCE_CONFLICT",
                    "The remaining receipt balance is inconsistent. Review its payments before deleting this course.");
        }
        requireSeparableOrderItemLines(order.getExtraFeeDetail(), target.getExtraFeeAmount(), target);
        requireSeparableOrderItemLines(order.getDiscountDetail(), target.getDiscountAmount(), target);

        deleteOrderItemPayments(orderId, itemId, allocations);
        if (target.getClassId() != null) {
            // 混合收据必须按课程项外键定位，不能按学生+班级回退，避免误退同班的另一张订单。
            List<ClassEnrollmentEntity> seats = classEnrollmentMapper.selectList(
                    new LambdaQueryWrapper<ClassEnrollmentEntity>()
                            .eq(ClassEnrollmentEntity::getEnrollmentOrderItemId, itemId)
                            .eq(ClassEnrollmentEntity::getIsDeleted, 0));
            if (seats != null && !seats.isEmpty()) {
                markClassEnrollmentsWithdrawn(seats);
                deleteOrderItemClassRecords(order, target);
            } else if (order.getStudentId() != null) {
                Long ambiguousSeats = classEnrollmentMapper.selectCount(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getClassId, target.getClassId())
                        .eq(ClassEnrollmentEntity::getStudentId, order.getStudentId())
                        .isNull(ClassEnrollmentEntity::getEnrollmentOrderItemId)
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0));
                if (ambiguousSeats != null && ambiguousSeats > 0) {
                    throw new BusinessException("COURSE_SEAT_LINK_MISSING",
                            "This course has an unlinked class seat. Review the enrollment before deleting it.");
                }
            }
        }
        List<CoursePackageEntity> packages = coursePackageMapper.selectList(new LambdaQueryWrapper<CoursePackageEntity>()
                .eq(CoursePackageEntity::getEnrollmentOrderItemId, itemId));
        if ("ONE_ON_ONE".equalsIgnoreCase(target.getCourseType()) && (packages == null || packages.isEmpty())) {
            Long unlinkedPackages = coursePackageMapper.selectCount(new LambdaQueryWrapper<CoursePackageEntity>()
                    .eq(CoursePackageEntity::getEnrollmentOrderId, orderId)
                    .isNull(CoursePackageEntity::getEnrollmentOrderItemId));
            if (unlinkedPackages != null && unlinkedPackages > 0) {
                throw new BusinessException("COURSE_PACKAGE_LINK_MISSING",
                        "This course has an unlinked package. Review its schedule before deleting it.");
            }
        }
        deleteOneOnOnePackageCascade(packages == null ? Collections.emptyList() : packages);
        enrollmentOrderItemMapper.deleteById(itemId);

        order.setTuitionSubtotalAmount(defaultAmount(order.getTuitionSubtotalAmount())
                .subtract(defaultAmount(target.getPriceAmount())));
        order.setPickupFeeAmount(defaultAmount(order.getPickupFeeAmount())
                .subtract(defaultAmount(target.getPickupFeeAmount())));
        order.setDropoffFeeAmount(defaultAmount(order.getDropoffFeeAmount())
                .subtract(defaultAmount(target.getDropoffFeeAmount())));
        order.setExtraFeeAmount(defaultAmount(order.getExtraFeeAmount())
                .subtract(defaultAmount(target.getExtraFeeAmount())));
        order.setOriginalAmount(defaultAmount(order.getOriginalAmount())
                .subtract(defaultAmount(target.getPriceAmount()))
                .subtract(defaultAmount(target.getPickupFeeAmount()))
                .subtract(defaultAmount(target.getDropoffFeeAmount()))
                .subtract(defaultAmount(target.getExtraFeeAmount())));
        order.setDiscountAmount(defaultAmount(order.getDiscountAmount())
                .subtract(defaultAmount(target.getDiscountAmount())));
        order.setAmountDue(newDue);
        order.setBalanceAmount(newDue.subtract(received));
        order.setPaymentStatus(resolvePaymentStatus(newDue, ZERO_AMOUNT, received));
        order.setExtraFeeDetail(filterOrderItemLines(order.getExtraFeeDetail(), target));
        order.setDiscountDetail(filterOrderItemLines(order.getDiscountDetail(), target));
        List<EnrollmentOrderItemEntity> remainingItems = items.stream()
                .filter(item -> !Objects.equals(item.getId(), itemId)).toList();
        order.setPickupRequiredSnapshot(remainingItems.stream()
                .anyMatch(item -> Boolean.TRUE.equals(item.getPickupRequiredSnapshot())));
        order.setDropoffRequiredSnapshot(remainingItems.stream()
                .anyMatch(item -> Boolean.TRUE.equals(item.getDropoffRequiredSnapshot())));
        order.setPickupAddressSnapshot(remainingItems.stream()
                .filter(item -> Boolean.TRUE.equals(item.getPickupRequiredSnapshot()))
                .map(EnrollmentOrderItemEntity::getPickupAddressSnapshot)
                .filter(StringUtils::hasText).findFirst().orElse(null));
        order.setDropoffAddressSnapshot(remainingItems.stream()
                .filter(item -> Boolean.TRUE.equals(item.getDropoffRequiredSnapshot()))
                .map(EnrollmentOrderItemEntity::getDropoffAddressSnapshot)
                .filter(StringUtils::hasText).findFirst().orElse(null));
        if (Objects.equals(order.getClassId(), target.getClassId())) {
            order.setClassId(remainingItems.stream()
                    .map(EnrollmentOrderItemEntity::getClassId).filter(Objects::nonNull).findFirst().orElse(null));
        }
        if (enrollmentOrderMapper.updateById(order) != 1) {
            throw new BusinessException("ORDER_CONCURRENT_UPDATE", "The order was updated by another request. Please retry.");
        }
        if (!participants.isEmpty()) {
            EnrollmentOrderParticipantEntity participant = participants.get(0);
            participant.setGrossAmount(order.getOriginalAmount());
            participant.setDiscountAmount(order.getDiscountAmount());
            participant.setDiscountDetail(order.getDiscountDetail());
            participant.setAmountDue(newDue);
            participant.setPaidAmount(received);
            participant.setBalanceAmount(order.getBalanceAmount());
            enrollmentOrderParticipantMapper.updateById(participant);
        }
        syncReceiptAfterOrderItemDeletion(order, target);
    }

    /** 将目标课程的正向付款和退款分摊一同移除，保留同笔付款中其他课程的份额。 */
    private void deleteOrderItemPayments(Long orderId, Long itemId, List<PaymentAllocationEntity> allocations) {
        Map<Long, BigDecimal> removedByPayment = new LinkedHashMap<>();
        for (PaymentAllocationEntity allocation : allocations) {
            removedByPayment.merge(allocation.getPaymentRecordId(), defaultAmount(allocation.getAmount()), BigDecimal::add);
        }
        for (Map.Entry<Long, BigDecimal> entry : removedByPayment.entrySet()) {
            PaymentRecordEntity payment = paymentRecordMapper.selectById(entry.getKey());
            if (payment == null || !Objects.equals(payment.getEnrollmentOrderId(), orderId)) {
                throw new BusinessException("COURSE_PAYMENT_MISSING", "A course payment record could not be found.");
            }
            BigDecimal remaining = defaultAmount(payment.getAmount()).subtract(entry.getValue());
            if (remaining.compareTo(ZERO_AMOUNT) == 0) {
                paymentRecordMapper.update(null, new LambdaUpdateWrapper<PaymentRecordEntity>()
                        .eq(PaymentRecordEntity::getId, payment.getId())
                        .setSql("payment_no = CONCAT(LEFT(payment_no, 8), '-DEL-', id)"));
                paymentRecordMapper.deleteById(payment.getId());
            } else {
                payment.setAmount(remaining);
                paymentRecordMapper.updateById(payment);
            }
        }
        paymentAllocationMapper.delete(new LambdaQueryWrapper<PaymentAllocationEntity>()
                .eq(PaymentAllocationEntity::getEnrollmentOrderItemId, itemId));
    }

    /** 班级排课是共享的，只清理该学生在目标班级的个人消课记录。 */
    private void deleteOrderItemClassRecords(EnrollmentOrderEntity order, EnrollmentOrderItemEntity target) {
        Long remainingSeats = classEnrollmentMapper.selectCount(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                .eq(ClassEnrollmentEntity::getClassId, target.getClassId())
                .eq(ClassEnrollmentEntity::getStudentId, order.getStudentId())
                .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                .eq(ClassEnrollmentEntity::getIsDeleted, 0));
        if (remainingSeats != null && remainingSeats > 0) {
            return;
        }
        List<Long> recordIds = listClassOrderLessonRecordIds(order, List.of(target.getId()));
        if (!recordIds.isEmpty()) {
            lessonRecordMapper.delete(new LambdaQueryWrapper<LessonRecordEntity>()
                    .in(LessonRecordEntity::getId, recordIds));
            hourLedgerMapper.delete(new LambdaQueryWrapper<HourLedgerEntity>()
                    .in(HourLedgerEntity::getLessonRecordId, recordIds));
            notificationLogMapper.delete(new LambdaQueryWrapper<NotificationLogEntity>()
                    .in(NotificationLogEntity::getLessonRecordId, recordIds));
        }
    }

    private String filterOrderItemLines(String json, EnrollmentOrderItemEntity target) {
        if (!StringUtils.hasText(json)) {
            return json;
        }
        List<Map<String, Object>> lines = jsonHelper.fromJson(json,
                new TypeReference<List<Map<String, Object>>>() {});
        if (lines == null) {
            return json;
        }
        return jsonHelper.toJson(lines.stream().filter(line -> !belongsToOrderItem(line, target)).toList());
    }

    private void requireSeparableOrderItemLines(String json, BigDecimal amount, EnrollmentOrderItemEntity target) {
        if (!StringUtils.hasText(json) || defaultAmount(amount).compareTo(ZERO_AMOUNT) == 0) {
            return;
        }
        List<Map<String, Object>> lines = jsonHelper.fromJson(json,
                new TypeReference<List<Map<String, Object>>>() {});
        if (lines != null && !lines.isEmpty() && lines.stream().noneMatch(line -> belongsToOrderItem(line, target))) {
            throw new BusinessException("COURSE_DELETE_UNASSIGNED_CHARGE",
                    "This course has a fee or discount without a course assignment. Edit the receipt first.");
        }
    }

    private boolean belongsToOrderItem(Map<?, ?> line, EnrollmentOrderItemEntity target) {
        return Objects.equals(String.valueOf(line.get("orderItemId")), String.valueOf(target.getId()))
                || target.getLineNo() != null && Objects.equals(
                        String.valueOf(line.get("orderItemLineNo")), String.valueOf(target.getLineNo()))
                || target.getLineNo() != null && Objects.equals(
                        String.valueOf(line.get("lineNo")), String.valueOf(target.getLineNo()));
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value == null) {
            return ZERO_AMOUNT;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException exception) {
            return ZERO_AMOUNT;
        }
    }

    private void syncReceiptAfterOrderItemDeletion(EnrollmentOrderEntity order, EnrollmentOrderItemEntity target) {
        List<ReceiptRecordEntity> receipts = receiptRecordMapper.selectList(
                new LambdaQueryWrapper<ReceiptRecordEntity>()
                        .eq(ReceiptRecordEntity::getEnrollmentOrderId, order.getId()));
        if (receipts == null) {
            return;
        }
        for (ReceiptRecordEntity receipt : receipts) {
            if (!StringUtils.hasText(receipt.getReceiptPayload())) {
                continue;
            }
            Map<String, Object> payload = jsonHelper.fromJson(receipt.getReceiptPayload(),
                    new TypeReference<LinkedHashMap<String, Object>>() {});
            if (payload == null) {
                continue;
            }
            for (String key : List.of("items", "extraFees", "discounts", "artifacts")) {
                if (payload.get(key) instanceof List<?> rawLines) {
                    payload.put(key, rawLines.stream()
                            .filter(raw -> !(raw instanceof Map<?, ?> map)
                                    || !belongsToOrderItem(map, target))
                            .toList());
                }
            }
            BigDecimal topUp = receiptCreditTopUpAmount(payload);
            payload.put("tuitionSubtotalAmount", order.getTuitionSubtotalAmount());
            payload.put("pickupFeeAmount", order.getPickupFeeAmount());
            payload.put("dropoffFeeAmount", order.getDropoffFeeAmount());
            payload.put("extraFeeAmount", order.getExtraFeeAmount());
            payload.put("originalAmount", defaultAmount(order.getOriginalAmount()).add(topUp));
            payload.put("discountAmount", order.getDiscountAmount());
            payload.put("amountDue", defaultAmount(order.getAmountDue()).add(topUp));
            payload.put("receivedAmount", defaultAmount(order.getReceivedAmount()).add(topUp));
            payload.put("paidAmount", defaultAmount(order.getReceivedAmount()).add(topUp));
            payload.put("balanceAmount", order.getBalanceAmount());
            payload.put("paymentStatus", order.getPaymentStatus());
            payload.put("participants", receiptParticipantPayloads(order.getId()));
            payload.put("payments", receiptPaymentPayloads(order.getId()));
            receipt.setReceiptPayload(jsonHelper.toJson(payload));
            receiptRecordMapper.updateById(receipt);
        }
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
        renameEnrollmentNoBeforeSoftDelete(order.getId());
        enrollmentOrderMapper.deleteById(id);
    }

    /**
     * 订单号和软删标记共同参与唯一索引；删除前先改写订单号，避免历史已删订单占用同一号码。
     */
    private void renameEnrollmentNoBeforeSoftDelete(Long orderId) {
        enrollmentOrderMapper.update(null, new LambdaUpdateWrapper<EnrollmentOrderEntity>()
                .eq(EnrollmentOrderEntity::getId, orderId)
                .eq(EnrollmentOrderEntity::getIsDeleted, 0)
                .setSql("enrollment_no = CONCAT(LEFT(enrollment_no, 8), '-DEL-', id)"));
    }

    // ─── 全量级联软删辅助 ──────────────────────────────────────────────

    private void deleteOneOnOneOrderCascade(EnrollmentOrderEntity order, List<Long> itemIds) {
        List<CoursePackageEntity> packages = coursePackageMapper.selectList(
                new LambdaQueryWrapper<CoursePackageEntity>()
                        .eq(CoursePackageEntity::getEnrollmentOrderId, order.getId()));
        deleteOneOnOnePackageCascade(packages);
        deleteOrderFinancialArtifacts(order, itemIds);
    }

    private void deleteClassOrderCascade(EnrollmentOrderEntity order, List<Long> itemIds) {
        // 1) 撤销本单关联的班课在册（兼容 item 模型与 legacy class_id+student_id，不限定 status）
        List<ClassEnrollmentEntity> classEnrollments = collectClassEnrollments(order, itemIds, false);
        List<Long> classEnrollmentIds = classEnrollments.stream()
                .map(ClassEnrollmentEntity::getId)
                .toList();
        markClassEnrollmentsWithdrawn(classEnrollments);

        // 2) 软删该学生个人的班课课时核销记录；共享排课 occurrence 不删
        List<Long> classRecordIds = listClassOrderLessonRecordIds(order, itemIds);
        if (!classRecordIds.isEmpty()) {
            lessonRecordMapper.delete(new LambdaQueryWrapper<LessonRecordEntity>()
                    .in(LessonRecordEntity::getId, classRecordIds));
            notificationLogMapper.delete(new LambdaQueryWrapper<NotificationLogEntity>()
                    .in(NotificationLogEntity::getLessonRecordId, classRecordIds));
        }
        // class 课时流水：当前无此类行（class 课时接线未启用），按在册 / 课时记录预留级联
        if (!classEnrollmentIds.isEmpty() || !classRecordIds.isEmpty()) {
            QueryWrapper<HourLedgerEntity> ledgerWrapper = new QueryWrapper<>();
            boolean hasClause = false;
            if (!classEnrollmentIds.isEmpty()) {
                // HourLedgerEntity 尚无 class_enrollment_id 字段，使用原列名
                ledgerWrapper.in("class_enrollment_id", classEnrollmentIds);
                hasClause = true;
            }
            if (!classRecordIds.isEmpty()) {
                if (hasClause) {
                    ledgerWrapper.or().in("lesson_record_id", classRecordIds);
                } else {
                    ledgerWrapper.in("lesson_record_id", classRecordIds);
                }
            }
            hourLedgerMapper.delete(ledgerWrapper);
        }

        // 3) 班课订单如有 course_package（当前为 0，item 模型未来可能产生），一并级联其下游
        List<CoursePackageEntity> packages = coursePackageMapper.selectList(
                new LambdaQueryWrapper<CoursePackageEntity>()
                        .eq(CoursePackageEntity::getEnrollmentOrderId, order.getId()));
        deleteOneOnOnePackageCascade(packages);

        deleteOrderFinancialArtifacts(order, itemIds);
    }

    private void deleteOneOnOnePackageCascade(List<CoursePackageEntity> packages) {
        if (packages.isEmpty()) {
            return;
        }
        List<Long> packageIds = packages.stream().map(CoursePackageEntity::getId).toList();

        // 收集下游 id
        List<Long> occurrenceIds = lessonOccurrenceMapper.selectList(
                        new LambdaQueryWrapper<LessonOccurrenceEntity>()
                                .select(LessonOccurrenceEntity::getId)
                                .in(LessonOccurrenceEntity::getPackageId, packageIds))
                .stream().map(LessonOccurrenceEntity::getId).toList();
        List<Long> recordIds = lessonRecordMapper.selectList(
                        new LambdaQueryWrapper<LessonRecordEntity>()
                                .select(LessonRecordEntity::getId)
                                .in(LessonRecordEntity::getPackageId, packageIds))
                .stream().map(LessonRecordEntity::getId).toList();
        List<Long> ruleIds = recurringScheduleRuleMapper.selectList(
                        new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                                .select(RecurringScheduleRuleEntity::getId)
                                .in(RecurringScheduleRuleEntity::getPackageId, packageIds))
                .stream().map(RecurringScheduleRuleEntity::getId).toList();

        // 子表 → 父表删除
        if (!occurrenceIds.isEmpty()) {
            // lesson_adjustment_log 无 is_deleted → 物理删
            lessonAdjustmentLogMapper.delete(new LambdaQueryWrapper<LessonAdjustmentLogEntity>()
                    .in(LessonAdjustmentLogEntity::getOccurrenceId, occurrenceIds));
        }
        lessonOccurrenceMapper.delete(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .in(LessonOccurrenceEntity::getPackageId, packageIds));

        if (!ruleIds.isEmpty()) {
            recurringScheduleSlotMapper.delete(new LambdaQueryWrapper<RecurringScheduleSlotEntity>()
                    .in(RecurringScheduleSlotEntity::getRuleId, ruleIds));
        }
        recurringScheduleRuleMapper.delete(new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                .in(RecurringScheduleRuleEntity::getPackageId, packageIds));

        if (!recordIds.isEmpty()) {
            lessonRecordMapper.delete(new LambdaQueryWrapper<LessonRecordEntity>()
                    .in(LessonRecordEntity::getId, recordIds));
            hourLedgerMapper.delete(new LambdaQueryWrapper<HourLedgerEntity>()
                    .in(HourLedgerEntity::getLessonRecordId, recordIds));
            notificationLogMapper.delete(new LambdaQueryWrapper<NotificationLogEntity>()
                    .in(NotificationLogEntity::getLessonRecordId, recordIds));
        }
        // hour_ledger / notification_log 按 package 关联一并软删
        hourLedgerMapper.delete(new LambdaQueryWrapper<HourLedgerEntity>()
                .in(HourLedgerEntity::getPackageId, packageIds));
        notificationLogMapper.delete(new LambdaQueryWrapper<NotificationLogEntity>()
                .in(NotificationLogEntity::getPackageId, packageIds));

        // package_balance_snapshot 无 is_deleted → 物理删（PK = package_id）
        for (Long packageId : packageIds) {
            packageBalanceSnapshotMapper.deleteById(packageId);
        }

        packageTeacherAssignmentMapper.delete(new LambdaQueryWrapper<PackageTeacherAssignmentEntity>()
                .in(PackageTeacherAssignmentEntity::getPackageId, packageIds));
        coursePackageMapper.delete(new LambdaQueryWrapper<CoursePackageEntity>()
                .in(CoursePackageEntity::getId, packageIds));
    }

    /**
     * 课程价格/订单附加费更正后，把课程项应收置空，由分摊重放时按新价格重算。
     */
    private void invalidateItemAmountDue(Long orderId) {
        enrollmentOrderItemMapper.update(null, new LambdaUpdateWrapper<EnrollmentOrderItemEntity>()
                .eq(EnrollmentOrderItemEntity::getEnrollmentOrderId, orderId)
                .eq(EnrollmentOrderItemEntity::getIsDeleted, 0)
                .set(EnrollmentOrderItemEntity::getAmountDue, null));
    }

    private void deleteOrderFinancialArtifacts(EnrollmentOrderEntity order, List<Long> itemIds) {
        // 课程级分摊明细随订单一并软删
        paymentAllocationMapper.delete(new LambdaQueryWrapper<PaymentAllocationEntity>()
                .eq(PaymentAllocationEntity::getEnrollmentOrderId, order.getId()));
        // 软删前把业务唯一号追加行级唯一后缀：uk_xxx_no(receipt_no/payment_no, is_deleted)
        // 复合唯一键只允许一行 (no,1)，若历史软删行占用键位，批量软删会撞 Duplicate entry。
        // 列宽 varchar(32)：原号截前 8 位 + '-DEL-' + 19 位雪花 id 恰好 32。
        paymentRecordMapper.update(null, new LambdaUpdateWrapper<PaymentRecordEntity>()
                .eq(PaymentRecordEntity::getEnrollmentOrderId, order.getId())
                .eq(PaymentRecordEntity::getIsDeleted, 0)
                .setSql("payment_no = CONCAT(LEFT(payment_no, 8), '-DEL-', id)"));
        receiptRecordMapper.update(null, new LambdaUpdateWrapper<ReceiptRecordEntity>()
                .eq(ReceiptRecordEntity::getEnrollmentOrderId, order.getId())
                .eq(ReceiptRecordEntity::getIsDeleted, 0)
                .setSql("receipt_no = CONCAT(LEFT(receipt_no, 8), '-DEL-', id)"));
        if (!itemIds.isEmpty()) {
            enrollmentOrderItemMapper.delete(new LambdaQueryWrapper<EnrollmentOrderItemEntity>()
                    .in(EnrollmentOrderItemEntity::getId, itemIds));
        }
        paymentRecordMapper.delete(new LambdaQueryWrapper<PaymentRecordEntity>()
                .eq(PaymentRecordEntity::getEnrollmentOrderId, order.getId()));
        receiptRecordMapper.delete(new LambdaQueryWrapper<ReceiptRecordEntity>()
                .eq(ReceiptRecordEntity::getEnrollmentOrderId, order.getId()));
    }

    private List<Long> listClassOrderLessonRecordIds(EnrollmentOrderEntity order, List<Long> itemIds) {
        Set<Long> classIds = new java.util.LinkedHashSet<>();
        List<EnrollmentOrderItemEntity> orderItems = listOrderItemsByOrderId(order.getId());
        if (order.getClassId() != null && (orderItems.isEmpty() || orderItems.stream()
                .anyMatch(item -> itemIds.contains(item.getId())
                        && Objects.equals(item.getClassId(), order.getClassId())))) {
            classIds.add(order.getClassId());
        }
        for (EnrollmentOrderItemEntity item : orderItems) {
            if (itemIds.contains(item.getId()) && item.getClassId() != null) {
                classIds.add(item.getClassId());
            }
        }
        Set<Long> studentIds = listOrderParticipants(order.getId()).stream()
                .map(EnrollmentOrderParticipantEntity::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (order.getStudentId() != null) {
            studentIds.add(order.getStudentId());
        }
        if (classIds.isEmpty() || studentIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> occurrenceIds = lessonOccurrenceMapper.selectList(
                        new LambdaQueryWrapper<LessonOccurrenceEntity>()
                                .select(LessonOccurrenceEntity::getId)
                                .in(LessonOccurrenceEntity::getClassGroupId, classIds))
                .stream().map(LessonOccurrenceEntity::getId).toList();
        if (occurrenceIds.isEmpty()) {
            return Collections.emptyList();
        }
        return lessonRecordMapper.selectList(
                        new LambdaQueryWrapper<LessonRecordEntity>()
                                .select(LessonRecordEntity::getId)
                                .in(LessonRecordEntity::getStudentId, studentIds)
                                .in(LessonRecordEntity::getOccurrenceId, occurrenceIds))
                .stream().map(LessonRecordEntity::getId).toList();
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
                List<ClassHolidayVO> holidays = classHolidayService.listHolidays(group.getProductId(), group.getGradeBand());
                inserted += ensureClassOccurrencesThrough(group, window[0], window[1], holidays);
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
                                               LocalDate horizonEnd,
                                               List<ClassHolidayVO> holidays) {
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
            if (classHolidayService.isHoliday(date, holidays)) {
                continue;
            }
            int weekday = date.getDayOfWeek().getValue(); // Monday=1, Sunday=7
            for (ClassGroupScheduleSlotEntity slot : slots) {
                if (!Objects.equals(slot.getWeekday(), weekday)
                        || !ScheduleSlotTime.isValidRange(slot.getStartTime(), slot.getEndTime())) {
                    continue;
                }
                LocalDateTime plannedStartAt = LocalDateTime.of(date, slot.getStartTime());
                if (existingStarts.contains(plannedStartAt)) {
                    continue;
                }
                long minutes = ScheduleSlotTime.durationMinutes(slot.getStartTime(), slot.getEndTime());
                LessonOccurrenceEntity occ = new LessonOccurrenceEntity();
                occ.setLessonNo(BusinessNoGenerator.generateClassLessonOccurrenceNo());
                occ.setClassGroupId(classGroup.getId());
                occ.setScheduleSlotId(slot.getId());
                occ.setOccurrenceType("REGULAR");
                ClassSlotRotation.Assignment assignment = ClassSlotRotation.resolve(slot, date,
                        classGroup.getStartDate(), holidayDate -> classHolidayService.isHoliday(holidayDate, holidays));
                occ.setPlannedTeacherId(assignment.teacherId() == null
                        ? classGroup.getTeacherId() : assignment.teacherId());
                occ.setSubjectName(assignment.subjectName());
                Long classroomId = assignment.alternate()
                        ? ("ONLINE".equals(assignment.deliveryMode()) ? null : assignment.classroomId())
                        : "ONLINE".equals(assignment.deliveryMode()) ? null
                                : assignment.classroomId() != null ? assignment.classroomId() : classGroup.getClassroomId();
                occ.setDeliveryMode(assignment.deliveryMode() == null && classroomId != null
                        ? "IN_PERSON" : assignment.deliveryMode());
                occ.setPlannedClassroomId(classroomId);
                occ.setPlannedStartAt(plannedStartAt);
                occ.setPlannedEndAt(ScheduleSlotTime.endAt(date, slot.getStartTime(), slot.getEndTime()));
                occ.setPlannedUnits((int) (minutes / 30));
                occ.setPlanningStatus("SCHEDULED");
                occ.setConflictChecked(1);
                occurrences.add(occ);
            }
        }

        if (!occurrences.isEmpty()) {
            occurrences.stream().map(LessonOccurrenceEntity::getPlannedClassroomId)
                    .filter(Objects::nonNull).distinct()
                    .forEach(classroomId -> ensureNoConflicts(occurrences.stream()
                            .filter(item -> Objects.equals(item.getPlannedClassroomId(), classroomId)).toList(),
                            null, null, classroomId));
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
                    + "-" + ScheduleSlotTime.formatEndTime(slot.getStartTime(), slot.getEndTime());
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
