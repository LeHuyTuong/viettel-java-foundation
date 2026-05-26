# Java / Spring Boot — Sổ tay học W1

Bộ ghi chú tổng hợp từ quá trình học Tuần 1 (Filter / Interceptor / AOP / Tracing).
Đọc theo thứ tự số file để hiểu từ tổng quan đến chi tiết.

## Mục lục

| File | Nội dung |
|---|---|
| [01-pipeline-overview.md](01-pipeline-overview.md) | Mô hình tầng (Filter → Interceptor → AOP → Controller). Cần đọc trước. |
| [02-filter.md](02-filter.md) | Filter — tầng ngoài cùng, mức Servlet. Day 1. |
| [03-interceptor.md](03-interceptor.md) | Interceptor — tầng Spring MVC, biết Controller. Day 2. |
| [04-aop.md](04-aop.md) | AOP — bao quanh method bất kỳ. Day 3. |
| [05-logging-slf4j.md](05-logging-slf4j.md) | Vì sao dùng SLF4J thay vì `System.out.println`. |
| [06-distributed-tracing.md](06-distributed-tracing.md) | traceId / spanId / W3C traceparent. Day 4-5. |
| [07-troubleshooting.md](07-troubleshooting.md) | Port conflict, lỗi thường gặp. |
| [08-mdc-log-correlation.md](08-mdc-log-correlation.md) | MDC + log correlation — gắn traceId vào mọi dòng log tự động. Bước graduation sau W1. |

## Tóm tắt 1 dòng cho mỗi tầng

```
HTTP Request
   ↓
[Filter]          ← Servlet level, biết URL/header, KHÔNG biết Controller
   ↓
[Interceptor]     ← Spring MVC, biết Controller method qua handler param
   ↓
[AOP @Around]     ← Bọc sát method, biết args + return value
   ↓
Controller / Service
```

Càng vào trong càng "gần code", càng ra ngoài càng "gần HTTP". Chọn tầng theo thông tin bạn cần.

## Code đã viết trong project

| Tầng | File | Trạng thái |
|---|---|---|
| Filter | `src/main/java/com/learning/w1/filter/LoggingFilter.java` | Done |
| Interceptor | `src/main/java/com/learning/w1/interceptor/LoggingInterceptor.java` | Done |
| Interceptor config | `src/main/java/com/learning/w1/interceptor/WebMvcConfig.java` | Done |
| AOP | `src/main/java/com/learning/w1/aop/LoggingAspect.java` | Done |
| Tracing context | `src/main/java/com/learning/w1/tracing/TraceContext.java` | Done |
| Tracing filter | `src/main/java/com/learning/w1/tracing/TracingFilter.java` | Done |
| Test endpoints | `src/main/java/com/learning/demo/DemoController.java` | Có sẵn |
