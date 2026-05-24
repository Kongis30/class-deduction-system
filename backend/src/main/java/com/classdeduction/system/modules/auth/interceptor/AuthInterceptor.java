package com.classdeduction.system.modules.auth.interceptor;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.auth.config.AuthProperties;
import com.classdeduction.system.modules.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Set;

/**
 * Request interceptor that validates the login session for protected API routes.
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String CURRENT_USER_ATTRIBUTE = "currentUser";

    private final AuthProperties authProperties;
    private final AuthService authService;

    private static final CurrentUserResponse DEV_DEFAULT = new CurrentUserResponse(
            2049011197944602625L, "admin", "System Administrator", "ADMIN", null, null, java.util.List.of("ADMIN"));

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!authProperties.isInterceptorEnabled()) {
            request.setAttribute(CURRENT_USER_ATTRIBUTE, DEV_DEFAULT);
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        CurrentUserResponse currentUser = authService.requireCurrentUser(request.getHeader(HttpHeaders.AUTHORIZATION));
        validateRoles((HandlerMethod) handler, currentUser);
        request.setAttribute(CURRENT_USER_ATTRIBUTE, currentUser);
        return true;
    }

    private void validateRoles(HandlerMethod handlerMethod, CurrentUserResponse currentUser) {
        // 角色校验入口：优先读取方法注解，没有则回退到类注解，便于未来细化到单个接口。
        RequireRoles requireRoles = handlerMethod.getMethodAnnotation(RequireRoles.class);
        if (requireRoles == null) {
            requireRoles = handlerMethod.getBeanType().getAnnotation(RequireRoles.class);
        }
        if (requireRoles == null || ObjectUtils.isEmpty(requireRoles.value())) {
            return;
        }

        Set<String> currentRoleSet = Set.copyOf(currentUser.roleCodes());
        boolean matched = Arrays.stream(requireRoles.value()).anyMatch(currentRoleSet::contains);
        if (!matched) {
            throw new BusinessException("FORBIDDEN", "You do not have permission to access this resource.");
        }
    }
}
