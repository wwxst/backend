package com.web.project.admin.service;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 系统用户头像文件存储。
 */
@Component
public class SysUserAvatarStorage {

    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024;
    private static final Set<String> SUPPORTED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final Path uploadDirectory;

    public SysUserAvatarStorage(
            @Value("${app.file.upload-dir:uploads}") String uploadDirectory
    ) {
        this.uploadDirectory = Paths.get(uploadDirectory).toAbsolutePath().normalize().resolve("sys-users");
    }

    /**
     * 保存头像并返回供前端展示的相对地址。
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择头像文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "头像文件不能超过2MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !SUPPORTED_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "头像仅支持JPG、PNG或WEBP格式");
        }

        String extension = switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "头像格式不支持");
        };
        String filename = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(uploadDirectory);
            file.transferTo(uploadDirectory.resolve(filename));
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, exception);
        }
        return "/uploads/sys-users/" + filename;
    }

    /**
     * 删除当前应用上传的旧头像。
     */
    public void delete(String avatar) {
        if (avatar == null || avatar.isBlank() || !avatar.startsWith("/uploads/sys-users/")) {
            return;
        }
        String filename = avatar.substring(avatar.lastIndexOf('/') + 1);
        Path target = uploadDirectory.resolve(filename).normalize();
        if (!target.startsWith(uploadDirectory)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, exception);
        }
    }
}
