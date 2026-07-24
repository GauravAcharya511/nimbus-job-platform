package com.gauravacharya.nimbus.ratelimit;

import com.gauravacharya.nimbus.metrics.JobMetrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Throttles authenticated callers. Runs after authentication so the bucket is keyed
 * by user rather than by IP, which would penalise everyone behind a shared address.
 */
@Component
@Order(100)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;
    private final JobMetrics metrics;

    public RateLimitFilter(RateLimiter limiter, JobMetrics metrics) {
        this.limiter = limiter;
        this.metrics = metrics;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth") || path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getDetails() == null) {
            chain.doFilter(request, response);
            return;
        }

        String identity = auth.getDetails().toString();
        if (!limiter.tryAcquire(identity)) {
            metrics.recordRateLimited();
            response.setStatus(429);
            response.setContentType("application/problem+json");
            response.getWriter().write(
                    "{\"status\":429,\"title\":\"Too Many Requests\","
                  + "\"detail\":\"Rate limit exceeded. Retry shortly.\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
