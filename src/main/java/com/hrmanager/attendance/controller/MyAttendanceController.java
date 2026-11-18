package com.hrmanager.attendance.controller;

import com.hrmanager.attendance.service.AttendanceService;
import com.hrmanager.common.ApiResponse;
import com.hrmanager.common.PageResult;
import com.hrmanager.common.Req;
import com.hrmanager.common.SessionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/my/attendance")
@RequiredArgsConstructor
public class MyAttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping("/current")
    public ApiResponse<Map<String, Object>> current(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            HttpServletRequest request) {
        return ApiResponse.ok(attendanceService.myCurrent(SessionUtil.current(request).getId(), year, month));
    }

    @GetMapping("/history")
    public ApiResponse<PageResult<Map<String, Object>>> history(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        return ApiResponse.ok(attendanceService.myHistory(SessionUtil.current(request).getId(), page, size));
    }

    @GetMapping("/{batchId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long batchId, HttpServletRequest request) {
        return ApiResponse.ok(attendanceService.myDetail(batchId, SessionUtil.current(request).getId()));
    }

    @PostMapping("/{batchId}/confirm")
    public ApiResponse<Map<String, Object>> confirm(
            @PathVariable Long batchId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        return ApiResponse.ok(attendanceService.confirm(batchId, SessionUtil.current(request).getId(),
                Req.str(body, "signatureImage"), Req.bool(body, "agree")));
    }
}
