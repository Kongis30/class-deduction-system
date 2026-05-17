package com.classdeduction.system.modules.auth.model;

import java.util.List;

/**
 * Session payload persisted in Redis for lightweight login state.
 */
public record AuthSessionPayload(
        Long userId,
        String username,
        String displayName,
        String userType,
        Long teacherId,
        List<String> roleCodes
) {
}
