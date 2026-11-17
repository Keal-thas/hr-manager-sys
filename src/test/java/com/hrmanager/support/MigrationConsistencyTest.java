package com.hrmanager.support;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationConsistencyTest {

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?i)^create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([a-z0-9_]+)");
    private static final Pattern ALTER_ADD_COLUMN = Pattern.compile(
            "(?i)^alter\\s+table\\s+([a-z0-9_]+)\\s+add\\s+column\\s+([a-z][a-z0-9_]*)");
    private static final Pattern COLUMN = Pattern.compile("^([a-z][a-z0-9_]*)\\s+");

    @Test
    void h2AndMysqlMigrationsStayInSync() throws Exception {
        Map<String, String> h2Files = versionedFiles("h2");
        Map<String, String> mysqlFiles = versionedFiles("mysql");

        assertEquals(new TreeSet<>(h2Files.keySet()), new TreeSet<>(mysqlFiles.keySet()),
                "migration versions differ");

        Map<String, Set<String>> h2 = parse(h2Files.values());
        Map<String, Set<String>> mysql = parse(mysqlFiles.values());

        assertFalse(h2.isEmpty(), "H2 migrations should contain tables");
        assertEquals(new TreeSet<>(h2.keySet()), new TreeSet<>(mysql.keySet()), "table sets differ");
        for (String table : h2.keySet()) {
            assertEquals(new TreeSet<>(h2.get(table)), new TreeSet<>(mysql.get(table)),
                    "column sets differ for table " + table);
        }
        assertTrue(mysql.containsKey("wecom_message_log"), "missing wecom_message_log");
        assertTrue(mysql.get("sys_user").contains("wecom_user_id"), "missing wecom_user_id");
    }

    private Map<String, String> versionedFiles(String vendor) throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:db/migration/" + vendor + "/V*.sql");
        List<Resource> sorted = new ArrayList<>(Arrays.asList(resources));
        sorted.sort((a, b) -> a.getFilename().compareTo(b.getFilename()));

        Map<String, String> files = new LinkedHashMap<>();
        for (Resource resource : sorted) {
            String filename = resource.getFilename();
            files.put(filename, read(resource));
        }
        return files;
    }

    private Map<String, Set<String>> parse(Iterable<String> sqlFiles) {
        Map<String, Set<String>> tables = new LinkedHashMap<>();
        for (String sql : sqlFiles) {
            String currentTable = null;
            Set<String> currentColumns = null;
            for (String rawLine : sql.split("\n")) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("--")) {
                    continue;
                }
                Matcher alter = ALTER_ADD_COLUMN.matcher(line);
                if (alter.find()) {
                    String table = alter.group(1).toLowerCase();
                    tables.computeIfAbsent(table, key -> new LinkedHashSet<>()).add(alter.group(2).toLowerCase());
                    continue;
                }
                Matcher create = CREATE_TABLE.matcher(line);
                if (create.find()) {
                    currentTable = create.group(1).toLowerCase();
                    currentColumns = new LinkedHashSet<>();
                    tables.put(currentTable, currentColumns);
                    continue;
                }
                if (currentTable == null) {
                    continue;
                }
                if (line.startsWith(")")) {
                    currentTable = null;
                    currentColumns = null;
                    continue;
                }
                String lower = line.toLowerCase();
                if (lower.startsWith("key ")
                        || lower.startsWith("primary key")
                        || lower.startsWith("unique key")
                        || lower.startsWith("constraint ")
                        || lower.startsWith("index ")) {
                    continue;
                }
                Matcher column = COLUMN.matcher(lower);
                if (column.find()) {
                    currentColumns.add(column.group(1));
                }
            }
        }
        return tables;
    }

    private String read(Resource resource) throws Exception {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
            }
        }
        return content.toString();
    }
}
