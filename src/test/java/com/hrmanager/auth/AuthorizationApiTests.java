package com.hrmanager.auth;

import com.hrmanager.support.AbstractApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AuthorizationApiTests extends AbstractApiTest {

    @Test
    void employeeCanUseSelfServiceButNotAdminApis() throws Exception {
        MockHttpSession employee = login("10003", "Emp123456");

        mockMvc.perform(get("/api/dashboard").session(employee))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/my/messages").session(employee))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/users").session(employee))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("无权限操作"));

        mockMvc.perform(get("/api/admin/attendance/batches").session(employee))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/wecom/status").session(employee))
                .andExpect(status().isForbidden());
    }

    @Test
    void hrCanUseHrMenusButNotRoleManagement() throws Exception {
        MockHttpSession hr = login("10002", "Hr123456");

        mockMvc.perform(get("/api/admin/users").session(hr))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/departments").session(hr))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/attendance/batches").session(hr))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/roles").session(hr))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("无权限操作"));

        mockMvc.perform(get("/api/admin/menus").session(hr))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/wecom/status").session(hr))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanUseAllAdminApis() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");

        mockMvc.perform(get("/api/admin/roles").session(admin))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/menus").session(admin))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/users").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("ok")));

        mockMvc.perform(get("/api/admin/wecom/status").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.ready").value(false));

        mockMvc.perform(get("/api/admin/wecom/logs").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());
    }
}
