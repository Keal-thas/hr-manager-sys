package com.hrmanager.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hrmanager.role.entity.SysRole;
import com.hrmanager.role.mapper.SysRoleMapper;
import com.hrmanager.support.AbstractApiTest;
import com.hrmanager.user.entity.SysUser;
import com.hrmanager.user.mapper.SysUserMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class RoleRuleApiTests extends AbstractApiTest {

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private SysUserMapper userMapper;

    private SysRole role(String code) {
        return roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getCode, code)
                .last("limit 1"));
    }

    private Long createRole(MockHttpSession admin, String code, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/roles")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.data.id");
        return id.longValue();
    }

    @Test
    void createRejectsMissingCodeOrNameAndDefaultsPermissions() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");

        mockMvc.perform(post("/api/admin/roles").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试角色\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("角色编码和名称不能为空"));

        mockMvc.perform(post("/api/admin/roles").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"TEST_ROLE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("角色编码和名称不能为空"));

        mockMvc.perform(post("/api/admin/roles").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"TEST_ROLE\",\"name\":\"测试角色\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.permissions").value(""));

        assertEquals("", role("TEST_ROLE").getPermissions());
    }

    @Test
    void updateAndDisableRole() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");
        Long id = createRole(admin, "TEST_ROLE", "测试角色");

        mockMvc.perform(put("/api/admin/roles/" + id).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新名称\",\"permissions\":\"dashboard,profile\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("新名称"))
                .andExpect(jsonPath("$.data.permissions").value("dashboard,profile"));

        mockMvc.perform(delete("/api/admin/roles/" + id).session(admin))
                .andExpect(status().isOk());
        assertEquals("DISABLED", roleMapper.selectById(id).getStatus());

        mockMvc.perform(put("/api/admin/roles/999999").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"不存在\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("角色不存在"));

        mockMvc.perform(delete("/api/admin/roles/999999").session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("角色不存在"));
    }

    @Test
    void disabledRoleGrantsNoPermissionsOnLogin() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");
        Long id = createRole(admin, "TEST_ROLE", "测试角色");
        mockMvc.perform(put("/api/admin/roles/" + id).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":\"dashboard,profile\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/roles/" + id).session(admin))
                .andExpect(status().isOk());

        SysUser seed = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getEmployeeNo, "10001")
                .last("limit 1"));
        mockMvc.perform(post("/api/admin/users").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeNo\":\"20001\",\"name\":\"测试员工\",\"departmentId\":"
                                + seed.getDepartmentId() + ",\"roleId\":" + id + "}"))
                .andExpect(status().isOk());

        MockHttpSession newUser = login("20001", "bydpcic@123");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/auth/me").session(newUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions.length()").value(0));
    }
}
