package com.classdeduction.system.modules.enrollment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.util.UnitConverter;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.dto.DataSummaryQuery;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.enrollment.entity.PackageTeacherAssignmentEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentRecordEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentRecordMapper;
import com.classdeduction.system.modules.enrollment.service.DataSummaryService;
import com.classdeduction.system.modules.enrollment.vo.DataSummaryResponseVO;
import com.classdeduction.system.modules.lesson.entity.HourLedgerEntity;
import com.classdeduction.system.modules.lesson.mapper.HourLedgerMapper;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataSummaryServiceImpl implements DataSummaryService {

    private static final int EXPIRING_SOON_DAYS = 14;
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final EnrollmentOrderMapper enrollmentOrderMapper;
    private final EnrollmentOrderItemMapper enrollmentOrderItemMapper;
    private final CoursePackageMapper coursePackageMapper;
    private final PackageTeacherAssignmentMapper packageTeacherAssignmentMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;
    private final ClassGroupMapper classGroupMapper;
    private final CourseProductMapper courseProductMapper;
    private final TeacherMapper teacherMapper;
    private final HourLedgerMapper hourLedgerMapper;

    @Override
    public DataSummaryResponseVO getSummary(DataSummaryQuery query) {
        List<EnrollmentOrderEntity> orders = listOrders(query);
        if (orders.isEmpty()) {
            return emptyResponse();
        }

        List<Long> orderIds = orders.stream().map(EnrollmentOrderEntity::getId).toList();
        Map<Long, EnrollmentOrderEntity> orderMap = orders.stream()
                .collect(Collectors.toMap(EnrollmentOrderEntity::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        List<PaymentRecordEntity> paymentRecords = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecordEntity>()
                        .in(PaymentRecordEntity::getEnrollmentOrderId, orderIds)
                        .orderByAsc(PaymentRecordEntity::getPaidAt)
                        .orderByAsc(PaymentRecordEntity::getId));
        Map<Long, String> latestPaymentMethodByOrderId = paymentRecords.stream()
                .filter(record -> positive(record.getAmount()) && StringUtils.hasText(record.getPaymentMethod()))
                .collect(Collectors.toMap(
                        PaymentRecordEntity::getEnrollmentOrderId,
                        PaymentRecordEntity::getPaymentMethod,
                        (left, right) -> right,
                        LinkedHashMap::new));

        List<EnrollmentOrderItemEntity> items = enrollmentOrderItemMapper.selectList(
                new LambdaQueryWrapper<EnrollmentOrderItemEntity>()
                        .in(EnrollmentOrderItemEntity::getEnrollmentOrderId, orderIds)
                        .orderByAsc(EnrollmentOrderItemEntity::getLineNo)
                        .orderByAsc(EnrollmentOrderItemEntity::getId));
        Map<Long, List<EnrollmentOrderItemEntity>> itemsByOrderId = items.stream()
                .collect(Collectors.groupingBy(EnrollmentOrderItemEntity::getEnrollmentOrderId, LinkedHashMap::new, Collectors.toList()));

        List<CoursePackageEntity> packages = coursePackageMapper.selectList(
                new LambdaQueryWrapper<CoursePackageEntity>()
                        .in(CoursePackageEntity::getEnrollmentOrderId, orderIds));
        Map<Long, CoursePackageEntity> packageByOrderItemId = packages.stream()
                .filter(item -> item.getEnrollmentOrderItemId() != null)
                .collect(Collectors.toMap(CoursePackageEntity::getEnrollmentOrderItemId, Function.identity(), (left, right) -> left));
        Map<Long, List<CoursePackageEntity>> packagesByOrderId = packages.stream()
                .collect(Collectors.groupingBy(CoursePackageEntity::getEnrollmentOrderId));

        Map<Long, List<String>> reviewIssuesByOrderId = buildReviewIssues(orders, packages, paymentRecords);

        Set<Long> itemIds = items.stream().map(EnrollmentOrderItemEntity::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<ClassEnrollmentEntity> classEnrollments = itemIds.isEmpty() ? Collections.emptyList() : classEnrollmentMapper.selectList(
                new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .in(ClassEnrollmentEntity::getEnrollmentOrderItemId, itemIds));
        Map<Long, ClassEnrollmentEntity> classEnrollmentByOrderItemId = classEnrollments.stream()
                .filter(item -> item.getEnrollmentOrderItemId() != null)
                .collect(Collectors.toMap(ClassEnrollmentEntity::getEnrollmentOrderItemId, Function.identity(), (left, right) -> left));

        Context context = buildContext(orders, items, packages, classEnrollments);
        List<DataSummaryResponseVO.RowVO> rows = buildRows(orderMap, itemsByOrderId, packagesByOrderId,
                        packageByOrderItemId, classEnrollmentByOrderItemId, reviewIssuesByOrderId,
                        latestPaymentMethodByOrderId, context)
                .stream()
                .filter(row -> matchesQuery(row, query))
                .filter(row -> !Boolean.TRUE.equals(query.getOnlyAlerts()) || hasAlerts(row))
                .filter(row -> !Boolean.TRUE.equals(query.getOnlyNeedsReview()) || hasReviewIssues(row))
                .sorted(Comparator.comparing(DataSummaryResponseVO.RowVO::signupDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DataSummaryResponseVO.RowVO::enrollmentNo, Comparator.nullsLast(String::compareTo))
                        .thenComparing(DataSummaryResponseVO.RowVO::orderItemId, Comparator.nullsLast(Long::compareTo)))
                .toList();

        Set<Long> visibleOrderIds = rows.stream().map(DataSummaryResponseVO.RowVO::orderId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> visibleStudentIds = rows.stream().map(DataSummaryResponseVO.RowVO::studentId).filter(Objects::nonNull).collect(Collectors.toSet());
        BigDecimal totalIncome = sumOrders(visibleOrderIds, orderMap, EnrollmentOrderEntity::getReceivedAmount);
        BigDecimal totalOutstanding = sumOutstandingOrders(visibleOrderIds, orderMap);

        return new DataSummaryResponseVO(
                (long) visibleStudentIds.size(),
                totalIncome,
                totalOutstanding,
                buildGroups(rows, orderMap, GroupKind.CAMPUS),
                buildGroups(rows, orderMap, GroupKind.COURSE_TYPE),
                buildGroups(rows, orderMap, GroupKind.CLASS_COURSE),
                buildGroups(rows, orderMap, GroupKind.PAYMENT_STATUS),
                rows
        );
    }

    private List<EnrollmentOrderEntity> listOrders(DataSummaryQuery query) {
        return enrollmentOrderMapper.selectList(new LambdaQueryWrapper<EnrollmentOrderEntity>()
                .eq(query.getCampusId() != null, EnrollmentOrderEntity::getCampusId, query.getCampusId())
                .eq(StringUtils.hasText(query.getPaymentStatus()), EnrollmentOrderEntity::getPaymentStatus, query.getPaymentStatus())
                .ge(query.getPeriodStartDate() != null, EnrollmentOrderEntity::getPeriodStartDate, query.getPeriodStartDate())
                .le(query.getPeriodEndDate() != null, EnrollmentOrderEntity::getPeriodEndDate, query.getPeriodEndDate())
                .ne(EnrollmentOrderEntity::getOrderStatus, "CANCELLED")
                .ne(EnrollmentOrderEntity::getPaymentStatus, "REFUNDED")
                .orderByDesc(EnrollmentOrderEntity::getSignupDate)
                .orderByDesc(EnrollmentOrderEntity::getCreatedAt));
    }

    private Map<Long, List<String>> buildReviewIssues(List<EnrollmentOrderEntity> orders,
                                                      List<CoursePackageEntity> packages,
                                                      List<PaymentRecordEntity> paymentRecords) {
        Map<Long, List<String>> issuesByOrderId = new HashMap<>();
        Map<Long, BigDecimal> paymentNetByOrderId = paymentRecords.stream()
                .collect(Collectors.groupingBy(PaymentRecordEntity::getEnrollmentOrderId,
                        Collectors.reducing(ZERO_AMOUNT, record -> amount(record.getAmount()), BigDecimal::add)));

        for (EnrollmentOrderEntity order : orders) {
            BigDecimal paymentNet = amount(paymentNetByOrderId.get(order.getId()));
            BigDecimal receivedAmount = amount(order.getReceivedAmount());
            if (paymentNet.compareTo(receivedAmount) != 0) {
                addReviewIssue(issuesByOrderId, order.getId(), "PAYMENT_NET_MISMATCH");
            }

            BigDecimal expectedBalance = amount(order.getAmountDue()).subtract(paymentNet);
            if (expectedBalance.compareTo(amount(order.getBalanceAmount())) != 0) {
                addReviewIssue(issuesByOrderId, order.getId(), "BALANCE_MISMATCH");
            }
        }

        Set<Long> packageIds = packages.stream()
                .map(CoursePackageEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (packageIds.isEmpty()) {
            return issuesByOrderId;
        }

        Map<Long, Integer> ledgerChangesByPackageId = hourLedgerMapper.selectList(
                        new LambdaQueryWrapper<HourLedgerEntity>()
                                .in(HourLedgerEntity::getPackageId, packageIds))
                .stream()
                .collect(Collectors.groupingBy(HourLedgerEntity::getPackageId,
                        Collectors.summingInt(ledger -> safeUnits(ledger.getChangeUnits()))));
        for (CoursePackageEntity coursePackage : packages) {
            int expectedRemaining = safeUnits(coursePackage.getTotalUnits())
                    + ledgerChangesByPackageId.getOrDefault(coursePackage.getId(), 0);
            int expectedConsumed = safeUnits(coursePackage.getTotalUnits()) - expectedRemaining;
            if (expectedRemaining != safeUnits(coursePackage.getRemainingUnits())
                    || expectedConsumed != safeUnits(coursePackage.getConsumedUnits())) {
                addReviewIssue(issuesByOrderId, coursePackage.getEnrollmentOrderId(), "PACKAGE_LEDGER_MISMATCH");
            }
        }
        return issuesByOrderId;
    }

    private void addReviewIssue(Map<Long, List<String>> issuesByOrderId, Long orderId, String issueCode) {
        if (orderId == null) {
            return;
        }
        List<String> issues = issuesByOrderId.computeIfAbsent(orderId, ignored -> new ArrayList<>());
        if (!issues.contains(issueCode)) {
            issues.add(issueCode);
        }
    }

    private Context buildContext(List<EnrollmentOrderEntity> orders,
                                 List<EnrollmentOrderItemEntity> items,
                                 List<CoursePackageEntity> packages,
                                 List<ClassEnrollmentEntity> classEnrollments) {
        Set<Long> classIds = new LinkedHashSet<>();
        Set<Long> productIds = new LinkedHashSet<>();
        Set<Long> packageIds = packages.stream().map(CoursePackageEntity::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> teacherIds = new LinkedHashSet<>();

        orders.stream().map(EnrollmentOrderEntity::getClassId).filter(Objects::nonNull).forEach(classIds::add);
        items.stream().map(EnrollmentOrderItemEntity::getClassId).filter(Objects::nonNull).forEach(classIds::add);
        classEnrollments.stream().map(ClassEnrollmentEntity::getClassId).filter(Objects::nonNull).forEach(classIds::add);
        items.stream().map(EnrollmentOrderItemEntity::getCourseProductId).filter(Objects::nonNull).forEach(productIds::add);
        packages.stream().map(CoursePackageEntity::getCourseProductId).filter(Objects::nonNull).forEach(productIds::add);
        items.stream().map(EnrollmentOrderItemEntity::getTeacherId).filter(Objects::nonNull).forEach(teacherIds::add);

        Map<Long, ClassGroupEntity> classGroups = classIds.isEmpty() ? Collections.emptyMap()
                : classGroupMapper.selectBatchIds(classIds).stream()
                        .collect(Collectors.toMap(ClassGroupEntity::getId, Function.identity()));
        classGroups.values().stream().map(ClassGroupEntity::getProductId).filter(Objects::nonNull).forEach(productIds::add);
        classGroups.values().stream().map(ClassGroupEntity::getTeacherId).filter(Objects::nonNull).forEach(teacherIds::add);

        Map<Long, CourseProductEntity> products = productIds.isEmpty() ? Collections.emptyMap()
                : courseProductMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(CourseProductEntity::getId, Function.identity()));

        Map<Long, PackageTeacherAssignmentEntity> primaryAssignments = packageIds.isEmpty() ? Collections.emptyMap()
                : packageTeacherAssignmentMapper.selectList(new LambdaQueryWrapper<PackageTeacherAssignmentEntity>()
                                .in(PackageTeacherAssignmentEntity::getPackageId, packageIds)
                                .eq(PackageTeacherAssignmentEntity::getIsPrimary, 1)
                                .orderByDesc(PackageTeacherAssignmentEntity::getEffectiveFrom))
                        .stream()
                        .collect(Collectors.toMap(PackageTeacherAssignmentEntity::getPackageId, Function.identity(), (left, right) -> left));
        primaryAssignments.values().stream().map(PackageTeacherAssignmentEntity::getTeacherId).filter(Objects::nonNull).forEach(teacherIds::add);

        Map<Long, TeacherEntity> teachers = teacherIds.isEmpty() ? Collections.emptyMap()
                : teacherMapper.selectBatchIds(teacherIds).stream()
                        .collect(Collectors.toMap(TeacherEntity::getId, Function.identity()));
        return new Context(classGroups, products, primaryAssignments, teachers);
    }

    private List<DataSummaryResponseVO.RowVO> buildRows(
            Map<Long, EnrollmentOrderEntity> orderMap,
            Map<Long, List<EnrollmentOrderItemEntity>> itemsByOrderId,
            Map<Long, List<CoursePackageEntity>> packagesByOrderId,
            Map<Long, CoursePackageEntity> packageByOrderItemId,
            Map<Long, ClassEnrollmentEntity> classEnrollmentByOrderItemId,
            Map<Long, List<String>> reviewIssuesByOrderId,
            Map<Long, String> latestPaymentMethodByOrderId,
            Context context) {
        LocalDate today = LocalDate.now();
        LocalDate soonLimit = today.plusDays(EXPIRING_SOON_DAYS);
        List<DataSummaryResponseVO.RowVO> rows = new ArrayList<>();

        for (EnrollmentOrderEntity order : orderMap.values()) {
            List<EnrollmentOrderItemEntity> orderItems = itemsByOrderId.get(order.getId());
            if (orderItems == null || orderItems.isEmpty()) {
                rows.add(toRow(order, null, firstPackage(packagesByOrderId.get(order.getId())), null,
                        reviewIssuesByOrderId.getOrDefault(order.getId(), Collections.emptyList()),
                        latestPaymentMethodByOrderId.get(order.getId()), context, today, soonLimit));
                continue;
            }
            for (EnrollmentOrderItemEntity item : orderItems) {
                CoursePackageEntity coursePackage = packageByOrderItemId.get(item.getId());
                ClassEnrollmentEntity classEnrollment = classEnrollmentByOrderItemId.get(item.getId());
                rows.add(toRow(order, item, coursePackage, classEnrollment,
                        reviewIssuesByOrderId.getOrDefault(order.getId(), Collections.emptyList()),
                        latestPaymentMethodByOrderId.get(order.getId()), context, today, soonLimit));
            }
        }
        return rows;
    }

    private DataSummaryResponseVO.RowVO toRow(EnrollmentOrderEntity order,
                                              EnrollmentOrderItemEntity item,
                                              CoursePackageEntity coursePackage,
                                              ClassEnrollmentEntity classEnrollment,
                                              List<String> reviewIssueCodes,
                                              String paymentMethod,
                                              Context context,
                                              LocalDate today,
                                              LocalDate soonLimit) {
        Long classId = firstNonNull(item == null ? null : item.getClassId(),
                classEnrollment == null ? null : classEnrollment.getClassId(),
                order.getClassId());
        ClassGroupEntity classGroup = classId == null ? null : context.classGroups().get(classId);
        Long productId = firstNonNull(item == null ? null : item.getCourseProductId(),
                coursePackage == null ? null : coursePackage.getCourseProductId(),
                classGroup == null ? null : classGroup.getProductId());
        CourseProductEntity product = productId == null ? null : context.products().get(productId);

        Long teacherId = firstNonNull(item == null ? null : item.getTeacherId(),
                coursePackage == null ? null : teacherIdByPackage(coursePackage.getId(), context),
                classGroup == null ? null : classGroup.getTeacherId());
        TeacherEntity teacher = teacherId == null ? null : context.teachers().get(teacherId);

        String courseType = firstText(item == null ? null : item.getCourseType(),
                isOneOnOne(product, coursePackage) ? "ONE_ON_ONE" : null,
                product == null ? null : product.getTeachingMode());
        String registerClass = firstText(item == null ? null : item.getRegisterClass(),
                classGroup == null ? null : classGroup.getClassName(),
                coursePackage == null ? null : coursePackage.getPackageName(),
                product == null ? null : product.getProductName());
        String detail = firstText(item == null ? null : item.getDetail(),
                coursePackage == null ? null : coursePackage.getPackageName(),
                product == null ? null : product.getProductName());

        LocalDate expiryDate = firstNonNull(coursePackage == null ? null : coursePackage.getEstimatedEndDate(),
                classEnrollment == null ? null : classEnrollment.getExpiryDate(),
                order.getPeriodEndDate());
        boolean overdue = positive(order.getBalanceAmount());
        boolean expiringSoon = expiryDate != null && !expiryDate.isBefore(today) && !expiryDate.isAfter(soonLimit);
        boolean oneOnOne = "ONE_ON_ONE".equals(courseType) || isOneOnOne(product, coursePackage);
        boolean lowOneOnOneHours = oneOnOne && coursePackage != null
                && safeUnits(coursePackage.getRemainingUnits()) <= renewalReminderUnits(coursePackage);
        List<String> alerts = new ArrayList<>();
        if (overdue) {
            alerts.add("OVERDUE");
        }
        if (expiringSoon) {
            alerts.add("EXPIRING_SOON");
        }
        if (lowOneOnOneHours) {
            alerts.add("LOW_ONE_ON_ONE_HOURS");
        }

        return new DataSummaryResponseVO.RowVO(
                order.getId(),
                order.getEnrollmentNo(),
                item == null ? null : item.getId(),
                order.getStudentId(),
                firstText(order.getStudentNameSnapshot(), null),
                order.getSchoolSnapshot(),
                order.getGradeSnapshot(),
                order.getSignupDate(),
                order.getPeriodStartDate(),
                order.getPeriodEndDate(),
                order.getCampusId(),
                order.getCampusNameSnapshot(),
                classId,
                classGroup == null ? null : classGroup.getClassName(),
                productId,
                product == null ? null : product.getProductName(),
                registerClass,
                courseType,
                detail,
                order.getParentPhoneSnapshot(),
                order.getParentPhone2Snapshot(),
                Boolean.TRUE.equals(order.getPickupRequiredSnapshot()),
                order.getPickupAddressSnapshot(),
                Boolean.TRUE.equals(order.getDropoffRequiredSnapshot()),
                order.getDropoffAddressSnapshot(),
                pickupDropoffLabel(order),
                paymentMethod,
                order.getPaymentStatus(),
                item == null ? null : amount(item.getPriceAmount()),
                amount(order.getPickupFeeAmount()),
                amount(order.getExtraFeeAmount()),
                amount(order.getDiscountAmount()),
                amount(order.getAmountDue()),
                amount(order.getReceivedAmount()),
                displayBalanceAmount(order.getBalanceAmount()),
                coursePackage == null ? null : UnitConverter.toHoursDecimal(coursePackage.getTotalUnits()),
                coursePackage == null ? null : UnitConverter.toHoursDecimal(coursePackage.getConsumedUnits()),
                coursePackage == null ? null : UnitConverter.toHoursDecimal(coursePackage.getRemainingUnits()),
                teacherId,
                teacher == null ? null : teacher.getTeacherName(),
                expiryDate,
                overdue,
                expiringSoon,
                lowOneOnOneHours,
                alerts,
                order.getOrderStatus(),
                reviewIssueCodes
        );
    }

    private boolean matchesQuery(DataSummaryResponseVO.RowVO row, DataSummaryQuery query) {
        if (query.getClassId() != null && !Objects.equals(query.getClassId(), row.classId())) {
            return false;
        }
        if (query.getCourseProductId() != null && !Objects.equals(query.getCourseProductId(), row.courseProductId())) {
            return false;
        }
        if (StringUtils.hasText(query.getCourseType()) && !query.getCourseType().equalsIgnoreCase(row.courseType())) {
            return false;
        }
        if (!StringUtils.hasText(query.getKeyword())) {
            return true;
        }
        String keyword = query.getKeyword().trim().toLowerCase();
        return contains(row.enrollmentNo(), keyword)
                || contains(row.studentName(), keyword)
                || contains(row.school(), keyword)
                || contains(row.phone(), keyword)
                || contains(row.phone2(), keyword)
                || contains(row.registerClass(), keyword)
                || contains(row.detail(), keyword)
                || contains(row.className(), keyword)
                || contains(row.courseProductName(), keyword);
    }

    private List<DataSummaryResponseVO.SummaryGroupVO> buildGroups(List<DataSummaryResponseVO.RowVO> rows,
                                                                   Map<Long, EnrollmentOrderEntity> orderMap,
                                                                   GroupKind groupKind) {
        Map<String, GroupBucket> buckets = new LinkedHashMap<>();
        for (DataSummaryResponseVO.RowVO row : rows) {
            GroupLabel label = groupLabel(row, groupKind);
            buckets.computeIfAbsent(label.key(), key -> new GroupBucket(label)).add(row);
        }
        return buckets.values().stream()
                .map(bucket -> bucket.toVO(orderMap))
                .sorted(Comparator.comparing(DataSummaryResponseVO.SummaryGroupVO::groupName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private GroupLabel groupLabel(DataSummaryResponseVO.RowVO row, GroupKind groupKind) {
        return switch (groupKind) {
            case CAMPUS -> new GroupLabel(stringKey(row.campusId()), firstText(row.campusName(), "Unknown Campus"));
            case COURSE_TYPE -> new GroupLabel(firstText(row.courseType(), "UNKNOWN"), firstText(row.courseType(), "Unknown Type"));
            case CLASS_COURSE -> {
                String key = row.classId() != null ? "CLASS:" + row.classId()
                        : row.courseProductId() != null ? "COURSE:" + row.courseProductId()
                        : firstText(row.registerClass(), row.detail(), "UNKNOWN");
                String name = firstText(row.className(), row.courseProductName(), row.registerClass(), row.detail(), "Unknown Course");
                yield new GroupLabel(key, name);
            }
            case PAYMENT_STATUS -> new GroupLabel(firstText(row.paymentStatus(), "UNKNOWN"), firstText(row.paymentStatus(), "Unknown"));
        };
    }

    private BigDecimal sumOrders(Set<Long> orderIds,
                                 Map<Long, EnrollmentOrderEntity> orderMap,
                                 Function<EnrollmentOrderEntity, BigDecimal> getter) {
        return orderIds.stream()
                .map(orderMap::get)
                .filter(Objects::nonNull)
                .map(getter)
                .map(this::amount)
                .reduce(ZERO_AMOUNT, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumOutstandingOrders(Set<Long> orderIds, Map<Long, EnrollmentOrderEntity> orderMap) {
        return orderIds.stream()
                .map(orderMap::get)
                .filter(Objects::nonNull)
                .map(EnrollmentOrderEntity::getBalanceAmount)
                .filter(this::positive)
                .map(this::amount)
                .reduce(ZERO_AMOUNT, BigDecimal::add)
                // The operational workbook uses negative values for unpaid balances.
                .negate()
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Keeps the persisted balance as a positive amount due for payment arithmetic,
     * while returning the customer-approved negative-balance convention to the workbook.
     */
    private BigDecimal displayBalanceAmount(BigDecimal storedBalanceAmount) {
        BigDecimal balance = amount(storedBalanceAmount);
        return positive(balance) ? balance.negate() : balance;
    }

    private DataSummaryResponseVO emptyResponse() {
        return new DataSummaryResponseVO(0L, ZERO_AMOUNT, ZERO_AMOUNT,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    private String pickupDropoffLabel(EnrollmentOrderEntity order) {
        List<String> labels = new ArrayList<>();
        if (Boolean.TRUE.equals(order.getPickupRequiredSnapshot())) {
            labels.add("Pick up");
        }
        if (Boolean.TRUE.equals(order.getDropoffRequiredSnapshot())) {
            labels.add("Drop off");
        }
        return labels.isEmpty() ? "None" : String.join(" / ", labels);
    }

    private CoursePackageEntity firstPackage(List<CoursePackageEntity> packages) {
        return packages == null || packages.isEmpty() ? null : packages.get(0);
    }

    private int renewalReminderUnits(CoursePackageEntity coursePackage) {
        return 8;
    }

    private Long teacherIdByPackage(Long packageId, Context context) {
        PackageTeacherAssignmentEntity assignment = context.primaryAssignments().get(packageId);
        return assignment == null ? null : assignment.getTeacherId();
    }

    private boolean isOneOnOne(CourseProductEntity product, CoursePackageEntity coursePackage) {
        return (product != null && "ONE_ON_ONE".equals(product.getTeachingMode()))
                || (coursePackage != null && "ONE_ON_ONE".equals(coursePackage.getTeachingMode()));
    }

    private boolean hasAlerts(DataSummaryResponseVO.RowVO row) {
        return Boolean.TRUE.equals(row.overdue())
                || Boolean.TRUE.equals(row.expiringSoon())
                || Boolean.TRUE.equals(row.lowOneOnOneHours());
    }

    private boolean hasReviewIssues(DataSummaryResponseVO.RowVO row) {
        return row.reviewIssueCodes() != null && !row.reviewIssueCodes().isEmpty();
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private int safeUnits(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? ZERO_AMOUNT : value.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private String stringKey(Long value) {
        return value == null ? "UNKNOWN" : String.valueOf(value);
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private record Context(
            Map<Long, ClassGroupEntity> classGroups,
            Map<Long, CourseProductEntity> products,
            Map<Long, PackageTeacherAssignmentEntity> primaryAssignments,
            Map<Long, TeacherEntity> teachers
    ) {
    }

    private record GroupLabel(String key, String name) {
    }

    private enum GroupKind {
        CAMPUS,
        COURSE_TYPE,
        CLASS_COURSE,
        PAYMENT_STATUS
    }

    private class GroupBucket {
        private final GroupLabel label;
        private final List<DataSummaryResponseVO.RowVO> rows = new ArrayList<>();
        private final Set<Long> orderIds = new LinkedHashSet<>();
        private final Set<Long> studentIds = new LinkedHashSet<>();

        private GroupBucket(GroupLabel label) {
            this.label = label;
        }

        private void add(DataSummaryResponseVO.RowVO row) {
            rows.add(row);
            orderIds.add(row.orderId());
            if (row.studentId() != null) {
                studentIds.add(row.studentId());
            }
        }

        private DataSummaryResponseVO.SummaryGroupVO toVO(Map<Long, EnrollmentOrderEntity> orderMap) {
            return new DataSummaryResponseVO.SummaryGroupVO(
                    label.key(),
                    label.name(),
                    (long) rows.size(),
                    (long) studentIds.size(),
                    sumOrders(orderIds, orderMap, EnrollmentOrderEntity::getReceivedAmount),
                    sumOutstandingOrders(orderIds, orderMap)
            );
        }
    }
}
