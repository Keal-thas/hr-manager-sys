package com.hrmanager.common;

import javax.servlet.http.HttpServletRequest;

public final class SessionUtil {

    private SessionUtil() {
    }

    public static LoginUser current(HttpServletRequest request) {
        Object value = request.getAttribute(SessionKeys.LOGIN_USER);
        if (value instanceof LoginUser) {
            return (LoginUser) value;
        }
        throw new BusinessException(401, "未登录");
    }
}
