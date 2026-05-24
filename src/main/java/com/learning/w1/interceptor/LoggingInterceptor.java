package com.learning.w1.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// BÀI TẬP - Ngày 2 (Mon 26/5)
// Đọc: https://www.baeldung.com/spring-mvc-handlerinterceptor
//
// Nhiệm vụ: dùng cả 3 hook để log lifecycle của request
//   - preHandle:       "[START] GET /api/hello"
//   - postHandle:      "[DONE]  GET /api/hello → 200"
//   - afterCompletion: "[END]   GET /api/hello (có exception không?)"
//
// Câu hỏi cần hiểu sau bài này:
//   - Tại sao Interceptor truy cập được handler (Controller method) còn Filter thì không?
//   - afterCompletion chạy kể cả khi có exception — dùng trường hợp nào?
@Component
public class LoggingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        // TODO: log lúc request bắt đầu vào Spring MVC
        // return true để cho request đi tiếp, false để chặn lại
        log.info("[START] {} {}", request.getMethod(), request.getRequestURI());
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler,
                           ModelAndView modelAndView) throws Exception {
        log.info("[DONE] {} {} → {}", request.getMethod(), request.getRequestURI(), response.getStatus());
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) throws Exception {
        // TODO: log khi toàn bộ request hoàn tất (kể cả khi có exception)
        // Hint: if (ex != null) → có lỗi xảy ra
        log.info("[END] {} {} (Exception: {})", request.getMethod(), request.getRequestURI(), 
        ex != null ? ex.getMessage() : "No");
    }
}
