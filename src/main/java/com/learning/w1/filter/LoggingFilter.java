package com.learning.w1.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

// BÀI TẬP - Ngày 1 (Sun 25/5)
// Đọc: https://www.baeldung.com/spring-boot-add-filter
//
// Nhiệm vụ: in ra "[METHOD] [URL] - [duration]ms" cho mọi request
// Ví dụ output: GET /api/hello - 12ms
//
// Gợi ý:
//   - System.currentTimeMillis() để lấy thời gian
//   - request.getMethod() → "GET", "POST"...
//   - request.getRequestURI() → "/api/hello"
//   - filterChain.doFilter() là bước chạy các tầng tiếp theo
//   - Đo duration = thời gian SAU doFilter - thời gian TRƯỚC doFilter
@Component
public class LoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // TODO: ghi lại thời điểm bắt đầu

        filterChain.doFilter(request, response);

        // TODO: tính duration và in ra log
    }
}
