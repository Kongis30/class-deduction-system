package com.classdeduction.system.modules.auth.service;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.dto.LoginRequest;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.auth.config.AuthProperties;
import com.classdeduction.system.modules.auth.dto.LoginResponse;
import com.classdeduction.system.modules.auth.entity.AuthSessionEntity;
import com.classdeduction.system.modules.auth.entity.UserAccountEntity;
import com.classdeduction.system.modules.auth.mapper.AuthSessionMapper;
import com.classdeduction.system.modules.auth.mapper.UserAccountMapper;
import com.classdeduction.system.modules.auth.mapper.UserRoleMapper;
import com.classdeduction.system.modules.auth.service.impl.AuthServiceImpl;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 认证服务单元测试：覆盖登录、会话恢复和登出这几个最关键的认证闭环。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserAccountMapper userAccountMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private TeacherMapper teacherMapper;

    @Mock
    private AuthSessionMapper authSessionMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private final AuthProperties authProperties = new AuthProperties();

    @InjectMocks
    private AuthServiceImpl authService;

    @Captor
    private ArgumentCaptor<String> sessionKeyCaptor;

    @Captor
    private ArgumentCaptor<String> sessionValueCaptor;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userAccountMapper,
                userRoleMapper,
                teacherMapper,
                authSessionMapper,
                stringRedisTemplate,
                objectMapper,
                passwordEncoder,
                authProperties
        );
    }

    @Test
    void shouldLoginAndPersistRedisSession() {
        UserAccountEntity user = buildActiveUser();
        when(userAccountMapper.selectOne(any())).thenReturn(user);
        when(userRoleMapper.listRoleCodesByUserId(1001L)).thenReturn(List.of("STAFF"));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        LoginRequest request = new LoginRequest();
        request.setUsername("staff");
        request.setPassword("Staff@123456");

        LoginResponse response = authService.login(request);

        assertThat(response.user().username()).isEqualTo("staff");
        assertThat(response.user().roleCodes()).containsExactly("STAFF");
        assertThat(response.token()).isNotBlank();
        verify(valueOperations).set(sessionKeyCaptor.capture(), sessionValueCaptor.capture(), eq(Duration.ofHours(12)));
        assertThat(sessionKeyCaptor.getValue()).startsWith("auth:session:");
        assertThat(sessionValueCaptor.getValue()).contains("\"username\":\"staff\"");
        verify(userAccountMapper).updateById(user);
    }

    @Test
    void shouldRejectInvalidPassword() {
        UserAccountEntity user = buildActiveUser();
        when(userAccountMapper.selectOne(any())).thenReturn(user);

        LoginRequest request = new LoginRequest();
        request.setUsername("staff");
        request.setPassword("wrong-password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid username or password.");
    }

    @Test
    void shouldRestoreCurrentUserFromBearerToken() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:session:token-123")).thenReturn(objectMapper.writeValueAsString(
                new com.classdeduction.system.modules.auth.model.AuthSessionPayload(
                        1001L,
                        "staff",
                        "Front Desk Staff",
                        "STAFF",
                        null,
                        List.of("STAFF")
                )
        ));

        CurrentUserResponse currentUser = authService.requireCurrentUser("Bearer token-123");

        assertThat(currentUser.username()).isEqualTo("staff");
        assertThat(currentUser.roleCodes()).containsExactly("STAFF");
    }

    @Test
    void shouldDeleteSessionOnLogout() {
        authService.logout("Bearer token-123");

        verify(stringRedisTemplate).delete("auth:session:token-123");
        verify(authSessionMapper).delete(any());
    }

    @Test
    void shouldLoginWithMysqlFallbackWhenRedisUnavailable() {
        UserAccountEntity user = buildActiveUser();
        when(userAccountMapper.selectOne(any())).thenReturn(user);
        when(userRoleMapper.listRoleCodesByUserId(1001L)).thenReturn(List.of("STAFF"));
        // Override opsForValue to always throw, triggering MySQL fallback path
        when(stringRedisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("connection refused"));

        LoginRequest request = new LoginRequest();
        request.setUsername("staff");
        request.setPassword("Staff@123456");

        LoginResponse response = authService.login(request);

        assertThat(response.user().username()).isEqualTo("staff");
        assertThat(response.token()).isNotBlank();
        verify(authSessionMapper).insert(any(AuthSessionEntity.class));
    }

    @Test
    void shouldRefreshSessionAndReturnNewToken() throws Exception {
        CurrentUserResponse currentUser = new CurrentUserResponse(
                1001L, "staff", "Front Desk Staff", "STAFF", null, List.of("STAFF"));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenReturn(objectMapper.writeValueAsString(
                new com.classdeduction.system.modules.auth.model.AuthSessionPayload(
                        1001L, "staff", "Front Desk Staff", "STAFF", null, List.of("STAFF")
                )
        ));

        LoginResponse refreshed = authService.refreshSession("Bearer old-token-456");

        assertThat(refreshed.user().username()).isEqualTo("staff");
        assertThat(refreshed.token()).isNotBlank();
        assertThat(refreshed.token()).isNotEqualTo("old-token-456");
    }

    @Test
    void shouldRejectTeacherLoginWhenTeacherBindingIsInactive() {
        UserAccountEntity user = buildTeacherUser();
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(9001L);
        teacher.setStatus("INACTIVE");
        when(userAccountMapper.selectOne(any())).thenReturn(user);
        when(teacherMapper.selectById(9001L)).thenReturn(teacher);

        LoginRequest request = new LoginRequest();
        request.setUsername("teacher");
        request.setPassword("Teacher@123456");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("This teacher account is not linked to an active teacher profile.");
    }

    private UserAccountEntity buildActiveUser() {
        UserAccountEntity user = new UserAccountEntity();
        user.setId(1001L);
        user.setUsername("staff");
        user.setPasswordHash(passwordEncoder.encode("Staff@123456"));
        user.setUserType("STAFF");
        user.setDisplayName("Front Desk Staff");
        user.setStatus("ACTIVE");
        user.setIsDeleted(0);
        return user;
    }

    private UserAccountEntity buildTeacherUser() {
        UserAccountEntity user = new UserAccountEntity();
        user.setId(1002L);
        user.setUsername("teacher");
        user.setPasswordHash(passwordEncoder.encode("Teacher@123456"));
        user.setUserType("TEACHER");
        user.setDisplayName("Teacher User");
        user.setTeacherId(9001L);
        user.setStatus("ACTIVE");
        user.setIsDeleted(0);
        return user;
    }
}
