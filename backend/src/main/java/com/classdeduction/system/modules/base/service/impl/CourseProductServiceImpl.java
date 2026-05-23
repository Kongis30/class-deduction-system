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
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductScheduleSlotMapper;
import com.classdeduction.system.modules.base.service.CourseProductService;
import com.classdeduction.system.modules.base.vo.CourseProductVO;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    private final CourseProductMapper courseProductMapper;
    private final CourseProductScheduleSlotMapper scheduleSlotMapper;
    private final ClassroomMapper classroomMapper;
    private final CoursePackageMapper coursePackageMapper;

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

        return PageResponse.of(records.stream()
                .map(entity -> toVO(entity, slotsByProduct.getOrDefault(entity.getId(), List.of()), classroomMap))
                .toList(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public CourseProductVO getCourseProduct(Long id) {
        return toVO(requireCourseProduct(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCourseProduct(CourseProductSaveRequest request) {
        ensureProductCodeUnique(null, request.getProductCode());
        ensureProductNameUnique(null, request.getProductName());
        validateByTeachingMode(request);
        CourseProductEntity entity = new CourseProductEntity();
        applyCourseProduct(entity, request);
        courseProductMapper.insert(entity);
        saveSlots(entity.getId(), request.getSlots());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCourseProduct(Long id, CourseProductSaveRequest request) {
        CourseProductEntity entity = requireCourseProduct(id);
        ensureProductCodeUnique(id, request.getProductCode());
        ensureProductNameUnique(id, request.getProductName());
        validateByTeachingMode(request);
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
        return toVO(entity, slotEntities, classroomMap);
    }

    /**
     * 将 entity 转为 VO（批量加载场景，传入预查询的时段和教室 Map，避免 N+1）。
     */
    private CourseProductVO toVO(CourseProductEntity entity,
                                  List<CourseProductScheduleSlotEntity> slotEntities,
                                  Map<Long, ClassroomEntity> classroomMap) {
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
                .listPrice(entity.getListPrice())
                .status(entity.getStatus())
                .notes(entity.getNotes())
                .slots(slotVOs)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
