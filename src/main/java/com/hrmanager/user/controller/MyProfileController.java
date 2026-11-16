package com.hrmanager.user.controller;

import com.hrmanager.auth.service.AuthService;
import com.hrmanager.common.ApiResponse;
import com.hrmanager.common.Req;
import com.hrmanager.common.SessionUtil;
import com.hrmanager.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/my/profile")
@RequiredArgsConstructor
public class MyProfileController {

    private final AuthService authService;
    private final UserService userService;

    @GetMapping
    public ApiResponse<Map<String, Object>> get(HttpServletRequest request) {
        return ApiResponse.ok(authService.me(SessionUtil.current(request)));
    }

    @PutMapping
    public ApiResponse<Map<String, Object>> update(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        userService.updateProfile(SessionUtil.current(request).getId(), Req.str(body, "phone"), Req.str(body, "email"));
        return ApiResponse.ok(authService.me(SessionUtil.current(request)));
    }
}
