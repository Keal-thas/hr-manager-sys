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
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class UserRuleApiTests extends AbstractApiTest {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    private SysUser user(String employeeNo) {
        return userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getEmployeeNo, employeeNo)
                .last("limit 1"));
    }

    private Long employeeRoleId() {
        return roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getCode, "EMPLOYEE")
                .last("limit 1")).getId();
    }

    private Map<String, Object> body(String employeeNo, String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("employeeNo", employeeNo);
        body.put("name", name);
        body.put("departmentId", user("10003").getDepartmentId());
        body.put("roleId", employeeRoleId());
        return body;
    }

    private Long createUser(MockHttpSession admin, String employeeNo, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/users")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body(employeeNo, name))))
                .andExpect(status().isOk())
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.data.id");
        return id.longValue();
    }

    @Test
    void createRejectsInvalidRequiredFieldsAndReferences() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");

        Map<String, Object> nonNumeric = body("ABC", "测试");
        mockMvc.perform(post("/api/admin/users").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(nonNumeric)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("工号必须为数字"));

        Map<String, Object> noName = body("20001", null);
        mockMvc.perform(post("/api/admin/users").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(noName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("姓名不能为空"));

        Map<String, Object> noDepartment = body("20001", "测试");
        noDepartment.remove("departmentId");
        mockMvc.perform(post("/api/admin/users").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(noDepartment)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请选择部门"));

        Map<String, Object> noRole = body("20001", "测试");
        noRole.remove("roleId");
        mockMvc.perform(post("/api/admin/users").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(noRole)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请选择角色"));

        Map<String, Object> missingDepartment = body("20001", "测试");
        missingDepartment.put("departmentId", 999999);
        mockMvc.perform(post("/api/admin/users").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(missingDepartment)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("部门不存在"));

        Map<String, Object> missingRole = body("20001", "测试");
        missingRole.put("roleId", 999999);
        mockMvc.perform(post("/api/admin/users").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(missingRole)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("角色不存在"));

        Map<String, Object> invalidPhone = body("20001", "测试");
        invalidPhone.put("phone", "123");
        mockMvc.perform(post("/api/admin/users").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(invalidPhone)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("手机号必须为11位数字"));

        Map<String, Object> invalidEmail = body("20001", "测试");
        invalidEmail.put("email", "test@example.com");
        mockMvc.perform(post("/api/admin/users").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(invalidEmail)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("邮箱必须为@bydpcic.com"));

        mockMvc.perform(post("/api/admin/users").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body("10001", "重复"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("员工工号已存在，请修改后保存"));
    }

    @Test
    void employeeNoCannotBeChangedAfterCreate() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");
        Long id = createUser(admin, "20001", "测试员工");

        Map<String, Object> change = body("20002", "测试员工");
        mockMvc.perform(put("/api/admin/users/" + id).session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(change)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("员工工号创建后不可修改"));

        assertEquals("20001", userMapper.selectById(id).getEmployeeNo());

        Map<String, Object> same = body("20001", "改名员工");
        mockMvc.perform(put("/api/admin/users/" + id).session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(same)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("改名员工"));
        assertEquals("改名员工", userMapper.selectById(id).getName());
    }

    @Test
    void updateRejectsInvalidReferencesAndContact() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");
        Long id = createUser(admin, "20001", "测试员工");

        Map<String, Object> badDepartment = new HashMap<>();
        badDepartment.put("departmentId", 999999);
        mockMvc.perform(put("/api/admin/users/" + id).session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(badDepartment)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("部门不存在"));

        Map<String, Object> badRole = new HashMap<>();
        badRole.put("roleId", 999999);
        mockMvc.perform(put("/api/admin/users/" + id).session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(badRole)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("角色不存在"));

        Map<String, Object> badPhone = new HashMap<>();
        badPhone.put("phone", "abc");
        mockMvc.perform(put("/api/admin/users/" + id).session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(badPhone)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("手机号必须为11位数字"));

        Map<String, Object> badEmail = new HashMap<>();
        badEmail.put("email", "test@example.com");
        mockMvc.perform(put("/api/admin/users/" + id).session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(badEmail)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("邮箱必须为@bydpcic.com"));

        mockMvc.perform(put("/api/admin/users/999999").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"不存在\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("用户不存在"));

        Map<String, Object> valid = new HashMap<>();
        valid.put("name", "新姓名");
        valid.put("phone", "13900000000");
        valid.put("email", "new@bydpcic.com");
        valid.put("status", "DISABLED");
        mockMvc.perform(put("/api/admin/users/" + id).session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(valid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("新姓名"))
                .andExpect(jsonPath("$.data.phone").value("13900000000"))
                .andExpect(jsonPath("$.data.email").value("new@bydpcic.com"))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    @Test
    void resetPasswordAndStatusValidation() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");
        Long id = createUser(admin, "20001", "测试员工");

        mockMvc.perform(post("/api/admin/users/" + id + "/reset-password")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"NewPass123\"}"))
                .andExpect(status().isOk());

        MockHttpSession newUser = login("20001", "NewPass123");
        assertNotNull(newUser);
        mockMvc.perform(get("/api/dashboard").session(newUser))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("请先修改初始密码"));

        mockMvc.perform(post("/api/admin/users/" + id + "/status")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"LOCKED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("状态不合法"));

        mockMvc.perform(post("/api/admin/users/" + id + "/status")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());
        assertEquals("DISABLED", userMapper.selectById(id).getStatus());

        mockMvc.perform(post("/api/admin/users/" + id + "/status")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        assertEquals("ACTIVE", userMapper.selectById(id).getStatus());
    }

    @Test
    void userPageFiltersByKeywordDepartmentRoleAndStatus() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");
        Long rdId = user("10003").getDepartmentId();
        Long employeeRoleId = employeeRoleId();

        mockMvc.perform(get("/api/admin/users").param("keyword", "10003").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].employeeNo").value("10003"));

        mockMvc.perform(get("/api/admin/users").param("departmentId", String.valueOf(rdId)).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.records[*].employeeNo").value(hasItem("10003")));

        mockMvc.perform(get("/api/admin/users").param("roleId", String.valueOf(employeeRoleId)).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/api/admin/users").param("status", "ACTIVE").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(4)));
    }

    @Test
    void profileUpdateValidatesContact() throws Exception {
        MockHttpSession employee = login("10003", "Emp123456");

        mockMvc.perform(put("/api/my/profile").session(employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"123\",\"email\":\"new@bydpcic.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("手机号必须为11位数字"));

        mockMvc.perform(put("/api/my/profile").session(employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13900000000\",\"email\":\"bad@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("邮箱必须为@bydpcic.com"));

        mockMvc.perform(put("/api/my/profile").session(employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13900000000\",\"email\":\"new@bydpcic.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.phone").value("13900000000"))
                .andExpect(jsonPath("$.data.user.email").value("new@bydpcic.com"));
        assertEquals("13900000000", user("10003").getPhone());
    }
}
