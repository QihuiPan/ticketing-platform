package com.portfolio.ticketing.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long REQUEST_LIMIT = 30;

    private final StringRedisTemplate redis;
    private final Counter fallbackCounter;
    private final Counter rejectedCounter;

    public RateLimitFilter(StringRedisTemplate redis, MeterRegistry registry) {
        this.redis = redis;
        this.fallbackCounter = registry.counter("rate_limit_fallback_total");
        this.rejectedCounter = registry.counter("rate_limit_rejected_total");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod())
                && (request.getRequestURI().equals("/api/holds")
                || request.getRequestURI().equals("/api/payments")));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String principal = request.getUserPrincipal() == null
                ? request.getRemoteAddr()
                : request.getUserPrincipal().getName();
        String key = "rate:" + request.getRequestURI() + ":" + principal;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofMinutes(1));
            }
            if (count != null && count > REQUEST_LIMIT) {
                rejectedCounter.increment();
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests\"}");
                return;
            }
        } catch (RuntimeException redisUnavailable) {
            fallbackCounter.increment();
        }
        filterChain.doFilter(request, response);
    }
}
