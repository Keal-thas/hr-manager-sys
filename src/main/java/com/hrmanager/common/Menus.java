package com.hrmanager.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Menus {

    private Menus() {
    }

    private static Map<String, String> menu(String code, String name) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("name", name);
        return item;
    }

    public static final List<Map<String, String>> ALL = Arrays.asList(
            menu("dashboard", "首页"),
            menu("my-attendance", "我的考勤"),
            menu("my-messages", "消息通知"),
            menu("profile", "个人中心"),
            menu("admin-attendance", "考勤管理"),
            menu("admin-notice", "通知发布"),
            menu("admin-template", "通知模板"),
            menu("admin-dept", "部门管理"),
            menu("admin-role", "角色管理"),
            menu("admin-user", "用户管理")
    );

    public static List<Map<String, String>> forPermissions(Set<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return new ArrayList<>();
        }
        if (permissions.contains("*")) {
            return ALL;
        }
        List<Map<String, String>> result = new ArrayList<>();
        for (Map<String, String> item : ALL) {
            if (permissions.contains(item.get("code"))) {
                result.add(item);
            }
        }
        return result;
    }
}
