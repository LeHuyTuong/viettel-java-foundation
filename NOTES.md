# W1 — Java Core Learning

## Chạy app

```bash
./mvnw spring-boot:run
```

Hoặc trong IDE: chạy `JavaCoreLearningApplication.java`

---

## Test nhanh bằng curl

```bash
# Endpoint cơ bản
curl http://localhost:8080/api/hello

# Endpoint chậm (~500ms) — test duration log
curl http://localhost:8080/api/slow

# POST với body
curl -X POST http://localhost:8080/api/echo \
  -H "Content-Type: application/json" \
  -d '{"name": "test"}'

# Endpoint ném exception — test afterCompletion / @AfterThrowing
curl http://localhost:8080/api/error

# Gửi kèm traceparent header (ngày 4-5)
curl http://localhost:8080/api/hello \
  -H "traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
```

---

## Lộ trình file theo ngày

| Ngày | File cần sửa | Mục tiêu |
|------|-------------|----------|
| Sun 25/5 | `w1/filter/LoggingFilter.java` | In `[METHOD] [URL] - Xms` |
| Mon 26/5 | `w1/interceptor/LoggingInterceptor.java` | Log 3 hook lifecycle |
| Tue 27/5 | `w1/aop/LoggingAspect.java` | Log method name + duration |
| Wed 28/5 | (đọc concepts) | Hiểu trace/span/propagation |
| Thu 29/5 | `w1/tracing/TraceContext.java` + `TracingFilter.java` | Parse/tạo W3C traceparent |

---

## Hierarchy request lifecycle (hiểu để biết mình đang đứng ở đâu)

```
HTTP Request
    │
    ▼
[Servlet Filter]         ← LoggingFilter.java (bạn đang code)
    │                       - thấy raw HTTP, chưa biết gì về Spring
    ▼
[DispatcherServlet]      ← Spring MVC entry point
    │
    ▼
[HandlerInterceptor]     ← LoggingInterceptor.java
    │                       - biết handler (Controller method) là gì
    ▼
[AOP Proxy]              ← LoggingAspect.java
    │                       - bọc quanh method call
    ▼
[Controller Method]      ← DemoController.java
    │
    ▼
HTTP Response
```

---

## Điểm khác biệt Filter vs Interceptor (học xong ghi nhớ cái này)

| | Filter | Interceptor |
|---|---|---|
| Layer | Servlet (trước Spring) | Spring MVC |
| Biết handler không? | Không | Có (`Object handler`) |
| Truy cập Spring bean? | Hạn chế | Có |
| Dùng cho | Raw HTTP, CORS, auth header | Business logic, logging có context |
| Đăng ký qua | `@Component` hoặc `FilterRegistrationBean` | `WebMvcConfigurer` |
