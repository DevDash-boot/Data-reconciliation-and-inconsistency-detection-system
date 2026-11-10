package com.example.recon.matching;

import java.util.List;

/** 불일치 상세를 JSON 문자열로 만든다. (외부 라이브러리 없이) */
public final class DiffDetailJson {

    private DiffDetailJson() {
    }

    /** {"amount":{"a":"50000","b":"45000"},"status":{...}} */
    public static String valueDiff(List<FieldDiff> diffs) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < diffs.size(); i++) {
            FieldDiff d = diffs.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(escape(d.field())).append("\":{\"a\":\"")
                    .append(escape(d.a())).append("\",\"b\":\"").append(escape(d.b())).append("\"}");
        }
        return sb.append('}').toString();
    }

    /** {"countA":2,"countB":1} */
    public static String duplicate(int countA, int countB) {
        return "{\"countA\":" + countA + ",\"countB\":" + countB + "}";
    }

    /** {"presentIn":"B"} : 키가 존재하는 쪽 */
    public static String missing(String presentIn) {
        return "{\"presentIn\":\"" + escape(presentIn) + "\"}";
    }

    static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
