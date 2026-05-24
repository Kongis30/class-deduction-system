package com.classdeduction.system.modules.auth.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.auth.entity.RoleEntity;
import com.classdeduction.system.modules.auth.entity.UserAccountEntity;
import com.classdeduction.system.modules.auth.entity.UserRoleEntity;
import com.classdeduction.system.modules.auth.mapper.RoleMapper;
import com.classdeduction.system.modules.auth.mapper.UserAccountMapper;
import com.classdeduction.system.modules.auth.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 启动初始化器：确保开发环境至少有可登录的管理员和教务账号，避免新环境无法进入系统。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthBootstrapInitializer implements ApplicationRunner {

    private final UserAccountMapper userAccountMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        ensureDefaultUser("admin", "Jin2025", "System Administrator", "ADMIN", "ADMIN");
        ensureDefaultUser("staff", "Staff@123456", "Front Desk Staff", "STAFF", "STAFF");
    }

    private void ensureDefaultUser(String username, String rawPassword, String displayName, String userType, String roleCode) {
        // 默认账号初始化：仅在账号不存在时插入，并补齐账号和角色的绑定关系。
        UserAccountEntity user = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccountEntity>()
                .eq(UserAccountEntity::getUsername, username)
                .eq(UserAccountEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (user == null) {
            user = new UserAccountEntity();
            user.setUsername(username);
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user.setUserType(userType);
            user.setDisplayName(displayName);
            user.setStatus("ACTIVE");
            user.setRemark("Bootstrapped development account");
            userAccountMapper.insert(user);
            log.info("Bootstrapped default auth account: {}", username);
        }

        RoleEntity role = roleMapper.selectOne(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getRoleCode, roleCode)
                .eq(RoleEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (role == null) {
            log.warn("Skipped auth bootstrap for {} because role {} does not exist.", username, roleCode);
            return;
        }

        Long existingCount = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getUserId, user.getId())
                .eq(UserRoleEntity::getRoleId, role.getId()));
        if (existingCount != null && existingCount > 0) {
            return;
        }

        UserRoleEntity userRole = new UserRoleEntity();
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        userRole.setCreatedAt(AppTime.nowLocalDateTime());
        userRoleMapper.insert(userRole);
    }
}
