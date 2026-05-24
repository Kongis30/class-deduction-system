package com.classdeduction.system.modules.auth.service;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.dto.LoginRequest;
import com.classdeduction.system.modules.auth.dto.ChangePasswordRequest;
import com.classdeduction.system.modules.auth.dto.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    /**
     * 刷新当前会话：基于已有 token 重新生成 session。
     * 用于前端主动续期，避免 session 过期导致被迫重新登录。
     */
    LoginResponse refreshSession(String authorizationHeader);

    CurrentUserResponse requireCurrentUser(String authorizationHeader);

    void logout(String authorizationHeader);

    /**
     * 自服务修改密码：验证原密码后更新为新密码。
     */
    void changePassword(ChangePasswordRequest request, CurrentUserResponse currentUser);
}
