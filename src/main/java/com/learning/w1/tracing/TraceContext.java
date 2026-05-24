package com.learning.w1.tracing;

// BÀI TẬP - Ngày 4-5 (Wed 28/5 – Thu 29/5)
// Đọc:
//   - https://opentelemetry.io/docs/concepts/observability-primer/ (phần Traces + Spans)
//   - https://www.w3.org/TR/trace-context/ (CHỈ section 3 — Traceparent Header)
//
// Nhiệm vụ: implement class này để đại diện cho 1 trace context
//
// Hiểu đúng trước khi code:
//   - trace_id:  ID duy nhất của 1 request xuyên suốt toàn bộ hệ thống (16 bytes hex = 32 chars)
//   - span_id:   ID của 1 đoạn xử lý cụ thể (8 bytes hex = 16 chars)
//   - W3C format: traceparent: 00-{traceId}-{spanId}-{flags}
//   - Ví dụ:      traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
public class TraceContext {

    private final String traceId;
    private final String spanId;
    private final String flags;

    public TraceContext(String traceId, String spanId, String flags) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.flags = flags;
    }

    // TODO: tạo TraceContext mới với random traceId + spanId
    // Hint: UUID.randomUUID().toString().replace("-", "") cho ra 32 hex chars
    public static TraceContext newTrace() {
        throw new UnsupportedOperationException("TODO: implement newTrace()");
    }

    // TODO: parse từ header "traceparent" theo W3C format
    // Input:  "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
    // Hint:   split("-") sẽ cho ["00", "traceId", "spanId", "flags"]
    public static TraceContext fromHeader(String traceparentHeader) {
        throw new UnsupportedOperationException("TODO: implement fromHeader()");
    }

    // TODO: tạo child span — giữ nguyên traceId, tạo spanId mới
    // Dùng khi 1 service gọi sang service khác (context propagation)
    public TraceContext newChildSpan() {
        throw new UnsupportedOperationException("TODO: implement newChildSpan()");
    }

    // TODO: serialize về W3C format để gắn vào HTTP header
    // Output: "00-{traceId}-{spanId}-{flags}"
    public String toHeader() {
        throw new UnsupportedOperationException("TODO: implement toHeader()");
    }

    public String getTraceId() { return traceId; }
    public String getSpanId()  { return spanId; }
    public String getFlags()   { return flags; }

    @Override
    public String toString() {
        return "TraceContext{traceId='" + traceId + "', spanId='" + spanId + "'}";
    }
}
