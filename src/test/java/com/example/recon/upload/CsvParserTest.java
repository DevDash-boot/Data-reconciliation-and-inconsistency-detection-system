package com.example.recon.upload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvParserTest {

    @Test
    @DisplayName("쉼표로 컬럼을 나눈다")
    void splitsByComma() {
        assertThat(CsvParser.parseLine("a,b,c")).isEqualTo(List.of("a", "b", "c"));
    }

    @Test
    @DisplayName("따옴표 안의 쉼표는 구분자가 아니다")
    void commaInsideQuotes() {
        assertThat(CsvParser.parseLine("a,\"b,c\",d")).isEqualTo(List.of("a", "b,c", "d"));
    }

    @Test
    @DisplayName("\"\" 는 따옴표 한 글자로 해석한다")
    void escapedQuote() {
        assertThat(CsvParser.parseLine("\"he said \"\"hi\"\"\"")).isEqualTo(List.of("he said \"hi\""));
    }

    @Test
    @DisplayName("끝의 쉼표는 빈 컬럼이다")
    void trailingComma() {
        assertThat(CsvParser.parseLine("a,b,")).isEqualTo(List.of("a", "b", ""));
    }

    @Test
    @DisplayName("따옴표가 닫히지 않으면 예외")
    void unclosedQuote() {
        assertThatThrownBy(() -> CsvParser.parseLine("a,\"b"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
