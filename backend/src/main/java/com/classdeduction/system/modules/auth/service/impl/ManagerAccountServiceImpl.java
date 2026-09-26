package com.classdeduction.system.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.dto.ManagerAccountCreateRequest;
import com.classdeduction.system.modules.auth.dto.ManagerAccountPageQuery;
import com.classdeduction.system.modules.auth.dto.ManagerAccountUpdateRequest;
import com.classdeduction.system.modules.auth.entity.RoleEntity;
import com.classdeduction.system.modules.auth.entity.UserAccountEntity;
import com.classdeduction.system.modules.auth.entity.UserRoleEntity;
import com.classdeduction.system.modules.auth.mapper.ManagerRegistrationStatsMapper;
import com.classdeduction.system.modules.auth.mapper.RoleMapper;
import com.classdeduction.system.modules.auth.mapper.UserAccountMapper;
import com.classdeduction.system.modules.auth.mapper.UserRoleMapper;
import com.classdeduction.system.modules.auth.service.ManagerAccountService;
import com.classdeduction.system.modules.auth.vo.ManagerAccountVO;
import com.classdeduction.system.modules.auth.vo.ManagerRegistrationStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ManagerAccountServiceImpl implements ManagerAccountService {

    private final UserAccountMapper userAccountMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final ManagerRegistrationStatsMapper registrationStatsMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResponse<ManagerAccountVO> pageManagerAccounts(ManagerAccountPageQuery query) {
        IPage<UserAccountEntity> page = userAccountMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                managerAccountQuery(query)
                        .orderByDesc(UserAccountEntity::getCreatedAt));
        var managers = page.getRecords();
        Map<Long, ManagerRegistrationStatsVO> statsByUserId = managers.isEmpty()
                ? Map.of()
                : registrationStatsMapper.selectStatsByOperatorUserIds(
                        managers.stream().map(UserAccountEntity::getId).toList()).stream()
                .collect(Collectors.toMap(ManagerRegistrationStatsVO::getUserId, Function.identity()));
        return PageResponse.of(managers.stream()
                        .map(user -> toVO(user, statsByUserId.get(user.getId())))
                        .toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createManagerAccount(ManagerAccountCreateRequest request) {
        ensureUsernameAvailable(request.getUsername());
        RoleEntity managerRole = roleMapper.selectOne(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getRoleCode, "MANAGER")
                .eq(RoleEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (managerRole == null) {
            throw new BusinessException("MANAGER_ROLE_NOT_FOUND", "Manager role is not configured.");
        }

        UserAccountEntity user = new UserAccountEntity();
        user.setUsername(request.getUsername().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setUserType("STAFF");
        user.setDisplayName(request.getDisplayName().trim());
        user.setMobile(request.getMobile());
        user.setEmail(request.getEmail());
        user.setStatus("ACTIVE");
        user.setRemark(request.getRemark());
        userAccountMapper.insert(user);

        UserRoleEntity binding = new UserRoleEntity();
        binding.setUserId(user.getId());
        binding.setRoleId(managerRole.getId());
        userRoleMapper.insert(binding);
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateManagerAccount(Long userId, ManagerAccountUpdateRequest request) {
        UserAccountEntity user = userAccountMapper.selectOne(managerAccountQuery(null)
                .eq(UserAccountEntity::getId, userId));
        if (user == null) {
            throw new BusinessException("MANAGER_ACCOUNT_NOT_FOUND", "Manager account not found.");
        }
        if (StringUtils.hasText(request.getPassword())) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        if (StringUtils.hasText(request.getDisplayName())) {
            user.setDisplayName(request.getDisplayName().trim());
        }
        if (request.getMobile() != null) {
            user.setMobile(request.getMobile());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (StringUtils.hasText(request.getStatus())) {
            String status = request.getStatus().trim().toUpperCase();
            if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
                throw new BusinessException("INVALID_ACCOUNT_STATUS", "Status must be ACTIVE or INACTIVE.");
            }
            user.setStatus(status);
        }
        if (request.getRemark() != null) {
            user.setRemark(request.getRemark());
        }
        userAccountMapper.updateById(user);
    }

    private LambdaQueryWrapper<UserAccountEntity> managerAccountQuery(ManagerAccountPageQuery query) {
        String managerUsers = "SELECT ur.user_id FROM sys_user_role ur "
                + "JOIN sys_role r ON r.id = ur.role_id "
                + "WHERE r.role_code = 'MANAGER' AND r.is_deleted = 0";
        LambdaQueryWrapper<UserAccountEntity> wrapper = new LambdaQueryWrapper<UserAccountEntity>()
                .eq(UserAccountEntity::getUserType, "STAFF")
                .inSql(UserAccountEntity::getId, managerUsers);
        if (query != null) {
            wrapper.eq(StringUtils.hasText(query.getStatus()), UserAccountEntity::getStatus, query.getStatus())
                    .and(StringUtils.hasText(query.getKeyword()), nested -> nested
                            .like(UserAccountEntity::getUsername, query.getKeyword())
                            .or().like(UserAccountEntity::getDisplayName, query.getKeyword()));
        }
        return wrapper;
    }

    private void ensureUsernameAvailable(String username) {
        Long count = userAccountMapper.selectCount(new LambdaQueryWrapper<UserAccountEntity>()
                .eq(UserAccountEntity::getUsername, username.trim())
                .eq(UserAccountEntity::getIsDeleted, 0));
        if (count != null && count > 0) {
            throw new BusinessException("USERNAME_ALREADY_EXISTS", "Username is already in use.");
        }
    }

    private ManagerAccountVO toVO(UserAccountEntity user, ManagerRegistrationStatsVO stats) {
        return new ManagerAccountVO(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getMobile(), user.getEmail(), user.getStatus(), user.getLastLoginAt(), user.getRemark(),
                stats == null ? 0L : stats.getRegistrationCount(),
                stats == null ? 0L : stats.getUniqueStudentCount());
    }
}
