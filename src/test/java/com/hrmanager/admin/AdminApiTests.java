package com.hrmanager.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hrmanager.role.entity.SysRole;
import com.hrmanager.role.mapper.SysRoleMapper;
import com.hrmanager.support.AbstractApiTest;
import com.hrmanager.user.entity.SysUser;
import com.hrmanager.user.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AdminApiTests extends AbstractApiTest {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    @Test
    void adminCanListDepartmentsRolesAndUsers() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");

        mockMvc.perform(get("/api/admin/departments/tree").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3));

        mockMvc.perform(get("/api/admin/roles").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/admin/users").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(4)));
    }

    @Test
    void createdUserMustChangeInitialPassword() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");
        SysUser adminUser = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getEmployeeNo, "10001")
                .last("limit 1"));
        SysRole employeeRole = roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getCode, "EMPLOYEE")
                .last("limit 1"));

        String createBody = "{\"employeeNo\":\"20001\",\"name\":\"测试员工\",\"departmentId\":"
                + adminUser.getDepartmentId() + ",\"roleId\":" + employeeRole.getId() + "}";
        mockMvc.perform(post("/api/admin/users")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));

        MockHttpSession newUser = login("20001", "Init1234");

        mockMvc.perform(get("/api/dashboard").session(newUser))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("请先修改初始密码"));

        mockMvc.perform(post("/api/auth/change-password")
                        .session(newUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"Init1234\",\"newPassword\":\"NewPass123\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/dashboard").session(newUser))
                .andExpect(status().isOk());
    }

    @Test
    void departmentWithActiveUsersCannotBeDeleted() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");
        SysUser adminUser = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getEmployeeNo, "10001")
                .last("limit 1"));

        mockMvc.perform(delete("/api/admin/departments/" + adminUser.getDepartmentId()).session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("部门或下属部门中存在有效用户，请调整用户的归属部门后再删除"));
    }

    @Test
    void duplicateDepartmentNameIsRejected() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");
        String body = "{\"name\":\"质量保证部\",\"sort\":1}";

        mockMvc.perform(post("/api/admin/departments")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").isString());

        mockMvc.perform(post("/api/admin/departments")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("同级部门名称已存在"));
    }

    @Test
    void duplicateRoleCodeIsRejected() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");

        mockMvc.perform(post("/api/admin/roles")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"EMPLOYEE\",\"name\":\"重复角色\",\"permissions\":\"dashboard\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("角色编码已存在"));
    }

}
