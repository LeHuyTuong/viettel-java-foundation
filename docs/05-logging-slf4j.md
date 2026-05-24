# Logging với SLF4J + Logback

## 1. Vì sao KHÔNG dùng `System.out.println`?

| | `System.out.println` | SLF4J Logger |
|---|---|---|
| Log level (DEBUG/INFO/WARN/ERROR) | ✗ | ✓ |
| Timestamp + thread + class name | ✗ (phải tự nhồi) | ✓ (tự động) |
| Bật/tắt theo môi trường (dev/prod) | ✗ | ✓ qua `application.properties` |
| Ghi ra file / JSON / gửi đi xa | ✗ | ✓ qua appender |
| Format placeholder `{}` (lazy eval) | ✗ | ✓ |
| Hiệu năng | Chậm (synchronized PrintStream) | Nhanh hơn, có async |

→ `System.out.println` chỉ dùng để debug nhanh trong main(). Không bao giờ dùng trong production code.

## 2. Phân biệt các tên gọi — dễ nhầm

- **SLF4J** = chỉ là *interface* (API). Code của bạn import cái này.
- **Logback** = *implementation* mặc định của Spring Boot. Không cần config gì, có sẵn.
- **Log4j 1.x** = framework cũ, đã end-of-life. **Không dùng.**
- **Log4j 2** = framework khác (Apache), từng dính Log4Shell (CVE-2021-44228). Spring Boot mặc định không dùng.

→ Trong Spring Boot, mặc định bạn dùng `SLF4J → Logback`. Không cần thêm dependency nào.

## 3. Kiểm tra có sẵn chưa

```bash
./mvnw dependency:tree | grep -E "(slf4j|logback)"
```

Trong project này đã có sẵn (kéo qua `spring-boot-starter-web` → `spring-boot-starter-logging`):

```
spring-boot-starter-logging
  ├─ logback-classic 1.4.14   ← implementation
  ├─ log4j-to-slf4j           ← bridge
  └─ jul-to-slf4j             ← bridge
slf4j-api 2.0.13              ← API bạn import
```

**Rule of thumb:** trước khi thêm dependency logging/JSON/validation/HTTP client cơ bản, chạy `dependency:tree | grep <tên>` để check. 90% trường hợp Spring Boot đã có sẵn.

## 4. Cách dùng chuẩn

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyClass {
    private static final Logger log = LoggerFactory.getLogger(MyClass.class);

    public void doSomething(String userId) {
        log.info("Processing user {}", userId);
        try {
            // ...
        } catch (Exception e) {
            log.error("Failed for user {}", userId, e);   // exception là arg cuối
        }
    }
}
```

**3 quy tắc:**

1. **Logger là `static final`** — 1 instance per class, không tạo lại mỗi lần.
2. **Tên biến là `log`** (hoặc `logger`) — convention.
3. **`MyClass.class`** — để log biết tên class, in ra trong output.

## 5. Cú pháp `{}` placeholder — quan trọng

```java
// ✓ ĐÚNG - chỉ build chuỗi khi log thực sự được in
log.debug("user={} action={}", userId, action);

// ✗ SAI - build chuỗi LUÔN, kể cả khi level DEBUG đang tắt
log.debug("user=" + userId + " action=" + action);
```

Lý do: nếu level DEBUG đang tắt ở production, dòng có `+` vẫn phải concat strings (tốn CPU). Dòng có `{}` thì SLF4J check level trước, chỉ build chuỗi nếu sẽ in.

→ Khi log dữ liệu lớn (object phức tạp, list dài), khác biệt này có thể đáng kể.

## 6. Khi nào dùng level nào?

| Level | Khi nào | Ví dụ |
|---|---|---|
| `TRACE` | Cực chi tiết, ít dùng | "entering method X with args ..." |
| `DEBUG` | Cho dev, tắt ở prod | "SQL query: SELECT * FROM ..." |
| `INFO` | Sự kiện bình thường | "Request received", "Job completed" |
| `WARN` | Bất thường nhưng app vẫn chạy | "Cache miss, fallback to DB" |
| `ERROR` | Lỗi nghiêm trọng cần xem | "Failed to send email after 3 retries" |

**Quy tắc:** đừng spam INFO. Đừng để ERROR cho thứ không cần dev xem ngay. Production team chỉ nhìn ERROR/WARN — nếu mọi thứ đều INFO sẽ "noise".

## 7. Config trong `application.properties`

File hiện tại của bạn:

```properties
# Mức log cho package của bạn
logging.level.com.learning=DEBUG

# Mức log cho Spring Framework
logging.level.org.springframework.web=INFO

# Format console
logging.pattern.console=%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n
```

Một số config hữu ích thêm:

```properties
# Ghi ra file
logging.file.name=logs/app.log

# Rotate file khi to
logging.logback.rollingpolicy.max-file-size=10MB
logging.logback.rollingpolicy.max-history=7

# Tắt log của 1 thư viện ồn ào
logging.level.org.hibernate.SQL=WARN
```

## 8. Log exception đúng cách

```java
// ✗ SAI - mất stack trace
} catch (Exception e) {
    log.error("Failed: " + e.getMessage());
}

// ✓ ĐÚNG - đặt exception là arg CUỐI cùng (không cần placeholder)
} catch (Exception e) {
    log.error("Failed to process user {}", userId, e);
}
```

SLF4J có quy ước đặc biệt: nếu arg cuối là `Throwable`, nó tự nhận biết và in stack trace. Đừng nhồi vào chuỗi.

## 9. Tracing correlation — kỹ thuật pro

Trong distributed system (xem [06-distributed-tracing.md](06-distributed-tracing.md)), người ta dùng **MDC** (Mapped Diagnostic Context) của SLF4J để gắn traceId vào MỌI log dòng tự động:

```java
import org.slf4j.MDC;

MDC.put("traceId", traceContext.getTraceId());
try {
    log.info("Processing");   // dòng này tự kèm traceId trong output
    log.info("Validating");   // dòng này cũng
} finally {
    MDC.clear();
}
```

Sau đó thêm `%X{traceId}` vào pattern:
```properties
logging.pattern.console=%d %X{traceId} [%thread] %-5level %logger - %msg%n
```

Output:
```
17:44:13 abc123-xyz [http-nio] INFO  c.l.OrderService - Processing
17:44:13 abc123-xyz [http-nio] INFO  c.l.OrderService - Validating
```

→ Đây là pattern chuẩn industrial. Sẽ học sâu hơn ở W2+.

## 10. Câu hỏi tự kiểm tra

1. Vì sao `log.info("x=" + x)` chậm hơn `log.info("x={}", x)`?
2. Khi nào dùng WARN vs ERROR?
3. Làm sao log stack trace đầy đủ khi catch exception?
4. SLF4J và Logback khác nhau ra sao?
