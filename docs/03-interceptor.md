# Interceptor (Day 2)

> Tham khảo trong project:
> - `src/main/java/com/learning/w1/interceptor/LoggingInterceptor.java`
> - `src/main/java/com/learning/w1/interceptor/WebMvcConfig.java`

## 1. Interceptor là gì?

Interceptor là tầng nằm **bên trong Spring DispatcherServlet**, chạy sau khi Spring đã resolve được controller method (handler) nhưng trước khi method thực sự được gọi.

Khác với Filter (mức Servlet), Interceptor là khái niệm của **Spring MVC**. Nó biết controller method cụ thể nào sẽ chạy.

Nhiệm vụ điển hình:
- Check role/permission theo controller method
- Audit log "user X gọi method Y"
- Đo timing chi tiết theo controller
- Inject dữ liệu vào model trước khi render view
- Validation custom theo annotation trên handler

## 2. Ba hook của HandlerInterceptor

Khác Filter chỉ có 1 method, Interceptor có 3 hook tương ứng 3 thời điểm:

```java
public class LoggingInterceptor implements HandlerInterceptor {

    // 1. CHẠY TRƯỚC khi controller method được gọi
    //    return true  → cho đi tiếp
    //    return false → CHẶN request (controller không chạy)
    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        log.info("[START] {} {}", req.getMethod(), req.getRequestURI());
        return true;
    }

    // 2. CHẠY SAU khi controller method return,
    //    NHƯNG TRƯỚC khi view được render.
    //    KHÔNG chạy nếu controller ném exception.
    @Override
    public void postHandle(HttpServletRequest req, HttpServletResponse res,
                           Object handler, ModelAndView mv) {
        log.info("[DONE] {} {} → {}", req.getMethod(), req.getRequestURI(), res.getStatus());
    }

    // 3. CHẠY KHI TOÀN BỘ REQUEST KẾT THÚC (kể cả khi exception)
    //    Đây là chỗ cleanup, đóng resource, log final.
    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res,
                                Object handler, Exception ex) {
        log.info("[END] {} {} (Exception: {})",
                 req.getMethod(), req.getRequestURI(),
                 ex != null ? ex.getMessage() : "No");
    }
}
```

## 3. Sơ đồ chạy của 3 hook

```
preHandle()  ─── return true? ──┐
                                 ↓ (nếu false → DỪNG, controller KHÔNG chạy)
                            Controller method
                                 │
                                 ├── return bình thường ──→ postHandle()
                                 │                              │
                                 └── ném exception ─────────────┤
                                                                ↓
                                                         afterCompletion()
                                                         (luôn chạy)
```

**Hệ quả quan trọng:**

| Trường hợp | preHandle | controller | postHandle | afterCompletion |
|---|:---:|:---:|:---:|:---:|
| Bình thường | ✓ | ✓ | ✓ | ✓ |
| Controller ném exception | ✓ | ✓ (đến chỗ lỗi) | ✗ | ✓ (có `ex != null`) |
| preHandle return false | ✓ | ✗ | ✗ | ✗ |

→ Code cleanup phải đặt trong `afterCompletion`, vì chỉ chỗ đó **đảm bảo** chạy.
→ `postHandle` không đáng tin cậy cho cleanup — chỉ dùng để modify ModelAndView trước render.

## 4. Tham số `handler` — điểm khác Filter

```java
public boolean preHandle(HttpServletRequest req, HttpServletResponse res,
                         Object handler) {
    // handler chính là HandlerMethod, đại diện cho controller method
    if (handler instanceof HandlerMethod hm) {
        Method m = hm.getMethod();              // java.lang.reflect.Method
        String controllerName = hm.getBeanType().getSimpleName();
        boolean hasMyAnnotation = m.isAnnotationPresent(RequireAdmin.class);
        // ...
    }
    return true;
}
```

→ Đây là siêu mạnh: bạn có thể **đọc annotation custom** trên controller method để quyết định hành vi. Ví dụ: tạo annotation `@RequireAdmin`, gắn lên method nào cần, rồi Interceptor đọc annotation để chặn user thường.

Filter không làm được vì nó không biết controller method.

## 5. Đăng ký Interceptor — KHÁC Filter

Filter chỉ cần `@Component`, nhưng Interceptor PHẢI đăng ký qua `WebMvcConfigurer`:

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private LoggingInterceptor loggingInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loggingInterceptor)
                .addPathPatterns("/api/**")          // áp dụng path nào
                .excludePathPatterns("/api/public/**");  // trừ path nào
    }
}
```

Đây là lý do `@Component` lên `LoggingInterceptor` không đủ. Nó tạo bean, nhưng không kích hoạt vào MVC chain — phải gọi `addInterceptor()` thủ công.

## 6. Pattern hay dùng: lưu state qua request attribute

`preHandle` và `afterCompletion` là 2 method khác nhau — không thể dùng biến local. Cách truyền state:

```java
@Override
public boolean preHandle(HttpServletRequest req, ...) {
    req.setAttribute("startTime", System.currentTimeMillis());
    return true;
}

@Override
public void afterCompletion(HttpServletRequest req, ..., Exception ex) {
    long start = (long) req.getAttribute("startTime");
    long duration = System.currentTimeMillis() - start;
    log.info("Request took {}ms", duration);
}
```

→ `HttpServletRequest` là một "túi đựng state" sống cho đến khi request kết thúc. Mọi tầng đều đọc/ghi được.

## 7. Có thể có nhiều Interceptor không?

Có. Đăng ký nhiều cái, thứ tự theo lúc `addInterceptor()`:

```java
@Override
public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(authInterceptor).order(1);      // chạy preHandle trước
    registry.addInterceptor(loggingInterceptor).order(2);   // chạy preHandle sau
}
```

Thứ tự `afterCompletion` ngược lại (LIFO), giống pattern ngoặc lồng.

## 8. Interceptor vs Filter — khi nào chọn gì?

| Yếu tố | Filter | Interceptor |
|---|---|---|
| Mức | Servlet (Tomcat) | Spring MVC |
| Áp dụng cho | Mọi request (kể cả static, jsp) | Chỉ request đi qua DispatcherServlet |
| Biết Controller | ✗ | ✓ |
| URL pattern | Servlet pattern (`/api/*`) | Spring pattern (`/api/**`, hỗ trợ regex) |
| Số method | 1 (`doFilter`) | 3 (`preHandle/postHandle/afterCompletion`) |
| DI Spring đầy đủ | Một phần | Đầy đủ |
| Có thể can thiệp response body | ✓ (qua wrapper) | Khó hơn |

**Quy tắc nhanh:**
- Logic dựa trên HTTP thô (header, IP, CORS) → Filter
- Logic dựa trên controller cụ thể (annotation, role) → Interceptor

## 9. Câu hỏi tự kiểm tra

1. Vì sao Filter chỉ cần `@Component` mà Interceptor cần đăng ký qua `WebMvcConfigurer`?
2. Khi `/api/error` ném RuntimeException, 3 hook nào của Interceptor chạy?
3. Đâu là chỗ DUY NHẤT trong Interceptor mà cleanup chắc chắn chạy?
4. Làm sao đọc `@RequireAdmin` custom annotation trên controller method trong `preHandle`?
