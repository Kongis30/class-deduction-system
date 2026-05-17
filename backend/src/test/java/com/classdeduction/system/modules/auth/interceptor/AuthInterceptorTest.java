package com.classdeduction.system.modules.auth.interceptor;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.auth.config.AuthProperties;
import com.classdeduction.system.modules.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 认证拦截器单元测试：验证会话透传与角色访问边界。
 */
@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {

    @Mock
    private AuthService authService;

    @Test
    void shouldAllowRequestWhenRoleMatches() throws Exception {
        AuthProperties authProperties = new AuthProperties();
        AuthInterceptor interceptor = new AuthInterceptor(authProperties, authService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handlerMethod = new HandlerMethod(new DummyController(), DummyController.class.getMethod("staffOnly"));
        CurrentUserResponse currentUser = new CurrentUserResponse(1001L, "staff", "Front Desk Staff", "STAFF", null, List.of("STAFF"));
        when(authService.requireCurrentUser("Bearer token-123")).thenReturn(currentUser);

        boolean allowed = interceptor.preHandle(request, response, handlerMethod);

        assertThat(allowed).isTrue();
        assertThat(request.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE)).isEqualTo(currentUser);
    }

    @Test
    void shouldRejectRequestWhenRoleDoesNotMatch() throws Exception {
        AuthProperties authProperties = new AuthProperties();
        AuthInterceptor interceptor = new AuthInterceptor(authProperties, authService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handlerMethod = new HandlerMethod(new DummyController(), DummyController.class.getMethod("staffOnly"));
        when(authService.requireCurrentUser("Bearer token-123"))
                .thenReturn(new CurrentUserResponse(1002L, "teacher", "Teacher", "TEACHER", 9001L, List.of("TEACHER")));

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(BusinessException.class)
                .hasMessage("You do not have permission to access this resource.");
    }

    @Test
    void shouldBypassRoleCheckWhenInterceptorDisabled() throws Exception {
        AuthProperties authProperties = new AuthProperties();
        authProperties.setInterceptorEnabled(false);
        AuthInterceptor interceptor = new AuthInterceptor(authProperties, authService);

        boolean allowed = interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(),
                new HandlerMethod(new DummyController(), DummyController.class.getMethod("staffOnly")));

        assertThat(allowed).isTrue();
    }

    static class DummyController {

        @RequireRoles({"ADMIN", "STAFF"})
        public void staffOnly() {
        }
    }
}
