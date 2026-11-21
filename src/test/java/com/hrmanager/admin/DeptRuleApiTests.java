package com.hrmanager.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hrmanager.dept.entity.SysDepartment;
import com.hrmanager.dept.mapper.SysDepartmentMapper;
import com.hrmanager.support.AbstractApiTest;
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
class DeptRuleApiTests extends AbstractApiTest {

    @Autowired
    private SysDepartmentMapper departmentMapper;

    private SysDepartment department(String name) {
        return departmentMapper.selectOne(Wrappers.<SysDepartment>lambdaQuery()
                .eq(SysDepartment::getName, name)
                .last("limit 1"));
    }

    private MvcResult createDepartment(MockHttpSession admin, String name, Long parentId) throws Exception {
        String body = parentId == null
                ? "{\"name\":\"" + name + "\"}"
                : "{\"name\":\"" + name + "\",\"parentId\":" + parentId + "}";
        return mockMvc.perform(post("/api/admin/departments")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
    }

    private Long idOf(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.data.id");
        return id.longValue();
    }

    private String codeOf(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.data.code");
    }

    @Test
    void createGeneratesTenCharacterCodesForRootsAndChildren() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");

        MvcResult root = createDepartment(admin, "测试一部", null);
        assertEquals("0300000000", codeOf(root));
        assertEquals(1, JsonPath.<Integer>read(root.getResponse().getContentAsString(StandardCharsets.UTF_8),
                "$.data.level").intValue());

        Long itId = department("信息科技部").getId();
        MvcResult child = createDepartment(admin, "测试子部", itId);
        assertEquals("0002000000", codeOf(child));
        assertEquals(2, JsonPath.<Integer>read(child.getResponse().getContentAsString(StandardCharsets.UTF_8),
                "$.data.level").intValue());
        assertEquals(10, codeOf(child).length());
    }

    @Test
    void createRequiresNameAndExistingParent() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");

        mockMvc.perform(post("/api/admin/departments").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("部门名称不能为空"));

        mockMvc.perform(post("/api/admin/departments").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试部\",\"parentId\":999999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("部门不存在"));
    }

    @Test
    void departmentHierarchyIsLimitedToFiveLevels() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");
        Long parentId = department("人事服务组").getId();

        for (int level = 3; level <= 5; level++) {
            MvcResult created = createDepartment(admin, "层级" + level, parentId);
            assertEquals(level, JsonPath.<Integer>read(created.getResponse().getContentAsString(
                    StandardCharsets.UTF_8), "$.data.level").intValue());
            parentId = idOf(created);
        }

        mockMvc.perform(post("/api/admin/departments").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"层级6\",\"parentId\":" + parentId + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("最多支持5级部门"));
    }

    @Test
    void updateValidatesNameAndExistence() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");
        createDepartment(admin, "测试A", null);
        Long idB = idOf(createDepartment(admin, "测试B", null));

        mockMvc.perform(put("/api/admin/departments/" + idB).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("同级部门名称已存在"));

        mockMvc.perform(put("/api/admin/departments/" + idB).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试B2\",\"sort\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("测试B2"))
                .andExpect(jsonPath("$.data.sort").value(5));

        mockMvc.perform(put("/api/admin/departments/" + idB).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试B2\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/admin/departments/999999").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"不存在\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("部门不存在"));
    }

    @Test
    void deleteRequiresNoChildrenAndMarksDepartmentDeleted() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");
        Long parentId = idOf(createDepartment(admin, "待删部", null));
        Long childId = idOf(createDepartment(admin, "待删子部", parentId));

        mockMvc.perform(delete("/api/admin/departments/" + parentId).session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("部门下存在子部门，请先删除子部门"));

        mockMvc.perform(delete("/api/admin/departments/" + childId).session(admin))
                .andExpect(status().isOk());
        assertEquals("DELETED", departmentMapper.selectById(childId).getStatus());

        mockMvc.perform(delete("/api/admin/departments/" + parentId).session(admin))
                .andExpect(status().isOk());
        assertEquals("DELETED", departmentMapper.selectById(parentId).getStatus());

        mockMvc.perform(delete("/api/admin/departments/" + parentId).session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("部门不存在"));

        mockMvc.perform(delete("/api/admin/departments/999999").session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("部门不存在"));
    }
}
