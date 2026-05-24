package com.learning.w1.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

// BÀI TẬP - Ngày 3 (Tue 27/5)
// Đọc: https://www.baeldung.com/spring-aop
//
// Nhiệm vụ: viết 1 Aspect log tên method + duration khi bất kỳ method nào
//           trong package com.learning.demo được gọi.
//
// Output mong muốn:
//   [AOP] hello() executed in 3ms
//   [AOP] slow() executed in 502ms
//
// Khái niệm cần hiểu:
//   - Pointcut:   "tôi muốn intercept CHỖ NÀO" (expression trong @Around)
//   - Advice:     "tôi muốn làm GÌ" (code bên trong method)
//   - JoinPoint:  điểm giao nhau — method đang bị intercept
//   - ProceedingJoinPoint: JoinPoint đặc biệt cho @Around, có .proceed() để chạy method gốc
@Aspect
@Component
public class LoggingAspect {

    // @Around bao quanh toàn bộ method — bạn kiểm soát cả trước lẫn sau
    // Pointcut expression: "execution(* com.learning.demo.*.*(..))"
    //   *          → return type bất kỳ
    //   com.learning.demo.*  → class bất kỳ trong package demo
    //   *(..)      → method bất kỳ, argument bất kỳ
    @Around("execution(* com.learning.demo.*.*(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        // TODO: lấy tên method: joinPoint.getSignature().getName()
        // TODO: đo thời gian trước và sau joinPoint.proceed()
        // TODO: in ra log
        // QUAN TRỌNG: phải return kết quả của joinPoint.proceed()
        //             nếu không response sẽ là null
        return joinPoint.proceed();
    }
}
