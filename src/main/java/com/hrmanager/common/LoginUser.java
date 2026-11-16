package com.hrmanager.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String employeeNo;
    private String name;
    private Long departmentId;
    private String roleCode;
    private Set<String> permissions;
    private boolean mustChangePassword;
}
