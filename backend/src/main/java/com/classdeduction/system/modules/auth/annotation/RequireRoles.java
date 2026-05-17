package com.classdeduction.system.modules.auth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色访问声明：标记在控制器类或方法上，表示当前接口允许的角色集合。
 * 拦截器读取该注解后执行“任一角色命中即可访问”的校验。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRoles {

    String[] value();
}
