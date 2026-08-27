package com.classdeduction.system.modules.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.base.dto.CourseCategorySaveRequest;
import com.classdeduction.system.modules.base.entity.CourseCategoryEntity;
import com.classdeduction.system.modules.base.mapper.CourseCategoryMapper;
import com.classdeduction.system.modules.base.service.CourseCategoryService;
import com.classdeduction.system.modules.base.vo.CourseCategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Course category service implementation.
 *
 * <p>The list of category codes is fixed by the system. This service self-heals the
 * table so the fixed set of categories always exists with sensible defaults, while
 * allowing staff to override the display name and display order.</p>
 */
@Service
@RequiredArgsConstructor
public class CourseCategoryServiceImpl implements CourseCategoryService {

    private static final String DEFAULT_STATUS = "ACTIVE";

    /** Fixed category codes with their default display name and order. */
    private static final List<CourseCategoryEntity> DEFAULTS = List.of(
            fixed("AFTER_SCHOOL", "After School", 1),
            fixed("SATURDAY", "Saturday Class", 2),
            fixed("SUMMER", "Summer Class", 3),
            fixed("SPRINT", "Sprint Class", 4),
            fixed("ONE_ON_ONE", "1 on 1", 5));

    private final CourseCategoryMapper courseCategoryMapper;

    @Override
    @Cacheable(value = "reference:courseCategories", key = "'listAll'")
    public List<CourseCategoryVO> listAll() {
        ensureSeeded();
        return courseCategoryMapper.selectList(new LambdaQueryWrapper<CourseCategoryEntity>()
                        .eq(CourseCategoryEntity::getIsDeleted, 0)
                        .orderByAsc(CourseCategoryEntity::getDisplayOrder)
                        .orderByAsc(CourseCategoryEntity::getId))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "reference:courseCategories", allEntries = true)
    public void updateCategory(Long id, CourseCategorySaveRequest request) {
        CourseCategoryEntity entity = courseCategoryMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("COURSE_CATEGORY_NOT_FOUND", "Course category not found.");
        }
        entity.setDisplayName(request.getDisplayName());
        if (request.getDisplayOrder() != null) {
            entity.setDisplayOrder(request.getDisplayOrder());
        }
        courseCategoryMapper.updateById(entity);
    }

    private void ensureSeeded() {
        Map<String, CourseCategoryEntity> existing = courseCategoryMapper
                .selectList(new LambdaQueryWrapper<CourseCategoryEntity>()
                        .eq(CourseCategoryEntity::getIsDeleted, 0))
                .stream()
                .collect(Collectors.toMap(CourseCategoryEntity::getTypeCode, e -> e, (a, b) -> a));
        for (CourseCategoryEntity def : DEFAULTS) {
            if (!existing.containsKey(def.getTypeCode())) {
                CourseCategoryEntity entity = new CourseCategoryEntity();
                entity.setTypeCode(def.getTypeCode());
                entity.setDisplayName(def.getDisplayName());
                entity.setDisplayOrder(def.getDisplayOrder());
                entity.setStatus(DEFAULT_STATUS);
                courseCategoryMapper.insert(entity);
            }
        }
    }

    private CourseCategoryVO toVO(CourseCategoryEntity entity) {
        return CourseCategoryVO.builder()
                .id(entity.getId())
                .typeCode(entity.getTypeCode())
                .displayName(entity.getDisplayName())
                .displayOrder(entity.getDisplayOrder())
                .status(StringUtils.hasText(entity.getStatus()) ? entity.getStatus() : DEFAULT_STATUS)
                .build();
    }

    private static CourseCategoryEntity fixed(String code, String name, int order) {
        CourseCategoryEntity entity = new CourseCategoryEntity();
        entity.setTypeCode(code);
        entity.setDisplayName(name);
        entity.setDisplayOrder(order);
        entity.setStatus(DEFAULT_STATUS);
        return entity;
    }
}
