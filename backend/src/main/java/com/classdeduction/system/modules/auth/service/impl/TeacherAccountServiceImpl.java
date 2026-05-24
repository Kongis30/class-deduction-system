package com.classdeduction.system.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.modules.auth.dto.TeacherAccountCreateRequest;
import com.classdeduction.system.modules.auth.dto.TeacherAccountPageQuery;
import com.classdeduction.system.modules.auth.entity.RoleEntity;
import com.classdeduction.system.modules.auth.entity.UserAccountEntity;
import com.classdeduction.system.modules.auth.entity.UserRoleEntity;
import com.classdeduction.system.modules.auth.mapper.RoleMapper;
import com.classdeduction.system.modules.auth.mapper.UserAccountMapper;
import com.classdeduction.system.modules.auth.mapper.UserRoleMapper;
import com.classdeduction.system.modules.auth.service.TeacherAccountService;
import com.classdeduction.system.modules.auth.vo.TeacherAccountVO;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 教师账号服务：创建老师档案同时创建登录账号。
 */
@Service
@RequiredArgsConstructor
public class TeacherAccountServiceImpl implements TeacherAccountService {

    private final UserAccountMapper userAccountMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final TeacherMapper teacherMapper;
    private final PasswordEncoder passwordEncoder;
    private final JsonHelper jsonHelper;

    @Override
    public PageResponse<TeacherAccountVO> pageTeacherAccounts(TeacherAccountPageQuery query) {
        Set<Long> matchedTeacherIds = findMatchedTeacherIds(query.getKeyword());
        if (StringUtils.hasText(query.getKeyword()) && matchedTeacherIds.isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }
        IPage<UserAccountEntity> page = userAccountMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<UserAccountEntity>()
                        .eq(UserAccountEntity::getUserType, "TEACHER")
                        .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                                .like(UserAccountEntity::getUsername, query.getKeyword())
                                .or(inner -> inner.in(!matchedTeacherIds.isEmpty(), UserAccountEntity::getTeacherId, matchedTeacherIds)))
                        .eq(query.getTeacherId() != null, UserAccountEntity::getTeacherId, query.getTeacherId())
                        .eq(StringUtils.hasText(query.getStatus()), UserAccountEntity::getStatus, query.getStatus())
                        .orderByDesc(UserAccountEntity::getCreatedAt)
        );
        if (page.getRecords().isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }
        Map<Long, TeacherEntity> teacherMap = listTeachersByIds(page.getRecords().stream()
                .map(UserAccountEntity::getTeacherId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return PageResponse.of(
                page.getRecords().stream().map(item -> toVO(item, teacherMap.get(item.getTeacherId()))).toList(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTeacherAccount(TeacherAccountCreateRequest request) {
        ensureUsernameUnique(request.getUsername());

        // 1. Create teacher profile
        TeacherEntity teacher = new TeacherEntity();
        teacher.setTeacherNo(generateTeacherNo());
        teacher.setTeacherName(request.getTeacherName());
        teacher.setEmail(request.getEmail());
        teacher.setMobile(request.getMobile());
        teacher.setSubjectTags(request.getSubjectTags() != null ? jsonHelper.toJson(request.getSubjectTags()) : null);
        teacher.setStatus("ACTIVE");
        teacherMapper.insert(teacher);

        // 2. Create login account linked to the teacher
        UserAccountEntity entity = new UserAccountEntity();
        entity.setUsername(request.getUsername());
        entity.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        entity.setUserType("TEACHER");
        entity.setDisplayName(StringUtils.hasText(request.getDisplayName()) ? request.getDisplayName() : teacher.getTeacherName());
        entity.setMobile(request.getMobile());
        entity.setEmail(request.getEmail());
        entity.setTeacherId(teacher.getId());
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : "ACTIVE");
        entity.setRemark(request.getRemark());
        userAccountMapper.insert(entity);
        bindTeacherRole(entity.getId());
        return entity.getId();
    }

    private String generateTeacherNo() {
        // Only look at existing INS-prefixed numbers to avoid collision with seed data (THxxx)
        TeacherEntity maxTeacher = teacherMapper.selectOne(new LambdaQueryWrapper<TeacherEntity>()
                .eq(TeacherEntity::getIsDeleted, 0)
                .likeRight(TeacherEntity::getTeacherNo, "INS-")
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

    private void ensureUsernameUnique(String username) {
        UserAccountEntity existing = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccountEntity>()
                .eq(UserAccountEntity::getUsername, username)
                .eq(UserAccountEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (existing != null) {
            throw new BusinessException("USERNAME_ALREADY_EXISTS", "Username already exists.");
        }
    }

    private void bindTeacherRole(Long userId) {
        RoleEntity teacherRole = roleMapper.selectOne(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getRoleCode, "TEACHER")
                .eq(RoleEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (teacherRole == null) {
            throw new BusinessException("TEACHER_ROLE_NOT_FOUND", "Teacher role is missing from the system.");
        }
        UserRoleEntity userRole = new UserRoleEntity();
        userRole.setUserId(userId);
        userRole.setRoleId(teacherRole.getId());
        userRole.setCreatedAt(AppTime.nowLocalDateTime());
        userRoleMapper.insert(userRole);
    }

    private Set<Long> findMatchedTeacherIds(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptySet();
        }
        return teacherMapper.selectList(new LambdaQueryWrapper<TeacherEntity>()
                        .and(wrapper -> wrapper.like(TeacherEntity::getTeacherNo, keyword)
                                .or()
                                .like(TeacherEntity::getTeacherName, keyword)))
                .stream()
                .map(TeacherEntity::getId)
                .collect(Collectors.toSet());
    }

    private Map<Long, TeacherEntity> listTeachersByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return teacherMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(TeacherEntity::getId, item -> item));
    }

    private TeacherAccountVO toVO(UserAccountEntity entity, TeacherEntity teacher) {
        return TeacherAccountVO.builder()
                .userId(entity.getId())
                .teacherId(entity.getTeacherId())
                .teacherNo(teacher == null ? null : teacher.getTeacherNo())
                .teacherName(teacher == null ? null : teacher.getTeacherName())
                .username(entity.getUsername())
                .displayName(entity.getDisplayName())
                .mobile(entity.getMobile())
                .email(entity.getEmail())
                .status(entity.getStatus())
                .lastLoginAt(entity.getLastLoginAt())
                .remark(entity.getRemark())
                .build();
    }
}
