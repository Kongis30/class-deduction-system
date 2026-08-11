package com.classdeduction.system.modules.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.base.dto.CourseProductPageQuery;
import com.classdeduction.system.modules.base.dto.CourseProductSaveRequest;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.service.CourseProductService;
import com.classdeduction.system.modules.base.vo.CourseProductVO;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.*;

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

        return PageResponse.of(records.stream()
                .map(this::toVO)
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
        entity.setBillingType(null);
        entity.setStartDate(null);
        entity.setTotalSessions(null);
        entity.setStartMonth(null);
        entity.setEndMonth(null);
        entity.setListPrice(null);
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : DEFAULT_STATUS);
        entity.setDefaultClassroomId(null);
        entity.setDefaultTeacherId(null);
        entity.setNotes(request.getNotes());
    }

    /**
     * 将 entity 转为 VO（单条查询，供 getCourseProduct 使用）。
     */
    private CourseProductVO toVO(CourseProductEntity entity) {
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
                .status(entity.getStatus())
                .notes(entity.getNotes())
                .referenced(countProductReferences(entity.getId()) > 0)
                .referenceCount(countProductReferences(entity.getId()))
                .referenceSources(resolveProductReferenceSources(entity.getId()))
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
