package com.web.project.admin.service;

import com.web.project.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SysUserAvatarStorageTest {

    @TempDir
    Path tempDirectory;

    @Test
    void storesAndDeletesSupportedAvatar() throws Exception {
        SysUserAvatarStorage storage = new SysUserAvatarStorage(tempDirectory.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[]{1, 2, 3});

        String avatar = storage.store(file);

        assertTrue(avatar.startsWith("/uploads/sys-users/"));
        Path storedFile = tempDirectory.resolve("sys-users").resolve(avatar.substring(avatar.lastIndexOf('/') + 1));
        assertTrue(Files.exists(storedFile));

        storage.delete(avatar);

        assertFalse(Files.exists(storedFile));
    }

    @Test
    void rejectsUnsupportedAvatarType() {
        SysUserAvatarStorage storage = new SysUserAvatarStorage(tempDirectory.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.gif", "image/gif", new byte[]{1});

        assertThrows(BusinessException.class, () -> storage.store(file));
    }
}
