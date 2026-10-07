package com.web.project.storage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record BindStorageSpaceDTO(@NotBlank @Size(max = 63) String bucketName,
                                  @NotBlank @Size(max = 32) String regionId,
                                  @Size(max = 255) String accessDomain,
                                  @NotBlank @Pattern(regexp = "public-read|public-read-write") String accessPermission) {}
