package com.hrmanager.support;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SchemaConsistencyTest {

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?i)^create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([a-z0-9_]+)");
    private static final Pattern COLUMN = Pattern.compile("^([a-z][a-z0-9_]*)\\s+");

    @Test
    void h2AndMysqlSchemasStayInSync() throws Exception {
        Map<String, Set<String>> h2 = parse(read("schema.sql"));
        Map<String, Set<String>> mysql = parse(read("schema-mysql.sql"));

        assertFalse(h2.isEmpty(), "H2 schema should contain tables");
        assertEquals(new TreeSet<>(h2.keySet()), new TreeSet<>(mysql.keySet()), "table sets differ");
        for (String table : h2.keySet()) {
            assertEquals(new TreeSet<>(h2.get(table)), new TreeSet<>(mysql.get(table)),
                    "column sets differ for table " + table);
        }
    }

    private Map<String, Set<String>> parse(String sql) {
        Map<String, Set<String>> tables = new LinkedHashMap<>();
        String currentTable = null;
        Set<String> currentColumns = null;
        for (String rawLine : sql.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("--")) {
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
        return tables;
    }

    private String read(String resource) throws Exception {
        InputStream input = getClass().getClassLoader().getResourceAsStream(resource);
        assertNotNull(input, "missing resource: " + resource);
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
            }
        }
        return content.toString();
    }
}
