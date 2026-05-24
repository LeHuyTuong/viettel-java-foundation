package com.learning.w1.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

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
// @Component  ← bỏ comment này khi bạn đã implement TraceContext xong
public class TracingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // TODO: đọc header "traceparent" từ request
        // TODO: parse hoặc tạo mới TraceContext
        // TODO: log trace info
        // TODO: gắn header vào response

        filterChain.doFilter(request, response);
    }
}
