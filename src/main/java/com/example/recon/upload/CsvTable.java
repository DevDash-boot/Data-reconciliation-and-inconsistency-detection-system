package com.example.recon.upload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 헤더가 있는 CSV 를 읽어 행 단위로 변환한다.
 * - 헤더가 다르면 파일 전체를 거부한다 (InvalidCsvException)
 * - 개별 행의 오류는 건너뛰고 RowError 로 모은다
 * - UTF-8 BOM(엑셀 저장 파일) 을 처리한다
 */
public final class CsvTable {

    @FunctionalInterface
    public interface RowMapper<T> {
        /** cols 는 trim 된 값. 잘못된 값이면 IllegalArgumentException/DateTimeException 을 던진다. */
        T map(int rowNo, List<String> cols);
    }

    private CsvTable() {
    }

    public static <T> ParseResult<T> parse(Reader reader, List<String> expectedHeader, RowMapper<T> mapper) {
        BufferedReader br = (reader instanceof BufferedReader b) ? b : new BufferedReader(reader, 1 << 16);
        try {
            String line = br.readLine();
            if (line == null) {
                throw new InvalidCsvException("빈 파일입니다. 헤더 행이 필요합니다.");
            }
            if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
                line = line.substring(1);
            }
            List<String> header;
            try {
                header = CsvParser.parseLine(line).stream().map(String::trim).toList();
            } catch (IllegalArgumentException e) {
                throw new InvalidCsvException("헤더를 읽을 수 없습니다: " + e.getMessage());
            }
            if (!header.equals(expectedHeader)) {
                throw new InvalidCsvException("헤더가 올바르지 않습니다. 기대: " + expectedHeader + " / 실제: " + header);
            }

            List<T> valid = new ArrayList<>();
            List<RowError> errors = new ArrayList<>();
            int total = 0;
            int lineNo = 1;
            while ((line = br.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }
                total++;
                try {
                    List<String> cols = CsvParser.parseLine(line);
                    if (cols.size() != expectedHeader.size()) {
                        throw new IllegalArgumentException(
                                "컬럼 수가 다릅니다. 기대 " + expectedHeader.size() + "개, 실제 " + cols.size() + "개");
                    }
                    valid.add(mapper.map(lineNo, cols.stream().map(String::trim).toList()));
                } catch (IllegalArgumentException | DateTimeException e) {
                    errors.add(new RowError(lineNo, e.getMessage()));
                }
            }
            return new ParseResult<>(total, valid, errors);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- 값 변환 도우미 ----

    public static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 값이 비어 있습니다");
        }
        return value;
    }

    public static long parseAmount(String value, String field) {
        required(value, field);
        long n;
        try {
            n = Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " 형식이 올바르지 않습니다: " + value);
        }
        if (n < 0) {
            throw new IllegalArgumentException(field + " 는 0 이상이어야 합니다: " + value);
        }
        return n;
    }

    /** "2026-11-01T10:00:00" 또는 "2026-11-01 10:00:00" */
    public static LocalDateTime parseDateTime(String value, String field) {
        required(value, field);
        try {
            return LocalDateTime.parse(value.replace(' ', 'T'), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException(field + " 형식이 올바르지 않습니다: " + value);
        }
    }
}
