package com.learning.w1.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    // @Around bao quanh toàn bộ method — bạn kiểm soát cả trước lẫn sau
    // Pointcut expression: "execution(* com.learning.demo.*.*(..))"
    //   *          → return type bất kỳ
    //   com.learning.demo.*  → class bất kỳ trong package demo
    //   *(..)      → method bất kỳ, argument bất kỳ
    @Around("execution(* com.learning.demo.*.*(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        //lấy tên method: joinPoint.getSignature().getName()
        String methodName = joinPoint.getSignature().getName();

        // đo thời gian trước và sau joinPoint.proceed()
         long startTime = System.currentTimeMillis();

        // in ra log
        // QUAN TRỌNG: phải return kết quả của joinPoint.proceed()
        //             nếu không response sẽ là null
        // vì spring gọi proxy , rồi logExecutionTime gọi method thật qua process(
    
        try {
            return joinPoint.proceed();
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("[AOP] {}() executed in {}ms", methodName, duration);
        }
    }
}
