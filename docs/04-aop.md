# AOP — Aspect-Oriented Programming (Day 3)

> Tham khảo trong project: `src/main/java/com/learning/w1/aop/LoggingAspect.java`

## 1. AOP giải quyết vấn đề gì?

**Bài toán:** bạn có 50 method service, muốn log thời gian chạy mỗi method.

Cách "thường" — copy-paste:

```java
public Order createOrder(...) {
    long start = System.currentTimeMillis();
    // ... business logic ...
    log.info("createOrder took {}ms", System.currentTimeMillis() - start);
}
public User createUser(...) {
    long start = System.currentTimeMillis();
    // ... business logic ...
    log.info("createUser took {}ms", System.currentTimeMillis() - start);
}
// ... lặp 48 lần nữa
```

→ Đây gọi là **cross-cutting concern** (mối quan tâm cắt ngang): logic logging "cắt ngang" mọi method, không thuộc về business của method nào.

Vấn đề:
- Lặp code (vi phạm DRY)
- Trộn lẫn kỹ thuật vào nghiệp vụ → khó đọc
- Muốn đổi format log → sửa 50 chỗ

**AOP giải:** viết logic logging MỘT LẦN ở 1 chỗ riêng (gọi là **Aspect**), khai báo với Spring "áp dụng cái này cho tất cả method trong package X". Method gốc giữ nguyên sạch.

## 2. Bốn khái niệm cốt lõi

| Tên | Vai trò | Trong code của bạn |
|---|---|---|
| **Aspect** | Class chứa logic cross-cutting | `class LoggingAspect` |
| **Pointcut** | Biểu thức chọn method nào bị intercept | `execution(* com.learning.demo.*.*(..))` |
| **Advice** | Code thực thi khi pointcut match | Body của `logExecutionTime()` |
| **JoinPoint** | Điểm giao nhau — method đang bị "đánh trúng" | Param `joinPoint` |

Công thức: **Pointcut** (chọn ở đâu) + **Advice** (làm gì) = **Aspect** (cả mô-đun).

## 3. Các loại Advice

```java
@Before("...")          // chạy TRƯỚC method
@After("...")           // chạy SAU method (cả khi exception)
@AfterReturning("...")  // chỉ chạy khi method return thành công
@AfterThrowing("...")   // chỉ chạy khi method ném exception
@Around("...")          // BAO QUANH cả method - kiểm soát mọi thứ
```

Bảng năng lực:

|           | Trước | Sau OK | Sau Lỗi | Đổi return | Đổi arg | Catch lỗi |
|-----------|:---:|:---:|:---:|:---:|:---:|:---:|
| @Before   | ✓ |    |    |    |    |    |
| @After    |    | ✓ | ✓ |    |    |    |
| @AfterReturning | | ✓ |    |    |    |    |
| @AfterThrowing  | |    | ✓ |    |    |    |
| @Around   | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |

**Quy tắc chọn:**
- Chỉ log đơn giản trước/sau → dùng `@Before` / `@AfterReturning` (rõ ý đồ hơn)
- Đo duration, cache, retry, catch exception → bắt buộc `@Around`

Bài tập Day 3 cần đo duration → phải `@Around`.

## 4. JoinPoint vs ProceedingJoinPoint

```java
@Before("...")
public void log(JoinPoint jp) { ... }              // chỉ đọc

@Around("...")
public Object log(ProceedingJoinPoint pjp)         // BẮT BUỘC là ProceedingJoinPoint
        throws Throwable {
    return pjp.proceed();   // .proceed() chỉ có ở ProceedingJoinPoint
}
```

`ProceedingJoinPoint` chỉ tồn tại trong `@Around` vì chỉ `@Around` mới có quyền:
- KHÔNG gọi method gốc (chặn)
- Gọi method gốc NHIỀU LẦN (retry)
- Đổi argument trước khi gọi

API thường dùng:
```java
jp.getSignature().getName()         // "hello"
jp.getSignature().toShortString()   // "DemoController.hello()"
jp.getArgs()                         // mảng arguments
jp.getTarget()                       // object bị intercept
```

## 5. Pointcut expression — đọc cho hiểu

```
execution( * com.learning.demo.*.*(..) )
   │       │       │             │  │
   │       │       │             │  └── argument bất kỳ (.. = 0..n params, kiểu gì)
   │       │       │             └───── tên method bất kỳ
   │       │       └─────────────────── class bất kỳ trong package demo
   │       └─────────────────────────── return type bất kỳ
   └─────────────────────────────────── matcher kiểu "execution" (gọi method)
```

Một số biến thể hay dùng:

```java
execution(* com.learning.demo..*.*(..))           // .. = package CON cũng tính
execution(public * *.create*(..))                  // method public bắt đầu bằng "create"
execution(* com.learning..*Service.*(..))          // class kết thúc bằng "Service"
@annotation(org.springframework.transaction.annotation.Transactional)  // method có @Transactional
within(com.learning.demo..*)                       // mọi method trong package
@within(org.springframework.stereotype.Service)    // method của class có @Service
```

## 6. Pattern chuẩn cho `@Around`

```java
@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    @Around("execution(* com.learning.demo.*.*(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        long start = System.currentTimeMillis();
        try {
            return joinPoint.proceed();          // ← BẮT BUỘC return kết quả
        } finally {
            long duration = System.currentTimeMillis() - start;
            log.info("[AOP] {}() executed in {}ms", methodName, duration);
        }
    }
}
```

**3 điều cần để ý:**

1. **`return joinPoint.proceed()`** — Aspect là trung gian giữa caller (Spring) và method gốc. Method gốc trả về gì, Aspect phải trả lại y vậy. Nếu return null → response API sẽ rỗng. Đây là gotcha số 1 của AOP.

2. **`throws Throwable`** — không phải `throws Exception`. Vì `proceed()` có thể ném cả `Error` (như `OutOfMemoryError`). `Throwable` là cha của cả `Exception` lẫn `Error`.

3. **`try { return ... } finally { ... }`** — `finally` chạy SAU khi `return` đã tính giá trị nhưng TRƯỚC khi method thực sự thoát. Đây là cú pháp Java chuẩn, không phải "return sớm".

## 7. Spring AOP hoạt động ra sao? — Proxy

Spring AOP dùng **dynamic proxy**. Khi bạn `@Autowired DemoController controller`, Spring KHÔNG inject controller thật, mà inject một **proxy** bao bọc nó:

```
Caller → Proxy (do Spring tạo bằng CGLIB)
            ├─ Chạy Aspect.before
            ├─ Gọi method thật trên controller gốc
            └─ Chạy Aspect.after
```

Bằng chứng — stack trace lúc `/api/error` của bạn từng thấy:

```
com.learning.demo.DemoController$$SpringCGLIB$$0.error(<generated>)
                                  ↑
                            ĐÂY LÀ PROXY (CGLIB tạo)
```

## 8. Gotcha lớn nhất: self-call không bị intercept

```java
@Service
class MyService {
    public void a() {
        b();   // ❌ KHÔNG bị AOP intercept!
    }

    @Around-target  // hypothetical
    public void b() { ... }
}
```

Khi `a()` gọi `b()` qua `this.b()` → đó là gọi trên object THẬT, không qua proxy → Aspect không chạy.

Đây là **lỗi kinh điển** ai cũng dính 1 lần.

**Fix:**
- Tách `b()` ra class khác
- Hoặc inject `MyService self` vào chính nó (self-inject hack)
- Hoặc dùng AspectJ thực sự (compile-time weaving) thay vì Spring AOP proxy

## 9. AOP vs Interceptor

| | Interceptor | AOP |
|---|---|---|
| Tầng | Spring MVC | Bất kỳ Spring bean nào |
| Target | Controller method | Method bất kỳ |
| Biết về HTTP | ✓ | ✗ |
| Áp dụng cho non-web (job, scheduler, batch) | ✗ | ✓ |
| Quy mô | Hẹp (chỉ web) | Rộng (toàn app) |

→ Interceptor chuyên dụng cho web. AOP tổng quát hơn — dùng cho service, repo, scheduled job, batch processing.

## 10. Ứng dụng thực tế của AOP — bạn đã dùng mà không biết

Nhiều annotation phổ biến của Spring chính là AOP dưới capo:

- `@Transactional` — Aspect bao quanh method để mở/commit/rollback transaction
- `@Cacheable` — Aspect check cache trước khi gọi method
- `@Async` — Aspect spawn thread mới để chạy method
- `@Retryable` — Aspect catch exception và gọi lại
- Spring Security `@PreAuthorize` — Aspect check permission

→ Hiểu AOP = hiểu được "magic" của hàng chục annotation Spring.

## 11. Câu hỏi tự kiểm tra

1. Vì sao trong `@Around` phải `return joinPoint.proceed()` mà không chỉ gọi `proceed()` rồi return null?
2. Khác biệt giữa `JoinPoint` và `ProceedingJoinPoint`?
3. Vì sao `@Around` declare `throws Throwable` chứ không phải `throws Exception`?
4. Vì sao gọi `this.b()` từ trong `a()` không bị Aspect intercept?
5. Pattern `try/finally` trong `@Around` khác gì so với Filter?
