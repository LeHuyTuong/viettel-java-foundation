# Filter (Day 1)

> Tham khảo trong project: `src/main/java/com/learning/w1/filter/LoggingFilter.java`

## 1. Filter là gì?

Filter là tầng **ngoài cùng** trong xử lý HTTP request của Java web app. Nó chạy ở mức **Servlet container** (Tomcat), trước khi request đến Spring DispatcherServlet.

Nhiệm vụ điển hình:
- Logging request thô (URL, method, header, IP)
- Đo response time tổng thể
- CORS handling
- Đọc/gắn trace header (W3C traceparent)
- Authentication header check sơ bộ
- Rate limiting theo IP

## 2. Filter biết và KHÔNG biết gì?

| Thông tin | Filter có biết? |
|---|:---:|
| URL, method, header | ✓ |
| Body (raw bytes) | ✓ (nhưng đọc xong là hết, cần wrap nếu muốn đọc lại) |
| IP client | ✓ |
| Controller method sẽ chạy | ✗ |
| Business object | ✗ |
| Return value của controller | ✗ |

→ Filter **không biết** Spring sẽ route đến controller nào, vì nó chạy TRƯỚC khi Spring quyết định.

## 3. Bộ khung tối thiểu

```java
@Component
public class LoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);   // chạy các tầng trong
        } finally {
            long duration = System.currentTimeMillis() - start;
            log.info("{} {} - {}ms",
                     request.getMethod(),
                     request.getRequestURI(),
                     duration);
        }
    }
}
```

**Các điểm cốt lõi:**

1. `@Component` — Spring Boot tự động đăng ký vào servlet chain. Không cần `web.xml` hay code đăng ký thủ công.

2. `extends OncePerRequestFilter` — đảm bảo filter chỉ chạy 1 lần per request, kể cả khi có forward / include internal. Đây là class tiện ích của Spring; nếu dùng `Filter` thuần thì phải tự xử lý.

3. `filterChain.doFilter(request, response)` — đây là **lệnh "chuyển tiếp"** sang tầng trong. Nếu không gọi, request sẽ dừng lại ở Filter (dùng cho block request: rate limit, auth fail).

4. `try { ... } finally { ... }` — quan trọng nhất. Đảm bảo log/đo duration chạy được **kể cả khi tầng trong ném exception**.

## 4. Vì sao `try/finally`, không phải `try/catch`?

```java
// SAI - nuốt exception, client nhận response 200 OK rỗng
try {
    filterChain.doFilter(...);
} catch (Exception e) {
    log.info(...);   // ✗ nuốt mất exception
}

// ĐÚNG - vẫn để exception bay lên, chỉ thêm hành vi đo đạc
try {
    filterChain.doFilter(...);
} finally {
    log.info(...);   // ✓ luôn chạy, không can thiệp exception
}
```

`finally` đảm bảo code đo duration vẫn chạy, nhưng KHÔNG ngăn exception bay lên Tomcat. Đây là pattern chuẩn cho mọi "đo đạc / cleanup": Filter, Interceptor afterCompletion, AOP `@Around`, JDBC connection close, file close...

## 5. Thứ tự log — gotcha cần hiểu

Vì Filter chỉ log ở `finally` (sau `doFilter` xong), dòng log Filter sẽ xuất hiện **cuối cùng** trong output:

```
[START]  GET /api/hello       ← Interceptor preHandle (trong)
[AOP]    hello() executed     ← AOP (trong cùng)
[DONE]   GET /api/hello → 200 ← Interceptor postHandle
[END]    GET /api/hello       ← Interceptor afterCompletion
GET /api/hello - 12ms          ← Filter (ngoài cùng nhưng log cuối)
```

→ Đừng nhầm "log cuối" với "chạy cuối". Filter vẫn là tầng **VÀO ĐẦU TIÊN**, chỉ là không log lúc vào thôi.

**Nếu muốn thấy rõ thứ tự**, thêm 1 dòng log trước `try`:

```java
log.info("[FILTER IN]  {} {}", request.getMethod(), request.getRequestURI());
try {
    filterChain.doFilter(request, response);
} finally {
    log.info("[FILTER OUT] {} {} - {}ms", ...);
}
```

Lúc này thứ tự sẽ là `[FILTER IN]` → ... → `[FILTER OUT]` — đúng mô hình ngoặc lồng.

## 6. Có thể đăng ký Filter mà không cần `@Component`?

Có 2 cách:

```java
// Cách 1: @Component (đơn giản nhất, dùng cho project nhỏ)
@Component
public class MyFilter extends OncePerRequestFilter { ... }

// Cách 2: FilterRegistrationBean (linh hoạt hơn)
@Configuration
public class FilterConfig {
    @Bean
    public FilterRegistrationBean<MyFilter> myFilter() {
        FilterRegistrationBean<MyFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new MyFilter());
        reg.addUrlPatterns("/api/*");        // chỉ áp dụng path này
        reg.setOrder(1);                      // thứ tự nếu có nhiều filter
        return reg;
    }
}
```

→ Khi cần control URL pattern, thứ tự nhiều filter, hoặc disable filter có điều kiện → dùng `FilterRegistrationBean`.

## 7. Filter chain ngoài Filter của bạn

Spring Boot tự đăng ký nhiều Filter built-in. Một số quan trọng:

- `CharacterEncodingFilter` — set UTF-8
- `FormContentFilter` — parse form body cho PUT/DELETE
- `RequestContextFilter` — expose `RequestContextHolder` cho code sau
- `OncePerRequestFilter` (parent class bạn dùng) — đảm bảo chỉ chạy 1 lần

Trong stack trace `/api/error` bạn từng thấy, có các Filter này theo thứ tự. Đó là lý do gọi là "chain".

## 8. Filter vs Interceptor — quyết định nhanh

| Yêu cầu | Tầng |
|---|---|
| Chỉ cần thông tin HTTP (URL, header) | Filter |
| Cần biết controller method | Interceptor |
| Áp dụng cho mọi request (kể cả static file) | Filter |
| Chỉ áp dụng cho controller (`/api/**`) | Interceptor |

→ Có thể coi Interceptor là "Filter biết về Spring MVC".
