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
                        .like(CourseProductEntity::getSubjectName, query.getKeyword()))
                .eq(StringUtils.hasText(query.getStatus()), CourseProductEntity::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getTeachingMode()), CourseProductEntity::getTeachingMode, query.getTeachingMode())
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
                  validateSlots(request.getSlots());
                CourseProductEntity entity = new CourseProductEntity();
                applyCourseProduct(entity, request);
                courseProductMapper.insert(entity);
                saveSlots(entity.getId(), request.getSlots());
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
          validateSlots(request.getSlots());
          protectReferencedProductCriticalFields(entity, request);
        applyCourseProduct(entity, request);
        courseProductMapper.updateById(entity);
        saveSlots(entity.getId(), request.getSlots());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCourseProduct(Long id) {
        requireCourseProduct(id);
        // 检查是否有课包引用了该产品（学生已报名缴费，不能删）
        if (coursePackageMapper.countByCourseProductId(id) > 0) {
            throw new BusinessException("PRODUCT_HAS_PACKAGES",
                    "Cannot delete course product: there are active course packages. "
                            + "Set the product status to INACTIVE instead.");
        }
        // 级联删除排课时段
        scheduleSlotMapper.delete(new LambdaQueryWrapper<CourseProductScheduleSlotEntity>()
                .eq(CourseProductScheduleSlotEntity::getProductId, id));
        courseProductMapper.deleteById(id);
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
        if ("GROUP".equals(request.getTeachingMode())) {
            if (request.getDefaultTotalUnits() != null) {
                throw new BusinessException("GROUP product should not have total units.");
            }
            if (request.getDefaultLessonUnits() != null) {
                throw new BusinessException("GROUP product should not have lesson units.");
            }
            if (request.getExpiryDurationDays() == null) {
                throw new BusinessException("Expiry duration days is required for GROUP products.");
            }
            if (request.getSlots() == null || request.getSlots().isEmpty()) {
                throw new BusinessException("At least one schedule slot is required for GROUP products.");
            }
        } else {
            // ONE_ON_ONE
            if (request.getDefaultTotalUnits() == null) {
                throw new BusinessException("Default total units is required for ONE_ON_ONE products.");
            }
            if (request.getDefaultLessonUnits() == null) {
                throw new BusinessException("Default lesson units is required for ONE_ON_ONE products.");
            }
            if (request.getExpiryDurationDays() != null) {
                throw new BusinessException("ONE_ON_ONE product should not have expiry duration days.");
            }
            if (request.getSlots() != null && !request.getSlots().isEmpty()) {
                throw new BusinessException("ONE_ON_ONE product should not have schedule slots.");
            }
        }
    }

     private void validateSlots(List<CourseProductSaveRequest.ProductScheduleSlotDTO> slots) {
         if (slots == null || slots.isEmpty()) {
             return;
         }
         Set<String> seen = new HashSet<>();
         for (CourseProductSaveRequest.ProductScheduleSlotDTO slot : slots) {
             if (slot.getWeekday() == null || slot.getWeekday() < 1 || slot.getWeekday() > 7) {
                 throw new BusinessException("INVALID_SLOT_WEEKDAY", "Schedule slot weekday must be between 1 and 7.");
             }
             LocalTime startTime = parseSlotTime(slot.getStartTime(), "start time");
             LocalTime endTime = parseSlotTime(slot.getEndTime(), "end time");
             if (!endTime.isAfter(startTime)) {
                 throw new BusinessException("INVALID_SLOT_TIME_RANGE", "Schedule slot end time must be after start time.");
             }
             String key = slot.getWeekday() + "|" + startTime + "|" + endTime;
             if (!seen.add(key)) {
                 throw new BusinessException("DUPLICATE_SLOT", "Duplicate schedule slots are not allowed.");
             }
         }
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
         addIfChanged(changedFields, "teaching format", existing.getTeachingMode(), request.getTeachingMode());
         addIfChanged(changedFields, "default total hours", existing.getDefaultTotalUnits(), request.getDefaultTotalUnits());
         addIfChanged(changedFields, "default lesson units", existing.getDefaultLessonUnits(), request.getDefaultLessonUnits());
         addIfChanged(changedFields, "expiry duration", existing.getExpiryDurationDays(), request.getExpiryDurationDays());
         addIfChanged(changedFields, "default classroom", existing.getDefaultClassroomId(), request.getDefaultClassroomId());
         addIfChanged(changedFields, "default teacher", existing.getDefaultTeacherId(), request.getDefaultTeacherId());
         if ("GROUP".equals(existing.getTeachingMode()) && slotsChanged(existing.getId(), request.getSlots())) {
             changedFields.add("weekly schedule");
         }
         if (!changedFields.isEmpty()) {
             throw new BusinessException("PRODUCT_ALREADY_REFERENCED",
                     "This course product is already used. Create a new product to change: "
                             + String.join(", ", changedFields) + ".");
         }
     }

     private boolean isProductReferenced(Long productId) {
         Long packageCount = coursePackageMapper.selectCount(new LambdaQueryWrapper<CoursePackageEntity>()
                 .eq(CoursePackageEntity::getCourseProductId, productId)
                 .eq(CoursePackageEntity::getIsDeleted, 0));
         Long classCount = classGroupMapper.selectCount(new LambdaQueryWrapper<ClassGroupEntity>()
                 .eq(ClassGroupEntity::getProductId, productId)
                 .eq(ClassGroupEntity::getIsDeleted, 0));
         return packageCount > 0 || classCount > 0;
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
        entity.setSubjectName(request.getSubjectName());
        entity.setTeachingMode(request.getTeachingMode());
        entity.setDefaultTotalUnits(request.getDefaultTotalUnits());
        entity.setDefaultLessonUnits(request.getDefaultLessonUnits());
        entity.setExpiryDurationDays(request.getExpiryDurationDays());
        entity.setListPrice(request.getListPrice());
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : DEFAULT_STATUS);
        entity.setDefaultClassroomId(request.getDefaultClassroomId());
        entity.setDefaultTeacherId(request.getDefaultTeacherId());
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
                .subjectName(entity.getSubjectName())
                .teachingMode(entity.getTeachingMode())
                .defaultTotalUnits(entity.getDefaultTotalUnits())
                .defaultLessonUnits(entity.getDefaultLessonUnits())
                .expiryDurationDays(entity.getExpiryDurationDays())
                .defaultClassroomId(entity.getDefaultClassroomId())
                .defaultClassroomName(defaultClassroomName)
                .defaultTeacherId(entity.getDefaultTeacherId())
                .defaultTeacherName(defaultTeacherName)
                .listPrice(entity.getListPrice())
                .status(entity.getStatus())
                .notes(entity.getNotes())
                .slots(slotVOs)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
