package com.classdeduction.system.modules.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.base.dto.DiscountPresetSaveRequest;
import com.classdeduction.system.modules.base.entity.DiscountPresetEntity;
import com.classdeduction.system.modules.base.mapper.DiscountPresetMapper;
import com.classdeduction.system.modules.base.service.DiscountPresetService;
import com.classdeduction.system.modules.base.vo.DiscountPresetVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Discount preset service implementation.
 */
@Service
@RequiredArgsConstructor
public class DiscountPresetServiceImpl implements DiscountPresetService {

    private static final String DEFAULT_STATUS = "ACTIVE";

    private final DiscountPresetMapper discountPresetMapper;

    @Override
    @Cacheable(value = "reference:discountPresets", key = "'listAllActive'")
    public List<DiscountPresetVO> listAllActive() {
        return discountPresetMapper.selectList(new LambdaQueryWrapper<DiscountPresetEntity>()
                        .eq(DiscountPresetEntity::getIsDeleted, 0)
                        .orderByAsc(DiscountPresetEntity::getSortOrder)
                        .orderByAsc(DiscountPresetEntity::getId))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Cacheable(value = "reference:discountPreset", key = "#id")
    public DiscountPresetVO getPreset(Long id) {
        return toVO(requirePreset(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"reference:discountPresets", "reference:discountPreset"}, allEntries = true)
    public Long createPreset(DiscountPresetSaveRequest request) {
        DiscountPresetEntity entity = new DiscountPresetEntity();
        applyPreset(entity, request);
        discountPresetMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"reference:discountPresets", "reference:discountPreset"}, allEntries = true)
    public void updatePreset(Long id, DiscountPresetSaveRequest request) {
        DiscountPresetEntity entity = requirePreset(id);
        applyPreset(entity, request);
        discountPresetMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"reference:discountPresets", "reference:discountPreset"}, allEntries = true)
    public void deletePreset(Long id) {
        requirePreset(id);
        discountPresetMapper.deleteById(id);
    }

    private DiscountPresetEntity requirePreset(Long id) {
        DiscountPresetEntity entity = discountPresetMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("PRESET_NOT_FOUND", "Discount preset not found.");
        }
        return entity;
    }

    private void applyPreset(DiscountPresetEntity entity, DiscountPresetSaveRequest request) {
        // 百分比是费率，可有小数；固定金额直接进入报名优惠，必须为整美元。
        if ("FIXED".equalsIgnoreCase(request.getDiscountType())
                && request.getDiscountValue() != null
                && request.getDiscountValue().stripTrailingZeros().scale() > 0) {
            throw new BusinessException("WHOLE_DOLLAR_REQUIRED", "Fixed discount must be a whole dollar amount.");
        }
        entity.setPresetName(request.getPresetName());
        entity.setDiscountType(request.getDiscountType());
        entity.setDiscountValue(request.getDiscountValue());
        entity.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 99);
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : DEFAULT_STATUS);
    }

    private DiscountPresetVO toVO(DiscountPresetEntity entity) {
        return DiscountPresetVO.builder()
                .id(entity.getId())
                .presetName(entity.getPresetName())
                .discountType(entity.getDiscountType())
                .discountValue(entity.getDiscountValue())
                .sortOrder(entity.getSortOrder())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
