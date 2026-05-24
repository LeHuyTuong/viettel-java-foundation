# Pipeline xử lý request — bức tranh tổng thể

> Đây là mental model quan trọng nhất của W1. Nắm được nó, các tầng bên dưới sẽ dễ hiểu hơn rất nhiều.

## 1. Thứ tự các tầng

```
HTTP Request từ client (curl, browser, mobile app...)
   │
   ▼
┌─────────────────────────────────────────────────┐
│ Tomcat (Servlet container)                       │
│   ┌───────────────────────────────────────────┐ │
│   │ [Filter]                                  │ │  ← LoggingFilter của bạn
│   │   ┌─────────────────────────────────────┐ │ │
│   │   │ Spring DispatcherServlet            │ │ │
│   │   │   ┌─────────────────────────────┐   │ │ │
│   │   │   │ [Interceptor]               │   │ │ │  ← LoggingInterceptor
│   │   │   │   ┌─────────────────────┐   │   │ │ │
│   │   │   │   │ [AOP @Around]       │   │   │ │ │  ← LoggingAspect
│   │   │   │   │   ┌─────────────┐   │   │   │ │ │
│   │   │   │   │   │ Controller  │   │   │   │ │ │  ← DemoController
│   │   │   │   │   │   ↓ gọi      │   │   │   │ │ │
│   │   │   │   │   │ Service     │   │   │   │ │ │
│   │   │   │   │   └─────────────┘   │   │   │ │ │
│   │   │   │   └─────────────────────┘   │   │ │ │
│   │   │   └─────────────────────────────┘   │ │ │
│   │   └─────────────────────────────────────┘ │ │
│   └───────────────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
   │
   ▼
HTTP Response về client
```

Đọc giống ngoặc lồng nhau: **vào trước → ra sau** (LIFO, stack-style).

## 2. Mỗi tầng "biết" cái gì?

| Tầng | Biết URL? | Biết Header? | Biết Controller method? | Biết args? | Biết return value? |
|---|:---:|:---:|:---:|:---:|:---:|
| Filter | ✓ | ✓ | ✗ | ✗ | ✗ |
| Interceptor | ✓ | ✓ | ✓ (qua `handler`) | ✗ | qua `ModelAndView` |
| AOP `@Around` | ✗ (không phải HTTP) | ✗ | ✓ | ✓ | ✓ |

→ **Filter** chạy ở tầng Servlet (Tomcat), trước khi Spring biết route đến controller nào.
→ **Interceptor** chạy bên trong DispatcherServlet, sau khi handler đã được resolve.
→ **AOP** không liên quan gì đến HTTP — nó bao quanh method bất kỳ (kể cả `@Scheduled` job, service nội bộ).

## 3. Khi nào dùng tầng nào — quy tắc thực tế

| Yêu cầu | Tầng phù hợp | Lý do |
|---|---|---|
| CORS, rate limit theo IP | Filter | Chỉ cần thông tin HTTP thô |
| Đọc/gắn trace header | Filter | Cùng lý do trên |
| Check role/permission của controller | Interceptor | Cần biết handler là method nào |
| Audit log "user X đã gọi controller Y" | Interceptor | Cần thông tin controller |
| Đo thời gian service method | AOP | Cần ôm sát method, biết tên |
| Cache return value | AOP | Cần biết args + can thiệp return |
| Transaction (mở/đóng DB transaction) | AOP | Spring `@Transactional` chính là AOP |

**Rule of thumb:** dùng tầng **gần nhất** với thông tin bạn cần. Đừng nhồi logic của Interceptor vào Filter cho "tiện".

## 4. Quan sát thực tế từ log

Khi gọi `curl http://localhost:8081/api/hello`, thứ tự log xuất hiện:

```
[FILTER IN]  GET /api/hello                  ← Filter VÀO (nếu có log trước doFilter)
[START]      GET /api/hello                  ← Interceptor preHandle
[AOP]        hello() executed in 2ms         ← AOP @Around finally
[DONE]       GET /api/hello → 200            ← Interceptor postHandle
[END]        GET /api/hello (Exception: No)  ← Interceptor afterCompletion
[FILTER OUT] GET /api/hello - 12ms           ← Filter RA (sau doFilter)
```

**Key insight:** nếu Filter chỉ log 1 lần (ở `finally`), nó sẽ xuất hiện **cuối cùng** trong output — nhưng KHÔNG có nghĩa Filter chạy cuối. Nó chỉ log cuối thôi. Thực sự Filter là tầng **vào đầu tiên** và **ra cuối cùng**.

## 5. Hành vi khi có exception

Khi controller ném exception (`/api/error`):

| Tầng | Có chạy? | Lý do |
|---|:---:|---|
| Filter `try {...} finally {...}` | ✓ | `finally` luôn chạy |
| Interceptor `preHandle` | ✓ | Chạy trước khi gặp exception |
| Interceptor `postHandle` | ✗ | Bị skip vì exception ném trước khi controller return |
| Interceptor `afterCompletion` | ✓ | Luôn chạy, nhận `ex != null` |
| AOP `@Around` `finally` block | ✓ | `finally` luôn chạy |
| Controller `@ExceptionHandler` | ✓ (nếu có) | Chỗ xử lý exception chính |

→ Để code đo duration / metrics / cleanup chạy được kể cả khi lỗi, **bắt buộc dùng `try/finally`** (không phải `try/catch` — đừng nuốt exception).

## 6. Pattern "wrap" — gặp lại ở mọi nơi

Cùng pattern này xuất hiện ở:
- HTTP middleware (Express, Koa, ASP.NET Core)
- Database transaction nesting
- AOP advice nhiều cấp
- Decorator pattern
- Java try-with-resources
- Python context manager (`with` statement)

→ Hiểu được mô hình ngoặc lồng + LIFO ở đây = hiểu được 80% các framework "trung gian" sau này.

## 7. Performance debugging — vì sao có 3 tầng đo

3 con số duration tạo thành **hệ tọa độ debug**:

```
Filter duration (ngoài cùng)  = TỔNG thời gian
Interceptor (không đo riêng, nhưng có thể thêm)
AOP duration (sát method)     = chỉ business logic
```

So sánh:
- Filter 800ms, AOP 790ms → business logic chậm. Optimize service.
- Filter 800ms, AOP 30ms → 770ms bị "đốt" giữa đường (serialization, security check, interceptor nặng).
- Filter 800ms, AOP 750ms → 50ms ở serialization JSON. Bình thường, không lo.

→ Đây là lý do production hay đo nhiều tầng — để **biết chỗ chậm ở đâu**, không phải "biết là chậm".
