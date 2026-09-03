package com.lingxi.common.ratelimit;

import com.lingxi.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Optional;

/**
 * 基于 Redis Lua 脚本的滑动窗口计数限流。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String LUA = """
            local v = redis.call('INCR', KEYS[1])
            if v == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return v
            """;

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return true;
        }
        String identity = rateLimit.by() == RateLimit.KeyType.IP
                ? clientIp(request)
                : currentUserIdOrIp(request);
        String scope = Optional.ofNullable(rateLimit.scope()).filter(s -> !s.isBlank())
                .orElseGet(() -> handlerMethod.getMethod().getName());
        String key = "lingxi:rl:" + scope + ":" + identity;

        Long count = redisTemplate.execute(
                new DefaultRedisScript<>(LUA, Long.class), List.of(key), String.valueOf(rateLimit.windowSeconds()));

        if (count != null && count > rateLimit.limit()) {
            log.warn("限流触发 scope={} identity={} count={}", scope, identity, count);
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null,\"success\":false}");
            return false;
        }
        return true;
    }

    private String currentUserIdOrIp(HttpServletRequest request) {
        try {
            return String.valueOf(SecurityUtils.current().userId());
        } catch (Exception e) {
            return clientIp(request);
        }
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return Optional.ofNullable(request.getHeader("X-Real-IP")).orElse(request.getRemoteAddr());
    }
}
