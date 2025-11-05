package com.routeforge.api.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter using Bucket4j
 */
@Slf4j
@Component
public class RateLimitFilter implements Filter {
    
    @Value("${routeforge.api.rate-limit.capacity:100}")
    private long capacity;
    
    @Value("${routeforge.api.rate-limit.refill-tokens:100}")
    private long refillTokens;
    
    @Value("${routeforge.api.rate-limit.refill-duration-sec:60}")
    private long refillDurationSec;
    
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // Skip rate limiting for actuator and docs
        String path = httpRequest.getRequestURI();
        if (path.startsWith("/actuator") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs")) {
            chain.doFilter(request, response);
            return;
        }
        
        String clientId = getClientId(httpRequest);
        Bucket bucket = buckets.computeIfAbsent(clientId, k -> createBucket());
        
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for client: {}", clientId);
            httpResponse.setStatus(429);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write(
                "{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Try again later.\"}"
            );
        }
    }
    
    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.classic(
            capacity,
            Refill.intervally(refillTokens, Duration.ofSeconds(refillDurationSec))
        );
        return Bucket.builder()
            .addLimit(limit)
            .build();
    }
    
    private String getClientId(HttpServletRequest request) {
        // Use client IP as identifier
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getRemoteAddr();
        }
        return clientIp;
    }
}
