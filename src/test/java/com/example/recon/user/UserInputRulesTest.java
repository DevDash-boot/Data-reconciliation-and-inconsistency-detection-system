package com.example.recon.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserInputRulesTest {

    @Test
    @DisplayName("아이디: 영문/숫자/_ 3~30자만 허용")
    void username() {
        UserInputRules.validateUsername("admin_01");
        UserInputRules.validateUsername("  abc  ");   // 앞뒤 공백은 무시
        assertThatThrownBy(() -> UserInputRules.validateUsername("ab")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserInputRules.validateUsername("한글아이디")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserInputRules.validateUsername("a b c")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserInputRules.validateUsername("a".repeat(31))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserInputRules.validateUsername(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("비밀번호: 8자 이상, 72바이트 이하")
    void password() {
        UserInputRules.validatePassword("12345678");
        assertThatThrownBy(() -> UserInputRules.validatePassword("1234567")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserInputRules.validatePassword("a".repeat(73))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserInputRules.validatePassword("가".repeat(25))).isInstanceOf(IllegalArgumentException.class);
        UserInputRules.validatePassword("가".repeat(24));   // 72바이트
        assertThatThrownBy(() -> UserInputRules.validatePassword(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("아이디는 소문자로 통일한다")
    void normalize() {
        assertThat(UserInputRules.normalizeUsername("  Admin ")).isEqualTo("admin");
    }
}
