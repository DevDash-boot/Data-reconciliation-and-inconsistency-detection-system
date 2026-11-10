package com.example.recon.matching;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiffDetailJsonTest {

    @Test
    @DisplayName("따옴표와 역슬래시가 이스케이프된다")
    void escapesSpecialCharacters() {
        String json = DiffDetailJson.valueDiff(List.of(new FieldDiff("status", "a\"b", "c\\d")));

        assertThat(json).isEqualTo("{\"status\":{\"a\":\"a\\\"b\",\"b\":\"c\\\\d\"}}");
    }

    @Test
    @DisplayName("차이가 없으면 빈 객체")
    void emptyDiffs() {
        assertThat(DiffDetailJson.valueDiff(List.of())).isEqualTo("{}");
    }
}
