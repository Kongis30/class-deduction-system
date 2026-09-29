package com.classdeduction.system.modules.auth.support;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 统一生成新账号的初始登录凭据，供学生、老师和管理者账号服务复用。
 */
public final class AccountCredentialGenerator {

    public static final String DEFAULT_INITIAL_PASSWORD = "9297019872";
    private static final String USERNAME_SUFFIX = "@jin";
    private static final Pattern NAME_SEPARATOR = Pattern.compile("\\s+");

    private AccountCredentialGenerator() {
    }

    /**
     * 按“名+姓首字母@jin”生成基础用户名；服务层负责处理重名后缀。
     */
    public static String usernameFromName(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return "";
        }
        String[] parts = NAME_SEPARATOR.split(displayName.trim());
        if (parts.length < 2) {
            return "";
        }
        String firstName = normalizeNamePart(parts[0]);
        String lastName = normalizeNamePart(parts[parts.length - 1]);
        if (firstName.isEmpty() || lastName.isEmpty()) {
            return "";
        }
        return (firstName + lastName.substring(0, 1) + USERNAME_SUFFIX).toLowerCase(Locale.ENGLISH);
    }

    private static String normalizeNamePart(String value) {
        return value.codePoints()
                .filter(codePoint -> Character.isLetterOrDigit(codePoint))
                .collect(StringBuilder::new,
                        (builder, codePoint) -> builder.appendCodePoint(codePoint),
                        StringBuilder::append)
                .toString();
    }
}
