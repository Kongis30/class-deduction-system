package com.classdeduction.system.modules.auth;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.dto.LoginRequest;
import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.dto.LoginResponse;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
import com.classdeduction.system.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller skeleton.
 * It currently returns stable request and response shapes before real auth is wired in.
 */
@Tag(name = "Auth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Sign in with username and password")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("Signed in successfully.", authService.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current signed-in user")
    public ApiResponse<CurrentUserResponse> currentUser(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestAttribute(value = AuthInterceptor.CURRENT_USER_ATTRIBUTE, required = false) CurrentUserResponse currentUser
    ) {
        CurrentUserResponse resolvedUser = currentUser == null
                ? authService.requireCurrentUser(authorizationHeader)
                : currentUser;
        return ApiResponse.success(resolvedUser);
    }

    @PostMapping("/logout")
    @Operation(summary = "Sign out the current session")
    public ApiResponse<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        authService.logout(authorizationHeader);
        return ApiResponse.success("Signed out successfully.", null);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh the current session token to extend expiry")
    public ApiResponse<LoginResponse> refresh(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        return ApiResponse.success("Session refreshed.", authService.refreshSession(authorizationHeader));
    }
}
