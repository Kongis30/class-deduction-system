package com.classdeduction.system.modules.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
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

    private static final Set<String> PAYABLE_STATUSES = Set.of("COMPLETED", "MAKEUP_COMPLETED", "SUBSTITUTED_COMPLETED");

    private final LessonRecordMapper lessonRecordMapper;
    private final CoursePackageMapper coursePackageMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final CourseProductMapper courseProductMapper;
    private final TeacherPayRateMapper payRateMapper;
    private final TeacherPaymentMapper paymentMapper;
    private final TeacherPaymentItemMapper paymentItemMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final ClassGroupMapper classGroupMapper;

    @Override
    public List<UnpaidLessonVO> queryUnpaidLessons(UnpaidLessonQuery query) {
        // 1. 查询已结算的 lesson_record_id
        List<Long> paidRecordIds = paymentItemMapper.selectList(
                        new LambdaQueryWrapper<TeacherPaymentItemEntity>()
                                .select(TeacherPaymentItemEntity::getLessonRecordId))
                .stream()
                .map(TeacherPaymentItemEntity::getLessonRecordId)
                .toList();

        // 1a. 如果已结算记录中有班课，找到其 occurrence_id，
        //     将同 occurrence 的所有记录排除（班课只结算一次，不按学生数重复结算）
        Set<Long> settledGroupOccurrenceIds = Collections.emptySet();
        if (!paidRecordIds.isEmpty()) {
            List<LessonRecordEntity> paidRecords = lessonRecordMapper.selectBatchIds(paidRecordIds);
            Set<Long> paidOccurrenceIds = paidRecords.stream()
                    .map(LessonRecordEntity::getOccurrenceId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!paidOccurrenceIds.isEmpty()) {
                settledGroupOccurrenceIds = lessonOccurrenceMapper.selectBatchIds(paidOccurrenceIds).stream()
                        .filter(o -> o.getClassGroupId() != null)
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
                        .in(LessonRecordEntity::getResultStatus, PAYABLE_STATUSES)
                        .notIn(!paidRecordIds.isEmpty(), LessonRecordEntity::getId, paidRecordIds)
                        .notIn(!settledGroupOccurrenceIds.isEmpty(),
                                LessonRecordEntity::getOccurrenceId, settledGroupOccurrenceIds)
                        .ge(start != null, LessonRecordEntity::getConfirmedAt, start)
                        .lt(end != null, LessonRecordEntity::getConfirmedAt, end)
                        .orderByAsc(LessonRecordEntity::getConfirmedAt)
        );
        if (records.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. 批量加载关联数据
        Set<Long> packageIds = records.stream().map(LessonRecordEntity::getPackageId).collect(Collectors.toSet());
        Set<Long> studentIds = records.stream().map(LessonRecordEntity::getStudentId).collect(Collectors.toSet());
        Set<Long> occurrenceIds = records.stream().map(LessonRecordEntity::getOccurrenceId).collect(Collectors.toSet());

        Map<Long, CoursePackageEntity> packageMap = coursePackageMapper.selectBatchIds(packageIds).stream()
                .collect(Collectors.toMap(CoursePackageEntity::getId, p -> p));
        Map<Long, StudentEntity> studentMap = studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(StudentEntity::getId, s -> s));

        // 3a. 查询 occurrence 信息（occurrenceType + classGroupId）
        Map<Long, LessonOccurrenceEntity> occurrenceMap = !occurrenceIds.isEmpty()
                ? lessonOccurrenceMapper.selectBatchIds(occurrenceIds).stream()
                .collect(Collectors.toMap(LessonOccurrenceEntity::getId, o -> o))
                : Collections.emptyMap();

        // 4. 获取产品的 productId 集合
        Set<Long> productIds = packageMap.values().stream()
                .map(CoursePackageEntity::getCourseProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, CourseProductEntity> productMap = !productIds.isEmpty()
                ? courseProductMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(CourseProductEntity::getId, p -> p))
                : Collections.emptyMap();

        // 5. 获取老师的课时单价
        List<TeacherPayRateEntity> rates = payRateMapper.selectList(
                new LambdaQueryWrapper<TeacherPayRateEntity>()
                        .eq(TeacherPayRateEntity::getTeacherId, query.getTeacherId()));
        Map<String, BigDecimal> rateMap = new HashMap<>();
        for (TeacherPayRateEntity rate : rates) {
            rateMap.put(rate.getTeacherId() + ":" + rate.getCourseProductId(), rate.getHourlyRate());
        }

        // 6. 组装结果
        // 班课按 occurrence 聚合成一条记录（用第一条的课时数，不叠加学生数）
        // 非班课一条记录一行
        List<UnpaidLessonVO> result = new ArrayList<>();
        Set<Long> groupedOccurrenceIds = new HashSet<>();

        for (LessonRecordEntity rec : records) {
            LessonOccurrenceEntity occurrence = occurrenceMap.get(rec.getOccurrenceId());

            // 班课：同一 occurrence 只取第一条记录
            if (occurrence != null && occurrence.getClassGroupId() != null) {
                if (groupedOccurrenceIds.contains(rec.getOccurrenceId())) {
                    continue; // 该 occurrence 已处理过
                }
                groupedOccurrenceIds.add(rec.getOccurrenceId());

                CoursePackageEntity pkg = packageMap.get(rec.getPackageId());
                Long productId = pkg != null ? pkg.getCourseProductId() : null;
                String rateKey = query.getTeacherId() + ":" + productId;
                BigDecimal hourlyRate = rateMap.getOrDefault(rateKey, BigDecimal.ZERO);

                BigDecimal hours = BigDecimal.valueOf(rec.getActualUnits())
                        .divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP);
                BigDecimal amount = hours.multiply(hourlyRate).setScale(2, RoundingMode.HALF_UP);

                CourseProductEntity product = productId != null ? productMap.get(productId) : null;

                result.add(UnpaidLessonVO.builder()
                        .lessonRecordId(rec.getId())
                        .lessonDate(rec.getConfirmedAt())
                        .studentId(rec.getStudentId())
                        .studentName(null) // 班课不显示学生名字
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
                        .occurrenceType(occurrence.getOccurrenceType())
                        .className(resolveClassName(occurrence))
                        .lessonRecordId(rec.getId()) // 只用一条 lessonRecordId 去结算
                        .lessonRecordIds(null)
                        .build());
                continue;
            }

            // 非班课：一条记录一行
            CoursePackageEntity pkg = packageMap.get(rec.getPackageId());
            Long productId = pkg != null ? pkg.getCourseProductId() : null;
            String rateKey = query.getTeacherId() + ":" + productId;
            BigDecimal hourlyRate = rateMap.getOrDefault(rateKey, BigDecimal.ZERO);

            BigDecimal hours = BigDecimal.valueOf(rec.getActualUnits())
                    .divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP);
            BigDecimal amount = hours.multiply(hourlyRate).setScale(2, RoundingMode.HALF_UP);

            StudentEntity student = studentMap.get(rec.getStudentId());
            CourseProductEntity product = productId != null ? productMap.get(productId) : null;

            result.add(UnpaidLessonVO.builder()
                    .lessonRecordId(rec.getId())
                    .lessonDate(rec.getConfirmedAt())
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
    public Map<String, Object> previewPayment(List<Long> lessonRecordIds) {
        if (lessonRecordIds == null || lessonRecordIds.isEmpty()) {
            throw new BusinessException("INVALID_PARAM", "At least one lesson record is required.");
        }
        // 直接从 unpaid lessons 查询逻辑复用 — 简化处理
        // 这里直接统计传入的 lessonRecordId 的总和
        List<LessonRecordEntity> records = lessonRecordMapper.selectBatchIds(lessonRecordIds);
        int totalUnits = records.stream().mapToInt(LessonRecordEntity::getActualUnits).sum();
        BigDecimal totalHours = BigDecimal.valueOf(totalUnits)
                .divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP);
        BigDecimal totalAmount = records.stream()
                .map(r -> BigDecimal.valueOf(r.getActualUnits())
                        .divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.ZERO)) // placeholder, 实际需要查 rate
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 重新计算金额（需要 rate）
        Set<Long> packageIds = records.stream().map(LessonRecordEntity::getPackageId).collect(Collectors.toSet());
        Map<Long, CoursePackageEntity> packageMap = coursePackageMapper.selectBatchIds(packageIds).stream()
                .collect(Collectors.toMap(CoursePackageEntity::getId, p -> p));

        // 取第一个记录的 teacherId
        Long teacherId = records.isEmpty() ? null : records.get(0).getActualTeacherId();
        List<TeacherPayRateEntity> rates = teacherId != null
                ? payRateMapper.selectList(new LambdaQueryWrapper<TeacherPayRateEntity>()
                .eq(TeacherPayRateEntity::getTeacherId, teacherId))
                : Collections.emptyList();
        Map<String, BigDecimal> rateMap = new HashMap<>();
        for (TeacherPayRateEntity rate : rates) {
            rateMap.put(rate.getTeacherId() + ":" + rate.getCourseProductId(), rate.getHourlyRate());
        }

        BigDecimal actualAmount = BigDecimal.ZERO;
        for (LessonRecordEntity rec : records) {
            CoursePackageEntity pkg = packageMap.get(rec.getPackageId());
            Long productId = pkg != null ? pkg.getCourseProductId() : null;
            BigDecimal hr = rateMap.getOrDefault(teacherId + ":" + productId, BigDecimal.ZERO);
            BigDecimal h = BigDecimal.valueOf(rec.getActualUnits())
                    .divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP);
            actualAmount = actualAmount.add(h.multiply(hr));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("lessonCount", records.size());
        result.put("totalUnits", totalUnits);
        result.put("totalHours", totalHours);
        result.put("totalAmount", actualAmount.setScale(2, RoundingMode.HALF_UP));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPayment(TeacherPaymentCreateRequest request, Long userId) {
        // 1. 校验老师和课次
        TeacherEntity teacher = teacherMapper.selectById(request.getTeacherId());
        if (teacher == null) {
            throw new BusinessException("TEACHER_NOT_FOUND", "Teacher not found.");
        }

        List<LessonRecordEntity> records = lessonRecordMapper.selectBatchIds(request.getLessonRecordIds());
        if (records.size() != request.getLessonRecordIds().size()) {
            throw new BusinessException("LESSON_RECORD_NOT_FOUND", "Some lesson records not found.");
        }

        // 检查是否有已结算的课次
        List<Long> paidIds = paymentItemMapper.selectList(
                        new LambdaQueryWrapper<TeacherPaymentItemEntity>()
                                .in(TeacherPaymentItemEntity::getLessonRecordId, request.getLessonRecordIds()))
                .stream()
                .map(TeacherPaymentItemEntity::getLessonRecordId)
                .toList();
        if (!paidIds.isEmpty()) {
            throw new BusinessException("ALREADY_PAID", paidIds.size() + " lesson record(s) are already settled.");
        }

        // 2. 计算总额
        Set<Long> packageIds = records.stream().map(LessonRecordEntity::getPackageId).collect(Collectors.toSet());
        Map<Long, CoursePackageEntity> packageMap = coursePackageMapper.selectBatchIds(packageIds).stream()
                .collect(Collectors.toMap(CoursePackageEntity::getId, p -> p));

        List<TeacherPayRateEntity> rates = payRateMapper.selectList(
                new LambdaQueryWrapper<TeacherPayRateEntity>()
                        .eq(TeacherPayRateEntity::getTeacherId, request.getTeacherId()));
        Map<String, BigDecimal> rateMap = new HashMap<>();
        for (TeacherPayRateEntity rate : rates) {
            rateMap.put(rate.getTeacherId() + ":" + rate.getCourseProductId(), rate.getHourlyRate());
        }

        int totalUnits = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<PaymentItemCalc> items = new ArrayList<>();

        for (LessonRecordEntity rec : records) {
            CoursePackageEntity pkg = packageMap.get(rec.getPackageId());
            Long productId = pkg != null ? pkg.getCourseProductId() : null;
            BigDecimal hr = rateMap.getOrDefault(request.getTeacherId() + ":" + productId, BigDecimal.ZERO);
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
