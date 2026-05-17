package com.classdeduction.system.modules.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.base.dto.CampusSaveRequest;
import com.classdeduction.system.modules.base.entity.CampusEntity;
import com.classdeduction.system.modules.base.mapper.CampusMapper;
import com.classdeduction.system.modules.base.service.CampusService;
import com.classdeduction.system.modules.base.vo.CampusVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * Campus service implementation.
 */
@Service
@RequiredArgsConstructor
public class CampusServiceImpl implements CampusService {

    private static final String DEFAULT_STATUS = "ACTIVE";

    private final CampusMapper campusMapper;

    @Override
    @Cacheable(value = "reference:campuses", key = "'listAllActive'")
    public List<CampusVO> listAllActive() {
        return campusMapper.selectList(new LambdaQueryWrapper<CampusEntity>()
                        .eq(CampusEntity::getIsDeleted, 0)
                        .orderByAsc(CampusEntity::getDisplayOrder)
                        .orderByAsc(CampusEntity::getId))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Cacheable(value = "reference:campus", key = "#id")
    public CampusVO getCampus(Long id) {
        return toVO(requireCampus(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"reference:campuses", "reference:campus"}, allEntries = true)
    public Long createCampus(CampusSaveRequest request) {
        ensureCampusNameUnique(null, request.getCampusName());
        CampusEntity entity = new CampusEntity();
        applyCampus(entity, request);
        campusMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"reference:campuses", "reference:campus"}, allEntries = true)
    public void updateCampus(Long id, CampusSaveRequest request) {
        CampusEntity entity = requireCampus(id);
        ensureCampusNameUnique(id, request.getCampusName());
        applyCampus(entity, request);
        campusMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"reference:campuses", "reference:campus"}, allEntries = true)
    public void deleteCampus(Long id) {
        requireCampus(id);
        campusMapper.deleteById(id);
    }

    private void ensureCampusNameUnique(Long currentId, String campusName) {
        CampusEntity existing = campusMapper.selectOne(new LambdaQueryWrapper<CampusEntity>()
                .eq(CampusEntity::getCampusName, campusName)
                .eq(CampusEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (existing != null && !Objects.equals(existing.getId(), currentId)) {
            throw new BusinessException("Campus name already exists.");
        }
    }

    private CampusEntity requireCampus(Long id) {
        CampusEntity entity = campusMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("CAMPUS_NOT_FOUND", "Campus not found.");
        }
        return entity;
    }

    private void applyCampus(CampusEntity entity, CampusSaveRequest request) {
        entity.setCampusName(request.getCampusName());
        entity.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 99);
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : DEFAULT_STATUS);
        entity.setNotes(request.getNotes());
    }

    private CampusVO toVO(CampusEntity entity) {
        return CampusVO.builder()
                .id(entity.getId())
                .campusName(entity.getCampusName())
                .displayOrder(entity.getDisplayOrder())
                .status(entity.getStatus())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
