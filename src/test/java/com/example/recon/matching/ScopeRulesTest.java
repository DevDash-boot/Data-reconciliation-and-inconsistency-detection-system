package com.example.recon.matching;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeRulesTest {

    @Test
    @DisplayName("A: paid, cancelled 만 범위 안이고 ready, failed 는 제외")
    void scopeA() {
        ScopeRules.Scoped s = ScopeRules.scopeA(List.of(
                new Row("O-1", 1, "paid"), new Row("O-2", 1, "cancelled"),
                new Row("O-3", 1, "ready"), new Row("O-4", 1, "failed")));

        assertThat(s.rows()).hasSize(2);
        assertThat(s.excluded()).isEqualTo(2);
    }

    @Test
    @DisplayName("A 의 상태는 B 와 비교할 수 있게 PAID / CANCELED 로 맞춘다")
    void normalizesAStatus() {
        ScopeRules.Scoped s = ScopeRules.scopeA(List.of(new Row("O-1", 1, "paid"), new Row("O-2", 1, "cancelled")));

        assertThat(s.rows().get(0).status()).isEqualTo("PAID");
        assertThat(s.rows().get(1).status()).isEqualTo("CANCELED");
    }

    @Test
    @DisplayName("B: PAID, CANCELED 만 범위 안이고 PENDING 은 제외")
    void scopeB() {
        ScopeRules.Scoped s = ScopeRules.scopeB(List.of(
                new Row("O-1", 1, "PAID"), new Row("O-2", 1, "CANCELED"), new Row("O-3", 1, "PENDING")));

        assertThat(s.rows()).hasSize(2);
        assertThat(s.excluded()).isEqualTo(1);
    }

    @Test
    @DisplayName("키 앞뒤 공백은 제거한다")
    void trimsKeys() {
        ScopeRules.Scoped s = ScopeRules.scopeB(List.of(new Row("  O-1 ", 1, "PAID")));

        assertThat(s.rows().get(0).key()).isEqualTo("O-1");
    }

    @Test
    @DisplayName("대소문자가 다르면 범위 밖이다 (정확 일치 규칙)")
    void caseSensitive() {
        assertThat(ScopeRules.inScopeA("PAID")).isFalse();
        assertThat(ScopeRules.inScopeB("paid")).isFalse();
    }
}
