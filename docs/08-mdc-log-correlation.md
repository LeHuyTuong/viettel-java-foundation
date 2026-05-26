# MDC + Log Correlation — kỹ thuật pro nối tiếp Day 5

> Đào sâu sau khi xong W1 Day 5 (TracingFilter). Đây là bước "graduation" — biến log của bạn từ "có traceId ở 1 dòng" thành "mọi dòng đều có traceId".

## 1. Vấn đề: log đang "rời rạc"

Sau khi xong Day 5, gọi `curl /api/hello` có 6 dòng log từ 4 tầng:

```
[TRACE]    traceId=abc spanId=1 GET /api/hello       ← TracingFilter (có traceId)
[START]    GET /api/hello                             ← Interceptor (KHÔNG có)
[AOP]      hello() executed in 2ms                    ← Aspect (KHÔNG có)
[DONE]     GET /api/hello → 200                       ← Interceptor (KHÔNG)
[END]      GET /api/hello (Exception: No)             ← Interceptor (KHÔNG)
GET /api/hello - 12ms                                 ← LoggingFilter (KHÔNG)
```

**Production thực tế:**
- 1000 req/giây → 5000-10000 dòng log/giây xen lẫn
- Khi debug 1 request, làm sao tìm đúng 6 dòng?
- `grep "/api/hello"` không đủ — có hàng nghìn req cùng URL!

→ Cần **mỗi dòng có traceId** để `grep` ra đúng 6 dòng của 1 request.

**Cách "ngây thơ":** sửa từng `log.info(...)` thành `log.info("[{}] ...", traceId)`. Nhưng:
- Phải sửa hàng trăm chỗ trong codebase
- Service nội bộ không có sẵn `traceId` (nó ở Filter)
- Truyền `traceId` xuyên qua mọi method = nightmare

→ Cần cơ chế **gắn traceId vào log ngầm**, không sửa từng `log.info`. Đó là **MDC**.

## 2. MDC là gì?

**MDC = Mapped Diagnostic Context** — một map (key-value) gắn liền với **thread hiện tại**, có sẵn trong SLF4J.

Hình dung như "túi đồ" mà mỗi thread mang theo:

```java
MDC.put("traceId", "abc123");
log.info("Hello");
// Trong log pattern có %X{traceId} → tự động in "abc123"
```

Mọi `log.info(...)` trên cùng thread đó đều có thể đọc "túi đồ" này. **Không cần truyền `traceId` qua từng method.**

**Cơ chế bên dưới:** MDC dùng `ThreadLocal<Map<String,String>>` — mỗi thread có 1 map riêng. Spring Boot mỗi request chạy trên 1 thread Tomcat → 1 request = 1 "túi MDC" độc lập.

## 3. API cốt lõi (3 method)

```java
import org.slf4j.MDC;

MDC.put("traceId", "abc123");      // gắn key vào túi
String tid = MDC.get("traceId");    // lấy ra
MDC.remove("traceId");              // xóa 1 key
MDC.clear();                        // xóa cả túi
```

## 4. Pattern config — log auto in MDC

Trong `application.properties`, đổi pattern console:

```properties
# CŨ:
logging.pattern.console=%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n

# MỚI - thêm %X{traceId}:
logging.pattern.console=%d{HH:mm:ss.SSS} [%thread] [trace=%X{traceId:-NONE}] %-5level %logger{36} - %msg%n
```

`%X{key}` = placeholder MDC. Khi log, Logback tự đọc `MDC.get("key")` và chèn.

`:-NONE` = default — nếu MDC không có `traceId` (vd log lúc startup), in `NONE` thay vì để trống.

## 5. Plan implement — 3 việc

### Việc 1: Sửa `TracingFilter` — gắn vào MDC

```java
@Override
protected void doFilterInternal(HttpServletRequest request,
                                HttpServletResponse response,
                                FilterChain filterChain)
        throws ServletException, IOException {

    String traceparent = request.getHeader("traceparent");
    TraceContext ctx = TraceContext.fromHeader(traceparent);

    // GẮN VÀO MDC trước khi gọi doFilter
    MDC.put("traceId", ctx.getTraceId());
    MDC.put("spanId", ctx.getSpanId());

    try {
        log.info("[TRACE] {} {}", request.getMethod(), request.getRequestURI());
        response.setHeader("traceparent", ctx.toHeader());
        filterChain.doFilter(request, response);
    } finally {
        // BẮT BUỘC clear sau khi xong - gotcha quan trọng nhất
        MDC.clear();
    }
}
```

### Việc 2: Sửa `application.properties`

```properties
logging.pattern.console=%d{HH:mm:ss.SSS} [%thread] [trace=%X{traceId:-NONE}] %-5level %logger{36} - %msg%n
```

### Việc 3: TracingFilter phải chạy TRƯỚC mọi filter khác

Vì nếu LoggingFilter chạy trước, lúc nó log MDC chưa có traceId.

```java
import org.springframework.core.annotation.Order;

@Component
@Order(1)   // càng nhỏ chạy càng sớm
public class TracingFilter extends OncePerRequestFilter {
```

Có thể thêm `@Order(2)` lên `LoggingFilter` cho rõ ràng thứ tự.

## 6. Vì sao BẮT BUỘC `MDC.clear()` trong `finally`?

Đây là gotcha lớn nhất, hỏng cái này là **bug production cực khó debug**.

Tomcat dùng **thread pool**: thread xử lý xong request sẽ quay lại pool, chờ request mới. Nếu không clear MDC:

```
Request 1 (thread-A):
  MDC.put("traceId", "abc")
  ... xử lý xong, KHÔNG clear ...

Thread-A quay lại pool, MDC vẫn có "traceId=abc"

Request 2 (thread-A được tái sử dụng):
  Log đầu tiên: [traceId=abc] ← SAI! Đây là traceId của request 1!
```

→ Hai user khác nhau, log của user B có traceId của user A. Tracing sai, audit sai, mọi thứ sai. **Bug tinh vi nhất** người mới hay dính.

→ Quy tắc: **MDC put → try → finally clear**. Y hệt pattern `try/finally` của Filter/AOP.

## 7. Kết quả mong đợi sau khi implement

```
17:44:13.601 [http-nio-8081-exec-1] [trace=4bf92f3577...] INFO  c.l.w1.t.TracingFilter      - [TRACE] GET /api/hello
17:44:13.602 [http-nio-8081-exec-1] [trace=4bf92f3577...] INFO  c.l.w1.i.LoggingInterceptor - [START] GET /api/hello
17:44:13.603 [http-nio-8081-exec-1] [trace=4bf92f3577...] INFO  c.l.w1.a.LoggingAspect      - [AOP] hello() executed in 2ms
17:44:13.604 [http-nio-8081-exec-1] [trace=4bf92f3577...] INFO  c.l.w1.i.LoggingInterceptor - [DONE] GET /api/hello → 200
17:44:13.605 [http-nio-8081-exec-1] [trace=4bf92f3577...] INFO  c.l.w1.i.LoggingInterceptor - [END] GET /api/hello
17:44:13.606 [http-nio-8081-exec-1] [trace=4bf92f3577...] INFO  c.l.w1.f.LoggingFilter      - GET /api/hello - 12ms
```

→ **Mọi dòng log của 1 request có cùng traceId.** `grep "4bf92f3577"` cho ra đúng 6 dòng, kể cả khi server nhận 1000 req/giây.

Đây là kỹ thuật **observability industrial-grade**.

## 8. Edge case: async / @Async / CompletableFuture

MDC dựa trên `ThreadLocal`. Khi code chạy ở thread khác (vd `@Async`, `CompletableFuture`, scheduler), thread mới KHÔNG kế thừa MDC từ thread cha.

```java
// Thread chính (request thread)
MDC.put("traceId", "abc");

CompletableFuture.runAsync(() -> {
    log.info("hello");  // ← traceId TRỐNG, vì thread khác
});
```

**Fix:** copy MDC sang thread con thủ công:

```java
Map<String, String> contextMap = MDC.getCopyOfContextMap();
CompletableFuture.runAsync(() -> {
    MDC.setContextMap(contextMap);
    try {
        log.info("hello");
    } finally {
        MDC.clear();
    }
});
```

Hoặc dùng wrapper executor: `TaskDecorator` của Spring, `MDCTaskDecorator`, hay thư viện `slf4j-mdc-task-decorator`.

→ Đây là lý do **distributed tracing với async là khó** — nhiều framework production có wrapper riêng để xử lý chuyện này.

## 9. Test concurrent request

Sau khi implement, mở 2 terminal cùng lúc:

```bash
# Terminal 1
curl http://localhost:8081/api/slow

# Terminal 2 (chạy ngay lập tức)
curl http://localhost:8081/api/slow
```

Trong log server, bạn sẽ thấy 2 traceId KHÁC nhau, không lẫn vào nhau. Mỗi log dòng thuộc đúng request của nó.

→ Đây là kiểm chứng `MDC.clear()` hoạt động đúng — nếu quên clear, traceId có thể bị "lệch" giữa request.

## 10. Bonus: kết hợp với log aggregator

Trong production, log không xem bằng `grep` mà bằng tool như Loki/Splunk/Datadog/CloudWatch. Lúc đó query:

```
{app="my-service"} | json | traceId="4bf92f3577..."
```

→ UI hiện ra cây trace, click vào span là thấy log tương ứng. Đây là **nền móng** của observability hiện đại.

## 11. Câu hỏi tự kiểm tra

1. MDC dùng cơ chế gì bên dưới? (Hint: liên quan đến thread.)
2. Vì sao phải `MDC.clear()` trong `finally`, không phải chỉ ở cuối method?
3. Trong `application.properties`, `%X{traceId:-NONE}` có nghĩa gì?
4. Khi nào MDC KHÔNG hoạt động đúng (không thấy traceId)?
5. Nếu 2 request đến cùng lúc trên 2 thread khác nhau, MDC của chúng có lẫn vào nhau không? Vì sao?
