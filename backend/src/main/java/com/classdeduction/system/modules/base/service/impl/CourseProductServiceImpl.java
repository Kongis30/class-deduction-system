package com.classdeduction.system.modules.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.base.dto.CourseProductPageQuery;
import com.classdeduction.system.modules.base.dto.CourseProductSaveRequest;
import com.classdeduction.system.modules.base.entity.CoursePriceOptionEntity;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.mapper.CoursePriceOptionMapper;
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
import java.util.function.Function;
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
    private final CoursePackageMapper coursePackageMapper;
    private final ClassGroupMapper classGroupMapper;
    private final com.classdeduction.system.modules.schedule.service.ClassGroupService classGroupService;
    private final PlatformTransactionManager transactionManager;
    private final CoursePriceOptionMapper coursePriceOptionMapper;

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
                .orderByAsc(CourseProductEntity::getSortOrder)
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
    public List<CourseProductVO> listCourseProducts() {
        return courseProductMapper.selectList(new LambdaQueryWrapper<CourseProductEntity>()
                        .orderByAsc(CourseProductEntity::getSortOrder)
                        .orderByDesc(CourseProductEntity::getCreatedAt)
                        .orderByDesc(CourseProductEntity::getId))
                .stream()
                .map(this::toTabVO)
                .toList();
    }

    /**
     * Tab 列表轻量映射：跳过逐条引用计数查询，避免全量产品时的 N+1 查询。
     */
    private CourseProductVO toTabVO(CourseProductEntity entity) {
        return CourseProductVO.builder()
                .id(entity.getId())
                .productCode(entity.getProductCode())
                .productName(entity.getProductName())
                .courseType(entity.getCourseType())
                .registerClass(entity.getRegisterClass())
                .subjectName(entity.getSubjectName())
                .teachingMode(entity.getTeachingMode())
                .sortOrder(entity.getSortOrder() == null ? 99 : entity.getSortOrder())
                .status(entity.getStatus())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
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
        applyCourseProduct(entity, request);
        courseProductMapper.updateById(entity);
        syncGroupOptionCourseType(entity);
    }

    /**
     * 编辑课程产品后，把本次写入的 course_type 级联刷到其关联的「班课」价格选项，
     * 让选项里的描述性 program 标签与产品保持一致（选项创建时是对产品 course_type 的快照）。
     * 仅同步 derived 的 course_type，不改其它字段；已报名订单报文（enrollment_order_item.course_type）是
     * 报名时自身的快照，不受影响。只能对非 ONE_ON_ONE 选项生效，避免覆盖 1v1 选项。
     */
    private void syncGroupOptionCourseType(CourseProductEntity entity) {
        String syncedCourseType = "ONE_ON_ONE".equals(entity.getTeachingMode())
                ? "ONE_ON_ONE"
                : StringUtils.hasText(entity.getCourseType()) ? entity.getCourseType() : "GROUP";
        coursePriceOptionMapper.update(null, new LambdaUpdateWrapper<CoursePriceOptionEntity>()
                .eq(CoursePriceOptionEntity::getCourseProductId, entity.getId())
                .ne(CoursePriceOptionEntity::getCourseType, "ONE_ON_ONE")
                .set(CoursePriceOptionEntity::getCourseType, syncedCourseType));
    }

    /**
     * 保存 Data Summary Product Tab 的系统级共享顺序。
     * 校验提交 ID 全部有效且不重复；客户端列表过期遗漏的产品按现有顺序追加到末尾，
     * 避免并发新建的产品被旧浏览器拖拽操作挤出 Tab 列表。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reorderCourseProducts(List<Long> requestedProductIds, Long operatorId) {
        List<CourseProductEntity> existingProducts = courseProductMapper.selectList(
                new LambdaQueryWrapper<CourseProductEntity>()
                        .orderByAsc(CourseProductEntity::getSortOrder)
                        .orderByDesc(CourseProductEntity::getCreatedAt)
                        .orderByDesc(CourseProductEntity::getId));

        List<Long> submittedIds = requestedProductIds == null ? List.of() : requestedProductIds;
        if (existingProducts.isEmpty()) {
            if (!submittedIds.isEmpty()) {
                throw new BusinessException("INVALID_PRODUCT_ORDER", "Product order contains unknown products.");
            }
            return;
        }
        if (submittedIds.isEmpty()) {
            throw new BusinessException("INVALID_PRODUCT_ORDER", "Product order cannot be empty while products exist.");
        }

        Map<Long, CourseProductEntity> existingById = existingProducts.stream()
                .collect(Collectors.toMap(CourseProductEntity::getId, Function.identity()));
        Set<Long> submittedSet = new LinkedHashSet<>();
        for (Long productId : submittedIds) {
            if (productId == null || !submittedSet.add(productId) || !existingById.containsKey(productId)) {
                throw new BusinessException("INVALID_PRODUCT_ORDER", "Product order contains duplicate or unknown products.");
            }
        }

        List<CourseProductEntity> orderedProducts = new ArrayList<>(existingProducts.size());
        submittedIds.forEach(productId -> orderedProducts.add(existingById.get(productId)));
        existingProducts.stream()
                .filter(product -> !submittedSet.contains(product.getId()))
                .forEach(orderedProducts::add);

        for (int index = 0; index < orderedProducts.size(); index++) {
            CourseProductEntity product = orderedProducts.get(index);
            int nextOrder = index + 1;
            if (Objects.equals(product.getSortOrder(), nextOrder)) {
                continue;
            }
            product.setSortOrder(nextOrder);
            product.setUpdatedBy(operatorId);
            courseProductMapper.updateById(product);
        }
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
        releaseProductCode(product);
        courseProductMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void forceDeleteCourseProduct(Long id, String reason) {
        CourseProductEntity product = requireCourseProduct(id);

        // 级联软删除关联班级：取消未来课次、退出报名、软删班级本身。历史消课与流水保留供审计。
        List<ClassGroupEntity> classes = classGroupMapper.selectList(new LambdaQueryWrapper<ClassGroupEntity>()
                .eq(ClassGroupEntity::getProductId, id)
                .eq(ClassGroupEntity::getIsDeleted, 0));
        for (ClassGroupEntity clz : classes) {
            classGroupService.deleteClassGroup(clz.getId());
        }

        releaseProductCode(product);
        courseProductMapper.deleteById(id);
    }

    /**
     * DB 上 uk_course_product_code 覆盖软删行，若不改写，删课后再建同号会撞唯一键报错。
     */
    private void releaseProductCode(CourseProductEntity entity) {
        if (!StringUtils.hasText(entity.getProductCode())) {
            return;
        }
        // product_code is varchar(32); keep the tombstone within that limit.
        String suffix = "_DEL_" + entity.getId();
        String base = entity.getProductCode();
        int maxBaseLength = Math.max(0, 32 - suffix.length());
        if (base.length() > maxBaseLength) {
            base = base.substring(0, maxBaseLength);
        }
        String releasedCode = base + suffix;
        CourseProductEntity patch = new CourseProductEntity();
        patch.setId(entity.getId());
        patch.setProductCode(releasedCode);
        courseProductMapper.updateById(patch);
        entity.setProductCode(releasedCode);
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
        entity.setSortOrder(request.getSortOrder() == null ? 99 : request.getSortOrder());
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
                .sortOrder(entity.getSortOrder() == null ? 99 : entity.getSortOrder())
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
