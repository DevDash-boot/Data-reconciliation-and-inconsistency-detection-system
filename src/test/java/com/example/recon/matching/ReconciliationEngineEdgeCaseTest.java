package com.example.recon.matching;

import com.example.recon.mismatch.MismatchType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationEngineEdgeCaseTest {

    private final ReconciliationEngine engine = new ReconciliationEngine();

    private static Row row(String key, long amount, String status) {
        return new Row(key, amount, status);
    }

    @Test
    @DisplayName("양쪽 모두 비어 있으면 결과도 비어 있다")
    void bothEmpty() {
        assertThat(engine.reconcile(List.of(), List.of()).items()).isEmpty();
    }

    @Test
    @DisplayName("A 가 비어 있으면 B 의 모든 행이 MISSING_IN_A")
    void aEmpty() {
        ReconciliationResult r = engine.reconcile(List.of(),
                List.of(row("O-1", 1, "PAID"), row("O-2", 1, "PAID")));

        assertThat(r.count(MismatchType.MISSING_IN_A)).isEqualTo(2);
        assertThat(r.items()).hasSize(2);
    }

    @Test
    @DisplayName("B 가 비어 있으면 A 의 모든 행이 MISSING_IN_B")
    void bEmpty() {
        ReconciliationResult r = engine.reconcile(
                List.of(row("O-1", 1, "PAID"), row("O-2", 1, "PAID")), List.of());

        assertThat(r.count(MismatchType.MISSING_IN_B)).isEqualTo(2);
    }

    @Test
    @DisplayName("전부 불일치여도 키마다 정확히 1건씩 나온다")
    void everythingMismatched() {
        List<Row> a = new ArrayList<>();
        List<Row> b = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            a.add(row("A-" + i, 1, "PAID"));
            b.add(row("B-" + i, 1, "PAID"));
        }

        ReconciliationResult r = engine.reconcile(a, b);

        assertThat(r.items()).hasSize(200);
        assertThat(r.count(MismatchType.MISSING_IN_A)).isEqualTo(100);
        assertThat(r.count(MismatchType.MISSING_IN_B)).isEqualTo(100);
    }

    @Test
    @DisplayName("금액이 0 원이어도 정상 비교된다")
    void zeroAmount() {
        assertThat(engine.reconcile(List.of(row("O-1", 0, "PAID")), List.of(row("O-1", 0, "PAID"))).items()).isEmpty();
        assertThat(engine.reconcile(List.of(row("O-1", 0, "PAID")), List.of(row("O-1", 1, "PAID"))).items()).hasSize(1);
    }

    @Test
    @DisplayName("같은 엔진을 여러 번 써도 결과가 같다 (상태를 갖지 않는다)")
    void engineIsStateless() {
        List<Row> a = List.of(row("O-1", 1, "PAID"), row("O-2", 1, "PAID"));
        List<Row> b = List.of(row("O-1", 2, "PAID"));

        ReconciliationResult first = engine.reconcile(a, b);
        ReconciliationResult second = engine.reconcile(a, b);

        assertThat(first.items()).isEqualTo(second.items());
    }

    @Test
    @DisplayName("입력 순서가 달라도 결과는 같다")
    void inputOrderDoesNotMatter() {
        List<Row> a = List.of(row("O-1", 1, "PAID"), row("O-2", 1, "PAID"), row("O-3", 1, "PAID"));
        List<Row> reversed = new ArrayList<>(a);
        java.util.Collections.reverse(reversed);
        List<Row> b = List.of(row("O-2", 5, "PAID"));

        assertThat(engine.reconcile(a, b).items()).isEqualTo(engine.reconcile(reversed, b).items());
    }

    @Test
    @DisplayName("AMOUNT_ONLY 전략에서도 중복은 DUPLICATE 로 분류된다")
    void duplicateWithAmountOnly() {
        ReconciliationResult r = new ReconciliationEngine(new AmountOnlyStrategy()).reconcile(
                List.of(row("O-1", 1, "PAID"), row("O-1", 1, "PAID")),
                List.of(row("O-1", 1, "PAID")));

        assertThat(r.count(MismatchType.DUPLICATE)).isEqualTo(1);
    }

    @Test
    @DisplayName("양쪽 모두 중복이면 countA, countB 가 둘 다 기록된다")
    void duplicateOnBothSides() {
        ReconciliationResult r = engine.reconcile(
                List.of(row("O-1", 1, "PAID"), row("O-1", 1, "PAID"), row("O-1", 1, "PAID")),
                List.of(row("O-1", 1, "PAID"), row("O-1", 1, "PAID")));

        assertThat(r.items().get(0).detail()).isEqualTo("{\"countA\":3,\"countB\":2}");
    }

    @Test
    @DisplayName("10만 건에서도 정상 동작한다 (정확성 sanity)")
    void largeInputSanity() {
        List<Row> a = new ArrayList<>();
        List<Row> b = new ArrayList<>();
        for (int i = 0; i < 100_000; i++) {
            a.add(row("K-" + i, i, "PAID"));
            b.add(row("K-" + i, (i % 1000 == 0) ? i + 1 : i, "PAID"));
        }

        ReconciliationResult r = engine.reconcile(a, b);

        assertThat(r.count(MismatchType.VALUE_DIFF)).isEqualTo(100);
        assertThat(r.items()).hasSize(100);
    }
}
