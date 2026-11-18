package com.hrmanager.attendance.controller;

import com.hrmanager.attendance.entity.AttendanceBatch;
import com.hrmanager.attendance.entity.AttendancePenalty;
import com.hrmanager.attendance.entity.AttendanceRecord;
import com.hrmanager.attendance.service.AttendanceService;
import com.hrmanager.common.ApiResponse;
import com.hrmanager.common.PageResult;
import com.hrmanager.common.SessionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/attendance")
@RequiredArgsConstructor
public class AttendanceAdminController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AttendanceService attendanceService;

    @GetMapping("/batches")
    public ApiResponse<PageResult<Map<String, Object>>> batches(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return ApiResponse.ok(attendanceService.pageBatches(page, size, year, month));
    }

    @GetMapping("/batches/{id}")
    public ApiResponse<Map<String, Object>> batch(@PathVariable Long id) {
        return ApiResponse.ok(attendanceService.batchView(attendanceService.getBatch(id)));
    }

    @GetMapping("/latest")
    public ApiResponse<Map<String, Object>> latest() {
        AttendanceBatch batch = attendanceService.latestBatch();
        return ApiResponse.ok(batch == null ? null : attendanceService.batchView(batch));
    }

    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> upload(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(defaultValue = "NEW") String mode,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        return ApiResponse.ok(attendanceService.upload(year, month, mode, file, SessionUtil.current(request).getId()));
    }

    @GetMapping("/batches/{id}/records")
    public ApiResponse<PageResult<Map<String, Object>>> records(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(attendanceService.pageRecords(id, page, size, keyword, status));
    }

    @GetMapping("/records/{id}")
    public ApiResponse<Map<String, Object>> record(@PathVariable Long id) {
        return ApiResponse.ok(attendanceService.recordDetail(id));
    }

    @PostMapping("/records/{id}/offline-signature")
    public ApiResponse<Map<String, Object>> offlineSignature(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(attendanceService.offlineSignature(id, file));
    }

    @PostMapping("/batches/{id}/remind")
    public ApiResponse<Map<String, Object>> remind(
            @PathVariable Long id,
            @RequestParam(defaultValue = "ATTENDANCE") String type) {
        int count = attendanceService.remind(id, type);
        return ApiResponse.ok(java.util.Collections.singletonMap("count", count));
    }

    @PostMapping("/batches/{id}/penalties/upload")
    public ApiResponse<Map<String, Object>> penalties(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        int count = attendanceService.uploadPenalties(id, file);
        return ApiResponse.ok(java.util.Collections.singletonMap("count", count));
    }

    @GetMapping("/batches/{id}/penalties")
    public ApiResponse<PageResult<Map<String, Object>>> penalties(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(attendanceService.pagePenalties(id, page, size, keyword));
    }

    @GetMapping("/batches/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id) {
        return download(attendanceService.export(id), "attendance-" + id + ".xlsx");
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        return download(attendanceService.template(), "attendance-template.xlsx");
    }

    @GetMapping("/penalty-template")
    public ResponseEntity<byte[]> penaltyTemplate() {
        return download(attendanceService.penaltyTemplate(), "penalty-template.xlsx");
    }

    private ResponseEntity<byte[]> download(byte[] data, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(XLSX)
                .body(data);
    }
}
