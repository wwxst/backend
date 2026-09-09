package com.web.project.common.exception;

import com.web.project.auth.controller.SysUserAuthController;
import com.web.project.auth.service.SysUserAuthService;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.result.PageResult;
import com.web.project.user.controller.UserAccountController;
import com.web.project.user.service.UserAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    private final UserAccountService users = mock(UserAccountService.class);
    private final SysUserAuthService auth = mock(SysUserAuthService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new UserAccountController(users), new SysUserAuthController(auth))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void invalidPageReturnsBadRequestWithoutCallingService() throws Exception {
        for (String page : List.of("not-a-number", "0")) {
            mvc.perform(get("/api/sys-user/users").param("page", page))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
        }
        verifyNoInteractions(users);
    }

    @Test
    void wrongMethodPreservesMethodNotAllowedAndAllowHeader() throws Exception {
        mvc.perform(delete("/api/sys-user/users"))
                .andExpect(status().isMethodNotAllowed()).andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.code").value(40000));
        verifyNoInteractions(users);
    }

    @Test
    void unsupportedContentTypeRemainsClientError() throws Exception {
        mvc.perform(post("/api/sys-user/auth/login").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(auth);
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/sys-user/auth/login").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
        verifyNoInteractions(auth);
    }

    @Test
    void validRequestAndBusinessErrorKeepTheirContracts() throws Exception {
        when(users.getUserPage(any())).thenReturn(new PageResult<>(0L, 1, 10, List.of()));
        mvc.perform(get("/api/sys-user/users"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records").isEmpty());
        when(auth.login(any())).thenThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        mvc.perform(post("/api/sys-user/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void unexpectedFailureRemainsServerErrorWithoutLeakingDetails() throws Exception {
        when(users.getUserPage(any())).thenThrow(new IllegalStateException("internal database details"));
        mvc.perform(get("/api/sys-user/users"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(50000))
                .andExpect(jsonPath("$.msg").value(ErrorCode.INTERNAL_SERVER_ERROR.getDefaultMessage()));
    }
}
