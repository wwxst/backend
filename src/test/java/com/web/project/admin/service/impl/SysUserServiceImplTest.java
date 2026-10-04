package com.web.project.admin.service.impl;

import com.web.project.admin.dto.CreateSysUserDTO;
import com.web.project.admin.dto.ResetSysUserPasswordDTO;
import com.web.project.admin.dto.UpdateSysUserDTO;
import com.web.project.admin.entity.SysUser;
import com.web.project.admin.mapper.SysUserMapper;
import com.web.project.admin.service.SysUserAvatarStorage;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SysUserServiceImplTest {

    private final SysUserMapper mapper = mock(SysUserMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final SysUserAvatarStorage avatarStorage = mock(SysUserAvatarStorage.class);
    private final SysUserServiceImpl service = new SysUserServiceImpl(mapper, passwordEncoder, avatarStorage);

    @Test
    void updateStatusDelegatesAndAcceptsOneUpdatedRow() {
        when(mapper.updateStatus(1L, 0)).thenReturn(1);

        service.updateStatus(1L, 0);

        verify(mapper).updateStatus(1L, 0);
    }

    @Test
    void updateStatusRejectsMissingUser() {
        when(mapper.updateStatus(99L, 1)).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateStatus(99L, 1)
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void createHashesPasswordAndReturnsSafeUser() {
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        doAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(7L);
            return 1;
        }).when(mapper).insert(any(SysUser.class));
        SysUser stored = user(7L, "operator", "运营人员", 1);
        stored.setPassword("hashed-password");
        when(mapper.selectById(7L)).thenReturn(stored);

        var result = service.create(new CreateSysUserDTO("operator", "password123", "运营人员", 1));

        assertEquals(7L, result.id());
        assertEquals("operator", result.username());
        verify(passwordEncoder).encode("password123");
        verify(mapper).insert(any(SysUser.class));
    }

    @Test
    void createRejectsUnexpectedInsertCount() {
        when(mapper.insert(any(SysUser.class))).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.create(new CreateSysUserDTO("operator", "password123", "运营人员", 1))
        );

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, exception.getErrorCode());
    }

    @Test
    void createRejectsPasswordOutsidePolicyBeforeWriting() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.create(new CreateSysUserDTO("operator", "short", "运营人员", 1))
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verifyNoInteractions(mapper, passwordEncoder);
    }

    @Test
    void updateAndResetPasswordWriteRequestedAccount() {
        when(mapper.update(any(SysUser.class))).thenReturn(1);
        when(mapper.selectById(8L)).thenReturn(user(8L, "edited", "编辑后", 0));
        when(passwordEncoder.encode("newpassword")).thenReturn("new-hash");
        when(mapper.updatePassword(8L, "new-hash")).thenReturn(1);

        var result = service.update(8L, new UpdateSysUserDTO(" edited ", " 编辑后 "));
        service.resetPassword(8L, new ResetSysUserPasswordDTO("newpassword"));

        assertEquals("edited", result.username());
        verify(mapper).update(any(SysUser.class));
        verify(mapper).updatePassword(8L, "new-hash");
    }

    @Test
    void deleteRejectsMissingAccount() {
        when(mapper.deleteById(88L)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.delete(88L));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void deleteAcceptsOneDeletedRow() {
        when(mapper.deleteById(8L)).thenReturn(1);

        service.delete(8L);

        verify(mapper).deleteById(8L);
    }

    private SysUser user(Long id, String username, String nickname, Integer status) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(nickname);
        user.setStatus(status);
        return user;
    }
}
