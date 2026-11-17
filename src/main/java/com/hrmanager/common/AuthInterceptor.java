package com.hrmanager.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        HttpSession session = request.getSession(false);
        LoginUser user = session == null ? null : (LoginUser) session.getAttribute(SessionKeys.LOGIN_USER);
        if (user == null) {
            write(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录");
            return false;
        }
        String path = request.getRequestURI();
        if (user.isMustChangePassword()
                && !path.equals("/api/auth/change-password")
                && !path.equals("/api/auth/me")
                && !path.equals("/api/auth/logout")) {
            write(response, HttpServletResponse.SC_FORBIDDEN, "请先修改初始密码");
            return false;
        }
        if (path.startsWith("/api/admin/")) {
            String required = requiredPermission(path);
            if (required == null || !user.getPermissions().contains(required)) {
                write(response, HttpServletResponse.SC_FORBIDDEN, "无权限操作");
                return false;
            }
        }
        request.setAttribute(SessionKeys.LOGIN_USER, user);
        return true;
    }

    private String requiredPermission(String path) {
        if (path.startsWith("/api/admin/attendance")) {
            return "admin-attendance";
        }
        if (path.startsWith("/api/admin/notices")) {
            return "admin-notice";
        }
        if (path.startsWith("/api/admin/templates")) {
            return "admin-template";
        }
        if (path.startsWith("/api/admin/departments")) {
            return "admin-dept";
        }
        if (path.startsWith("/api/admin/wecom")) {
            return "admin-notice";
        }
        if (path.startsWith("/api/admin/roles") || path.equals("/api/admin/menus")) {
            return "admin-role";
        }
        if (path.startsWith("/api/admin/users")) {
            return "admin-user";
        }
        return null;
    }

    private void write(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(message)));
    }
}
