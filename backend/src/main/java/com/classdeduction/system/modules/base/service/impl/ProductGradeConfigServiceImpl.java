package com.classdeduction.system.modules.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.base.dto.ProductGradeConfigSaveRequest;
import com.classdeduction.system.modules.base.entity.CoursePriceOptionEntity;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.ProductGradeConfigEntity;
import com.classdeduction.system.modules.base.mapper.CoursePriceOptionMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.ProductGradeConfigMapper;
import com.classdeduction.system.modules.base.service.ProductGradeConfigService;
import com.classdeduction.system.modules.base.vo.ProductGradeConfigVO;
import com.classdeduction.system.modules.finance.entity.TeacherPayRateEntity;
import com.classdeduction.system.modules.finance.mapper.TeacherPayRateMapper;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.ClassHolidayEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassHolidayMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * grade 配置服务实现。
 *
 * <p>grade_band 是散落在 course_price_option / class_group / teacher_pay_rate 中的
 * 自由文本。本服务维护产品级 grade 元数据，并在同一事务内级联更新改名和共享日期，
 * 保证价格行、班级、假期和老师费率作用域同步，历史订单快照不动。</p>
 */
@Service
@RequiredArgsConstructor
public class ProductGradeConfigServiceImpl implements ProductGradeConfigService {

    private static final int DEFAULT_ORDER = 99;
    /** grade_label varchar(64)；tombstone 后缀需保证同产品下软删行不撞 uk_product_grade_label。 */
    private static final String TOMBSTONE_SUFFIX = "_DEL_";

    private final ProductGradeConfigMapper productGradeConfigMapper;
    private final CourseProductMapper courseProductMapper;
    private final CoursePriceOptionMapper coursePriceOptionMapper;
    private final ClassGroupMapper classGroupMapper;
    private final ClassHolidayMapper classHolidayMapper;
    private final TeacherPayRateMapper teacherPayRateMapper;

    @Override
    public List<ProductGradeConfigVO> listGradeConfigs(Long productId) {
        requireProduct(productId);
        return buildGradeConfigs(productId);
    }

    /**
     * 合并配置表与价格行自动发现的 grade，按 displayOrder → 字典序排序。
     */
    private List<ProductGradeConfigVO> buildGradeConfigs(Long productId) {
        Map<String, ProductGradeConfigEntity> configured = new LinkedHashMap<>();
        for (ProductGradeConfigEntity entity : productGradeConfigMapper.selectList(
                new LambdaQueryWrapper<ProductGradeConfigEntity>()
                        .eq(ProductGradeConfigEntity::getCourseProductId, productId))) {
            configured.put(entity.getGradeLabel(), entity);
        }

        // 自动发现：价格行里实际存在、但尚未入库配置的 grade（含仅存在于配置表的残留项）。
        Set<String> knownLabels = new LinkedHashSet<>(configured.keySet());
        Map<String, ClassPeriod> legacyPeriods = new LinkedHashMap<>();
        for (ClassGroupEntity classGroup : classGroupMapper.selectList(
                new LambdaQueryWrapper<ClassGroupEntity>()
                        .eq(ClassGroupEntity::getProductId, productId)
                        .eq(ClassGroupEntity::getIsDeleted, 0)
                        .orderByAsc(ClassGroupEntity::getId))) {
            if (!StringUtils.hasText(classGroup.getGradeBand())) {
                continue;
            }
            String key = classGroup.getGradeBand().trim().toLowerCase();
            ClassPeriod previous = legacyPeriods.get(key);
            LocalDate startDate = previous != null && previous.startDate() != null
                    ? previous.startDate() : classGroup.getStartDate();
            // endDate 可能已因假期顺延；scheduleBaseEndDate 才是管理员最初设定的结束日期。
            LocalDate endDate = previous != null && previous.endDate() != null
                    ? previous.endDate()
                    : classGroup.getScheduleBaseEndDate() == null
                            ? classGroup.getEndDate() : classGroup.getScheduleBaseEndDate();
            legacyPeriods.put(key, new ClassPeriod(startDate, endDate));
            knownLabels.add(classGroup.getGradeBand());
        }
        for (CoursePriceOptionEntity option : coursePriceOptionMapper.selectList(
                new LambdaQueryWrapper<CoursePriceOptionEntity>()
                        .eq(CoursePriceOptionEntity::getCourseProductId, productId)
                        .isNotNull(CoursePriceOptionEntity::getGradeBand))) {
            String label = option.getGradeBand();
            if (StringUtils.hasText(label)) {
                knownLabels.add(label);
            }
        }

        List<ProductGradeConfigVO> result = new ArrayList<>();
        for (String label : knownLabels) {
            ProductGradeConfigEntity entity = configured.get(label);
            ClassPeriod fallback = legacyPeriods.get(label.trim().toLowerCase());
            boolean classPeriodConfigured = entity != null && Boolean.TRUE.equals(entity.getClassPeriodConfigured());
            result.add(ProductGradeConfigVO.builder()
                    .id(entity == null ? null : entity.getId())
                    .gradeLabel(label)
                    .displayOrder(entity == null ? DEFAULT_ORDER : safeOrder(entity.getDisplayOrder()))
                    .startDate(classPeriodConfigured ? entity.getClassStartDate()
                            : fallback == null ? null : fallback.startDate())
                    .endDate(classPeriodConfigured ? entity.getClassEndDate()
                            : fallback == null ? null : fallback.endDate())
                    .classPeriodConfigured(classPeriodConfigured)
                    .build());
        }
        result.sort((left, right) -> {
            int byOrder = Integer.compare(left.displayOrder(), right.displayOrder());
            return byOrder != 0 ? byOrder : left.gradeLabel().compareToIgnoreCase(right.gradeLabel());
        });
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveGradeConfigs(Long productId, ProductGradeConfigSaveRequest request) {
        requireProduct(productId);

        List<ProductGradeConfigSaveRequest.Item> items = request.getItems() == null
                ? List.of() : request.getItems();

        // 1. 规范化：newLabel 为空 = 不改名；传了纯空白 = 非法。
        List<ProductGradeConfigVO> existingGrades = buildGradeConfigs(productId);
        Map<String, ProductGradeConfigVO> existingByLabel = existingGrades.stream()
                .collect(java.util.stream.Collectors.toMap(
                        item -> item.gradeLabel().toLowerCase(), item -> item));
        List<NormalizedItem> normalized = new ArrayList<>();
        for (ProductGradeConfigSaveRequest.Item item : items) {
            String currentLabel = item.getCurrentLabel() == null ? "" : item.getCurrentLabel().trim();
            if (!StringUtils.hasText(currentLabel)) {
                throw new BusinessException("GRADE_LABEL_REQUIRED", "Current grade label is required.");
            }
            String newLabel = currentLabel;
            if (item.getNewLabel() != null) {
                if (!StringUtils.hasText(item.getNewLabel().trim())) {
                    throw new BusinessException("GRADE_LABEL_BLANK", "New grade label cannot be blank.");
                }
                newLabel = item.getNewLabel().trim();
            }
            ProductGradeConfigVO existing = existingByLabel.get(currentLabel.toLowerCase());
            boolean updateClassPeriod = Boolean.TRUE.equals(item.getUpdateClassPeriod());
            LocalDate startDate = updateClassPeriod ? item.getStartDate()
                    : existing == null ? null : existing.startDate();
            LocalDate endDate = updateClassPeriod ? item.getEndDate()
                    : existing == null ? null : existing.endDate();
            if (updateClassPeriod && startDate != null && endDate != null && endDate.isBefore(startDate)) {
                throw new BusinessException("INVALID_GRADE_CLASS_PERIOD",
                        "Class end date cannot be earlier than the start date for grade " + currentLabel + ".");
            }
            normalized.add(new NormalizedItem(currentLabel, newLabel, safeOrder(item.getDisplayOrder()),
                    startDate, endDate, updateClassPeriod || (existing != null && Boolean.TRUE.equals(existing.classPeriodConfigured()))));
        }

        // 2. currentLabel 不得重复提交。
        Set<String> currentKeys = new LinkedHashSet<>();
        for (NormalizedItem item : normalized) {
            if (!currentKeys.add(item.currentLabel.toLowerCase())) {
                throw new BusinessException("GRADE_DUPLICATED",
                        "Duplicate grade in the request: " + item.currentLabel);
            }
        }

        // 3. currentLabel 必须真实存在（配置表 ∪ 价格行），拒绝过期数据。
        Set<String> knownKeys = new LinkedHashSet<>();
        for (ProductGradeConfigVO vo : existingGrades) {
            knownKeys.add(vo.gradeLabel().toLowerCase());
        }
        for (NormalizedItem item : normalized) {
            if (!knownKeys.contains(item.currentLabel.toLowerCase())) {
                throw new BusinessException("GRADE_NOT_FOUND",
                        "Grade does not exist for this course: " + item.currentLabel);
            }
        }

        // 4. 改名冲突校验：最终名互不重复，且不能撞任何当前已存在的其他 grade（防止把两个班合成一个）。
        Set<String> finalKeys = new LinkedHashSet<>();
        for (NormalizedItem item : normalized) {
            if (!finalKeys.add(item.newLabel.toLowerCase())) {
                throw new BusinessException("GRADE_CONFLICT",
                        "Duplicate grade label in the request: " + item.newLabel);
            }
        }
        for (NormalizedItem item : normalized) {
            if (!item.newLabel.equals(item.currentLabel) && knownKeys.contains(item.newLabel.toLowerCase())) {
                throw new BusinessException("GRADE_CONFLICT",
                        "Grade label already exists for this course: " + item.newLabel);
            }
        }

        // 5. 级联改名：价格行 / 班级 / 假期 / 老师费率作用域（历史订单快照不动）。
        for (NormalizedItem item : normalized) {
            if (!item.newLabel.equals(item.currentLabel)) {
                coursePriceOptionMapper.update(null, new LambdaUpdateWrapper<CoursePriceOptionEntity>()
                        .set(CoursePriceOptionEntity::getGradeBand, item.newLabel)
                        .eq(CoursePriceOptionEntity::getCourseProductId, productId)
                        .eq(CoursePriceOptionEntity::getGradeBand, item.currentLabel));
                classGroupMapper.update(null, new LambdaUpdateWrapper<ClassGroupEntity>()
                        .set(ClassGroupEntity::getGradeBand, item.newLabel)
                        .eq(ClassGroupEntity::getProductId, productId)
                        .eq(ClassGroupEntity::getGradeBand, item.currentLabel));
                classHolidayMapper.update(null, new LambdaUpdateWrapper<ClassHolidayEntity>()
                        .set(ClassHolidayEntity::getGradeBand, item.newLabel)
                        .eq(ClassHolidayEntity::getCourseProductId, productId)
                        .eq(ClassHolidayEntity::getGradeBand, item.currentLabel));
                teacherPayRateMapper.update(null, new LambdaUpdateWrapper<TeacherPayRateEntity>()
                        .set(TeacherPayRateEntity::getGradeBand, item.newLabel)
                        .eq(TeacherPayRateEntity::getCourseProductId, productId)
                        .eq(TeacherPayRateEntity::getGradeBand, item.currentLabel));
            }
            if (item.classPeriodConfigured) {
                classGroupMapper.update(null, new LambdaUpdateWrapper<ClassGroupEntity>()
                        .set(ClassGroupEntity::getStartDate, item.startDate)
                        .set(ClassGroupEntity::getEndDate, item.endDate)
                        .set(ClassGroupEntity::getScheduleBaseEndDate, item.endDate)
                        .eq(ClassGroupEntity::getProductId, productId)
                        .eq(ClassGroupEntity::getGradeBand, item.newLabel)
                        .eq(ClassGroupEntity::getIsDeleted, 0));
            }
        }

        // 6. 全量重建配置行：旧行改写墓碑名后软删，再按提交顺序插入（uk 含 is_deleted，墓碑名保证不撞）。
        for (ProductGradeConfigEntity existing : productGradeConfigMapper.selectList(
                new LambdaQueryWrapper<ProductGradeConfigEntity>()
                        .eq(ProductGradeConfigEntity::getCourseProductId, productId))) {
            releaseConfigLabel(existing);
            productGradeConfigMapper.deleteById(existing.getId());
        }
        for (NormalizedItem item : normalized) {
            ProductGradeConfigEntity entity = new ProductGradeConfigEntity();
            entity.setCourseProductId(productId);
            entity.setGradeLabel(item.newLabel);
            entity.setDisplayOrder(item.displayOrder);
            entity.setClassStartDate(item.startDate);
            entity.setClassEndDate(item.endDate);
            entity.setClassPeriodConfigured(item.classPeriodConfigured);
            productGradeConfigMapper.insert(entity);
        }
    }

    private void requireProduct(Long productId) {
        CourseProductEntity product = courseProductMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException("COURSE_PRODUCT_NOT_FOUND", "Course product not found.");
        }
    }

    /**
     * 软删前把 grade_label 改写为墓碑名，避免多次"发现-保存-消失"循环后
     * 软删行撞 uk_product_grade_label (course_product_id, grade_label, is_deleted)。
     */
    private void releaseConfigLabel(ProductGradeConfigEntity entity) {
        String suffix = TOMBSTONE_SUFFIX + entity.getId();
        String base = entity.getGradeLabel();
        int maxBaseLength = Math.max(0, 64 - suffix.length());
        if (base.length() > maxBaseLength) {
            base = base.substring(0, maxBaseLength);
        }
        ProductGradeConfigEntity patch = new ProductGradeConfigEntity();
        patch.setId(entity.getId());
        patch.setGradeLabel(base + suffix);
        productGradeConfigMapper.updateById(patch);
    }

    private int safeOrder(Integer order) {
        return order == null ? DEFAULT_ORDER : order;
    }

    private record NormalizedItem(String currentLabel, String newLabel, int displayOrder,
                                  LocalDate startDate, LocalDate endDate, boolean classPeriodConfigured) {
    }

    private record ClassPeriod(LocalDate startDate, LocalDate endDate) {
    }
}
