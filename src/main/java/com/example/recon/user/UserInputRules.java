package com.example.recon.user;

import java.nio.charset.StandardCharsets;

/** 회원가입 입력 규칙. 위반하면 IllegalArgumentException. */
public final class UserInputRules {

    private UserInputRules() {
    }

    public static void validateUsername(String username) {
        if (username == null || !username.trim().matches("[A-Za-z0-9_]{3,30}")) {
            throw new IllegalArgumentException("아이디는 영문, 숫자, _ 로 3~30자여야 합니다");
        }
    }

    public static void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("비밀번호는 8자 이상이어야 합니다");
        }
        // BCrypt 는 앞의 72바이트만 사용한다. 한글은 한 글자가 3바이트
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("비밀번호가 너무 깁니다 (72바이트 이하, 한글은 약 24자)");
        }
    }

    /** 아이디는 소문자로 통일해 저장/조회한다. (DB 마다 대소문자 구분이 달라서) */
    public static String normalizeUsername(String username) {
        return username.trim().toLowerCase();
    }
}
