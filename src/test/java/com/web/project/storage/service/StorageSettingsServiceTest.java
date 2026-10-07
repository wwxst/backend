package com.web.project.storage.service;

import com.web.project.channel.entity.SysConfig;
import com.web.project.common.exception.BusinessException;
import com.web.project.storage.dto.StorageCredentialsDTO;
import com.web.project.storage.dto.BindStorageSpaceDTO;
import com.web.project.storage.entity.StorageSpace;
import com.web.project.storage.mapper.StorageConfigMapper;
import com.web.project.storage.mapper.StorageFileMapper;
import com.web.project.storage.mapper.StorageSpaceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StorageSettingsServiceTest {
    StorageConfigMapper config;
    StorageSpaceMapper spaces;
    StorageFileMapper files;
    OssStorageClient oss;
    StorageSettingsService service;
    Map<String, String> values;
    StorageSpace space;

    @BeforeEach void setUp() {
        config = mock(StorageConfigMapper.class); spaces = mock(StorageSpaceMapper.class);
        files = mock(StorageFileMapper.class); oss = mock(OssStorageClient.class);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        var cipher = new CredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
        service = new StorageSettingsService(config, spaces, files, cipher, oss, manager);
        values = new LinkedHashMap<>();
        values.put("storage.type", "local"); values.put("storage.aliyun.access-key-id", "id");
        values.put("storage.aliyun.access-key-secret", cipher.encrypt("secret")); values.put("storage.aliyun.default-space-id", "7");
        when(config.selectStorageConfig()).thenAnswer(call -> rows());
        when(config.selectStorageConfigForUpdate()).thenAnswer(call -> rows());
        when(config.updateValue(anyString(), anyString())).thenAnswer(call -> { values.put(call.getArgument(0), call.getArgument(1)); return 1; });
        space = new StorageSpace(); space.setId(7L); space.setBucketName("old-bucket"); space.setRegionId("oss-cn-hangzhou");
        when(spaces.selectById(7L)).thenReturn(space);
    }

    @Test void defaultAndProviderSwitchRequireRemoteValidationBeforeWriting() {
        doThrow(StoragePolicy.bad("空间不可用")).when(oss).validateSpace(eq(space), any());
        assertThrows(BusinessException.class, () -> service.setDefault(7L));
        assertThrows(BusinessException.class, () -> service.updateType("aliyun"));
        verify(config, never()).updateValue(any(), any());
        verify(config, never()).selectStorageConfigForUpdate();
    }

    @Test void defaultUsesSharedConfigAndKeepsExistingSpace() {
        assertTrue(service.setDefault(7L).isDefault());
        verify(config).selectStorageConfigForUpdate();
        verify(config).updateValue("storage.aliyun.default-space-id", "7");
        verify(spaces, never()).insert(any());
        assertEquals("aliyun", service.updateType("aliyun").storageType());
    }

    @Test void deletionRejectsDefaultAndReferencedSpaces() {
        assertThrows(BusinessException.class, () -> service.deleteSpace(7L));
        values.put("storage.aliyun.default-space-id", "");
        when(files.countBySpaceId(7L)).thenReturn(2L);
        assertThrows(BusinessException.class, () -> service.deleteSpace(7L));
        verify(spaces, never()).delete(any());
        when(files.countBySpaceId(7L)).thenReturn(0L); when(spaces.delete(7L)).thenReturn(1);
        service.deleteSpace(7L);
        verify(spaces).delete(7L);
        verifyNoInteractions(oss);
    }

    @Test void syncAddsCloudSpacesWithoutChangingExistingDomainOrDefault() {
        space.setAccessDomain("https://cdn.example.com");
        when(oss.listBuckets(any())).thenReturn(List.of(new OssStorageClient.BucketInfo("old-bucket", "oss-cn-hangzhou"),
                new OssStorageClient.BucketInfo("new-bucket", "oss-cn-shanghai")));
        when(spaces.selectByBucketName("old-bucket")).thenReturn(space);
        when(spaces.insert(any())).thenReturn(1);
        assertEquals(1, service.sync());
        assertEquals("https://cdn.example.com", space.getAccessDomain());
        assertEquals("7", values.get("storage.aliyun.default-space-id"));
        verify(spaces, never()).delete(any()); verify(spaces, never()).updateDomain(any(), any());
    }

    @Test void concurrentConfigChangeRejectsStaleRemoteValidation() {
        doAnswer(call -> { values.put("storage.aliyun.default-space-id", ""); return null; }).when(oss).validateSpace(eq(space), any());
        assertThrows(BusinessException.class, () -> service.updateType("aliyun"));
        verify(config, never()).updateValue(any(), any());
    }

    @Test void syncRejectsRegionChangeForFilesAndUpdatesUnreferencedRegion() {
        when(oss.listBuckets(any())).thenReturn(List.of(new OssStorageClient.BucketInfo("old-bucket", "oss-cn-shanghai")));
        when(spaces.selectByBucketName("old-bucket")).thenReturn(space);
        when(files.countBySpaceId(7L)).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.sync());
        verify(spaces, never()).updateRegion(any(), any());
        when(files.countBySpaceId(7L)).thenReturn(0L); when(spaces.updateRegion(7L, "oss-cn-shanghai")).thenReturn(1);
        assertEquals(1, service.sync());
        verify(spaces).updateRegion(7L, "oss-cn-shanghai");
        assertEquals("7", values.get("storage.aliyun.default-space-id"));
    }

    @Test void credentialsValidateAccountThenStoreOnlyCiphertextAndRedactLogs() {
        var dto = new StorageCredentialsDTO("new-id", "new-secret");
        service.updateCredentials(dto);
        assertEquals("new-id", values.get("storage.aliyun.access-key-id"));
        assertFalse(values.get("storage.aliyun.access-key-secret").contains("new-secret"));
        assertEquals("new-secret", service.credentials(service.snapshot()).accessKeySecret());
        assertFalse(dto.toString().contains("new-secret")); assertFalse(dto.toString().contains("new-id"));
        assertFalse(service.snapshot().toString().contains("new-id"));
        verify(oss).listBuckets(any());
    }

    @Test void failedCredentialsValidationDoesNotReplaceStoredCredentials() {
        String before = values.get("storage.aliyun.access-key-secret");
        doThrow(new BusinessException(com.web.project.common.error.ErrorCode.INTERNAL_SERVER_ERROR, new RuntimeException("provider secret")))
                .when(oss).listBuckets(any());
        BusinessException error = assertThrows(BusinessException.class, () -> service.updateCredentials(new StorageCredentialsDTO("new-id", "new-secret")));
        assertNotNull(error.getCause()); assertFalse(error.getMessage().contains("provider secret"));
        assertEquals(before, values.get("storage.aliyun.access-key-secret"));
        verify(config, never()).updateValue(any(), any());
    }

    @Test void unknownProviderIsRejected() {
        assertThrows(BusinessException.class, () -> service.updateType("qiniu"));
    }

    @Test void bindingRejectsSelectedPermissionMismatchBeforeDatabaseWrite() {
        when(oss.validateSpace(any(), any())).thenReturn("public-read");
        var dto = new BindStorageSpaceDTO("new-bucket", "cn-hangzhou", null, "public-read-write");
        BusinessException error = assertThrows(BusinessException.class, () -> service.bind(dto));
        assertEquals("Bucket 实际读写权限与选择的权限不一致", error.getMessage());
        verify(spaces, never()).insert(any());
        verify(config, never()).selectStorageConfigForUpdate();
    }

    @Test void bothMatchingPermissionsBindAndCanBecomeDefaultForOss() {
        for (String permission : List.of("public-read", "public-read-write")) {
            when(oss.validateSpace(any(), any())).thenReturn(permission);
            doAnswer(call -> {
                StorageSpace inserted = call.getArgument(0);
                inserted.setId(7L);
                when(spaces.selectById(7L)).thenReturn(inserted);
                return 1;
            }).when(spaces).insert(any());
            assertEquals("new-bucket", service.bind(new BindStorageSpaceDTO("new-bucket", "cn-hangzhou", null, permission)).bucketName());
            assertTrue(service.setDefault(7L).isDefault());
            assertEquals("aliyun", service.updateType("aliyun").storageType());
        }
    }

    private List<SysConfig> rows() {
        return values.entrySet().stream().map(entry -> {
            SysConfig row = new SysConfig(); row.setConfigKey(entry.getKey()); row.setConfigValue(entry.getValue()); return row;
        }).toList();
    }
}
