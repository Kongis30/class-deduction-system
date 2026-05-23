package com.classdeduction.system.modules.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.modules.auth.entity.UserAccountEntity;
import com.classdeduction.system.modules.auth.mapper.UserAccountMapper;
import com.classdeduction.system.modules.base.dto.TeacherPageQuery;
import com.classdeduction.system.modules.base.dto.TeacherSaveRequest;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.base.service.TeacherService;
import com.classdeduction.system.modules.base.vo.TeacherVO;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Teacher service for CRUD operations and subject tag conversion.
 */
@Service
@RequiredArgsConstructor
public class TeacherServiceImpl implements TeacherService {

    private static final String DEFAULT_STATUS = "ACTIVE";

    private final TeacherMapper teacherMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final RecurringScheduleRuleMapper recurringScheduleRuleMapper;
    private final UserAccountMapper userAccountMapper;
    private final ClassGroupMapper classGroupMapper;
    private final JsonHelper jsonHelper;

    @Override
    @Cacheable(value = "reference:teachers", key = "'listAllActive'")
    public List<TeacherVO> listAllActive() {
        return teacherMapper.selectList(new LambdaQueryWrapper<TeacherEntity>()
                        .eq(TeacherEntity::getStatus, "ACTIVE")
                        .eq(TeacherEntity::getIsDeleted, 0)
                        .orderByAsc(TeacherEntity::getTeacherNo)
                        .orderByAsc(TeacherEntity::getId))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public PageResponse<TeacherVO> pageTeachers(TeacherPageQuery query) {
        Page<TeacherEntity> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<TeacherEntity> wrapper = new LambdaQueryWrapper<TeacherEntity>()
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(TeacherEntity::getTeacherNo, query.getKeyword())
                        .or()
                        .like(TeacherEntity::getTeacherName, query.getKeyword()))
                .eq(StringUtils.hasText(query.getStatus()), TeacherEntity::getStatus, query.getStatus())
                .orderByDesc(TeacherEntity::getCreatedAt);
        IPage<TeacherEntity> result = teacherMapper.selectPage(page, wrapper);
        return PageResponse.of(result.getRecords().stream().map(this::toVO).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @Cacheable(value = "reference:teacher", key = "#id")
    public TeacherVO getTeacher(Long id) {
        return toVO(requireTeacher(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"reference:teachers", "reference:teacher"}, allEntries = true)
    public Long createTeacher(TeacherSaveRequest request) {
        String teacherNo = StringUtils.hasText(request.getTeacherNo())
                ? request.getTeacherNo()
                : generateTeacherNo();
        request.setTeacherNo(teacherNo);
        ensureTeacherNoUnique(null, teacherNo);
        TeacherEntity entity = new TeacherEntity();
        applyTeacher(entity, request);
        teacherMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"reference:teachers", "reference:teacher"}, allEntries = true)
    public void updateTeacher(Long id, TeacherSaveRequest request) {
        TeacherEntity entity = requireTeacher(id);
        if (!StringUtils.hasText(request.getTeacherNo())) {
            request.setTeacherNo(entity.getTeacherNo());
        }
        ensureTeacherNoUnique(id, request.getTeacherNo());
        applyTeacher(entity, request);
        teacherMapper.updateById(entity);
    }

    private String generateTeacherNo() {
        TeacherEntity maxTeacher = teacherMapper.selectOne(new LambdaQueryWrapper<TeacherEntity>()
                .eq(TeacherEntity::getIsDeleted, 0)
                .orderByDesc(TeacherEntity::getTeacherNo)
                .last("LIMIT 1"));
        int nextSeq = 1;
        if (maxTeacher != null && maxTeacher.getTeacherNo() != null) {
            String maxNo = maxTeacher.getTeacherNo();
            try {
                String numPart = maxNo.replaceAll("[^0-9]", "");
                if (!numPart.isEmpty()) {
                    nextSeq = Integer.parseInt(numPart) + 1;
                }
            } catch (NumberFormatException e) {
                nextSeq = 1;
            }
        }
        return String.format("INS-%04d", nextSeq);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"reference:teachers", "reference:teacher"}, allEntries = true)
    public void deleteTeacher(Long id) {
        requireTeacher(id);
        // 检查未来的排课
        Long futureOccurrences = lessonOccurrenceMapper.selectCount(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getPlannedTeacherId, id)
                .eq(LessonOccurrenceEntity::getIsDeleted, 0)
                .gt(LessonOccurrenceEntity::getPlannedStartAt, LocalDateTime.now()));
        // 检查活跃的排课规则
        Long activeRules = recurringScheduleRuleMapper.selectCount(new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                .eq(RecurringScheduleRuleEntity::getPrimaryTeacherId, id)
                .eq(RecurringScheduleRuleEntity::getRuleStatus, "ACTIVE")
                .eq(RecurringScheduleRuleEntity::getIsDeleted, 0));
        // 检查登录账号
        Long userAccounts = userAccountMapper.selectCount(new LambdaQueryWrapper<UserAccountEntity>()
                .eq(UserAccountEntity::getTeacherId, id)
                .eq(UserAccountEntity::getIsDeleted, 0));
        // 检查班级
        Long classGroups = classGroupMapper.selectCount(new LambdaQueryWrapper<ClassGroupEntity>()
                .eq(ClassGroupEntity::getTeacherId, id)
                .eq(ClassGroupEntity::getIsDeleted, 0));
        if (futureOccurrences + activeRules + userAccounts + classGroups > 0) {
            throw new BusinessException("TEACHER_HAS_REFERENCES",
                    "Cannot delete instructor: there are future lessons, active schedule rules, "
                            + "user accounts, or assigned classes. Set to INACTIVE instead.");
        }
        teacherMapper.deleteById(id);
    }

    private void ensureTeacherNoUnique(Long currentId, String teacherNo) {
        TeacherEntity existing = teacherMapper.selectOne(new LambdaQueryWrapper<TeacherEntity>()
                .eq(TeacherEntity::getTeacherNo, teacherNo)
                .eq(TeacherEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (existing != null && !Objects.equals(existing.getId(), currentId)) {
            throw new BusinessException("Instructor ID already exists.");
        }
    }

    private TeacherEntity requireTeacher(Long id) {
        TeacherEntity entity = teacherMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("TEACHER_NOT_FOUND", "Instructor not found.");
        }
        return entity;
    }

    private void applyTeacher(TeacherEntity entity, TeacherSaveRequest request) {
        entity.setTeacherNo(request.getTeacherNo());
        entity.setTeacherName(request.getTeacherName());
        entity.setEmail(request.getEmail());
        entity.setMobile(request.getMobile());
        entity.setSubjectTags(jsonHelper.toJson(request.getSubjectTags()));
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : DEFAULT_STATUS);
        entity.setHireDate(request.getHireDate());
        entity.setNotes(request.getNotes());
    }

    private TeacherVO toVO(TeacherEntity entity) {
        List<String> subjectTags = jsonHelper.fromJson(entity.getSubjectTags(), new TypeReference<>() {
        });
        return TeacherVO.builder()
                .id(entity.getId())
                .teacherNo(entity.getTeacherNo())
                .teacherName(entity.getTeacherName())
                .email(entity.getEmail())
                .mobile(entity.getMobile())
                .subjectTags(subjectTags == null ? Collections.emptyList() : subjectTags)
                .status(entity.getStatus())
                .hireDate(entity.getHireDate())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
