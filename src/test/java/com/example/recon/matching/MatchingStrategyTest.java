package com.example.recon.matching;

import com.example.recon.mismatch.MismatchType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchingStrategyTest {

    private static final Row A = new Row("O-1", 100, "PAID");

    @Test
    @DisplayName("EXACT: 금액과 상태가 모두 같으면 차이가 없다")
    void exactSame() {
        assertThat(new ExactMatchStrategy().compare(A, new Row("O-1", 100, "PAID"))).isEmpty();
    }

    @Test
    @DisplayName("EXACT: 금액과 상태가 모두 다르면 두 필드가 나온다")
    void exactBothDiff() {
        List<FieldDiff> diffs = new ExactMatchStrategy().compare(A, new Row("O-1", 90, "CANCELED"));

        assertThat(diffs).hasSize(2);
        assertThat(diffs.get(0).field()).isEqualTo("amount");
        assertThat(diffs.get(1).field()).isEqualTo("status");
    }

    @Test
    @DisplayName("AMOUNT_ONLY: 상태가 달라도 금액이 같으면 일치")
    void amountOnlyIgnoresStatus() {
        assertThat(new AmountOnlyStrategy().compare(A, new Row("O-1", 100, "CANCELED"))).isEmpty();
    }

    @Test
    @DisplayName("전략을 바꾸면 엔진 코드를 수정하지 않아도 결과가 달라진다")
    void engineUsesInjectedStrategy() {
        List<Row> a = List.of(A);
        List<Row> b = List.of(new Row("O-1", 100, "CANCELED"));

        ReconciliationResult exact = new ReconciliationEngine(new ExactMatchStrategy()).reconcile(a, b);
        ReconciliationResult amountOnly = new ReconciliationEngine(new AmountOnlyStrategy()).reconcile(a, b);

        assertThat(exact.count(MismatchType.VALUE_DIFF)).isEqualTo(1);
        assertThat(amountOnly.items()).isEmpty();
    }

    @Test
    @DisplayName("이름으로 전략을 찾고 모르는 이름은 거부한다")
    void lookupByName() {
        assertThat(MatchingStrategies.of("exact").name()).isEqualTo("EXACT");
        assertThat(MatchingStrategies.of("AMOUNT_ONLY").name()).isEqualTo("AMOUNT_ONLY");
        assertThatThrownBy(() -> MatchingStrategies.of("FUZZY")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MatchingStrategies.of(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("EXACT: 금액만 다르면 amount 차이 1건만 나온다")
    void exactAmountOnlyDiff() {
        List<FieldDiff> diffs = new ExactMatchStrategy().compare(A, new Row("O-1", 90, "PAID"));

        assertThat(diffs).hasSize(1);
        assertThat(diffs.get(0).field()).isEqualTo("amount");
        assertThat(diffs.get(0).a()).isEqualTo("100");
        assertThat(diffs.get(0).b()).isEqualTo("90");
    }

    @Test
    @DisplayName("EXACT: 상태만 다르면 status 차이 1건만 나온다")
    void exactStatusOnlyDiff() {
        List<FieldDiff> diffs = new ExactMatchStrategy().compare(A, new Row("O-1", 100, "CANCELED"));

        assertThat(diffs).hasSize(1);
        assertThat(diffs.get(0).field()).isEqualTo("status");
        assertThat(diffs.get(0).a()).isEqualTo("PAID");
        assertThat(diffs.get(0).b()).isEqualTo("CANCELED");
    }

    @Test
    @DisplayName("AMOUNT_ONLY: 금액이 다르면 amount 차이만 나온다 (상태가 달라도 status 는 안 나온다)")
    void amountOnlyAmountDiff() {
        List<FieldDiff> diffs = new AmountOnlyStrategy().compare(A, new Row("O-1", 90, "CANCELED"));

        assertThat(diffs).hasSize(1);
        assertThat(diffs.get(0).field()).isEqualTo("amount");
    }

    @Test
    @DisplayName("전략 이름은 앞뒤 공백과 대소문자를 무시하고, 사용 가능한 이름 목록을 제공한다")
    void lookupIgnoresCaseAndSpaces() {
        assertThat(MatchingStrategies.of("  amount_only ").name()).isEqualTo("AMOUNT_ONLY");
        assertThat(MatchingStrategies.names()).containsExactly("EXACT", "AMOUNT_ONLY");
    }
}
