package com.hrmanager.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MenuController {

    @GetMapping("/menus")
    public ApiResponse<List<Map<String, String>>> menus(HttpServletRequest request) {
        return ApiResponse.ok(Menus.forPermissions(SessionUtil.current(request).getPermissions()));
    }

    @GetMapping("/admin/menus")
    public ApiResponse<List<Map<String, String>>> allMenus() {
        return ApiResponse.ok(Menus.ALL);
    }

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.ok("UP");
    }
}
