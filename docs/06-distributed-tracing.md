# Distributed Tracing (Day 4-5)

> Tham khảo trong project:
> - `src/main/java/com/learning/w1/tracing/TraceContext.java`
> - `src/main/java/com/learning/w1/tracing/TracingFilter.java`

## 1. Vấn đề: debug khi 1 request đi qua N service

Kiến trúc microservice thực tế:

```
User click "Mua hàng"
   ↓ HTTP POST /orders
[order-service]
   ↓ gọi
[inventory-service] ──→ DB check stock
   ↓ gọi
[payment-service]   ──→ API Stripe
   ↓ gọi
[notification-service] ──→ gửi email + SMS
```

Khi user báo "tôi click mua mà bị lỗi", bạn vào server xem log. Vấn đề:

- 5 service = 5 file log riêng
- Mỗi giây có hàng trăm request → log của bạn xen lẫn với log của 200 user khác
- Log ở `payment-service` chỉ ghi "card declined" — không biết là **của request nào**

→ Cần **một sợi chỉ xuyên suốt** để buộc các log lại với nhau. Đó là `traceId`.

## 2. Trace và Span

- **Trace** = toàn bộ hành trình của 1 request từ đầu đến cuối (qua nhiều service).
- **Span** = 1 đoạn nhỏ trong hành trình (vd: "gọi DB", "gọi Stripe").

Hình dung như cây:

```
Trace (traceId=abc123)               ← 1 request, 1 traceId
├─ Span 1: POST /orders               (spanId=A, parent=null)        ← root span
│  ├─ Span 2: check stock             (spanId=B, parent=A)
│  ├─ Span 3: charge card             (spanId=C, parent=A)
│  │  └─ Span 4: HTTP call to Stripe  (spanId=D, parent=C)
│  └─ Span 5: send notification       (spanId=E, parent=A)
│     └─ Span 6: SMTP send            (spanId=F, parent=E)
```

→ Mọi span trong 1 trace đều dùng **chung `traceId`**.
→ Mỗi span có **`spanId` riêng** + **`parentSpanId`** để biết ai gọi ai.

Bài tập Day 4 đơn giản hóa: chỉ có `traceId`, `spanId`, `flags` (chưa làm parent chain).

## 3. W3C `traceparent` — chuẩn truyền tải

Trước đây mỗi hãng có format riêng (Zipkin B3, Datadog, AWS X-Ray...). W3C thống nhất:

```
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
             │   │                                │                │
             │   │                                │                └─ flags (sample/not)
             │   │                                └─ spanId (16 hex = 8 bytes)
             │   └─ traceId (32 hex = 16 bytes)
             └─ version (luôn "00" hiện tại)
```

Tách bằng `-`, đúng 4 phần. Dùng `header.split("-")` parse được dễ.

**Lưu ý kỹ thuật:**
- `traceId` = 32 hex chars (128 bit). Tạo: `UUID.randomUUID().toString().replace("-", "")`.
- `spanId` = 16 hex chars (64 bit). Tạo: lấy 16 chars đầu của UUID.
- `flags` = `"01"` → trace được sampled (giữ lại). `"00"` → bỏ. Hardcode `"01"` là OK.

## 4. Context propagation — cơ chế cốt lõi

```
Service A nhận request từ user (KHÔNG có traceparent header)
   ↓
Service A: tạo TraceContext mới → traceId=abc, spanId=1
   ↓ Log: "[TRACE] traceId=abc spanId=1 POST /orders"
   ↓
Service A gọi Service B qua HTTP:
   GET /inventory
   Header: traceparent: 00-abc-1-01            ← GẮN VÀO ĐÂY
   ↓
Service B nhận request, ĐỌC header traceparent
   → traceId=abc (GIỮ NGUYÊN), tạo spanId=2 mới
   ↓ Log: "[TRACE] traceId=abc spanId=2 GET /inventory"
   ↓
Service B gọi Service C → traceparent: 00-abc-2-01
   ↓
Service C: traceId=abc, spanId=3
```

→ Khi lỗi ở C, `grep "traceId=abc"` thấy **cả 3 dòng log** ở A, B, C theo thứ tự. Magic.

## 5. Bốn method của `TraceContext` — mục đích

| Method | Khi nào dùng | Hành vi |
|---|---|---|
| `newTrace()` | Service là **origin** (request đầu từ user/browser) | Random cả `traceId` lẫn `spanId` |
| `fromHeader(s)` | Nhận request từ service khác có sẵn traceparent | Parse string, giữ nguyên cả 3 phần |
| `newChildSpan()` | Service gọi sang service khác | **Giữ nguyên traceId**, tạo `spanId` mới |
| `toHeader()` | Trước khi gọi service khác qua HTTP | Serialize ngược về W3C string |

**Logic `newChildSpan()` quan trọng nhất** — điểm hay nhầm:

```java
public TraceContext newChildSpan() {
    return new TraceContext(
        this.traceId,    // ← GIỮ NGUYÊN! Đây là sợi chỉ xuyên suốt
        randomSpanId(),  // ← spanId MỚI, vì đây là đoạn xử lý mới
        this.flags
    );
}
```

→ Sai lầm: random cả `traceId` → mất luôn liên kết với caller, log không nối được nữa.

## 6. TracingFilter — ráp vào Spring Boot

```java
@Component
public class TracingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TracingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ... {

        // 1. Đọc header
        String header = req.getHeader("traceparent");

        // 2. Parse hoặc tạo mới
        TraceContext ctx = (header != null)
            ? TraceContext.fromHeader(header)
            : TraceContext.newTrace();

        // 3. Log
        log.info("[TRACE] traceId={} spanId={} {} {}",
                 ctx.getTraceId(), ctx.getSpanId(),
                 req.getMethod(), req.getRequestURI());

        // 4. Gắn header vào response (để client cũng biết trace)
        res.setHeader("traceparent", ctx.toHeader());

        // (W2+: lưu ctx vào ThreadLocal/MDC để tầng dưới đọc được)
        chain.doFilter(req, res);
    }
}
```

## 7. Three Pillars of Observability

| | Logging | Metrics | Tracing |
|---|---|---|---|
| Trả lời | "Đã có chuyện gì?" | "Hệ thống có khỏe không?" | "Request này đi đâu, mất bao lâu ở đâu?" |
| Dữ liệu | Text dòng | Số (counter, gauge) | Cây span có cấu trúc |
| Stack điển hình | ELK, Loki | Prometheus, Grafana | Jaeger, Tempo, X-Ray |
| Câu hỏi mẫu | "Error nào xảy ra lúc 3am?" | "QPS có vượt 1000?" | "Vì sao request X mất 5s?" |

→ Tracing **không thay thế** logging. Chúng bổ sung nhau. Pattern chuẩn industrial: gắn `traceId` vào MỌI log dòng (qua SLF4J MDC) để liên kết logs với traces.

## 8. Spring Boot 3.x đã tự làm chưa?

CÓ — qua **Micrometer Tracing** + **OpenTelemetry**, mặc định trong Spring Boot 3.x. Nhưng "magic" — nhiều dev dùng không hiểu bên dưới chạy gì.

Bài tập Day 4-5 là làm **tay** một bản tối giản để **hiểu cơ chế**. Sau khi tự code, đọc Micrometer Tracing sẽ thấy "à ra là vậy" thay vì coi là phép màu.

## 9. Gợi ý implement Day 4

```java
public class TraceContext {

    private final String traceId;
    private final String spanId;
    private final String flags;

    public TraceContext(String traceId, String spanId, String flags) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.flags = flags;
    }

    public static TraceContext newTrace() {
        return new TraceContext(randomTraceId(), randomSpanId(), "01");
    }

    public static TraceContext fromHeader(String header) {
        if (header == null) return newTrace();
        String[] parts = header.split("-");
        if (parts.length != 4) return newTrace();   // format sai → tạo mới
        return new TraceContext(parts[1], parts[2], parts[3]);
    }

    public TraceContext newChildSpan() {
        return new TraceContext(this.traceId, randomSpanId(), this.flags);
    }

    public String toHeader() {
        return String.format("00-%s-%s-%s", traceId, spanId, flags);
    }

    private static String randomTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String randomSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // getters...
}
```

(Tự implement trước khi nhìn code mẫu này — học sẽ chắc hơn.)

## 10. Test thủ công

Sau khi viết xong cả 2 file:

```bash
# Test 1: origin request - không có header
curl -i http://localhost:8081/api/hello
# → Server tạo traceId mới, response có header traceparent

# Test 2: continuation - giả lập gọi từ service khác
curl -i -H "traceparent: 00-aaaabbbbccccdddd1111222233334444-1111222233334444-01" \
       http://localhost:8081/api/hello
# → Server giữ nguyên traceId trong log
```

Log sẽ có dòng:
```
[TRACE] traceId=aaaa... spanId=1111... GET /api/hello
```

## 11. Câu hỏi tự kiểm tra

1. Trace và Span khác gì nhau?
2. Vì sao `newChildSpan()` KHÔNG được đổi `traceId`?
3. W3C traceparent có 4 phần, mỗi phần là gì?
4. Khi service B gọi service C, traceparent gửi đi có spanId của B hay của C?
5. Vì sao distributed tracing cần thiết — cụ thể giải bài toán gì mà logging không giải được?
