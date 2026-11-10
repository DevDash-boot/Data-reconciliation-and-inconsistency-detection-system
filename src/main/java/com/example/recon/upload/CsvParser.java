package com.example.recon.upload;

import java.util.ArrayList;
import java.util.List;

/**
 * 한 줄짜리 CSV 파서. 쌍따옴표 감싸기와 "" 이스케이프를 지원한다.
 * (따옴표 안의 줄바꿈은 지원하지 않는다. 이 프로젝트의 데이터에는 필요 없다.)
 */
public final class CsvParser {

    private CsvParser() {
    }

    public static List<String> parseLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                out.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        if (inQuotes) {
            throw new IllegalArgumentException("따옴표가 닫히지 않았습니다");
        }
        out.add(sb.toString());
        return out;
    }
}
