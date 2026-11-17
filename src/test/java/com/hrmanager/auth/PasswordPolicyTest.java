package com.hrmanager.auth;

import com.hrmanager.auth.service.AuthService;
import com.hrmanager.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

    private final AuthService authService = new AuthService(null, null, null, null, null, null);

    @Test
    void acceptsStrongPassword() {
        assertDoesNotThrow(() -> authService.validatePassword("GoodPass1"));
    }

    @Test
    void rejectsShortPassword() {
        assertMessage("short", "新密码不能小于8位，请重新输入");
    }

    @Test
    void rejectsPasswordWithoutUppercase() {
        assertMessage("lowercase1", "新密码缺少大写字母，请重新输入");
    }

    @Test
    void rejectsPasswordWithoutLowercase() {
        assertMessage("UPPERCASE1", "新密码缺少小写字母，请重新输入");
    }

    @Test
    void rejectsPasswordWithoutDigit() {
        assertMessage("NoDigitsHere", "新密码缺少数字，请重新输入");
    }

    @Test
    void rejectsPasswordWithSpace() {
        assertMessage("Good Pass1", "密码不能包含空格");
    }

    @Test
    void rejectsPasswordWithChinese() {
        assertMessage("GoodPass1中", "密码不能包含汉字");
    }

    private void assertMessage(String password, String expected) {
        BusinessException error = assertThrows(BusinessException.class,
                () -> authService.validatePassword(password));
        assertEquals(expected, error.getMessage());
    }
}
