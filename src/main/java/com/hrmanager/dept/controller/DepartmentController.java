package com.hrmanager.dept.controller;

import com.hrmanager.common.ApiResponse;
import com.hrmanager.dept.entity.SysDepartment;
import com.hrmanager.dept.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    public ApiResponse<List<SysDepartment>> list() {
        return ApiResponse.ok(departmentService.list());
    }

    @GetMapping("/tree")
    public ApiResponse<List<Map<String, Object>>> tree() {
        return ApiResponse.ok(departmentService.tree());
    }

    @PostMapping
    public ApiResponse<SysDepartment> create(@RequestBody SysDepartment body) {
        return ApiResponse.ok(departmentService.create(body));
    }

    @PutMapping("/{id}")
    public ApiResponse<SysDepartment> update(@PathVariable Long id, @RequestBody SysDepartment body) {
        return ApiResponse.ok(departmentService.update(id, body));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        departmentService.delete(id);
        return ApiResponse.ok();
    }
}
