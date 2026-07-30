package com.classdeduction.system.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.common.util.StudentNoGenerator;
import com.classdeduction.system.modules.auth.dto.StudentAccountCreateRequest;
import com.classdeduction.system.modules.auth.dto.StudentAccountPageQuery;
import com.classdeduction.system.modules.auth.dto.StudentAccountUpdateRequest;
import com.classdeduction.system.modules.auth.entity.AuthSessionEntity;
import com.classdeduction.system.modules.auth.entity.RoleEntity;
import com.classdeduction.system.modules.auth.entity.UserAccountEntity;
import com.classdeduction.system.modules.auth.entity.UserRoleEntity;
import com.classdeduction.system.modules.auth.mapper.AuthSessionMapper;
import com.classdeduction.system.modules.auth.mapper.RoleMapper;
import com.classdeduction.system.modules.auth.mapper.UserAccountMapper;
import com.classdeduction.system.modules.auth.mapper.UserRoleMapper;
import com.classdeduction.system.modules.auth.service.StudentAccountService;
import com.classdeduction.system.modules.auth.vo.StudentAccountVO;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 学生账号服务：创建学生档案同时创建登录账号。
 */
@Service
@RequiredArgsConstructor
public class StudentAccountServiceImpl implements StudentAccountService {

    private final UserAccountMapper userAccountMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final StudentMapper studentMapper;
    private final StudentNoGenerator studentNoGenerator;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionMapper authSessionMapper;
    private final StudentService studentService;

    @Override
    public PageResponse<StudentAccountVO> pageStudentAccounts(StudentAccountPageQuery query) {
        Set<Long> matchedStudentIds = findMatchedStudentIds(query.getKeyword());
        if (StringUtils.hasText(query.getKeyword()) && matchedStudentIds.isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }
        IPage<UserAccountEntity> page = userAccountMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<UserAccountEntity>()
                        .eq(UserAccountEntity::getUserType, "STUDENT")
                        .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                                .like(UserAccountEntity::getUsername, query.getKeyword())
                                .or(inner -> inner.in(!matchedStudentIds.isEmpty(), UserAccountEntity::getStudentId, matchedStudentIds)))
                        .eq(query.getStudentId() != null, UserAccountEntity::getStudentId, query.getStudentId())
                        .eq(StringUtils.hasText(query.getStatus()), UserAccountEntity::getStatus, query.getStatus())
                        .orderByDesc(UserAccountEntity::getCreatedAt)
        );
        if (page.getRecords().isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }
        Map<Long, StudentEntity> studentMap = listStudentsByIds(page.getRecords().stream()
                .map(UserAccountEntity::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return PageResponse.of(
                page.getRecords().stream().map(item -> toVO(item, studentMap.get(item.getStudentId()))).toList(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createStudentAccount(StudentAccountCreateRequest request) {
        ensureUsernameUnique(request.getUsername());

        StudentEntity student = resolveStudentForAccount(request);

        // Login access belongs to one student profile. The profile may be existing or newly created.
        UserAccountEntity entity = new UserAccountEntity();
        entity.setUsername(request.getUsername());
        entity.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        entity.setUserType("STUDENT");
        entity.setDisplayName(StringUtils.hasText(request.getDisplayName()) ? request.getDisplayName() : student.getStudentName());
        entity.setEmail(StringUtils.hasText(request.getEmail()) ? request.getEmail() : student.getGuardianEmail());
        entity.setStudentId(student.getId());
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : "ACTIVE");
        entity.setRemark(request.getRemark());
        userAccountMapper.insert(entity);
        bindStudentRole(entity.getId());
        return entity.getId();
    }

    private StudentEntity resolveStudentForAccount(StudentAccountCreateRequest request) {
        if (request.getStudentId() != null) {
            StudentEntity student = studentMapper.selectById(request.getStudentId());
            if (student == null) {
                throw new BusinessException("STUDENT_NOT_FOUND", "Student profile not found.");
            }
            ensureStudentHasNoAccount(student.getId());
            return student;
        }
        if (!StringUtils.hasText(request.getStudentName())) {
            throw new BusinessException("STUDENT_NAME_REQUIRED", "Student name is required.");
        }
        StudentEntity student = new StudentEntity();
        student.setStudentNo(studentNoGenerator.generate());
        student.setStudentName(request.getStudentName());
        student.setStatus("ACTIVE");
        student.setGuardianName(request.getGuardianName());
        student.setGuardianPhone(request.getGuardianPhone());
        student.setGuardianEmail(request.getGuardianEmail());
        studentMapper.insert(student);
        return student;
    }

    private void ensureStudentHasNoAccount(Long studentId) {
        Long existing = userAccountMapper.selectCount(new LambdaQueryWrapper<UserAccountEntity>()
                .eq(UserAccountEntity::getStudentId, studentId)
                .eq(UserAccountEntity::getIsDeleted, 0));
        if (existing != null && existing > 0) {
            throw new BusinessException("STUDENT_ACCOUNT_EXISTS", "This student already has a login account.");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStudentAccount(Long userId, StudentAccountUpdateRequest request) {
        UserAccountEntity entity = userAccountMapper.selectById(userId);
        if (entity == null || !"STUDENT".equals(entity.getUserType())) {
            throw new BusinessException("ACCOUNT_NOT_FOUND", "Student account not found.");
        }
        if (StringUtils.hasText(request.getUsername())
                && !request.getUsername().equals(entity.getUsername())) {
            ensureUsernameUnique(request.getUsername());
            entity.setUsername(request.getUsername());
        }
        if (StringUtils.hasText(request.getPassword())) {
            entity.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        if (StringUtils.hasText(request.getDisplayName())) {
            entity.setDisplayName(request.getDisplayName());
        }
        if (StringUtils.hasText(request.getEmail())) {
            entity.setEmail(request.getEmail());
        }
        if (StringUtils.hasText(request.getStatus())) {
            entity.setStatus(request.getStatus());
        }
        userAccountMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteStudentAccount(Long userId) {
        UserAccountEntity entity = userAccountMapper.selectById(userId);
        if (entity == null || !"STUDENT".equals(entity.getUserType())) {
            throw new BusinessException("ACCOUNT_NOT_FOUND", "Student account not found.");
        }
        Long studentId = entity.getStudentId();
        // 硬删除用户-角色关联（sys_user_role 没有 is_deleted 字段）
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getUserId, userId));
        // 清理 MySQL fallback 会话；Redis 会话会在下一次鉴权时因账号已删除而失效。
        authSessionMapper.delete(new LambdaQueryWrapper<AuthSessionEntity>()
                .eq(AuthSessionEntity::getUserId, userId));
        // 软删除登录账号（保留删除前的账号-档案关联用于审计）
        userAccountMapper.deleteById(userId);
        // 同步软删除学生档案，保持学生账号与学生档案一体删除。
        if (studentId != null) {
            studentService.deleteStudent(studentId);
        }
    }

    @Override
    public List<StudentAccountVO> listDeletedStudentAccounts() {
        List<UserAccountEntity> accounts = userAccountMapper.selectDeletedByType("STUDENT");
        if (accounts.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> studentIds = accounts.stream()
                .map(UserAccountEntity::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, StudentEntity> studentMap = studentIds.isEmpty()
                ? Collections.emptyMap()
                : studentMapper.selectByIdsIgnoreLogicDelete(studentIds).stream()
                        .collect(Collectors.toMap(StudentEntity::getId, item -> item));
        return accounts.stream()
                .map(item -> toVO(item, studentMap.get(item.getStudentId())))
                .toList();
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

    private void bindStudentRole(Long userId) {
        RoleEntity studentRole = roleMapper.selectOne(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getRoleCode, "STUDENT")
                .eq(RoleEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (studentRole == null) {
            throw new BusinessException("STUDENT_ROLE_NOT_FOUND", "Student role is missing from the system.");
        }
        UserRoleEntity userRole = new UserRoleEntity();
        userRole.setUserId(userId);
        userRole.setRoleId(studentRole.getId());
        userRole.setCreatedAt(AppTime.nowLocalDateTime());
        userRoleMapper.insert(userRole);
    }

    private Set<Long> findMatchedStudentIds(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptySet();
        }
        return studentMapper.selectList(new LambdaQueryWrapper<StudentEntity>()
                        .and(wrapper -> wrapper.like(StudentEntity::getStudentNo, keyword)
                                .or()
                                .like(StudentEntity::getStudentName, keyword)))
                .stream()
                .map(StudentEntity::getId)
                .collect(Collectors.toSet());
    }

    private Map<Long, StudentEntity> listStudentsByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return studentMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(StudentEntity::getId, item -> item));
    }

    private StudentAccountVO toVO(UserAccountEntity entity, StudentEntity student) {
        return StudentAccountVO.builder()
                .userId(entity.getId())
                .studentId(entity.getStudentId())
                .studentNo(student == null ? null : student.getStudentNo())
                .studentName(student == null ? null : student.getStudentName())
                .username(entity.getUsername())
                .displayName(entity.getDisplayName())
                .email(entity.getEmail())
                .status(entity.getStatus())
                .lastLoginAt(entity.getLastLoginAt())
                .remark(entity.getRemark())
                .build();
    }
}
