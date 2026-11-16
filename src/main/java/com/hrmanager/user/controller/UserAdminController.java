package com.hrmanager.user.controller;

import com.hrmanager.common.ApiResponse;
import com.hrmanager.common.PageResult;
import com.hrmanager.common.Req;
import com.hrmanager.user.entity.SysUser;
import com.hrmanager.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserService userService;

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(userService.page(page, size, keyword, departmentId, roleId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(userService.view(id));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody SysUser body) {
        return ApiResponse.ok(userService.view(userService.create(body)));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody SysUser body) {
        return ApiResponse.ok(userService.view(userService.update(id, body)));
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        userService.resetPassword(id, body == null ? null : Req.str(body, "newPassword"));
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/status")
    public ApiResponse<Void> status(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        userService.setStatus(id, Req.str(body, "status"));
        return ApiResponse.ok();
    }
}
