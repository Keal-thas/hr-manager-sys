package com.hrmanager.auth;

import com.hrmanager.support.AbstractApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AuthApiTests extends AbstractApiTest {

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("UP"));
    }

    @Test
    void protectedApiRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.message").value("未登录"));
    }

    @Test
    void loginReturnsSessionAndProfile() throws Exception {
        MockHttpSession session = login("10001", "Admin1234");

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.user.employeeNo").value("10001"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false))
                .andExpect(jsonPath("$.data.menus").isArray());
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        MockHttpSession session = login("10001", "Admin1234");

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRejectedWithoutLocking() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeNo\":\"10004\",\"password\":\"wrong\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("密码错误，请重新输入"));
    }

    @Test
    void fiveWrongPasswordsLockAccount() throws Exception {
        for (int attempt = 1; attempt <= 4; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"employeeNo\":\"10004\",\"password\":\"wrong\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("密码错误，请重新输入"));
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeNo\":\"10004\",\"password\":\"wrong\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("密码错误5次，请稍后再试"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeNo\":\"10004\",\"password\":\"Emp123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("密码错误5次，请稍后再试"));
    }

    @Test
    void changePasswordRejectsWeakPassword() throws Exception {
        MockHttpSession session = login("10003", "Emp123456");

        mockMvc.perform(post("/api/auth/change-password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"Emp123456\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("新密码不能小于8位，请重新输入"));
    }

    @Test
    void changePasswordRejectsSamePassword() throws Exception {
        MockHttpSession session = login("10003", "Emp123456");

        mockMvc.perform(post("/api/auth/change-password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"Emp123456\",\"newPassword\":\"Emp123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("新密码不能与原密码相同，请重新录入"));
    }
}
