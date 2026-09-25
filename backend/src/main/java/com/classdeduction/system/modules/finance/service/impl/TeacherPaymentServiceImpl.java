package com.classdeduction.system.modules.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupMemberEntity;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMemberMapper;
import com.classdeduction.system.modules.finance.dto.TeacherPaymentCreateRequest;
import com.classdeduction.system.modules.finance.dto.UnpaidLessonQuery;
import com.classdeduction.system.modules.finance.entity.TeacherPayRateEntity;
import com.classdeduction.system.modules.finance.entity.TeacherPaymentEntity;
import com.classdeduction.system.modules.finance.entity.TeacherPaymentItemEntity;
import com.classdeduction.system.modules.finance.mapper.TeacherPayRateMapper;
import com.classdeduction.system.modules.finance.mapper.TeacherPaymentItemMapper;
import com.classdeduction.system.modules.finance.mapper.TeacherPaymentMapper;
import com.classdeduction.system.modules.finance.service.TeacherPaymentService;
import com.classdeduction.system.modules.finance.vo.TeacherPaymentItemVO;
import com.classdeduction.system.modules.finance.vo.TeacherPaymentVO;
import com.classdeduction.system.modules.finance.vo.UnpaidLessonVO;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherPaymentServiceImpl implements TeacherPaymentService {

    private static final Set<String> PAYABLE_STATUSES = Set.of("COMPLETED", "MAKEUP_COMPLETED", "SUBSTITUTED");

    private final LessonRecordMapper lessonRecordMapper;
    private final CoursePackageMapper coursePackageMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final CourseProductMapper courseProductMapper;
    private final ClassroomMapper classroomMapper;
    private final EnrollmentOrderMapper enrollmentOrderMapper;
    private final EnrollmentOrderItemMapper enrollmentOrderItemMapper;
    private final TeacherPayRateMapper payRateMapper;
    private final TeacherPaymentMapper paymentMapper;
    private final TeacherPaymentItemMapper paymentItemMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final ClassGroupMapper classGroupMapper;
    private final PrivateLessonGroupMemberMapper privateLessonGroupMemberMapper;

    @Override
    public List<UnpaidLessonVO> queryUnpaidLessons(UnpaidLessonQuery query) {
        validatePeriod(query.getStartDate(), query.getEndDate());
        // 1. 查询已结算的 lesson_record_id
        List<Long> paidRecordIds = paymentItemMapper.selectList(
                        new LambdaQueryWrapper<TeacherPaymentItemEntity>()
                                .select(TeacherPaymentItemEntity::getLessonRecordId))
                .stream()
                .map(TeacherPaymentItemEntity::getLessonRecordId)
                .toList();

        // 1a. 共享课次（班课或 1 对 N 私教）只结算一次，不按学生记录重复结算。
        Set<Long> settledSharedOccurrenceIds = Collections.emptySet();
        if (!paidRecordIds.isEmpty()) {
            List<LessonRecordEntity> paidRecords = lessonRecordMapper.selectBatchIds(paidRecordIds);
            Set<Long> paidOccurrenceIds = paidRecords.stream()
                    .map(LessonRecordEntity::getOccurrenceId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!paidOccurrenceIds.isEmpty()) {
                settledSharedOccurrenceIds = lessonOccurrenceMapper.selectBatchIds(paidOccurrenceIds).stream()
                        .filter(this::isSharedOccurrence)
                        .map(LessonOccurrenceEntity::getId)
                        .collect(Collectors.toSet());
            }
        }

        // 2. 查询未结算的课次
        LocalDateTime start = query.getStartDate() != null
                ? query.getStartDate().atStartOfDay() : null;
        LocalDateTime end = query.getEndDate() != null
                ? query.getEndDate().plusDays(1).atStartOfDay() : null;

        List<LessonRecordEntity> records = lessonRecordMapper.selectList(
                new LambdaQueryWrapper<LessonRecordEntity>()
                        .eq(LessonRecordEntity::getActualTeacherId, query.getTeacherId())
                        .eq(LessonRecordEntity::getConfirmStage, "CONFIRMED")
                        .in(LessonRecordEntity::getResultStatus, PAYABLE_STATUSES)
                        .notIn(!paidRecordIds.isEmpty(), LessonRecordEntity::getId, paidRecordIds)
                        .notIn(!settledSharedOccurrenceIds.isEmpty(),
                                LessonRecordEntity::getOccurrenceId, settledSharedOccurrenceIds)
                        .ge(start != null, LessonRecordEntity::getConfirmedAt, start)
                        .lt(end != null, LessonRecordEntity::getConfirmedAt, end)
                        .orderByAsc(LessonRecordEntity::getConfirmedAt)
        );
        if (records.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. 批量加载关联数据
        Set<Long> packageIds = records.stream().map(LessonRecordEntity::getPackageId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> studentIds = records.stream().map(LessonRecordEntity::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> occurrenceIds = records.stream().map(LessonRecordEntity::getOccurrenceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, CoursePackageEntity> packageMap = !packageIds.isEmpty()
                ? coursePackageMapper.selectBatchIds(packageIds).stream()
                .collect(Collectors.toMap(CoursePackageEntity::getId, p -> p))
                : Collections.emptyMap();
        Map<Long, StudentEntity> studentMap = !studentIds.isEmpty()
                ? studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(StudentEntity::getId, s -> s))
                : Collections.emptyMap();

        // 3a. 查询 occurrence 信息（occurrenceType + classGroupId）
        Map<Long, LessonOccurrenceEntity> occurrenceMap = !occurrenceIds.isEmpty()
                ? lessonOccurrenceMapper.selectBatchIds(occurrenceIds).stream()
                .collect(Collectors.toMap(LessonOccurrenceEntity::getId, o -> o))
                : Collections.emptyMap();

        // 3b. 批量加载 class_group（班课通过 class_group.productId 找 rate）
        Set<Long> classGroupIds = occurrenceMap.values().stream()
                .map(LessonOccurrenceEntity::getClassGroupId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ClassGroupEntity> classGroupMap = !classGroupIds.isEmpty()
                ? classGroupMapper.selectBatchIds(classGroupIds).stream()
                .collect(Collectors.toMap(ClassGroupEntity::getId, cg -> cg))
                : Collections.emptyMap();
        Map<Long, Long> privatePrimaryPackageByGroup = loadPrivatePrimaryPackageByGroup(occurrenceMap.values());
        Map<Long, EnrollmentOrderItemEntity> orderItemMap = loadOrderItems(packageMap.values());
        Map<Long, EnrollmentOrderEntity> orderMap = loadOrders(packageMap.values());
        Map<Long, ClassroomEntity> classroomMap = loadClassrooms(occurrenceMap.values(), classGroupMap.values());

        // 4. 获取产品的 productId 集合（包括 1v1 课包和班课产品）
        Set<Long> productIds = packageMap.values().stream()
                .map(CoursePackageEntity::getCourseProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        productIds.addAll(classGroupMap.values().stream()
                .map(ClassGroupEntity::getProductId)
                .filter(Objects::nonNull)
                .toList());

        Map<Long, CourseProductEntity> productMap = !productIds.isEmpty()
                ? courseProductMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(CourseProductEntity::getId, p -> p))
                : Collections.emptyMap();

        // 5. 获取老师的课时单价
        List<TeacherPayRateEntity> rates = payRateMapper.selectList(
                new LambdaQueryWrapper<TeacherPayRateEntity>()
                        .eq(TeacherPayRateEntity::getTeacherId, query.getTeacherId()));
        Map<String, BigDecimal> rateMap = toRateMap(rates);

        // 6. 组装结果
        // 班课按 occurrence 聚合成一条记录（用第一条的课时数，不叠加学生数）
        // 非班课一条记录一行
        List<UnpaidLessonVO> result = new ArrayList<>();
        Map<Long, Long> representativeRecordByOccurrence = records.stream()
                .filter(record -> {
                    LessonOccurrenceEntity occurrence = occurrenceMap.get(record.getOccurrenceId());
                    return isSharedOccurrence(occurrence);
                })
                .collect(Collectors.toMap(
                        LessonRecordEntity::getOccurrenceId,
                        LessonRecordEntity::getId,
                        (left, right) -> chooseRepresentativeRecord(left, right, records, occurrenceMap,
                                privatePrimaryPackageByGroup)));

        for (LessonRecordEntity rec : records) {
            LessonOccurrenceEntity occurrence = occurrenceMap.get(rec.getOccurrenceId());
            // 优先显示实际授课时段；旧记录缺少实际时间时使用课次上的排课时段。
            LocalDateTime lessonStartAt = rec.getActualStartAt();
            LocalDateTime lessonEndAt = rec.getActualEndAt();
            if (lessonStartAt == null || lessonEndAt == null) {
                lessonStartAt = rec.getPlannedStartAt();
                lessonEndAt = rec.getPlannedEndAt();
            }
            if ((lessonStartAt == null || lessonEndAt == null) && occurrence != null) {
                lessonStartAt = occurrence.getPlannedStartAt();
                lessonEndAt = occurrence.getPlannedEndAt();
            }

            // 共享课次固定使用最小 lesson_record_id，确保列表和结算使用同一代表记录。
            if (isSharedOccurrence(occurrence)) {
                if (!Objects.equals(rec.getId(), representativeRecordByOccurrence.get(rec.getOccurrenceId()))) {
                    continue;
                }

                ClassGroupEntity cg = classGroupMap.get(occurrence.getClassGroupId());
                CoursePackageEntity privatePackage = occurrence.getPrivateLessonGroupId() == null
                        ? null : packageMap.get(rec.getPackageId());
                EnrollmentOrderItemEntity privateOrderItem = privatePackage == null
                        ? null : orderItemMap.get(privatePackage.getEnrollmentOrderItemId());
                EnrollmentOrderEntity privateOrder = privatePackage == null
                        ? null : orderMap.get(privatePackage.getEnrollmentOrderId());
                Long productId = cg != null
                        ? cg.getProductId()
                        : privatePackage == null ? null : privatePackage.getCourseProductId();
                BigDecimal hourlyRate = resolvePayRate(rateMap, query.getTeacherId(), productId,
                        cg == null ? rec.getStudentId() : null,
                        resolveGradeBand(privateOrderItem, studentMap.get(rec.getStudentId()), cg),
                        resolveDeliveryMode(occurrence, cg, privateOrder, classroomMap));

                BigDecimal hours = BigDecimal.valueOf(rec.getActualUnits())
                        .divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP);
                BigDecimal amount = hours.multiply(hourlyRate).setScale(2, RoundingMode.HALF_UP);

                StudentEntity sharedStudent = studentMap.get(rec.getStudentId());
                CourseProductEntity product = productId != null ? productMap.get(productId) : null;
                result.add(UnpaidLessonVO.builder()
                        .lessonRecordId(rec.getId())
                        .lessonStartAt(lessonStartAt)
                        .lessonEndAt(lessonEndAt)
                        .studentId(rec.getStudentId())
                        .studentName(sharedStudent == null ? null : sharedStudent.getStudentName())
                        .packageId(privatePackage == null ? null : privatePackage.getId())
                        .packageName(privatePackage == null ? null : privatePackage.getPackageName())
                        .courseProductId(productId)
                        .productName(product != null ? product.getProductName() : null)
                        .subjectName(product != null ? product.getSubjectName() : null)
                        .actualUnits(rec.getActualUnits())
                        .hours(hours)
                        .hourlyRate(hourlyRate)
                        .amount(amount)
                        .resultStatus(rec.getResultStatus())
                        .occurrenceType(occurrence.getOccurrenceType())
                        .className(cg != null ? cg.getClassName() : null)
                        .participantNames(resolvePrivateParticipantNames(
                                rec.getOccurrenceId(), records, studentMap, occurrenceMap))
                        .lessonRecordIds(null)
                        .build());
                continue;
            }

            // 非班课：一条记录一行
            CoursePackageEntity pkg = packageMap.get(rec.getPackageId());
            Long productId = pkg != null ? pkg.getCourseProductId() : null;
            EnrollmentOrderItemEntity orderItem = pkg == null ? null : orderItemMap.get(pkg.getEnrollmentOrderItemId());
            EnrollmentOrderEntity order = pkg == null ? null : orderMap.get(pkg.getEnrollmentOrderId());
            BigDecimal hourlyRate = resolvePayRate(rateMap, query.getTeacherId(), productId, rec.getStudentId(),
                    resolveGradeBand(orderItem, studentMap.get(rec.getStudentId()), null),
                    resolveDeliveryMode(occurrence, null, order, classroomMap));
            BigDecimal hours = BigDecimal.valueOf(rec.getActualUnits())
                    .divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP);
            BigDecimal amount = hours.multiply(hourlyRate).setScale(2, RoundingMode.HALF_UP);

            StudentEntity student = studentMap.get(rec.getStudentId());
            CourseProductEntity product = productId != null ? productMap.get(productId) : null;

            result.add(UnpaidLessonVO.builder()
                    .lessonRecordId(rec.getId())
                    .lessonStartAt(lessonStartAt)
                    .lessonEndAt(lessonEndAt)
                    .studentId(rec.getStudentId())
                    .studentName(student != null ? student.getStudentName() : null)
                    .packageId(rec.getPackageId())
                    .packageName(pkg != null ? pkg.getPackageName() : null)
                    .courseProductId(productId)
                    .productName(product != null ? product.getProductName() : null)
                    .subjectName(product != null ? product.getSubjectName() : null)
                    .actualUnits(rec.getActualUnits())
                    .hours(hours)
                    .hourlyRate(hourlyRate)
                    .amount(amount)
                    .resultStatus(rec.getResultStatus())
                    .occurrenceType(occurrence != null ? occurrence.getOccurrenceType() : null)
                    .className(resolveClassName(occurrence))
                    .lessonRecordIds(null)
                    .build());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPayment(TeacherPaymentCreateRequest request, Long userId) {
        // 1. 校验老师和课次
        TeacherEntity teacher = teacherMapper.selectById(request.getTeacherId());
        if (teacher == null || !"ACTIVE".equals(teacher.getStatus())) {
            throw new BusinessException("TEACHER_NOT_AVAILABLE", "Teacher not found or inactive.");
        }

        List<LessonRecordEntity> records = lessonRecordMapper.selectBatchIds(request.getLessonRecordIds());
        if (records.size() != request.getLessonRecordIds().size()) {
            throw new BusinessException("LESSON_RECORD_NOT_FOUND", "Some lesson records not found.");
        }

        validatePayableLessonRecords(records, request.getTeacherId());
        validatePaymentPeriod(request, records);

        // Recheck settlement status immediately before creating any payment rows.
        List<Long> paidIds = paymentItemMapper.selectList(
                        new LambdaQueryWrapper<TeacherPaymentItemEntity>()
                                .in(TeacherPaymentItemEntity::getLessonRecordId, request.getLessonRecordIds()))
                .stream()
                .map(TeacherPaymentItemEntity::getLessonRecordId)
                .toList();
        if (!paidIds.isEmpty()) {
            throw new BusinessException("ALREADY_PAID", paidIds.size() + " lesson record(s) are already settled.");
        }

        // 2. 计算总额 — 先批量加载 occurrence + classGroup（用于班课）
        Set<Long> occurrenceIds = records.stream().map(LessonRecordEntity::getOccurrenceId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, LessonOccurrenceEntity> occurrenceMap = !occurrenceIds.isEmpty()
                ? lessonOccurrenceMapper.selectBatchIds(occurrenceIds).stream()
                .collect(Collectors.toMap(LessonOccurrenceEntity::getId, o -> o))
                : Collections.emptyMap();
        Set<Long> classGroupIds = occurrenceMap.values().stream()
                .map(LessonOccurrenceEntity::getClassGroupId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ClassGroupEntity> classGroupMap = !classGroupIds.isEmpty()
                ? classGroupMapper.selectBatchIds(classGroupIds).stream()
                .collect(Collectors.toMap(ClassGroupEntity::getId, cg -> cg))
                : Collections.emptyMap();
        Map<Long, Long> privatePrimaryPackageByGroup = loadPrivatePrimaryPackageByGroup(occurrenceMap.values());
        validateRepresentativeSharedRecords(records, occurrenceMap, privatePrimaryPackageByGroup);

        Set<Long> sharedOccurrenceIds = occurrenceMap.values().stream()
                .filter(this::isSharedOccurrence)
                .map(LessonOccurrenceEntity::getId)
                .collect(Collectors.toSet());
        if (!sharedOccurrenceIds.isEmpty()) {
            List<Long> sharedRecordIds = lessonRecordMapper.selectList(
                            new LambdaQueryWrapper<LessonRecordEntity>()
                                    .in(LessonRecordEntity::getOccurrenceId, sharedOccurrenceIds)
                                    .eq(LessonRecordEntity::getIsDeleted, 0))
                    .stream().map(LessonRecordEntity::getId).toList();
            List<Long> paidSharedRecordIds = sharedRecordIds.isEmpty() ? Collections.emptyList() : paymentItemMapper.selectList(
                            new LambdaQueryWrapper<TeacherPaymentItemEntity>()
                                    .in(TeacherPaymentItemEntity::getLessonRecordId, sharedRecordIds))
                    .stream().map(TeacherPaymentItemEntity::getLessonRecordId).toList();
            if (!paidSharedRecordIds.isEmpty()) {
                throw new BusinessException("ALREADY_PAID", "A shared lesson occurrence is already settled.");
            }
        }

        Set<Long> packageIds = records.stream().map(LessonRecordEntity::getPackageId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, CoursePackageEntity> packageMap = !packageIds.isEmpty()
                ? coursePackageMapper.selectBatchIds(packageIds).stream()
                .collect(Collectors.toMap(CoursePackageEntity::getId, p -> p))
                : Collections.emptyMap();
        Map<Long, StudentEntity> studentMap = loadStudents(records);
        Map<Long, EnrollmentOrderItemEntity> orderItemMap = loadOrderItems(packageMap.values());
        Map<Long, EnrollmentOrderEntity> orderMap = loadOrders(packageMap.values());
        Map<Long, ClassroomEntity> classroomMap = loadClassrooms(occurrenceMap.values(), classGroupMap.values());

        List<TeacherPayRateEntity> rates = payRateMapper.selectList(
                new LambdaQueryWrapper<TeacherPayRateEntity>()
                        .eq(TeacherPayRateEntity::getTeacherId, request.getTeacherId()));
        Map<String, BigDecimal> rateMap = toRateMap(rates);

        int totalUnits = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<PaymentItemCalc> items = new ArrayList<>();

        for (LessonRecordEntity rec : records) {
            Long productId;
            if (rec.getPackageId() != null) {
                // 1v1：从课包获取 productId
                CoursePackageEntity pkg = packageMap.get(rec.getPackageId());
                productId = pkg != null ? pkg.getCourseProductId() : null;
            } else {
                // 班课：从 occurrence → class_group 获取 productId
                LessonOccurrenceEntity occ = occurrenceMap.get(rec.getOccurrenceId());
                ClassGroupEntity cg = occ != null ? classGroupMap.get(occ.getClassGroupId()) : null;
                productId = cg != null ? cg.getProductId() : null;
            }
            LessonOccurrenceEntity occ = occurrenceMap.get(rec.getOccurrenceId());
            CoursePackageEntity pkg = rec.getPackageId() == null ? null : packageMap.get(rec.getPackageId());
            ClassGroupEntity cg = occ == null ? null : classGroupMap.get(occ.getClassGroupId());
            EnrollmentOrderItemEntity orderItem = pkg == null ? null : orderItemMap.get(pkg.getEnrollmentOrderItemId());
            EnrollmentOrderEntity order = pkg == null ? null : orderMap.get(pkg.getEnrollmentOrderId());
            BigDecimal hr = requirePayRate(
                    resolvePayRate(rateMap, request.getTeacherId(), productId,
                            rec.getPackageId() == null ? null : rec.getStudentId(),
                            resolveGradeBand(orderItem, studentMap.get(rec.getStudentId()), cg),
                            resolveDeliveryMode(occ, cg, order, classroomMap)),
                    productId);
            BigDecimal hours = BigDecimal.valueOf(rec.getActualUnits())
                    .divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP);
            BigDecimal amount = hours.multiply(hr).setScale(2, RoundingMode.HALF_UP);

            totalUnits += rec.getActualUnits();
            totalAmount = totalAmount.add(amount);

            items.add(new PaymentItemCalc(rec.getId(), rec.getPackageId(), productId,
                    rec.getActualUnits(), hr, amount));
        }

        BigDecimal totalHours = BigDecimal.valueOf(totalUnits)
                .divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP);

        // 3. 创建结算批次
        String paymentNo = generatePaymentNo(teacher.getTeacherNo());

        TeacherPaymentEntity payment = new TeacherPaymentEntity();
        payment.setPaymentNo(paymentNo);
        payment.setTeacherId(request.getTeacherId());
        payment.setPeriodStart(request.getPeriodStart());
        payment.setPeriodEnd(request.getPeriodEnd());
        payment.setTotalUnits(totalUnits);
        payment.setTotalHours(totalHours);
        payment.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        payment.setStatus("PENDING");
        payment.setNotes(request.getNotes());
        paymentMapper.insert(payment);

        // 4. 创建结算明细
        LocalDateTime now = AppTime.nowLocalDateTime();
        for (PaymentItemCalc item : items) {
            TeacherPaymentItemEntity pi = new TeacherPaymentItemEntity();
            pi.setPaymentId(payment.getId());
            pi.setLessonRecordId(item.lessonRecordId);
            pi.setPackageId(item.packageId);
            pi.setCourseProductId(item.courseProductId);
            pi.setActualUnits(item.actualUnits);
            pi.setHourlyRate(item.hourlyRate);
            pi.setAmount(item.amount);
            pi.setCreatedAt(now);
            paymentItemMapper.insert(pi);
        }

        return payment.getId();
    }

    private void validatePayableLessonRecords(List<LessonRecordEntity> records, Long teacherId) {
        for (LessonRecordEntity record : records) {
            if (!Objects.equals(record.getActualTeacherId(), teacherId)) {
                throw new BusinessException("LESSON_RECORD_TEACHER_MISMATCH",
                        "Selected lesson record does not belong to the requested teacher.");
            }
            if (!"CONFIRMED".equals(record.getConfirmStage())) {
                throw new BusinessException("LESSON_RECORD_NOT_CONFIRMED",
                        "Selected lesson record must be finally confirmed before payment.");
            }
            if (!PAYABLE_STATUSES.contains(record.getResultStatus())) {
                throw new BusinessException("LESSON_RECORD_NOT_PAYABLE",
                        "Selected lesson record result status is not eligible for teacher payment.");
            }
        }
    }

    private void validateRepresentativeSharedRecords(List<LessonRecordEntity> selectedRecords,
                                                     Map<Long, LessonOccurrenceEntity> occurrenceMap,
                                                     Map<Long, Long> privatePrimaryPackageByGroup) {
        Map<Long, List<LessonRecordEntity>> selectedByOccurrence = selectedRecords.stream()
                .filter(record -> {
                    LessonOccurrenceEntity occurrence = occurrenceMap.get(record.getOccurrenceId());
                    return isSharedOccurrence(occurrence);
                })
                .collect(Collectors.groupingBy(LessonRecordEntity::getOccurrenceId));
        if (selectedByOccurrence.isEmpty()) {
            return;
        }
        if (selectedByOccurrence.values().stream().anyMatch(records -> records.size() > 1)) {
            boolean includesGroupClass = selectedByOccurrence.keySet().stream()
                    .map(occurrenceMap::get)
                    .anyMatch(occurrence -> occurrence != null && occurrence.getClassGroupId() != null);
            throw new BusinessException(
                    includesGroupClass ? "DUPLICATE_CLASS_LESSON_PAYMENT" : "DUPLICATE_SHARED_LESSON_PAYMENT",
                    "A shared lesson occurrence can only be included once in a teacher payment.");
        }

        List<LessonRecordEntity> classRecords = lessonRecordMapper.selectList(
                new LambdaQueryWrapper<LessonRecordEntity>()
                        .in(LessonRecordEntity::getOccurrenceId, selectedByOccurrence.keySet())
                        .eq(LessonRecordEntity::getConfirmStage, "CONFIRMED")
                        .eq(LessonRecordEntity::getIsDeleted, 0));
        Map<Long, Long> representativeRecordByOccurrence = classRecords.stream()
                .collect(Collectors.toMap(
                        LessonRecordEntity::getOccurrenceId,
                        LessonRecordEntity::getId,
                        (left, right) -> chooseRepresentativeRecord(left, right, classRecords, occurrenceMap,
                                privatePrimaryPackageByGroup)));
        for (Map.Entry<Long, List<LessonRecordEntity>> entry : selectedByOccurrence.entrySet()) {
            Long selectedRecordId = entry.getValue().get(0).getId();
            if (!Objects.equals(selectedRecordId, representativeRecordByOccurrence.get(entry.getKey()))) {
                throw new BusinessException("SHARED_LESSON_RECORD_NOT_REPRESENTATIVE",
                        "Refresh unpaid lessons before creating the teacher payment.");
            }
        }
    }

    private boolean isSharedOccurrence(LessonOccurrenceEntity occurrence) {
        return occurrence != null && (occurrence.getClassGroupId() != null
                || occurrence.getPrivateLessonGroupId() != null);
    }

    private Map<Long, Long> loadPrivatePrimaryPackageByGroup(Collection<LessonOccurrenceEntity> occurrences) {
        Set<Long> groupIds = occurrences.stream()
                .map(LessonOccurrenceEntity::getPrivateLessonGroupId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (groupIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return privateLessonGroupMemberMapper.selectList(new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                        .in(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId, groupIds)
                        .eq(PrivateLessonGroupMemberEntity::getParticipantIndex, 1)
                        .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                        .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0))
                .stream()
                .collect(Collectors.toMap(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId,
                        PrivateLessonGroupMemberEntity::getPackageId, (left, right) -> left));
    }

    private Long chooseRepresentativeRecord(Long leftId,
                                            Long rightId,
                                            List<LessonRecordEntity> records,
                                            Map<Long, LessonOccurrenceEntity> occurrenceMap,
                                            Map<Long, Long> privatePrimaryPackageByGroup) {
        LessonRecordEntity left = records.stream().filter(item -> Objects.equals(item.getId(), leftId)).findFirst().orElse(null);
        LessonRecordEntity right = records.stream().filter(item -> Objects.equals(item.getId(), rightId)).findFirst().orElse(null);
        LessonOccurrenceEntity occurrence = left == null ? null : occurrenceMap.get(left.getOccurrenceId());
        if (occurrence != null && occurrence.getPrivateLessonGroupId() != null) {
            Long primaryPackageId = privatePrimaryPackageByGroup.get(occurrence.getPrivateLessonGroupId());
            if (primaryPackageId != null) {
                if (left != null && Objects.equals(left.getPackageId(), primaryPackageId)) return leftId;
                if (right != null && Objects.equals(right.getPackageId(), primaryPackageId)) return rightId;
            }
        }
        return leftId == null ? rightId : rightId == null ? leftId : Math.min(leftId, rightId);
    }

    private List<String> resolvePrivateParticipantNames(Long occurrenceId,
                                                         List<LessonRecordEntity> records,
                                                         Map<Long, StudentEntity> studentMap,
                                                         Map<Long, LessonOccurrenceEntity> occurrenceMap) {
        LessonOccurrenceEntity occurrence = occurrenceMap.get(occurrenceId);
        if (occurrence == null || occurrence.getPrivateLessonGroupId() == null) {
            return Collections.emptyList();
        }
        return records.stream()
                .filter(record -> Objects.equals(record.getOccurrenceId(), occurrenceId))
                .sorted(Comparator.comparing(LessonRecordEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(record -> studentMap.get(record.getStudentId()))
                .filter(Objects::nonNull)
                .map(StudentEntity::getStudentName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private Map<String, BigDecimal> toRateMap(List<TeacherPayRateEntity> rates) {
        Map<String, BigDecimal> rateMap = new HashMap<>();
        for (TeacherPayRateEntity rate : rates) {
            rateMap.put(rateKey(rate.getTeacherId(), rate.getCourseProductId(), rate.getStudentId(),
                    rate.getGradeBand(), rate.getDeliveryMode()), rate.getHourlyRate());
        }
        return rateMap;
    }

    private BigDecimal requirePayRate(BigDecimal rate, Long productId) {
        if (productId == null || rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("TEACHER_PAY_RATE_REQUIRED",
                    "Configure a positive teacher pay rate for every selected course before creating payment.");
        }
        return rate;
    }

    private BigDecimal resolvePayRate(Map<String, BigDecimal> rateMap,
                                      Long teacherId,
                                      Long productId,
                                      Long studentId,
                                      String gradeBand,
                                      String deliveryMode) {
        if (studentId != null) {
            BigDecimal studentOverride = resolveScopedRate(rateMap, teacherId, productId, studentId,
                    gradeBand, deliveryMode);
            if (studentOverride != null) {
                return studentOverride;
            }
        }
        BigDecimal defaultRate = resolveScopedRate(rateMap, teacherId, productId, null, gradeBand, deliveryMode);
        return defaultRate == null ? BigDecimal.ZERO : defaultRate;
    }

    private BigDecimal resolveScopedRate(Map<String, BigDecimal> rateMap,
                                         Long teacherId,
                                         Long productId,
                                         Long studentId,
                                         String gradeBand,
                                         String deliveryMode) {
        List<String> gradeCandidates = scopeCandidates(gradeBand);
        List<String> deliveryCandidates = scopeCandidates(deliveryMode);
        for (Long productCandidate : productCandidates(productId)) {
            for (String grade : gradeCandidates) {
                for (String delivery : deliveryCandidates) {
                    BigDecimal rate = rateMap.get(rateKey(teacherId, productCandidate, studentId, grade, delivery));
                    if (rate != null) {
                        return rate;
                    }
                }
            }
        }
        return null;
    }

    private List<Long> productCandidates(Long productId) {
        return productId == null ? Collections.singletonList(null) : Arrays.asList(productId, null);
    }

    private List<String> scopeCandidates(String value) {
        String normalized = normalizeScope(value);
        return normalized == null ? List.of("ANY") : List.of(normalized, "ANY");
    }

    private String rateKey(Long teacherId, Long productId, Long studentId, String gradeBand, String deliveryMode) {
        return teacherId + ":" + (productId == null ? "ANY_PRODUCT" : productId)
                + ":" + (studentId == null ? "DEFAULT" : studentId)
                + ":" + scopeKey(gradeBand) + ":" + scopeKey(deliveryMode);
    }

    private String scopeKey(String value) {
        String normalized = normalizeScope(value);
        return normalized == null ? "ANY" : normalized;
    }

    private String normalizeScope(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ELEMENTARY SCHOOL", "K TO 5" -> "K-5";
            case "MIDDLE SCHOOL", "6 TO 8" -> "6-8";
            case "HIGH SCHOOL", "9 TO 12" -> "9-12";
            case "AP", "AP LEVEL" -> "AP LEVEL";
            default -> normalized;
        };
    }

    private String resolveGradeBand(EnrollmentOrderItemEntity orderItem, StudentEntity student, ClassGroupEntity classGroup) {
        if (orderItem != null && StringUtils.hasText(orderItem.getGradeBand())) {
            return toPayGradeBand(orderItem.getGradeBand());
        }
        if (classGroup != null && StringUtils.hasText(classGroup.getGradeBand())) {
            return toPayGradeBand(classGroup.getGradeBand());
        }
        return student == null ? null : toPayGradeBand(student.getGrade());
    }

    private String toPayGradeBand(String value) {
        String normalized = normalizeScope(value);
        if (normalized == null) {
            return null;
        }
        if ("AP LEVEL".equals(normalized) || normalized.contains("AP")) {
            return "AP LEVEL";
        }
        if ("K-5".equals(normalized) || "6-8".equals(normalized) || "9-12".equals(normalized)) {
            return normalized;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("G?(\\d{1,2})").matcher(normalized);
        if (!matcher.find()) {
            return normalized;
        }
        int grade = Integer.parseInt(matcher.group(1));
        if (grade <= 5) {
            return "K-5";
        }
        if (grade <= 8) {
            return "6-8";
        }
        return "9-12";
    }

    private String resolveDeliveryMode(LessonOccurrenceEntity occurrence,
                                       ClassGroupEntity classGroup,
                                       EnrollmentOrderEntity order,
                                       Map<Long, ClassroomEntity> classroomMap) {
        ClassroomEntity plannedRoom = occurrence == null ? null : classroomMap.get(occurrence.getPlannedClassroomId());
        if (plannedRoom != null) {
            return isOnlineText(plannedRoom.getRoomName(), plannedRoom.getCampus(), plannedRoom.getNotes())
                    ? "ONLINE" : "OFFLINE";
        }
        ClassroomEntity classRoom = classGroup == null ? null : classroomMap.get(classGroup.getClassroomId());
        if (classRoom != null) {
            return isOnlineText(classRoom.getRoomName(), classRoom.getCampus(), classRoom.getNotes())
                    ? "ONLINE" : "OFFLINE";
        }
        if (order != null && StringUtils.hasText(order.getCampusNameSnapshot())) {
            return isOnlineText(order.getCampusNameSnapshot()) ? "ONLINE" : "OFFLINE";
        }
        return null;
    }

    private boolean isOnlineText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value) && value.toLowerCase(Locale.ROOT).contains("online")) {
                return true;
            }
        }
        return false;
    }

    private Map<Long, StudentEntity> loadStudents(List<LessonRecordEntity> records) {
        Set<Long> studentIds = records.stream()
                .map(LessonRecordEntity::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return studentIds.isEmpty() ? Collections.emptyMap()
                : studentMapper.selectBatchIds(studentIds).stream()
                        .collect(Collectors.toMap(StudentEntity::getId, s -> s));
    }

    private Map<Long, EnrollmentOrderItemEntity> loadOrderItems(Collection<CoursePackageEntity> packages) {
        Set<Long> itemIds = packages.stream()
                .map(CoursePackageEntity::getEnrollmentOrderItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return itemIds.isEmpty() ? Collections.emptyMap()
                : enrollmentOrderItemMapper.selectBatchIds(itemIds).stream()
                        .collect(Collectors.toMap(EnrollmentOrderItemEntity::getId, item -> item, (left, right) -> left));
    }

    private Map<Long, EnrollmentOrderEntity> loadOrders(Collection<CoursePackageEntity> packages) {
        Set<Long> orderIds = packages.stream()
                .map(CoursePackageEntity::getEnrollmentOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return orderIds.isEmpty() ? Collections.emptyMap()
                : enrollmentOrderMapper.selectBatchIds(orderIds).stream()
                        .collect(Collectors.toMap(EnrollmentOrderEntity::getId, order -> order, (left, right) -> left));
    }

    private Map<Long, ClassroomEntity> loadClassrooms(Collection<LessonOccurrenceEntity> occurrences,
                                                      Collection<ClassGroupEntity> classGroups) {
        Set<Long> classroomIds = new HashSet<>();
        occurrences.stream()
                .map(LessonOccurrenceEntity::getPlannedClassroomId)
                .filter(Objects::nonNull)
                .forEach(classroomIds::add);
        classGroups.stream()
                .map(ClassGroupEntity::getClassroomId)
                .filter(Objects::nonNull)
                .forEach(classroomIds::add);
        return classroomIds.isEmpty() ? Collections.emptyMap()
                : classroomMapper.selectBatchIds(classroomIds).stream()
                        .collect(Collectors.toMap(ClassroomEntity::getId, classroom -> classroom, (left, right) -> left));
    }

    private void validatePaymentPeriod(TeacherPaymentCreateRequest request, List<LessonRecordEntity> records) {
        if (request.getPeriodStart() == null || request.getPeriodEnd() == null) {
            throw new BusinessException("PAYMENT_PERIOD_REQUIRED",
                    "Payment period start and end dates are required.");
        }
        validatePeriod(request.getPeriodStart(), request.getPeriodEnd());
        for (LessonRecordEntity record : records) {
            if (record.getConfirmedAt() == null
                    || record.getConfirmedAt().toLocalDate().isBefore(request.getPeriodStart())
                    || record.getConfirmedAt().toLocalDate().isAfter(request.getPeriodEnd())) {
                throw new BusinessException("LESSON_OUTSIDE_PAYMENT_PERIOD",
                        "Every selected lesson must be finally confirmed within the payment period.");
            }
        }
    }

    private void validatePeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException("INVALID_PAYMENT_PERIOD",
                    "Payment period end date cannot be earlier than start date.");
        }
    }

    @Override
    public PageResponse<TeacherPaymentVO> pagePayments(Long teacherId, String status, int pageNum, int pageSize) {
        IPage<TeacherPaymentEntity> page = paymentMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<TeacherPaymentEntity>()
                        .eq(teacherId != null, TeacherPaymentEntity::getTeacherId, teacherId)
                        .eq(StringUtils.hasText(status), TeacherPaymentEntity::getStatus, status)
                        .orderByDesc(TeacherPaymentEntity::getCreatedAt)
        );
        if (page.getRecords().isEmpty()) {
            return PageResponse.empty(pageNum, pageSize);
        }

        Set<Long> teacherIds = page.getRecords().stream().map(TeacherPaymentEntity::getTeacherId).collect(Collectors.toSet());
        Map<Long, TeacherEntity> teacherMap = teacherMapper.selectBatchIds(teacherIds).stream()
                .collect(Collectors.toMap(TeacherEntity::getId, t -> t));

        List<TeacherPaymentVO> vos = page.getRecords().stream()
                .map(p -> toPaymentVO(p, teacherMap.get(p.getTeacherId()), null))
                .toList();

        return PageResponse.of(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PageResponse<TeacherPaymentVO> pagePaymentsForTeacher(Long teacherId, String status, int pageNum, int pageSize) {
        if (teacherId == null) {
            throw new BusinessException("TEACHER_BINDING_REQUIRED", "This account is not linked to a teacher profile.");
        }
        return pagePayments(teacherId, status, pageNum, pageSize);
    }

    @Override
    public TeacherPaymentVO getPaymentDetail(Long id) {
        TeacherPaymentEntity payment = paymentMapper.selectById(id);
        if (payment == null) {
            throw new BusinessException("PAYMENT_NOT_FOUND", "Payment not found.");
        }

        TeacherEntity teacher = teacherMapper.selectById(payment.getTeacherId());

        // 查询明细
        List<TeacherPaymentItemEntity> items = paymentItemMapper.selectList(
                new LambdaQueryWrapper<TeacherPaymentItemEntity>()
                        .eq(TeacherPaymentItemEntity::getPaymentId, id));
        List<TeacherPaymentItemVO> itemVOs = toItemVOs(items);

        return toPaymentVO(payment, teacher, itemVOs);
    }

    @Override
    public TeacherPaymentVO getPaymentDetailForTeacher(Long id, Long teacherId) {
        if (teacherId == null) {
            throw new BusinessException("TEACHER_BINDING_REQUIRED", "This account is not linked to a teacher profile.");
        }
        TeacherPaymentVO detail = getPaymentDetail(id);
        if (!Objects.equals(detail.teacherId(), teacherId)) {
            throw new BusinessException("PAYMENT_NOT_FOUND", "Payment not found.");
        }
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmPayment(Long id, Long userId) {
        TeacherPaymentEntity payment = paymentMapper.selectById(id);
        if (payment == null) {
            throw new BusinessException("PAYMENT_NOT_FOUND", "Payment not found.");
        }
        if (!"PENDING".equals(payment.getStatus())) {
            throw new BusinessException("INVALID_STATUS", "Only PENDING payments can be confirmed.");
        }

        payment.setStatus("PAID");
        payment.setPaidAt(AppTime.nowLocalDateTime());
        payment.setPaidBy(userId);
        paymentMapper.updateById(payment);
    }

    // ========== 辅助方法 ==========

    private String generatePaymentNo(String teacherNo) {
        String datePart = LocalDate.now().toString().replace("-", "");
        // 取今天已生成的结算数
        long count = paymentMapper.selectCount(new LambdaQueryWrapper<TeacherPaymentEntity>()
                .likeRight(TeacherPaymentEntity::getPaymentNo, "SAL-" + teacherNo + "-" + datePart));
        return String.format("SAL-%s-%s-%02d", teacherNo, datePart, count + 1);
    }

    private TeacherPaymentVO toPaymentVO(TeacherPaymentEntity p, TeacherEntity teacher, List<TeacherPaymentItemVO> items) {
        return TeacherPaymentVO.builder()
                .id(p.getId())
                .paymentNo(p.getPaymentNo())
                .teacherId(p.getTeacherId())
                .teacherNo(teacher != null ? teacher.getTeacherNo() : null)
                .teacherName(teacher != null ? teacher.getTeacherName() : null)
                .periodStart(p.getPeriodStart())
                .periodEnd(p.getPeriodEnd())
                .totalUnits(p.getTotalUnits())
                .totalHours(p.getTotalHours())
                .totalAmount(p.getTotalAmount())
                .status(p.getStatus())
                .paidAt(p.getPaidAt())
                .notes(p.getNotes())
                .createdAt(p.getCreatedAt())
                .items(items)
                .build();
    }

    private List<TeacherPaymentItemVO> toItemVOs(List<TeacherPaymentItemEntity> items) {
        if (items.isEmpty()) return Collections.emptyList();

        Set<Long> packageIds = items.stream().map(TeacherPaymentItemEntity::getPackageId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> productIds = items.stream().map(TeacherPaymentItemEntity::getCourseProductId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> lessonRecordIds = items.stream().map(TeacherPaymentItemEntity::getLessonRecordId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, CoursePackageEntity> packageMap = !packageIds.isEmpty()
                ? coursePackageMapper.selectBatchIds(packageIds).stream()
                .collect(Collectors.toMap(CoursePackageEntity::getId, p -> p))
                : Collections.emptyMap();

        Map<Long, CourseProductEntity> productMap = !productIds.isEmpty()
                ? courseProductMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(CourseProductEntity::getId, p -> p))
                : Collections.emptyMap();

        // 批量查询 lesson_record 获取 occurrence_id
        Map<Long, LessonRecordEntity> recordMap = !lessonRecordIds.isEmpty()
                ? lessonRecordMapper.selectBatchIds(lessonRecordIds).stream()
                .collect(Collectors.toMap(LessonRecordEntity::getId, r -> r))
                : Collections.emptyMap();

        Set<Long> occurrenceIds = recordMap.values().stream()
                .map(LessonRecordEntity::getOccurrenceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, LessonOccurrenceEntity> occurrenceMap = !occurrenceIds.isEmpty()
                ? lessonOccurrenceMapper.selectBatchIds(occurrenceIds).stream()
                .collect(Collectors.toMap(LessonOccurrenceEntity::getId, o -> o))
                : Collections.emptyMap();

        return items.stream().map(item -> {
            CoursePackageEntity pkg = packageMap.get(item.getPackageId());
            CourseProductEntity product = productMap.get(item.getCourseProductId());
            LessonRecordEntity rec = recordMap.get(item.getLessonRecordId());
            LessonOccurrenceEntity occurrence = rec != null ? occurrenceMap.get(rec.getOccurrenceId()) : null;
            return TeacherPaymentItemVO.builder()
                    .id(item.getId())
                    .lessonRecordId(item.getLessonRecordId())
                    .packageId(item.getPackageId())
                    .packageName(pkg != null ? pkg.getPackageName() : null)
                    .courseProductId(item.getCourseProductId())
                    .productName(product != null ? product.getProductName() : null)
                    .subjectName(product != null ? product.getSubjectName() : null)
                    .actualUnits(item.getActualUnits())
                    .hours(BigDecimal.valueOf(item.getActualUnits())
                            .divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP))
                    .hourlyRate(item.getHourlyRate())
                    .amount(item.getAmount())
                    .occurrenceType(occurrence != null ? occurrence.getOccurrenceType() : null)
                    .className(resolveClassName(occurrence))
                    .build();
        }).toList();
    }

    private record PaymentItemCalc(Long lessonRecordId, Long packageId, Long courseProductId,
                                   Integer actualUnits, BigDecimal hourlyRate, BigDecimal amount) {}

    /**
     * 根据 occurrence 解析班课名称（如果是班课则查 class_group 表）。
     */
    private String resolveClassName(LessonOccurrenceEntity occurrence) {
        if (occurrence == null || occurrence.getClassGroupId() == null) return null;
        ClassGroupEntity cg = classGroupMapper.selectById(occurrence.getClassGroupId());
        return cg != null ? cg.getClassName() : null;
    }
}
