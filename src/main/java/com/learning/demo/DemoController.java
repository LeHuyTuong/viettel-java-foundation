package com.learning.demo;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

// Controller này để bạn test các bài tập.
// Gọi các endpoint này để xem filter/interceptor/aop có chạy không.
@RestController
@RequestMapping("/api")
public class DemoController {

    // GET /api/hello
    @GetMapping("/hello")
    public Map<String, String> hello() {
        return Map.of("message", "Hello World", "status", "ok");
    }

    // GET /api/slow — giả lập endpoint chậm (sleep 500ms) để test duration log
    @GetMapping("/slow")
    public Map<String, String> slow() throws InterruptedException {
        Thread.sleep(500);
        return Map.of("message", "slow response", "duration_hint", "~500ms");
    }

    // POST /api/echo — echo lại body gửi lên
    @PostMapping("/echo")
    public Map<String, Object> echo(@RequestBody Map<String, Object> body) {
        return body;
    }

    // GET /api/error — ném exception để test afterCompletion / @AfterThrowing
    @GetMapping("/error")
    public Map<String, String> error() {
        throw new RuntimeException("Intentional error for testing");
    }
}
