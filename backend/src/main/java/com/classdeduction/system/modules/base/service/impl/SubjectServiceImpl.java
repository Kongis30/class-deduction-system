package com.classdeduction.system.modules.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.base.dto.SubjectSaveRequest;
import com.classdeduction.system.modules.base.entity.SubjectEntity;
import com.classdeduction.system.modules.base.mapper.SubjectMapper;
import com.classdeduction.system.modules.base.service.SubjectService;
import com.classdeduction.system.modules.base.vo.SubjectVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * Subject service implementation.
 */
@Service
@RequiredArgsConstructor
public class SubjectServiceImpl implements SubjectService {

    private static final String DEFAULT_STATUS = "ACTIVE";

    private final SubjectMapper subjectMapper;

    @Override
    @Cacheable(value = "reference:subjects", key = "'listAllActive'")
    public List<SubjectVO> listAllActive() {
        return subjectMapper.selectList(new LambdaQueryWrapper<SubjectEntity>()
                        .eq(SubjectEntity::getStatus, "ACTIVE")
                        .eq(SubjectEntity::getIsDeleted, 0)
                        .orderByAsc(SubjectEntity::getDisplayOrder)
                        .orderByAsc(SubjectEntity::getId))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Cacheable(value = "reference:subject", key = "#id")
    public SubjectVO getSubject(Long id) {
        return toVO(requireSubject(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"reference:subjects", "reference:subject"}, allEntries = true)
    public Long createSubject(SubjectSaveRequest request) {
        ensureSubjectNameUnique(null, request.getSubjectName());
        SubjectEntity entity = new SubjectEntity();
        applySubject(entity, request);
        subjectMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"reference:subjects", "reference:subject"}, allEntries = true)
    public void updateSubject(Long id, SubjectSaveRequest request) {
        SubjectEntity entity = requireSubject(id);
        ensureSubjectNameUnique(id, request.getSubjectName());
        applySubject(entity, request);
        subjectMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"reference:subjects", "reference:subject"}, allEntries = true)
    public void deleteSubject(Long id) {
        requireSubject(id);
        subjectMapper.deleteById(id);
    }

    private void ensureSubjectNameUnique(Long currentId, String subjectName) {
        SubjectEntity existing = subjectMapper.selectOne(new LambdaQueryWrapper<SubjectEntity>()
                .eq(SubjectEntity::getSubjectName, subjectName)
                .last("LIMIT 1"));
        if (existing != null && !Objects.equals(existing.getId(), currentId)) {
            throw new BusinessException("Subject name already exists.");
        }
    }

    private SubjectEntity requireSubject(Long id) {
        SubjectEntity entity = subjectMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("SUBJECT_NOT_FOUND", "Subject not found.");
        }
        return entity;
    }

    private void applySubject(SubjectEntity entity, SubjectSaveRequest request) {
        entity.setSubjectName(request.getSubjectName());
        entity.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 99);
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : DEFAULT_STATUS);
        entity.setNotes(request.getNotes());
    }

    private SubjectVO toVO(SubjectEntity entity) {
        return SubjectVO.builder()
                .id(entity.getId())
                .subjectName(entity.getSubjectName())
                .displayOrder(entity.getDisplayOrder())
                .status(entity.getStatus())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
