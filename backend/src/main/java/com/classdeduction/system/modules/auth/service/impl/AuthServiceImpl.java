package com.classdeduction.system.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.dto.LoginRequest;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.auth.config.AuthProperties;
import com.classdeduction.system.modules.auth.dto.LoginResponse;
import com.classdeduction.system.modules.auth.entity.AuthSessionEntity;
import com.classdeduction.system.modules.auth.entity.UserAccountEntity;
import com.classdeduction.system.modules.auth.mapper.AuthSessionMapper;
import com.classdeduction.system.modules.auth.mapper.UserAccountMapper;
import com.classdeduction.system.modules.auth.mapper.UserRoleMapper;
import com.classdeduction.system.modules.auth.model.AuthSessionPayload;
import com.classdeduction.system.modules.auth.service.AuthService;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 认证服务：负责用户名密码校验、会话生成（Redis 优先，MySQL 降级）以及当前登录用户解析。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String SESSION_PREFIX = "auth:session:";

    private final UserAccountMapper userAccountMapper;
    private final UserRoleMapper userRoleMapper;
    private final TeacherMapper teacherMapper;
    private final StudentMapper studentMapper;
    private final AuthSessionMapper authSessionMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request) {
        UserAccountEntity user = requireUserByUsername(request.getUsername());
        validateUserStatus(user);
        validateEntityBinding(user.getUserType(), user.getTeacherId(), user.getStudentId());
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("INVALID_CREDENTIALS", "Invalid username or password.");
        }

        List<String> roleCodes = userRoleMapper.listRoleCodesByUserId(user.getId());
        CurrentUserResponse currentUser = new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getUserType(),
                user.getTeacherId(),
                user.getStudentId(),
                roleCodes
        );
        String token = createSession(currentUser);
        user.setLastLoginAt(AppTime.nowLocalDateTime());
        userAccountMapper.updateById(user);
        return new LoginResponse(token, currentUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse refreshSession(String authorizationHeader) {
        CurrentUserResponse currentUser = requireCurrentUser(authorizationHeader);
        String newToken = createSession(currentUser);
        return new LoginResponse(newToken, currentUser);
    }

    @Override
    public CurrentUserResponse requireCurrentUser(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        if (!StringUtils.hasText(token)) {
            throw new BusinessException("UNAUTHORIZED", "Please sign in to continue.");
        }

        // Try Redis first
        try {
            String sessionJson = stringRedisTemplate.opsForValue().get(sessionKey(token));
            if (StringUtils.hasText(sessionJson)) {
                AuthSessionPayload payload = objectMapper.readValue(sessionJson, AuthSessionPayload.class);
                validateEntityBinding(payload.userType(), payload.teacherId(), payload.studentId());
                return toCurrentUser(payload);
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable during session lookup, falling back to MySQL: {}", e.getMessage());
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize session from Redis", e);
        }

        // Fallback to MySQL
        AuthSessionEntity dbSession = authSessionMapper.selectOne(new LambdaQueryWrapper<AuthSessionEntity>()
                .eq(AuthSessionEntity::getToken, token)
                .gt(AuthSessionEntity::getExpiresAt, AppTime.nowLocalDateTime())
                .eq(AuthSessionEntity::getIsDeleted, 0)
                .last("LIMIT 1"));

        if (dbSession == null) {
            throw new BusinessException("UNAUTHORIZED", "Your session has expired. Please sign in again.");
        }

        try {
            AuthSessionPayload payload = objectMapper.readValue(dbSession.getSessionData(), AuthSessionPayload.class);
            validateEntityBinding(payload.userType(), payload.teacherId(), payload.studentId());
            // Try to re-populate Redis for next request
            backfillRedis(token, payload);
            return toCurrentUser(payload);
        } catch (JsonProcessingException e) {
            throw new BusinessException("UNAUTHORIZED", "Your session could not be restored. Please sign in again.");
        }
    }

    @Override
    public void logout(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        if (!StringUtils.hasText(token)) {
            return;
        }
        // Delete from Redis (ignore failure if Redis is down)
        try {
            stringRedisTemplate.delete(sessionKey(token));
        } catch (RedisConnectionFailureException e) {
            log.warn("Could not delete Redis session (Redis may be down): {}", e.getMessage());
        }
        // Always delete from MySQL (source of truth)
        authSessionMapper.delete(new LambdaQueryWrapper<AuthSessionEntity>()
                .eq(AuthSessionEntity::getToken, token));
    }

    private String createSession(CurrentUserResponse currentUser) {
        String token = UUID.randomUUID().toString().replace("-", "");
        AuthSessionPayload payload = new AuthSessionPayload(
                currentUser.userId(),
                currentUser.username(),
                currentUser.displayName(),
                currentUser.userType(),
                currentUser.teacherId(),
                currentUser.studentId(),
                currentUser.roleCodes()
        );

        // Try Redis first
        boolean persistedToMysql = false;
        try {
            stringRedisTemplate.opsForValue().set(
                    sessionKey(token),
                    objectMapper.writeValueAsString(payload),
                    Duration.ofHours(authProperties.getSessionTtlHours())
            );
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable during login, falling back to MySQL session: {}", e.getMessage());
            persistedToMysql = persistSessionToMysql(token, currentUser, payload);
        } catch (JsonProcessingException e) {
            log.error("JSON serialization failed for session payload", e);
            throw new BusinessException("SESSION_WRITE_ERROR", "Unable to create a login session right now.");
        }

        // If Redis write failed, ensure MySQL has the session
        if (persistedToMysql) {
            log.info("Session persisted to MySQL only (Redis unavailable)");
        }

        return token;
    }

    /**
     * Persists session to MySQL auth_session table as fallback.
     * Returns true if session was written to MySQL.
     */
    private boolean persistSessionToMysql(String token, CurrentUserResponse currentUser, AuthSessionPayload payload) {
        try {
            String sessionData = objectMapper.writeValueAsString(payload);
            AuthSessionEntity session = new AuthSessionEntity();
            session.setToken(token);
            session.setUserId(currentUser.userId());
            session.setSessionData(sessionData);
            session.setExpiresAt(AppTime.nowLocalDateTime().plusHours(authProperties.getSessionTtlHours()));
            authSessionMapper.insert(session);
            return true;
        } catch (JsonProcessingException e) {
            log.error("Failed to persist session to MySQL", e);
            throw new BusinessException("SESSION_WRITE_ERROR", "Unable to create a login session right now.");
        } catch (DataAccessException e) {
            log.error("Failed to insert session into MySQL", e);
            throw new BusinessException("SESSION_WRITE_ERROR", "Unable to create a login session right now.");
        }
    }

    /**
     * Attempts to backfill session into Redis after MySQL fallback read.
     * Silently ignored if Redis is unavailable — MySQL remains source of truth.
     */
    private void backfillRedis(String token, AuthSessionPayload payload) {
        try {
            stringRedisTemplate.opsForValue().set(
                    sessionKey(token),
                    objectMapper.writeValueAsString(payload),
                    Duration.ofHours(authProperties.getSessionTtlHours())
            );
            log.info("Session backfilled to Redis from MySQL fallback");
        } catch (RedisConnectionFailureException e) {
            log.debug("Could not backfill session to Redis (still unavailable): {}", e.getMessage());
        } catch (JsonProcessingException e) {
            log.debug("Could not backfill session to Redis (serialization error): {}", e.getMessage());
        }
    }

    private CurrentUserResponse toCurrentUser(AuthSessionPayload payload) {
        return new CurrentUserResponse(
                payload.userId(),
                payload.username(),
                payload.displayName(),
                payload.userType(),
                payload.teacherId(),
                payload.studentId(),
                payload.roleCodes()
        );
    }

    private String extractBearerToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            return null;
        }
        if (authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorizationHeader.substring(7).trim();
        }
        return authorizationHeader.trim();
    }

    private String sessionKey(String token) {
        return SESSION_PREFIX + token;
    }

    private UserAccountEntity requireUserByUsername(String username) {
        UserAccountEntity user = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccountEntity>()
                .eq(UserAccountEntity::getUsername, username)
                .eq(UserAccountEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (user == null) {
            throw new BusinessException("INVALID_CREDENTIALS", "Invalid username or password.");
        }
        return user;
    }

    private void validateUserStatus(UserAccountEntity user) {
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new BusinessException("ACCOUNT_DISABLED", "This account is not active. Please contact an administrator.");
        }
    }

    private void validateEntityBinding(String userType, Long teacherId, Long studentId) {
        if ("TEACHER".equalsIgnoreCase(userType)) {
            if (teacherId == null) {
                throw new BusinessException("TEACHER_BINDING_REQUIRED", "This teacher account is not linked to an active teacher profile.");
            }
            TeacherEntity teacher = teacherMapper.selectById(teacherId);
            if (teacher == null || !"ACTIVE".equalsIgnoreCase(teacher.getStatus())) {
                throw new BusinessException("TEACHER_BINDING_INVALID", "This teacher account is not linked to an active teacher profile.");
            }
        } else if ("STUDENT".equalsIgnoreCase(userType)) {
            if (studentId == null) {
                throw new BusinessException("STUDENT_BINDING_REQUIRED", "This student account is not linked to an active student profile.");
            }
            StudentEntity student = studentMapper.selectById(studentId);
            if (student == null || !"ACTIVE".equalsIgnoreCase(student.getStatus())) {
                throw new BusinessException("STUDENT_BINDING_INVALID", "This student account is not linked to an active student profile.");
            }
        }
    }
}
