package com.example.recon.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 테스트에서 JSON 응답의 값 하나를 꺼내는 최소 도구. (JSON 라이브러리 버전에 의존하지 않으려고) */
public final class JsonLite {

    private JsonLite() {
    }

    /** 처음 나오는 "field": 123 의 숫자 */
    public static long getLong(String json, String field) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        if (!m.find()) {
            throw new AssertionError("JSON 에 숫자 필드 '" + field + "' 가 없습니다: " + json);
        }
        return Long.parseLong(m.group(1));
    }
}
