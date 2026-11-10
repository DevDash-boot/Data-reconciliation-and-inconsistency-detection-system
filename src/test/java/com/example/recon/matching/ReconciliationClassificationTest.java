package com.example.recon.matching;

import com.example.recon.mismatch.MismatchType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationClassificationTest {

    private final ReconciliationEngine engine = new ReconciliationEngine();

    private static Row row(String key, long amount, String status) {
        return new Row(key, amount, status);
    }

    @Test
    @DisplayName("A 에 같은 키가 2건이면 DUPLICATE (값 비교는 하지 않는다)")
    void duplicateInA() {
        ReconciliationResult r = engine.reconcile(
                List.of(row("O-1", 100, "PAID"), row("O-1", 100, "PAID")),
                List.of(row("O-1", 100, "PAID")));

        assertThat(r.items()).hasSize(1);
        assertThat(r.items().get(0).type()).isEqualTo(MismatchType.DUPLICATE);
        assertThat(r.items().get(0).detail()).isEqualTo("{\"countA\":2,\"countB\":1}");
    }

    @Test
    @DisplayName("B 에 같은 키가 2건이어도 DUPLICATE")
    void duplicateInB() {
        ReconciliationResult r = engine.reconcile(
                List.of(row("O-1", 100, "PAID")),
                List.of(row("O-1", 100, "PAID"), row("O-1", 100, "PAID")));

        assertThat(r.count(MismatchType.DUPLICATE)).isEqualTo(1);
    }

    @Test
    @DisplayName("우선순위: 중복이면서 값도 다르면 DUPLICATE 1건만 나온다")
    void duplicateBeatsValueDiff() {
        ReconciliationResult r = engine.reconcile(
                List.of(row("O-1", 100, "PAID"), row("O-1", 100, "PAID")),
                List.of(row("O-1", 999, "CANCELED")));

        assertThat(r.items()).hasSize(1);
        assertThat(r.items().get(0).type()).isEqualTo(MismatchType.DUPLICATE);
    }

    @Test
    @DisplayName("우선순위: A 에 2건이고 B 에 없으면 DUPLICATE 가 MISSING_IN_B 보다 먼저")
    void duplicateBeatsMissing() {
        ReconciliationResult r = engine.reconcile(
                List.of(row("O-1", 100, "PAID"), row("O-1", 100, "PAID")),
                List.of());

        assertThat(r.items()).hasSize(1);
        assertThat(r.items().get(0).type()).isEqualTo(MismatchType.DUPLICATE);
    }

    @Test
    @DisplayName("VALUE_DIFF: 금액과 상태가 모두 다르면 한 건에 필드 두 개를 담는다")
    void valueDiffDetail() {
        ReconciliationResult r = engine.reconcile(
                List.of(row("O-1", 100, "PAID")),
                List.of(row("O-1", 90, "CANCELED")));

        assertThat(r.items()).hasSize(1);
        assertThat(r.items().get(0).detail()).isEqualTo(
                "{\"amount\":{\"a\":\"100\",\"b\":\"90\"},\"status\":{\"a\":\"PAID\",\"b\":\"CANCELED\"}}");
    }

    @Test
    @DisplayName("MISSING 상세는 키가 존재하는 쪽을 알려준다")
    void missingDetail() {
        ReconciliationResult r = engine.reconcile(
                List.of(row("O-1", 100, "PAID")),
                List.of(row("O-2", 100, "PAID")));

        assertThat(r.items().get(0).detail()).isEqualTo("{\"presentIn\":\"A\"}");
        assertThat(r.items().get(1).detail()).isEqualTo("{\"presentIn\":\"B\"}");
    }

    @Test
    @DisplayName("결과는 키 순서로 정렬되고 키 하나당 1건이다")
    void sortedAndOnePerKey() {
        ReconciliationResult r = engine.reconcile(
                List.of(row("O-3", 1, "PAID"), row("O-1", 1, "PAID")),
                List.of(row("O-2", 1, "PAID"), row("O-1", 2, "PAID")));

        assertThat(r.items()).hasSize(3);
        assertThat(r.items().get(0).key()).isEqualTo("O-1");
        assertThat(r.items().get(1).key()).isEqualTo("O-2");
        assertThat(r.items().get(2).key()).isEqualTo("O-3");
    }
}
