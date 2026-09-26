package com.classdeduction.system.modules.auth.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.auth.dto.ManagerAccountCreateRequest;
import com.classdeduction.system.modules.auth.dto.ManagerAccountPageQuery;
import com.classdeduction.system.modules.auth.dto.ManagerAccountUpdateRequest;
import com.classdeduction.system.modules.auth.entity.RoleEntity;
import com.classdeduction.system.modules.auth.entity.UserAccountEntity;
import com.classdeduction.system.modules.auth.entity.UserRoleEntity;
import com.classdeduction.system.modules.auth.mapper.RoleMapper;
import com.classdeduction.system.modules.auth.mapper.ManagerRegistrationStatsMapper;
import com.classdeduction.system.modules.auth.mapper.UserAccountMapper;
import com.classdeduction.system.modules.auth.mapper.UserRoleMapper;
import com.classdeduction.system.modules.auth.service.impl.ManagerAccountServiceImpl;
import com.classdeduction.system.modules.auth.vo.ManagerRegistrationStatsVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagerAccountServiceImplTest {

    @Mock private UserAccountMapper userAccountMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private RoleMapper roleMapper;
    @Mock private ManagerRegistrationStatsMapper registrationStatsMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private ManagerAccountServiceImpl service;

    @Test
    void shouldCreateStaffWorkspaceAccountWithOnlyManagerRole() {
        ManagerAccountCreateRequest request = new ManagerAccountCreateRequest();
        request.setUsername("manager1");
        request.setPassword("password123");
        request.setDisplayName("Registration Manager");
        RoleEntity role = new RoleEntity();
        role.setId(140L);
        when(userAccountMapper.selectCount(any())).thenReturn(0L);
        when(roleMapper.selectOne(any())).thenReturn(role);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");

        service.createManagerAccount(request);

        ArgumentCaptor<UserAccountEntity> userCaptor = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(userAccountMapper).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("manager1");
        assertThat(userCaptor.getValue().getUserType()).isEqualTo("STAFF");
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("encoded-password");
        ArgumentCaptor<UserRoleEntity> roleCaptor = ArgumentCaptor.forClass(UserRoleEntity.class);
        verify(userRoleMapper).insert(roleCaptor.capture());
        assertThat(roleCaptor.getValue().getRoleId()).isEqualTo(140L);
    }

    @Test
    void shouldRejectInvalidManagerStatus() {
        UserAccountEntity user = new UserAccountEntity();
        user.setId(12L);
        user.setStatus("ACTIVE");
        when(userAccountMapper.selectOne(any())).thenReturn(user);
        ManagerAccountUpdateRequest request = new ManagerAccountUpdateRequest();
        request.setStatus("DELETED");

        assertThatThrownBy(() -> service.updateManagerAccount(12L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Status must be ACTIVE or INACTIVE.");
    }

    @Test
    void shouldIncludeLifetimeRegistrationAndUniqueStudentCounts() {
        UserAccountEntity manager = new UserAccountEntity();
        manager.setId(12L);
        manager.setUsername("manager1");
        manager.setDisplayName("Manager One");
        Page<UserAccountEntity> page = new Page<>(1, 20);
        page.setRecords(java.util.List.of(manager));
        page.setTotal(1);
        when(userAccountMapper.selectPage(any(), any())).thenReturn(page);

        ManagerRegistrationStatsVO stats = new ManagerRegistrationStatsVO();
        stats.setUserId(12L);
        stats.setRegistrationCount(8L);
        stats.setUniqueStudentCount(6L);
        when(registrationStatsMapper.selectStatsByOperatorUserIds(java.util.List.of(12L)))
                .thenReturn(java.util.List.of(stats));

        var result = service.pageManagerAccounts(new ManagerAccountPageQuery());

        assertThat(result.records()).singleElement().satisfies(account -> {
            assertThat(account.registrationCount()).isEqualTo(8L);
            assertThat(account.uniqueStudentCount()).isEqualTo(6L);
        });
    }
}
