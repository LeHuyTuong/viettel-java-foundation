package com.learning.w1.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        try{
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("{} {} - {}ms", request.getMethod(), request.getRequestURI(), duration);
        }
    }
}
