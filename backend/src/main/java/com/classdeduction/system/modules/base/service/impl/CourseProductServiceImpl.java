package com.classdeduction.system.modules.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.base.dto.CourseProductPageQuery;
import com.classdeduction.system.modules.base.dto.CourseProductSaveRequest;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.CourseProductScheduleSlotEntity;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductScheduleSlotMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.base.service.CourseProductService;
import com.classdeduction.system.modules.base.vo.CourseProductVO;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Course product service for maintaining reusable course templates.
 */
@Service
@RequiredArgsConstructor
public class CourseProductServiceImpl implements CourseProductService {

    private static final String DEFAULT_STATUS = "ACTIVE";
    private static final Set<String> VALID_COURSE_TYPES = Set.of(
            "AFTER_SCHOOL", "SATURDAY", "SUMMER", "SPRINT", "ONE_ON_ONE");
    private final Object createProductLock = new Object();

    private final CourseProductMapper courseProductMapper;
    private final CourseProductScheduleSlotMapper scheduleSlotMapper;
    private final ClassroomMapper classroomMapper;
    private final TeacherMapper teacherMapper;
    private final CoursePackageMapper coursePackageMapper;
    private final ClassGroupMapper classGroupMapper;
    private final PlatformTransactionManager transactionManager;

    @Override
    public PageResponse<CourseProductVO> pageCourseProducts(CourseProductPageQuery query) {
        Page<CourseProductEntity> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<CourseProductEntity> wrapper = new LambdaQueryWrapper<CourseProductEntity>()
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(CourseProductEntity::getProductCode, query.getKeyword())
                        .or()
                        .like(CourseProductEntity::getProductName, query.getKeyword())
                        .or()
                        .like(CourseProductEntity::getRegisterClass, query.getKeyword())
                        .or()
                        .like(CourseProductEntity::getCourseType, query.getKeyword())
                        .or()
                        .like(CourseProductEntity::getSubjectName, query.getKeyword()))
                .eq(StringUtils.hasText(query.getStatus()), CourseProductEntity::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getTeachingMode()), CourseProductEntity::getTeachingMode, query.getTeachingMode())
                .eq(StringUtils.hasText(query.getCourseType()), CourseProductEntity::getCourseType, query.getCourseType())
                .orderByDesc(CourseProductEntity::getCreatedAt);
        IPage<CourseProductEntity> result = courseProductMapper.selectPage(page, wrapper);

        List<CourseProductEntity> records = result.getRecords();
        if (records.isEmpty()) {
            return PageResponse.of(List.of(), result.getTotal(), result.getCurrent(), result.getSize());
        }

        // 批量加载排课时段
        List<Long> productIds = records.stream().map(CourseProductEntity::getId).toList();
        List<CourseProductScheduleSlotEntity> allSlots = scheduleSlotMapper.selectList(
                new LambdaQueryWrapper<CourseProductScheduleSlotEntity>()
                        .in(CourseProductScheduleSlotEntity::getProductId, productIds));
        Map<Long, List<CourseProductScheduleSlotEntity>> slotsByProduct = allSlots.stream()
                .collect(Collectors.groupingBy(CourseProductScheduleSlotEntity::getProductId));

        // 批量加载教室名称
        Set<Long> classroomIds = records.stream()
                .map(CourseProductEntity::getDefaultClassroomId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ClassroomEntity> classroomMap = classroomIds.isEmpty() ? Collections.emptyMap()
                : classroomMapper.selectBatchIds(classroomIds).stream()
                        .collect(Collectors.toMap(ClassroomEntity::getId, c -> c));

        // 批量加载老师名称
        Set<Long> teacherIds = records.stream()
                .map(CourseProductEntity::getDefaultTeacherId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, TeacherEntity> teacherMap = teacherIds.isEmpty() ? Collections.emptyMap()
                : teacherMapper.selectBatchIds(teacherIds).stream()
                        .collect(Collectors.toMap(TeacherEntity::getId, t -> t));

        return PageResponse.of(records.stream()
                .map(entity -> toVO(entity, slotsByProduct.getOrDefault(entity.getId(), List.of()), classroomMap, teacherMap))
                .toList(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public CourseProductVO getCourseProduct(Long id) {
        return toVO(requireCourseProduct(id));
    }

    @Override
    public Long createCourseProduct(CourseProductSaveRequest request) {
        synchronized (createProductLock) {
            TransactionTemplate tt = new TransactionTemplate(transactionManager);
            return tt.execute(status -> {
                if (!StringUtils.hasText(request.getProductCode())) {
                    request.setProductCode(generateProductCode());
                }
                ensureProductCodeUnique(null, request.getProductCode());
                ensureProductNameUnique(null, request.getProductName());
                validateByTeachingMode(request);
                CourseProductEntity entity = new CourseProductEntity();
                applyCourseProduct(entity, request);
                courseProductMapper.insert(entity);
                return entity.getId();
            });
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCourseProduct(Long id, CourseProductSaveRequest request) {
        CourseProductEntity entity = requireCourseProduct(id);
        if (!StringUtils.hasText(request.getProductCode())) {
            request.setProductCode(entity.getProductCode());
        }
        ensureProductCodeUnique(id, request.getProductCode());
        ensureProductNameUnique(id, request.getProductName());
        validateByTeachingMode(request);
        protectReferencedProductCriticalFields(entity, request);
        applyCourseProduct(entity, request);
        courseProductMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCourseProduct(Long id) {
        CourseProductEntity product = requireCourseProduct(id);
        if ("ONE_ON_ONE".equals(product.getTeachingMode())) {
            // 1对1 产品在报名时会被快照拷贝为课包，删除后会导致历史订单/收据的产品名悬挂。
            // 因此只要被任何课包引用过（不分 DRAFT/ACTIVE/CLOSED），就禁止删除，引导改为 INACTIVE 停售。
            if (hasAnyPackageReference(id)) {
                throw new BusinessException("PRODUCT_HAS_PACKAGES",
                        "Cannot delete course product: it has been used by course packages. "
                                + "Set the product status to INACTIVE instead.");
            }
        } else {
            // 班课产品是班级的活数据源：slots/计费窗口会被续排任务持续回读，class_group 也与产品 1:1 绑定。
            // 一旦开过班就不能删，否则会中断在读班级并悬挂排课/报名/财务数据。
            // 想停止招生改为 INACTIVE，想退役班级请走"删除班级"流程。
            if (hasAnyClassGroupReference(id)) {
                throw new BusinessException("PRODUCT_HAS_CLASSES",
                        "Cannot delete course product: a class has been opened for it. "
                                + "Set the product status to INACTIVE, or delete the class instead.");
            }
        }
        // 级联删除排课时段
        scheduleSlotMapper.delete(new LambdaQueryWrapper<CourseProductScheduleSlotEntity>()
                .eq(CourseProductScheduleSlotEntity::getProductId, id));
        courseProductMapper.deleteById(id);
    }

    /**
     * 判断 1对1 产品是否被任意课包引用过（不分课包状态），用于删除保护。
     */
    private boolean hasAnyPackageReference(Long productId) {
        return coursePackageMapper.selectCount(new LambdaQueryWrapper<CoursePackageEntity>()
                .eq(CoursePackageEntity::getCourseProductId, productId)
                .eq(CoursePackageEntity::getIsDeleted, 0)) > 0;
    }

    /**
     * 判断班课产品是否已被任意班级引用（即已开班），用于删除保护。
     */
    private boolean hasAnyClassGroupReference(Long productId) {
        return classGroupMapper.selectCount(new LambdaQueryWrapper<ClassGroupEntity>()
                .eq(ClassGroupEntity::getProductId, productId)
                .eq(ClassGroupEntity::getIsDeleted, 0)) > 0;
    }

    /**
     * 根据 teachingMode 校验必填字段。
     */
    private void validateByTeachingMode(CourseProductSaveRequest request) {
         if (!Set.of("ONE_ON_ONE", "GROUP").contains(request.getTeachingMode())) {
             throw new BusinessException("INVALID_TEACHING_MODE", "Teaching mode must be ONE_ON_ONE or GROUP.");
         }
         if (StringUtils.hasText(request.getStatus()) && !Set.of("ACTIVE", "INACTIVE").contains(request.getStatus())) {
             throw new BusinessException("INVALID_PRODUCT_STATUS", "Course product status must be ACTIVE or INACTIVE.");
         }
         if (StringUtils.hasText(request.getCourseType()) && !VALID_COURSE_TYPES.contains(request.getCourseType())) {
             throw new BusinessException("INVALID_COURSE_TYPE", "Course type is not supported.");
         }
         if ("ONE_ON_ONE".equals(request.getTeachingMode()) && StringUtils.hasText(request.getCourseType())
                 && !"ONE_ON_ONE".equals(request.getCourseType())) {
             throw new BusinessException("COURSE_TYPE_MODE_MISMATCH", "One-on-one products must use ONE_ON_ONE course type.");
         }
         if ("GROUP".equals(request.getTeachingMode()) && "ONE_ON_ONE".equals(request.getCourseType())) {
             throw new BusinessException("COURSE_TYPE_MODE_MISMATCH", "Group products cannot use ONE_ON_ONE course type.");
         }
        if ("GROUP".equals(request.getTeachingMode())) {
            if (request.getDefaultTotalUnits() != null) {
                throw new BusinessException("GROUP product should not have total units.");
            }
            if (request.getDefaultLessonUnits() != null) {
                throw new BusinessException("GROUP product should not have lesson units.");
            }
        } else {
            // ONE_ON_ONE
            if (request.getDefaultTotalUnits() == null) {
                throw new BusinessException("Default total units is required for ONE_ON_ONE products.");
            }
            if (request.getDefaultLessonUnits() == null) {
                throw new BusinessException("Default lesson units is required for ONE_ON_ONE products.");
            }
            if (request.getDefaultTotalUnits() <= 0
                    || request.getDefaultLessonUnits() <= 0
                    || request.getDefaultLessonUnits() > request.getDefaultTotalUnits() * 2) {
                throw new BusinessException("INVALID_LESSON_UNITS",
                        "Default lesson hours must be greater than zero and cannot exceed total hours.");
            }
            if (request.getBillingType() != null || request.getStartDate() != null || request.getTotalSessions() != null
                    || StringUtils.hasText(request.getStartMonth()) || StringUtils.hasText(request.getEndMonth())) {
                throw new BusinessException("ONE_ON_ONE product should not have class billing fields.");
            }
            if (request.getSlots() != null && !request.getSlots().isEmpty()) {
                throw new BusinessException("ONE_ON_ONE product should not have schedule slots.");
            }
        }
    }

    /**
     * 校验班课计费字段：PER_SESSION 需要 startDate + totalSessions；MONTHLY 需要 startMonth + endMonth。
     */
    private void validateGroupBilling(CourseProductSaveRequest request) {
        String billingType = request.getBillingType();
        if (!Set.of("PER_SESSION", "MONTHLY").contains(billingType)) {
            throw new BusinessException("INVALID_BILLING_TYPE", "Billing type must be PER_SESSION or MONTHLY for GROUP products.");
        }
        if ("PER_SESSION".equals(billingType)) {
            if (request.getStartDate() == null) {
                throw new BusinessException("Start date is required for per-session billing.");
            }
            if (request.getTotalSessions() == null || request.getTotalSessions() < 1) {
                throw new BusinessException("Total sessions must be at least 1 for per-session billing.");
            }
            if (StringUtils.hasText(request.getStartMonth()) || StringUtils.hasText(request.getEndMonth())) {
                throw new BusinessException("Per-session billing should not have start/end month.");
            }
        } else {
            // MONTHLY
            LocalDate startMonth = parseMonth(request.getStartMonth(), "start month");
            LocalDate endMonth = parseMonth(request.getEndMonth(), "end month");
            if (endMonth.isBefore(startMonth)) {
                throw new BusinessException("End month cannot be earlier than start month.");
            }
            if (request.getStartDate() != null || request.getTotalSessions() != null) {
                throw new BusinessException("Monthly billing should not have start date or total sessions.");
            }
        }
    }

    /**
     * 解析 yyyy-MM 为当月 1 号；非法格式抛业务异常。
     */
    private LocalDate parseMonth(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("INVALID_MONTH", "Schedule " + fieldName + " is required for monthly billing.");
        }
        try {
            return java.time.YearMonth.parse(value).atDay(1);
        } catch (RuntimeException ex) {
            throw new BusinessException("INVALID_MONTH", "Schedule " + fieldName + " must use yyyy-MM format.");
        }
    }

    /**
     * 宽松解析 yyyy-MM 为当月 1 号；空值返回 null，非法格式也返回 null（仅用于字段比对）。
     */
    private LocalDate parseMonthOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return java.time.YearMonth.parse(value).atDay(1);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 将存储的月份（当月 1 号）格式化为 yyyy-MM 返回前端。
     */
    private String formatMonth(LocalDate month) {
        return month != null ? java.time.YearMonth.from(month).toString() : null;
    }

    /**
     * 计算含端点的月数；任一为空返回 null。
     */
    private Integer monthsBetweenInclusive(LocalDate startMonth, LocalDate endMonth) {
        if (startMonth == null || endMonth == null) {
            return null;
        }
        return (int) java.time.temporal.ChronoUnit.MONTHS.between(
                java.time.YearMonth.from(startMonth), java.time.YearMonth.from(endMonth)) + 1;
    }

     private void validateSlots(List<CourseProductSaveRequest.ProductScheduleSlotDTO> slots) {
         if (slots == null || slots.isEmpty()) {
             return;
         }
         Set<String> seen = new HashSet<>();
         List<SlotWindow> windows = new ArrayList<>();
         for (CourseProductSaveRequest.ProductScheduleSlotDTO slot : slots) {
             if (slot.getWeekday() == null || slot.getWeekday() < 1 || slot.getWeekday() > 7) {
                 throw new BusinessException("INVALID_SLOT_WEEKDAY", "Schedule slot weekday must be between 1 and 7.");
             }
             LocalTime startTime = parseSlotTime(slot.getStartTime(), "start time");
             LocalTime endTime = parseSlotTime(slot.getEndTime(), "end time");
             if (!endTime.isAfter(startTime)) {
                 throw new BusinessException("INVALID_SLOT_TIME_RANGE", "Schedule slot end time must be after start time.");
             }
             long minutes = java.time.Duration.between(startTime, endTime).toMinutes();
             if (minutes % 30 != 0) {
                 throw new BusinessException("INVALID_SLOT_DURATION",
                         "Schedule slot duration must use 0.5-hour increments.");
             }
             String key = slot.getWeekday() + "|" + startTime + "|" + endTime;
             if (!seen.add(key)) {
                 throw new BusinessException("DUPLICATE_SLOT", "Duplicate schedule slots are not allowed.");
             }
             boolean overlaps = windows.stream().anyMatch(existing ->
                     Objects.equals(existing.weekday(), slot.getWeekday())
                             && startTime.isBefore(existing.endTime())
                             && endTime.isAfter(existing.startTime()));
             if (overlaps) {
                 throw new BusinessException("OVERLAPPING_SLOT",
                         "Schedule slots on the same weekday cannot overlap.");
             }
             windows.add(new SlotWindow(slot.getWeekday(), startTime, endTime));
         }
     }

     private record SlotWindow(Integer weekday, LocalTime startTime, LocalTime endTime) {
     }

     private LocalTime parseSlotTime(String value, String fieldName) {
         if (!StringUtils.hasText(value)) {
             throw new BusinessException("INVALID_SLOT_TIME", "Schedule slot " + fieldName + " is required.");
         }
         try {
             return LocalTime.parse(value);
         } catch (RuntimeException ex) {
             throw new BusinessException("INVALID_SLOT_TIME", "Schedule slot " + fieldName + " must use HH:mm format.");
         }
     }

     private void protectReferencedProductCriticalFields(CourseProductEntity existing, CourseProductSaveRequest request) {
         if (!isProductReferenced(existing.getId())) {
             return;
         }
         List<String> changedFields = new ArrayList<>();
         addIfChanged(changedFields, "course type", existing.getCourseType(), normalizeCourseType(request));
         addIfChanged(changedFields, "register class", existing.getRegisterClass(), normalizeRegisterClass(request));
         addIfChanged(changedFields, "teaching format", existing.getTeachingMode(), request.getTeachingMode());
         addIfChanged(changedFields, "default total hours", existing.getDefaultTotalUnits(), request.getDefaultTotalUnits());
         addIfChanged(changedFields, "default lesson units", existing.getDefaultLessonUnits(), request.getDefaultLessonUnits());
         if (!"GROUP".equals(existing.getTeachingMode())) {
             addIfChanged(changedFields, "default classroom", existing.getDefaultClassroomId(), request.getDefaultClassroomId());
             addIfChanged(changedFields, "default teacher", existing.getDefaultTeacherId(), request.getDefaultTeacherId());
         }
         if (!changedFields.isEmpty()) {
             throw new BusinessException("PRODUCT_ALREADY_REFERENCED",
                     "This course product is already used. Create a new product to change: "
                             + String.join(", ", changedFields) + ".");
         }
     }

     private boolean isProductReferenced(Long productId) {
         return countProductReferences(productId) > 0;
     }

     private long countProductReferences(Long productId) {
         return countCoursePackageReferences(productId) + countClassGroupReferences(productId);
     }

     private long countCoursePackageReferences(Long productId) {
         Long packageCount = coursePackageMapper.selectCount(new LambdaQueryWrapper<CoursePackageEntity>()
                 .eq(CoursePackageEntity::getCourseProductId, productId)
                 .eq(CoursePackageEntity::getIsDeleted, 0));
         return packageCount == null ? 0L : packageCount;
     }

     private long countClassGroupReferences(Long productId) {
         Long classCount = classGroupMapper.selectCount(new LambdaQueryWrapper<ClassGroupEntity>()
                 .eq(ClassGroupEntity::getProductId, productId)
                 .eq(ClassGroupEntity::getIsDeleted, 0));
         return classCount == null ? 0L : classCount;
     }

     private List<String> resolveProductReferenceSources(Long productId) {
         List<String> sources = new ArrayList<>();
         if (countCoursePackageReferences(productId) > 0) {
             sources.add("Course packages");
         }
         if (countClassGroupReferences(productId) > 0) {
             sources.add("Class groups");
         }
         return sources;
     }

     private void addIfChanged(List<String> changedFields, String label, Object oldValue, Object newValue) {
         if (!Objects.equals(oldValue, newValue)) {
             changedFields.add(label);
         }
     }

     private boolean slotsChanged(Long productId, List<CourseProductSaveRequest.ProductScheduleSlotDTO> requestSlots) {
         List<CourseProductScheduleSlotEntity> existingSlots = scheduleSlotMapper.selectList(
                 new LambdaQueryWrapper<CourseProductScheduleSlotEntity>()
                         .eq(CourseProductScheduleSlotEntity::getProductId, productId)
                         .eq(CourseProductScheduleSlotEntity::getIsDeleted, 0));
         return !slotKeys(existingSlots).equals(requestSlotKeys(requestSlots));
     }

     private Set<String> slotKeys(List<CourseProductScheduleSlotEntity> slots) {
         if (slots == null || slots.isEmpty()) {
             return Collections.emptySet();
         }
         return slots.stream()
                 .map(slot -> slot.getWeekday() + "|" + slot.getStartTime() + "|" + slot.getEndTime())
                 .collect(Collectors.toCollection(TreeSet::new));
     }

     private Set<String> requestSlotKeys(List<CourseProductSaveRequest.ProductScheduleSlotDTO> slots) {
         if (slots == null) {
             return Collections.emptySet();
         }
         return slots.stream()
                 .map(slot -> slot.getWeekday() + "|" + LocalTime.parse(slot.getStartTime()) + "|" + LocalTime.parse(slot.getEndTime()))
                 .collect(Collectors.toCollection(TreeSet::new));
     }

    /**
     * 保存排课时段：先删后插。
     */
    private void saveSlots(Long productId, List<CourseProductSaveRequest.ProductScheduleSlotDTO> slots) {
        // 删除旧 slots
        scheduleSlotMapper.delete(new LambdaQueryWrapper<CourseProductScheduleSlotEntity>()
                .eq(CourseProductScheduleSlotEntity::getProductId, productId));

        if (slots == null || slots.isEmpty()) {
            return;
        }

        for (CourseProductSaveRequest.ProductScheduleSlotDTO slot : slots) {
            CourseProductScheduleSlotEntity entity = new CourseProductScheduleSlotEntity();
            entity.setProductId(productId);
            entity.setWeekday(slot.getWeekday());
            entity.setStartTime(LocalTime.parse(slot.getStartTime()));
            entity.setEndTime(LocalTime.parse(slot.getEndTime()));
            scheduleSlotMapper.insert(entity);
        }
    }

    private void ensureProductCodeUnique(Long currentId, String productCode) {
        CourseProductEntity existing = courseProductMapper.selectOne(new LambdaQueryWrapper<CourseProductEntity>()
                .eq(CourseProductEntity::getProductCode, productCode)
                .eq(CourseProductEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (existing != null && !Objects.equals(existing.getId(), currentId)) {
            throw new BusinessException("Course product code already exists.");
        }
    }

    private synchronized String generateProductCode() {
        String maxCode = courseProductMapper.selectMaxProductCodeByPrefix("PRD-");
        int nextSeq = 1;
        if (StringUtils.hasText(maxCode)) {
            try {
                String numPart = maxCode.replaceAll("[^0-9]", "");
                if (!numPart.isEmpty()) {
                    nextSeq = Integer.parseInt(numPart) + 1;
                }
            } catch (NumberFormatException e) {
                nextSeq = 1;
            }
        }
        return String.format("PRD-%04d", nextSeq);
    }

    private void ensureProductNameUnique(Long currentId, String productName) {
        CourseProductEntity existing = courseProductMapper.selectOne(new LambdaQueryWrapper<CourseProductEntity>()
                .eq(CourseProductEntity::getProductName, productName)
                .eq(CourseProductEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (existing != null && !Objects.equals(existing.getId(), currentId)) {
            throw new BusinessException("Course product name already exists.");
        }
    }

    private CourseProductEntity requireCourseProduct(Long id) {
        CourseProductEntity entity = courseProductMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("COURSE_PRODUCT_NOT_FOUND", "Course product not found.");
        }
        return entity;
    }

    private void applyCourseProduct(CourseProductEntity entity, CourseProductSaveRequest request) {
        entity.setProductCode(request.getProductCode());
        entity.setProductName(request.getProductName());
        entity.setCourseType(normalizeCourseType(request));
        entity.setRegisterClass(normalizeRegisterClass(request));
        entity.setSubjectName(request.getSubjectName());
        entity.setTeachingMode(request.getTeachingMode());
        entity.setDefaultTotalUnits(request.getDefaultTotalUnits());
        entity.setDefaultLessonUnits(request.getDefaultLessonUnits());
        boolean groupProduct = "GROUP".equals(request.getTeachingMode());
        entity.setBillingType(groupProduct ? null : request.getBillingType());
        entity.setStartDate(groupProduct ? null : request.getStartDate());
        entity.setTotalSessions(groupProduct ? null : request.getTotalSessions());
        entity.setStartMonth(groupProduct ? null : parseMonthOrNull(request.getStartMonth()));
        entity.setEndMonth(groupProduct ? null : parseMonthOrNull(request.getEndMonth()));
        // Product prices depend on grade and duration, so the legacy field is never user-configured.
        entity.setListPrice(request.getListPrice() == null ? BigDecimal.ZERO : request.getListPrice());
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : DEFAULT_STATUS);
        entity.setDefaultClassroomId(groupProduct ? null : request.getDefaultClassroomId());
        entity.setDefaultTeacherId(groupProduct ? null : request.getDefaultTeacherId());
        entity.setNotes(request.getNotes());
    }

    /**
     * 将 entity 转为 VO（单条查询，供 getCourseProduct 使用）。
     */
    private CourseProductVO toVO(CourseProductEntity entity) {
        List<CourseProductScheduleSlotEntity> slotEntities = scheduleSlotMapper.selectList(
                new LambdaQueryWrapper<CourseProductScheduleSlotEntity>()
                        .eq(CourseProductScheduleSlotEntity::getProductId, entity.getId()));
        Map<Long, ClassroomEntity> classroomMap = entity.getDefaultClassroomId() != null
                ? Map.of(entity.getDefaultClassroomId(), classroomMapper.selectById(entity.getDefaultClassroomId()))
                : Collections.emptyMap();
        Map<Long, TeacherEntity> teacherMap = entity.getDefaultTeacherId() != null
                ? Map.of(entity.getDefaultTeacherId(), teacherMapper.selectById(entity.getDefaultTeacherId()))
                : Collections.emptyMap();
        return toVO(entity, slotEntities, classroomMap, teacherMap);
    }

    /**
     * 将 entity 转为 VO（批量加载场景，传入预查询的时段和教室 Map，避免 N+1）。
     */
    private CourseProductVO toVO(CourseProductEntity entity,
                                  List<CourseProductScheduleSlotEntity> slotEntities,
                                  Map<Long, ClassroomEntity> classroomMap,
                                  Map<Long, TeacherEntity> teacherMap) {
        List<CourseProductVO.ProductScheduleSlotVO> slotVOs = slotEntities.stream()
                .map(s -> CourseProductVO.ProductScheduleSlotVO.builder()
                        .id(s.getId())
                        .weekday(s.getWeekday())
                        .startTime(s.getStartTime().toString())
                        .endTime(s.getEndTime().toString())
                        .build())
                .toList();

        String defaultClassroomName = null;
        if (entity.getDefaultClassroomId() != null) {
            ClassroomEntity classroom = classroomMap.get(entity.getDefaultClassroomId());
            if (classroom != null) {
                defaultClassroomName = classroom.getRoomName();
            }
        }

        String defaultTeacherName = null;
        if (entity.getDefaultTeacherId() != null) {
            TeacherEntity teacher = teacherMap.get(entity.getDefaultTeacherId());
            if (teacher != null) {
                defaultTeacherName = teacher.getTeacherName();
            }
        }

        return CourseProductVO.builder()
                .id(entity.getId())
                .productCode(entity.getProductCode())
                .productName(entity.getProductName())
                .courseType(entity.getCourseType())
                .registerClass(entity.getRegisterClass())
                .subjectName(entity.getSubjectName())
                .teachingMode(entity.getTeachingMode())
                .defaultTotalUnits(entity.getDefaultTotalUnits())
                .defaultLessonUnits(entity.getDefaultLessonUnits())
                .billingType(entity.getBillingType())
                .startDate(entity.getStartDate() != null ? entity.getStartDate().toString() : null)
                .totalSessions(entity.getTotalSessions())
                .startMonth(formatMonth(entity.getStartMonth()))
                .endMonth(formatMonth(entity.getEndMonth()))
                .totalMonths(monthsBetweenInclusive(entity.getStartMonth(), entity.getEndMonth()))
                .defaultClassroomId(entity.getDefaultClassroomId())
                .defaultClassroomName(defaultClassroomName)
                .defaultTeacherId(entity.getDefaultTeacherId())
                .defaultTeacherName(defaultTeacherName)
                .listPrice(entity.getListPrice())
                .status(entity.getStatus())
                .notes(entity.getNotes())
                .referenced(countProductReferences(entity.getId()) > 0)
                .referenceCount(countProductReferences(entity.getId()))
                .referenceSources(resolveProductReferenceSources(entity.getId()))
                .slots(slotVOs)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String normalizeCourseType(CourseProductSaveRequest request) {
        if (StringUtils.hasText(request.getCourseType())) {
            return request.getCourseType().trim();
        }
        return "ONE_ON_ONE".equals(request.getTeachingMode()) ? "ONE_ON_ONE" : null;
    }

    private String normalizeRegisterClass(CourseProductSaveRequest request) {
        if (StringUtils.hasText(request.getRegisterClass())) {
            return request.getRegisterClass().trim();
        }
        return "ONE_ON_ONE".equals(request.getTeachingMode()) ? "1 on 1" : request.getProductName();
    }
}
