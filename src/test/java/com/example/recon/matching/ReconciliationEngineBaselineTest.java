package com.example.recon.matching;

import com.example.recon.mismatch.MismatchType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 유형별 기본 케이스: 정상, 누락 A, 누락 B, 중복, 값 불일치(금액/상태) */
class ReconciliationEngineBaselineTest {

    private final ReconciliationEngine engine = new ReconciliationEngine();

    private static Row row(String key, long amount, String status) {
        return new Row(key, amount, status);
    }

    @Test
    @DisplayName("정상: 양쪽이 같으면 불일치가 없다")
    void noMismatch() {
        // given
        List<Row> a = List.of(row("O-1", 100, "PAID"));
        List<Row> b = List.of(row("O-1", 100, "PAID"));

        // when
        ReconciliationResult r = engine.reconcile(a, b);

        // then
        assertThat(r.items()).isEmpty();
    }

    @Test
    @DisplayName("누락 A: B 에만 있는 키는 MISSING_IN_A")
    void missingInA() {
        // given
        List<Row> a = List.of(row("O-1", 100, "PAID"));
        List<Row> b = List.of(row("O-1", 100, "PAID"), row("O-2", 100, "PAID"));

        // when
        ReconciliationResult r = engine.reconcile(a, b);

        // then
        assertThat(r.items()).hasSize(1);
        assertThat(r.items().get(0).key()).isEqualTo("O-2");
        assertThat(r.items().get(0).type()).isEqualTo(MismatchType.MISSING_IN_A);
    }

    @Test
    @DisplayName("누락 B: A 에만 있는 키는 MISSING_IN_B")
    void missingInB() {
        // given
        List<Row> a = List.of(row("O-1", 100, "PAID"), row("O-2", 100, "PAID"));
        List<Row> b = List.of(row("O-1", 100, "PAID"));

        // when
        ReconciliationResult r = engine.reconcile(a, b);

        // then
        assertThat(r.items()).hasSize(1);
        assertThat(r.items().get(0).key()).isEqualTo("O-2");
        assertThat(r.items().get(0).type()).isEqualTo(MismatchType.MISSING_IN_B);
    }

    @Test
    @DisplayName("중복: 한쪽에 같은 키가 2건이면 DUPLICATE")
    void duplicate() {
        // given
        List<Row> a = List.of(row("O-1", 100, "PAID"), row("O-1", 100, "PAID"));
        List<Row> b = List.of(row("O-1", 100, "PAID"));

        // when
        ReconciliationResult r = engine.reconcile(a, b);

        // then
        assertThat(r.items()).hasSize(1);
        assertThat(r.items().get(0).type()).isEqualTo(MismatchType.DUPLICATE);
    }

    @Test
    @DisplayName("값 불일치: 금액이 다르면 VALUE_DIFF (amount 필드)")
    void valueDiffAmount() {
        // given
        List<Row> a = List.of(row("O-1", 100, "PAID"));
        List<Row> b = List.of(row("O-1", 90, "PAID"));

        // when
        ReconciliationResult r = engine.reconcile(a, b);

        // then
        assertThat(r.items()).hasSize(1);
        assertThat(r.items().get(0).type()).isEqualTo(MismatchType.VALUE_DIFF);
        assertThat(r.items().get(0).detail()).contains("\"amount\"").doesNotContain("\"status\"");
    }

    @Test
    @DisplayName("값 불일치: 상태가 다르면 VALUE_DIFF (status 필드)")
    void valueDiffStatus() {
        // given
        List<Row> a = List.of(row("O-1", 100, "PAID"));
        List<Row> b = List.of(row("O-1", 100, "CANCELED"));

        // when
        ReconciliationResult r = engine.reconcile(a, b);

        // then
        assertThat(r.items()).hasSize(1);
        assertThat(r.items().get(0).type()).isEqualTo(MismatchType.VALUE_DIFF);
        assertThat(r.items().get(0).detail()).contains("\"status\"").doesNotContain("\"amount\"");
    }
}
