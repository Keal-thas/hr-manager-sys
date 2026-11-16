package com.hrmanager.auth.controller;

import com.hrmanager.auth.service.AuthService;
import com.hrmanager.common.ApiResponse;
import com.hrmanager.common.LoginUser;
import com.hrmanager.common.Req;
import com.hrmanager.common.SessionKeys;
import com.hrmanager.common.SessionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String employeeNo = Req.str(body, "employeeNo");
        String password = Req.str(body, "password");
        Map<String, Object> result = authService.login(employeeNo, password);
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) result.get("user");
        LoginUser loginUser = new LoginUser(
                ((Number) user.get("id")).longValue(),
                (String) user.get("employeeNo"),
                (String) user.get("name"),
                user.get("departmentId") == null ? null : ((Number) user.get("departmentId")).longValue(),
                (String) result.get("roleCode"),
                (java.util.Set<String>) result.get("permissions"),
                Boolean.TRUE.equals(result.get("mustChangePassword"))
        );
        request.getSession(true).setAttribute(SessionKeys.LOGIN_USER, loginUser);
        return ApiResponse.ok(result);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(HttpServletRequest request) {
        return ApiResponse.ok(authService.me(SessionUtil.current(request)));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        LoginUser loginUser = SessionUtil.current(request);
        authService.changePassword(loginUser.getId(), Req.str(body, "oldPassword"), Req.str(body, "newPassword"));
        loginUser.setMustChangePassword(false);
        request.getSession(true).setAttribute(SessionKeys.LOGIN_USER, loginUser);
        return ApiResponse.ok();
    }
}
