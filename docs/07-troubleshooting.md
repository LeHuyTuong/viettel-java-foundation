# Troubleshooting — lỗi thường gặp

## 1. Port 8080 bị chiếm

**Triệu chứng:** Spring Boot không start, log có `Web server failed to start. Port 8080 was already in use.`

### Cách 1: Tìm và kill process đang chiếm

```bash
lsof -i :8080                    # xem PID + tên process
kill -9 <PID>                    # kill cụ thể

# hoặc 1 dòng:
kill -9 $(lsof -ti :8080)
```

### Cách 2: Đổi port của app

Sửa `src/main/resources/application.properties`:

```properties
server.port=8081      # hoặc 8082, 9000, bất kỳ
```

### Cách 3: Để Spring tự pick port trống

```properties
server.port=0
```

Khi app start sẽ in log:
```
Tomcat started on port(s): 54231 (http)
```

→ Tốt khi chạy nhiều instance trên cùng máy / khi không quan trọng port cụ thể.

## 2. Maven download chậm hoặc fail

```bash
# Refresh hoàn toàn
./mvnw clean install -U                 # -U: force update snapshots
./mvnw dependency:purge-local-repository  # xóa local cache nếu nghi corrupt
```

## 3. Compile error: "cannot find symbol log"

**Nguyên nhân:** dùng `log.info(...)` mà chưa khai báo logger.

**Fix:** thêm 2 dòng vào đầu class:

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyClass {
    private static final Logger log = LoggerFactory.getLogger(MyClass.class);
    // ...
}
```

## 4. Compile error: "unreachable statement"

**Nguyên nhân:** code sau `return` hoặc sau `try { return ... }` mà không có branch nào đi được tới.

```java
try {
    return joinPoint.proceed();
} finally {
    log.info(...);
}
return joinPoint.proceed();   // ✗ UNREACHABLE - try đã return, finally không có return
```

**Fix:** xóa dòng dư.

## 5. Response API trả về `null` mặc dù controller có return

**Nguyên nhân (AOP):** trong `@Around` không `return joinPoint.proceed()`:

```java
@Around("...")
public Object myAdvice(ProceedingJoinPoint pjp) throws Throwable {
    pjp.proceed();   // ✗ chạy method nhưng không hứng + return result
    return null;
}
```

**Fix:**

```java
return pjp.proceed();   // ✓ phải BẮT kết quả và return
```

## 6. Interceptor được tạo nhưng KHÔNG chạy

**Nguyên nhân:** chỉ có `@Component` không đủ. Phải đăng ký qua `WebMvcConfigurer`:

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Autowired private LoggingInterceptor interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/**");
    }
}
```

→ Filter thì khác — chỉ cần `@Component` là đủ.

## 7. AOP không intercept method gọi nội bộ (self-call)

```java
@Service
class MyService {
    public void outer() {
        inner();    // ✗ AOP KHÔNG bắt được cuộc gọi này
    }

    public void inner() { ... }   // có @Around target
}
```

**Lý do:** Spring AOP dùng proxy. `this.inner()` gọi trên object thật, bỏ qua proxy.

**Fix:**
- Tách `inner()` ra class khác
- Hoặc inject `MyService self` vào chính nó (self-inject)
- Hoặc dùng AspectJ thực sự (compile-time weaving)

## 8. `/api/error` không log duration ở Filter

**Nguyên nhân:** code đo duration sau `doFilter` không có `try/finally`:

```java
filterChain.doFilter(req, res);
long duration = ...;        // ✗ exception bay lên trước → dòng này bị skip
log.info(...);
```

**Fix:**

```java
long start = System.currentTimeMillis();
try {
    filterChain.doFilter(req, res);
} finally {
    long duration = System.currentTimeMillis() - start;
    log.info(...);   // ✓ luôn chạy
}
```

## 9. Stack trace dài ngoằng khi gọi `/api/error`

Đây **KHÔNG phải lỗi của Filter/Interceptor**. Đây là Tomcat in stack trace vì:
- Controller ném exception
- Không có `@ControllerAdvice` / `@ExceptionHandler` xử lý → exception bay lên Tomcat
- Tomcat in stack trace + trả 500

**Fix khi muốn response JSON đẹp:**

```java
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handle(RuntimeException e) {
        return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
    }
}
```

## 10. Hot reload không hoạt động

Spring Boot DevTools đã có trong `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <scope>runtime</scope>
    <optional>true</optional>
</dependency>
```

Nhưng nó chỉ reload khi **classpath thay đổi**. Trong IntelliJ:
- Build → Recompile (Ctrl+Shift+F9 / Cmd+Shift+F9) sau khi sửa code
- Hoặc bật Settings → Build → "Build project automatically" + Advanced "Allow auto-make to start..."

## 11. Lệnh hữu ích thường dùng

```bash
# Chạy app
./mvnw spring-boot:run

# Build jar
./mvnw clean package
java -jar target/*.jar

# Xem dependency tree
./mvnw dependency:tree

# Skip test khi build cho nhanh
./mvnw clean package -DskipTests

# Test endpoint
curl -i http://localhost:8081/api/hello
curl -X POST -H "Content-Type: application/json" \
     -d '{"name":"abc"}' http://localhost:8081/api/echo
```
