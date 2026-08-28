package com.web.project.admin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SysUserTableNamingTest {

    private final Path projectRoot = Path.of(System.getProperty("user.dir"));

    @Test
    void freshSchemaAndMapperUseSysUserTableName() throws IOException {
        String schema = read("web_project.sql");
        String mapper = read("src/main/resources/mapper/admin/AdminUserMapper.xml");

        assertTrue(schema.contains("CREATE TABLE sys_user"));
        assertTrue(schema.contains("uk_sys_user_username"));
        assertFalse(schema.contains("admin_user"));
        assertFalse(mapper.contains("admin_user"));
        assertTrue(mapper.contains("FROM sys_user"));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
