package com.classdeduction.system.modules.auth.service;

import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.auth.dto.TeacherAccountCreateRequest;
import com.classdeduction.system.modules.auth.entity.RoleEntity;
import com.classdeduction.system.modules.auth.entity.UserAccountEntity;
import com.classdeduction.system.modules.auth.entity.UserRoleEntity;
import com.classdeduction.system.modules.auth.mapper.RoleMapper;
import com.classdeduction.system.modules.auth.mapper.UserAccountMapper;
import com.classdeduction.system.modules.auth.mapper.UserRoleMapper;
import com.classdeduction.system.modules.auth.service.impl.TeacherAccountServiceImpl;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 教师账号服务测试：确保老师账号和老师档案保持一对一绑定。
 */
@ExtendWith(MockitoExtension.class)
class TeacherAccountServiceImplTest {

    @Mock
    private UserAccountMapper userAccountMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private TeacherMapper teacherMapper;

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Captor
    private ArgumentCaptor<UserAccountEntity> userCaptor;

    @Captor
    private ArgumentCaptor<UserRoleEntity> userRoleCaptor;

    private TeacherAccountServiceImpl teacherAccountService;

    @BeforeEach
    void setUp() {
        teacherAccountService = new TeacherAccountServiceImpl(
                userAccountMapper,
                userRoleMapper,
                roleMapper,
                teacherMapper,
                passwordEncoder
        );
    }

    @Test
    void shouldCreateTeacherAccountAndBindTeacherRole() {
        TeacherAccountCreateRequest request = new TeacherAccountCreateRequest();
        request.setTeacherId(701L);
        request.setUsername("emma.wang");
        request.setPassword("Teacher@123456");

        when(teacherMapper.selectById(701L)).thenReturn(buildActiveTeacher());
        when(userAccountMapper.selectOne(any())).thenReturn(null);
        when(roleMapper.selectOne(any())).thenReturn(buildTeacherRole());
        doAnswer(invocation -> {
            UserAccountEntity entity = invocation.getArgument(0);
            entity.setId(9001L);
            return 1;
        }).when(userAccountMapper).insert(any(UserAccountEntity.class));

        Long userId = teacherAccountService.createTeacherAccount(request);

        verify(userAccountMapper).insert(userCaptor.capture());
        verify(userRoleMapper).insert(userRoleCaptor.capture());
        assertThat(userId).isEqualTo(9001L);
        assertThat(userCaptor.getValue().getTeacherId()).isEqualTo(701L);
        assertThat(userCaptor.getValue().getUserType()).isEqualTo("TEACHER");
        assertThat(passwordEncoder.matches("Teacher@123456", userCaptor.getValue().getPasswordHash())).isTrue();
        assertThat(userRoleCaptor.getValue().getUserId()).isEqualTo(9001L);
        assertThat(userRoleCaptor.getValue().getRoleId()).isEqualTo(3003L);
    }

    @Test
    void shouldRejectWhenTeacherAlreadyHasAccount() {
        TeacherAccountCreateRequest request = new TeacherAccountCreateRequest();
        request.setTeacherId(701L);
        request.setUsername("emma.wang");
        request.setPassword("Teacher@123456");

        when(teacherMapper.selectById(701L)).thenReturn(buildActiveTeacher());
        when(userAccountMapper.selectOne(any()))
                .thenReturn(buildExistingTeacherAccount());

        assertThatThrownBy(() -> teacherAccountService.createTeacherAccount(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("This teacher is already linked to a login account.");
    }

    private TeacherEntity buildActiveTeacher() {
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(701L);
        teacher.setTeacherNo("T-701");
        teacher.setTeacherName("Emma Wang");
        teacher.setEmail("emma@example.com");
        teacher.setMobile("1234567890");
        teacher.setStatus("ACTIVE");
        return teacher;
    }

    private RoleEntity buildTeacherRole() {
        RoleEntity role = new RoleEntity();
        role.setId(3003L);
        role.setRoleCode("TEACHER");
        role.setStatus("ACTIVE");
        return role;
    }

    private UserAccountEntity buildExistingTeacherAccount() {
        UserAccountEntity entity = new UserAccountEntity();
        entity.setId(8111L);
        entity.setTeacherId(701L);
        return entity;
    }
}
