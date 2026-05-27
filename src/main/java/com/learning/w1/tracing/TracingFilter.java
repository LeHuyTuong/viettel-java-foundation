package com.learning.w1.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// BÀI TẬP NÂNG CAO - Ngày 5 (Thu 29/5)
// Kết hợp Filter (ngày 1) + TraceContext (ngày 4-5)
//
// Nhiệm vụ:
//   1. Đọc header "traceparent" từ incoming request
//   2. Nếu có → parse thành TraceContext (context propagation từ service khác)
//   3. Nếu không có → tạo TraceContext mới (đây là origin request)
//   4. Gắn traceId vào response header "traceparent"
//   5. In ra log: "[TRACE] traceId=xxx spanId=yyy GET /api/hello"
//
// Đây chính là cơ chế cốt lõi của distributed tracing.
// Bước tiếp theo (W2+) sẽ lưu context này vào ThreadLocal để các layer khác đọc được.
@Component
@Order(1) // Chạy trước các filter khác (nếu có)
public class TracingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TracingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        // đọc header "traceparent" từ request
        String traceparent = request.getHeader("traceparent");

        // parse hoặc tạo mới TraceContext
        TraceContext traceContext = TraceContext.fromHeader(traceparent);

        try {
            MDC.put("traceId", traceContext.getTraceId());
            MDC.put("spanId", traceContext.getSpanId());

            // log trace info
            log.info("[TRACE] traceId={} spanId={} {} {}", traceContext.getTraceId(), traceContext.getSpanId(),
            request.getMethod(), request.getRequestURI());
            // gắn header vào response
            response.setHeader("traceparent", traceContext.toHeader());

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();

        }

    }
}
