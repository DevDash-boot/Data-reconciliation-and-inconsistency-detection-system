package com.example.recon.matching;

import com.example.recon.mismatch.MismatchType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationEngineBaselineTest {

    private final ReconciliationEngine engine = new ReconciliationEngine();

    private static Row row(String key, long amount, String status) {
        return new Row(key, amount, status);
    }

    @Test
    @DisplayName("양쪽이 같으면 불일치가 없다")
    void noMismatch() {
        ReconciliationResult r = engine.reconcile(
                List.of(row("O-1", 100, "PAID")),
                List.of(row("O-1", 100, "PAID")));

        assertThat(r.items()).isEmpty();
    }

    @Test
    @DisplayName("A 에만 있으면 MISSING_IN_B, B 에만 있으면 MISSING_IN_A")
    void missing() {
        ReconciliationResult r = engine.reconcile(
                List.of(row("O-1", 100, "PAID")),
                List.of(row("O-2", 100, "PAID")));

        assertThat(r.count(MismatchType.MISSING_IN_B)).isEqualTo(1);
        assertThat(r.count(MismatchType.MISSING_IN_A)).isEqualTo(1);
        assertThat(r.items().get(0).key()).isEqualTo("O-1");
    }

    @Test
    @DisplayName("키는 같고 금액이 다르면 VALUE_DIFF")
    void valueDiff() {
        ReconciliationResult r = engine.reconcile(
                List.of(row("O-1", 100, "PAID")),
                List.of(row("O-1", 90, "PAID")));

        assertThat(r.count(MismatchType.VALUE_DIFF)).isEqualTo(1);
    }
}
