package com.hrmanager.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TestDatabase {

    private static final int STALE_HOURS = 6;
    private static final String HOST = env("TEST_DB_HOST", "localhost");
    private static final String PORT = env("TEST_DB_PORT", "3306");
    private static final String USER = env("TEST_DB_USER", "root");
    private static final String PASSWORD = env("TEST_DB_PASSWORD", "");
    private static final String SCHEMA = "hr_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    private static String url;

    private TestDatabase() {
    }

    public static synchronized String url() {
        if (url == null) {
            createSchema();
            url = "jdbc:mysql://" + HOST + ":" + PORT + "/" + SCHEMA
                    + "?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true"
                    + "&sessionVariables=sql_mode='STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION',time_zone='+08:00'"
                    + "&connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true";
        }
        return url;
    }

    public static String username() {
        return USER;
    }

    public static String password() {
        return PASSWORD;
    }

    private static void createSchema() {
        String adminUrl = "jdbc:mysql://" + HOST + ":" + PORT
                + "/?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8";
        try (Connection conn = DriverManager.getConnection(adminUrl, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {
            cleanupStale(stmt);
            stmt.executeUpdate("CREATE DATABASE " + SCHEMA
                    + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        } catch (SQLException e) {
            throw new IllegalStateException("无法创建测试库 " + SCHEMA + ", 请确认本机 MySQL 8 已启动且账号可建库", e);
        }
    }

    private static void cleanupStale(Statement stmt) throws SQLException {
        // 只清理超过 STALE_HOURS 的遗留库, 避免并行运行的测试互相删库
        try (ResultSet rs = stmt.executeQuery(
                "SELECT table_schema FROM information_schema.tables"
                        + " WHERE table_schema LIKE 'hr\\_test\\_%'"
                        + " GROUP BY table_schema"
                        + " HAVING MIN(create_time) < NOW() - INTERVAL " + STALE_HOURS + " HOUR")) {
            List<String> stale = new ArrayList<>();
            while (rs.next()) {
                stale.add(rs.getString(1));
            }
            for (String name : stale) {
                stmt.executeUpdate("DROP DATABASE `" + name + "`");
            }
        }
    }

    private static String env(String name, String def) {
        String value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? def : value;
    }
}
