package com.lingxi.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解（Redis 计数器实现）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 限流键前缀，默认取方法名 */
    String scope() default "";

    /** 窗口内最大请求数 */
    int limit();

    /** 窗口大小（秒） */
    int windowSeconds();

    /** 按用户或按 IP 限流 */
    KeyType by() default KeyType.USER;

    enum KeyType { USER, IP }
}
