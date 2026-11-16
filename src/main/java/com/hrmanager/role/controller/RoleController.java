package com.hrmanager.role.controller;

import com.hrmanager.common.ApiResponse;
import com.hrmanager.role.entity.SysRole;
import com.hrmanager.role.service.RoleService;
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

@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public ApiResponse<List<SysRole>> list() {
        return ApiResponse.ok(roleService.list());
    }

    @PostMapping
    public ApiResponse<SysRole> create(@RequestBody SysRole body) {
        return ApiResponse.ok(roleService.create(body));
    }

    @PutMapping("/{id}")
    public ApiResponse<SysRole> update(@PathVariable Long id, @RequestBody SysRole body) {
        return ApiResponse.ok(roleService.update(id, body));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        roleService.disable(id);
        return ApiResponse.ok();
    }
}
