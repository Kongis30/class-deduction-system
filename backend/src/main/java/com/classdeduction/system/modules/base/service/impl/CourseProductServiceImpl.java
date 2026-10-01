package com.classdeduction.system.modules.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.base.dto.CourseProductPageQuery;
import com.classdeduction.system.modules.base.dto.CourseProductSaveRequest;
import com.classdeduction.system.modules.base.entity.CoursePriceOptionEntity;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.GroupCourseProjectEntity;
import com.classdeduction.system.modules.base.entity.GroupRegistrationPlanEntity;
import com.classdeduction.system.modules.base.mapper.CoursePriceOptionMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.GroupCourseProjectMapper;
import com.classdeduction.system.modules.base.mapper.GroupRegistrationPlanMapper;
import com.classdeduction.system.modules.base.service.CourseProductService;
import com.classdeduction.system.modules.base.vo.CourseProductVO;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.entity.ClassHolidayEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassHolidayMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.LocalDate;

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
    private final EnrollmentOrderItemMapper enrollmentOrderItemMapper;
    private final ClassGroupMapper classGroupMapper;
    private final com.classdeduction.system.modules.schedule.service.ClassGroupService classGroupService;
    private final PlatformTransactionManager transactionManager;
    private final CoursePriceOptionMapper coursePriceOptionMapper;
    private final ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;
    private final GroupCourseProjectMapper groupCourseProjectMapper;
    private final GroupRegistrationPlanMapper groupRegistrationPlanMapper;
    private final ClassHolidayMapper classHolidayMapper;

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

    /**
     * Product 列表的复制入口。新建独立 ID 的产品、班级、价格选项和固定周课表；
     * 新产品先停用，避免配置尚未核对时进入报名，事务保证失败时整组回滚。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long copyCourseProduct(Long sourceId, String newProductName, int dateShiftYears) {
        synchronized (createProductLock) {
            CourseProductEntity source = requireCourseProduct(sourceId);
            if (!StringUtils.hasText(newProductName)) {
                throw new BusinessException("COURSE_PRODUCT_NAME_REQUIRED", "Course name is required.");
            }
            String normalizedName = newProductName.trim();
            ensureProductNameUnique(null, normalizedName);

            CourseProductEntity copy = new CourseProductEntity();
            org.springframework.beans.BeanUtils.copyProperties(source, copy);
            clearAuditFields(copy);
            copy.setId(null);
            copy.setProductCode(generateProductCode());
            ensureProductCodeUnique(null, copy.getProductCode());
            copy.setProductName(normalizedName);
            copy.setRegisterClass(normalizedName);
            copy.setStatus("INACTIVE");
            copy.setStartDate(shiftDate(copy.getStartDate(), dateShiftYears));
            copy.setStartMonth(shiftDate(copy.getStartMonth(), dateShiftYears));
            copy.setEndMonth(shiftDate(copy.getEndMonth(), dateShiftYears));
            courseProductMapper.insert(copy);

            if ("GROUP".equals(source.getTeachingMode())) {
                copyV2GroupSetup(sourceId, copy.getId(), dateShiftYears);
            } else {
                Map<Long, Long> classIdMap = copyProductClasses(sourceId, copy.getId(), dateShiftYears, Map.of(), false);
                copyProductOptions(sourceId, copy.getId(), copy.getProductCode(), normalizedName, classIdMap);
            }
            return copy.getId();
        }
    }

    /** 班课复制只复制项目、报名方案和实际班级模板；不复制学生业务或教师、教室分配。 */
    private void copyV2GroupSetup(Long sourceProductId, Long targetProductId, int dateShiftYears) {
        List<ClassGroupEntity> sourceClasses = classGroupMapper.selectList(new LambdaQueryWrapper<ClassGroupEntity>()
                .eq(ClassGroupEntity::getProductId, sourceProductId)
                .eq(ClassGroupEntity::getIsDeleted, 0));
        List<GroupCourseProjectEntity> projects = groupCourseProjectMapper.selectList(
                new LambdaQueryWrapper<GroupCourseProjectEntity>()
                        .eq(GroupCourseProjectEntity::getCourseProductId, sourceProductId)
                        .eq(GroupCourseProjectEntity::getIsDeleted, 0)
                        .orderByAsc(GroupCourseProjectEntity::getDisplayOrder)
                        .orderByAsc(GroupCourseProjectEntity::getId));
        if (!sourceClasses.isEmpty() && (projects.isEmpty()
                || sourceClasses.stream().anyMatch(row -> row.getGroupProjectId() == null))) {
            throw new BusinessException("COURSE_PRODUCT_COPY_GROUP_SETUP_INCOMPLETE",
                    "This group product must be migrated to the project model before it can be copied.");
        }
        Map<Long, Long> projectIdMap = new HashMap<>();
        for (GroupCourseProjectEntity sourceProject : projects) {
            GroupCourseProjectEntity targetProject = new GroupCourseProjectEntity();
            org.springframework.beans.BeanUtils.copyProperties(sourceProject, targetProject);
            clearAuditFields(targetProject);
            targetProject.setId(null);
            targetProject.setCourseProductId(targetProductId);
            groupCourseProjectMapper.insert(targetProject);
            projectIdMap.put(sourceProject.getId(), targetProject.getId());
            copyProjectPlans(sourceProject.getId(), targetProject.getId(), dateShiftYears);
            copyProjectHolidays(sourceProject.getId(), targetProject.getId(), dateShiftYears);
        }

        copyProductClasses(sourceProductId, targetProductId, dateShiftYears, projectIdMap, true);
    }

    private void copyProjectPlans(Long sourceProjectId, Long targetProjectId, int dateShiftYears) {
        List<GroupRegistrationPlanEntity> plans = groupRegistrationPlanMapper.selectList(
                new LambdaQueryWrapper<GroupRegistrationPlanEntity>()
                        .eq(GroupRegistrationPlanEntity::getProjectId, sourceProjectId)
                        .eq(GroupRegistrationPlanEntity::getIsDeleted, 0)
                        .orderByAsc(GroupRegistrationPlanEntity::getDisplayOrder)
                        .orderByAsc(GroupRegistrationPlanEntity::getId));
        for (GroupRegistrationPlanEntity sourcePlan : plans) {
            GroupRegistrationPlanEntity targetPlan = new GroupRegistrationPlanEntity();
            org.springframework.beans.BeanUtils.copyProperties(sourcePlan, targetPlan);
            clearAuditFields(targetPlan);
            targetPlan.setId(null);
            targetPlan.setProjectId(targetProjectId);
            targetPlan.setCoverageStartDate(shiftDate(sourcePlan.getCoverageStartDate(), dateShiftYears));
            targetPlan.setCoverageEndDate(shiftDate(sourcePlan.getCoverageEndDate(), dateShiftYears));
            groupRegistrationPlanMapper.insert(targetPlan);
        }
    }

    private void copyProjectHolidays(Long sourceProjectId, Long targetProjectId, int dateShiftYears) {
        List<ClassHolidayEntity> holidays = classHolidayMapper.selectList(
                new LambdaQueryWrapper<ClassHolidayEntity>()
                        .eq(ClassHolidayEntity::getGroupProjectId, sourceProjectId)
                        .eq(ClassHolidayEntity::getIsDeleted, 0)
                        .orderByAsc(ClassHolidayEntity::getStartDate));
        for (ClassHolidayEntity sourceHoliday : holidays) {
            ClassHolidayEntity targetHoliday = new ClassHolidayEntity();
            org.springframework.beans.BeanUtils.copyProperties(sourceHoliday, targetHoliday);
            clearAuditFields(targetHoliday);
            targetHoliday.setId(null);
            targetHoliday.setGroupProjectId(targetProjectId);
            targetHoliday.setStartDate(shiftDate(sourceHoliday.getStartDate(), dateShiftYears));
            targetHoliday.setEndDate(shiftDate(sourceHoliday.getEndDate(), dateShiftYears));
            classHolidayMapper.insert(targetHoliday);
        }
    }

    /** 班級可按舊關聯 project ID 重映射；複製班課模板時不帶老師和教室。 */
    private Map<Long, Long> copyProductClasses(Long sourceProductId, Long targetProductId, int dateShiftYears,
                                                Map<Long, Long> projectIdMap, boolean clearAssignments) {
        List<ClassGroupEntity> sourceClasses = classGroupMapper.selectList(
                new LambdaQueryWrapper<ClassGroupEntity>()
                        .eq(ClassGroupEntity::getProductId, sourceProductId)
                        .eq(ClassGroupEntity::getIsDeleted, 0)
                        .orderByAsc(ClassGroupEntity::getId));
        Map<Long, Long> classIdMap = new HashMap<>();
        for (ClassGroupEntity sourceClass : sourceClasses) {
            ClassGroupEntity targetClass = new ClassGroupEntity();
            org.springframework.beans.BeanUtils.copyProperties(sourceClass, targetClass);
            clearAuditFields(targetClass);
            targetClass.setId(null);
            targetClass.setProductId(targetProductId);
            if (sourceClass.getGroupProjectId() != null) {
                Long targetProjectId = projectIdMap.get(sourceClass.getGroupProjectId());
                if (targetProjectId == null) {
                    throw new BusinessException("COURSE_PRODUCT_COPY_PROJECT_MISSING",
                            "A teaching project is missing; the course was not copied.");
                }
                targetClass.setGroupProjectId(targetProjectId);
            }
            if (clearAssignments) {
                targetClass.setTeacherId(null);
                targetClass.setClassroomId(null);
            }
            targetClass.setStartDate(shiftDate(targetClass.getStartDate(), dateShiftYears));
            targetClass.setEndDate(shiftDate(targetClass.getEndDate(), dateShiftYears));
            targetClass.setScheduleBaseEndDate(shiftDate(targetClass.getScheduleBaseEndDate(), dateShiftYears));
            targetClass.setStartMonth(shiftDate(targetClass.getStartMonth(), dateShiftYears));
            targetClass.setEndMonth(shiftDate(targetClass.getEndMonth(), dateShiftYears));
            classGroupMapper.insert(targetClass);
            classIdMap.put(sourceClass.getId(), targetClass.getId());

            List<ClassGroupScheduleSlotEntity> sourceSlots = classGroupScheduleSlotMapper.selectList(
                    new LambdaQueryWrapper<ClassGroupScheduleSlotEntity>()
                            .eq(ClassGroupScheduleSlotEntity::getClassGroupId, sourceClass.getId())
                            .eq(ClassGroupScheduleSlotEntity::getIsDeleted, 0)
                            .orderByAsc(ClassGroupScheduleSlotEntity::getWeekday)
                            .orderByAsc(ClassGroupScheduleSlotEntity::getStartTime));
            for (ClassGroupScheduleSlotEntity sourceSlot : sourceSlots) {
                ClassGroupScheduleSlotEntity targetSlot = new ClassGroupScheduleSlotEntity();
                org.springframework.beans.BeanUtils.copyProperties(sourceSlot, targetSlot);
                clearAuditFields(targetSlot);
                targetSlot.setId(null);
                targetSlot.setClassGroupId(targetClass.getId());
                if (clearAssignments) {
                    targetSlot.setTeacherId(null);
                    targetSlot.setClassroomId(null);
                    targetSlot.setAlternateTeacherId(null);
                    targetSlot.setAlternateClassroomId(null);
                }
                targetSlot.setRotationAnchorDate(shiftDate(sourceSlot.getRotationAnchorDate(), dateShiftYears));
                classGroupScheduleSlotMapper.insert(targetSlot);
            }
        }
        return classIdMap;
    }

    private LocalDate shiftDate(LocalDate date, int years) {
        return date == null || years == 0 ? date : date.plusYears(years);
    }

    /** 复制报名价格选项并重映射到新班级；不会复制任何学生订单或付款。 */
    private void copyProductOptions(Long sourceProductId, Long targetProductId, String targetProductCode,
                                    String targetProductName, Map<Long, Long> classIdMap) {
        List<CoursePriceOptionEntity> sourceOptions = coursePriceOptionMapper.selectList(
                new LambdaQueryWrapper<CoursePriceOptionEntity>()
                        .eq(CoursePriceOptionEntity::getCourseProductId, sourceProductId)
                        .eq(CoursePriceOptionEntity::getIsDeleted, 0)
                        .orderByAsc(CoursePriceOptionEntity::getSortOrder)
                        .orderByAsc(CoursePriceOptionEntity::getId));
        int sequence = 1;
        for (CoursePriceOptionEntity sourceOption : sourceOptions) {
            CoursePriceOptionEntity targetOption = new CoursePriceOptionEntity();
            org.springframework.beans.BeanUtils.copyProperties(sourceOption, targetOption);
            clearAuditFields(targetOption);
            targetOption.setId(null);
            targetOption.setCourseProductId(targetProductId);
            targetOption.setRegisterClass(targetProductName);
            targetOption.setOptionCode(String.format(Locale.ROOT, "%s_OPT_%04d", targetProductCode, sequence++));
            if (sourceOption.getClassId() != null) {
                Long targetClassId = classIdMap.get(sourceOption.getClassId());
                if (targetClassId == null) {
                    throw new BusinessException("COURSE_PRODUCT_COPY_CLASS_MISSING",
                            "A linked class is missing; the course was not copied.");
                }
                targetOption.setClassId(targetClassId);
            }
            coursePriceOptionMapper.insert(targetOption);
        }
    }

    /** 复制对象重新生成审计信息，避免沿用源记录的创建和删除状态。 */
    private void clearAuditFields(com.classdeduction.system.common.model.BaseEntity entity) {
        entity.setCreatedAt(null);
        entity.setCreatedBy(null);
        entity.setUpdatedAt(null);
        entity.setUpdatedBy(null);
        entity.setIsDeleted(0);
        entity.setDeletedAt(null);
        entity.setDeletedBy(null);
        entity.setVersion(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCourseProduct(Long id, CourseProductSaveRequest request) {
        CourseProductEntity entity = courseProductMapper.selectByIdForUpdate(id);
        if (entity == null) {
            throw new BusinessException("COURSE_PRODUCT_NOT_FOUND", "Course product not found.");
        }
        if (!StringUtils.hasText(request.getProductCode())) {
            request.setProductCode(entity.getProductCode());
        }
        ensureProductCodeUnique(id, request.getProductCode());
        ensureProductNameUnique(id, request.getProductName());
        validateByTeachingMode(request);
        if (!Objects.equals(entity.getTeachingMode(), request.getTeachingMode())
                && hasAnyTeachingModelReferences(id)) {
            throw new BusinessException("PRODUCT_TEACHING_MODE_IN_USE",
                    "Teaching mode cannot be changed after prices, classes, packages, projects, or enrollment history have been created.");
        }
        applyCourseProduct(entity, request);
        courseProductMapper.updateById(entity);
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
            // V2项目是班课目录和历史订单的外键目标；即使还没有实际班级，也不能删除产品行。
            // 想停止招生改为 INACTIVE，想退役班级请走"删除班级"流程。
            if (hasAnyClassGroupReference(id) || hasAnyGroupProjectReference(id)) {
                throw new BusinessException("PRODUCT_HAS_CLASSES",
                        "Cannot delete course product: it has teaching-project setup or class history. "
                                + "Set the product status to INACTIVE instead.");
            }
        }
        releaseProductCode(product);
        courseProductMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void forceDeleteCourseProduct(Long id, String reason) {
        CourseProductEntity product = requireCourseProduct(id);

        // 保留项目和方案行供历史收据、退款及 Data Summary 查询，只停止后续招生。
        List<GroupCourseProjectEntity> projects = groupCourseProjectMapper.selectList(
                new LambdaQueryWrapper<GroupCourseProjectEntity>()
                        .eq(GroupCourseProjectEntity::getCourseProductId, id));
        for (GroupCourseProjectEntity project : projects) {
            project.setStatus("INACTIVE");
            groupCourseProjectMapper.updateById(project);
            List<GroupRegistrationPlanEntity> plans = groupRegistrationPlanMapper.selectList(
                    new LambdaQueryWrapper<GroupRegistrationPlanEntity>()
                            .eq(GroupRegistrationPlanEntity::getProjectId, project.getId()));
            for (GroupRegistrationPlanEntity plan : plans) {
                plan.setStatus("INACTIVE");
                groupRegistrationPlanMapper.updateById(plan);
            }
        }

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

    /** 项目外键和订单历史依赖产品行，即使项目暂时没有实际班级也要阻止删除。 */
    private boolean hasAnyGroupProjectReference(Long productId) {
        return groupCourseProjectMapper.selectCount(new LambdaQueryWrapper<GroupCourseProjectEntity>()
                .eq(GroupCourseProjectEntity::getCourseProductId, productId)) > 0;
    }

    /** 防止产品切换教学模式后，价格、班级、项目或历史报名落入另一套业务链路。 */
    private boolean hasAnyTeachingModelReferences(Long productId) {
        return hasPositiveCount(coursePackageMapper.selectCount(new LambdaQueryWrapper<CoursePackageEntity>()
                        .eq(CoursePackageEntity::getCourseProductId, productId)))
                || hasPositiveCount(classGroupMapper.selectCount(new LambdaQueryWrapper<ClassGroupEntity>()
                        .eq(ClassGroupEntity::getProductId, productId)))
                || hasPositiveCount(groupCourseProjectMapper.selectCount(new LambdaQueryWrapper<GroupCourseProjectEntity>()
                        .eq(GroupCourseProjectEntity::getCourseProductId, productId)))
                || hasPositiveCount(coursePriceOptionMapper.selectCount(new LambdaQueryWrapper<CoursePriceOptionEntity>()
                        .eq(CoursePriceOptionEntity::getCourseProductId, productId)))
                || hasPositiveCount(enrollmentOrderItemMapper.countProductHistoryReferences(productId));
    }

    private boolean hasPositiveCount(Long count) {
        return count != null && count > 0;
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
