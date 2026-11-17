package com.hrmanager.common;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BusinessMetrics {

    private final MeterRegistry registry;

    public void login(String result) {
        counter("hr.auth.login", "result", result);
    }

    public void passwordChange(String result) {
        counter("hr.auth.password-change", "result", result);
    }

    public void attendanceUpload(String mode) {
        counter("hr.attendance.upload", "mode", mode);
    }

    public void attendanceConfirm() {
        counter("hr.attendance.confirm", "result", "success");
    }

    public void attendanceRemind(String type) {
        counter("hr.attendance.remind", "type", type);
    }

    public void attendancePenaltyUpload(int rows) {
        registry.counter("hr.attendance.penalty.upload").increment();
        registry.counter("hr.attendance.penalty.rows").increment(rows);
    }

    public void noticePublished(String targetType, int recipients) {
        counter("hr.notice.publish", "target", targetType);
        registry.counter("hr.notice.recipients").increment(recipients);
    }

    public void messageSent(String type) {
        counter("hr.message.send", "type", type);
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void stopTimer(Timer.Sample sample, String name, String kind) {
        sample.stop(registry.timer(name, "kind", kind));
    }

    private void counter(String name, String key, String value) {
        Counter.builder(name).tag(key, value).register(registry).increment();
    }
}
