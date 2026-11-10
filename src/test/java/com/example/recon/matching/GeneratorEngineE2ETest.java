package com.example.recon.matching;

import com.example.recon.generator.SampleDataGenerator;
import com.example.recon.mismatch.MismatchType;
import com.example.recon.upload.OrderCsvMapper;
import com.example.recon.upload.OrderRecord;
import com.example.recon.upload.ParseResult;
import com.example.recon.upload.PaymentCsvMapper;
import com.example.recon.upload.PaymentRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 샘플 데이터 생성기(#4) -> CSV 파서(#5) -> 범위 규칙 -> 엔진(#6~#8) 까지
 * 정답(expected)과 정확히 일치하는지 검증한다.
 */
class GeneratorEngineE2ETest {

    private ReconciliationResult run(Path dir, MatchingStrategy strategy) throws IOException {
        ParseResult<PaymentRecord> pay;
        try (Reader r = Files.newBufferedReader(dir.resolve(SampleDataGenerator.PAYMENTS_FILE), StandardCharsets.UTF_8)) {
            pay = PaymentCsvMapper.parse(r);
        }
        ParseResult<OrderRecord> ord;
        try (Reader r = Files.newBufferedReader(dir.resolve(SampleDataGenerator.ORDERS_FILE), StandardCharsets.UTF_8)) {
            ord = OrderCsvMapper.parse(r);
        }
        assertThat(pay.errorRows()).isZero();
        assertThat(ord.errorRows()).isZero();

        List<Row> rawA = new ArrayList<>();
        for (PaymentRecord p : pay.valid()) {
            rawA.add(new Row(p.merchantUid(), p.amount(), p.status()));
        }
        List<Row> rawB = new ArrayList<>();
        for (OrderRecord o : ord.valid()) {
            rawB.add(new Row(o.orderNo(), o.totalAmount(), o.status()));
        }
        ScopeRules.Scoped a = ScopeRules.scopeA(rawA);
        ScopeRules.Scoped b = ScopeRules.scopeB(rawB);

        SampleDataGenerator.Expected expected = SampleDataGenerator.readExpected(dir);
        assertThat(a.excluded()).isEqualTo(expected.excludedA());
        assertThat(b.excluded()).isEqualTo(expected.excludedB());
        assertThat(a.rows().size()).isEqualTo(expected.inScopeA());
        assertThat(b.rows().size()).isEqualTo(expected.inScopeB());

        return new ReconciliationEngine(strategy).reconcile(a.rows(), b.rows());
    }

    @Test
    @DisplayName("5천 건: 유형별 건수가 expected 와 100% 일치한다")
    void countsMatchExpected() throws IOException {
        Path dir = Files.createTempDirectory("recon-e2e-");
        SampleDataGenerator.Expected expected = SampleDataGenerator.generate(
                new SampleDataGenerator.Config(5000, 0.02, 0.02, 0.01, 0.03, 0.02, 42L, dir));

        ReconciliationResult result = run(dir, new ExactMatchStrategy());

        assertThat(result.count(MismatchType.MISSING_IN_A)).isEqualTo(expected.missingInA());
        assertThat(result.count(MismatchType.MISSING_IN_B)).isEqualTo(expected.missingInB());
        assertThat(result.count(MismatchType.DUPLICATE)).isEqualTo(expected.duplicate());
        assertThat(result.count(MismatchType.VALUE_DIFF)).isEqualTo(expected.valueDiff());
        assertThat(result.items().size()).isEqualTo(expected.totalMismatches());
    }

    @Test
    @DisplayName("5천 건: 불일치 키와 유형이 expected_mismatches.csv 와 한 건도 다르지 않다")
    void keysAndTypesMatchExpected() throws IOException {
        Path dir = Files.createTempDirectory("recon-e2e-");
        SampleDataGenerator.generate(new SampleDataGenerator.Config(5000, 0.02, 0.02, 0.01, 0.03, 0.02, 99L, dir));

        ReconciliationResult result = run(dir, new ExactMatchStrategy());

        List<String> expectedLines = Files.readAllLines(dir.resolve(SampleDataGenerator.EXPECTED_MISMATCHES_FILE));
        List<String> actualLines = new ArrayList<>();
        actualLines.add("match_key,type");
        for (MismatchItem it : result.items()) {
            actualLines.add(it.key() + "," + it.type().name());
        }
        assertThat(actualLines).isEqualTo(expectedLines);
    }

    @Test
    @DisplayName("범위 필터가 없으면 제외 대상 행이 누락으로 잡힌다 (범위 규칙이 필요한 이유)")
    void withoutScopeRulesNoiseBecomesMismatch() throws IOException {
        Path dir = Files.createTempDirectory("recon-e2e-");
        SampleDataGenerator.Expected expected = SampleDataGenerator.generate(
                new SampleDataGenerator.Config(1000, 0.02, 0.02, 0.01, 0.03, 0.05, 5L, dir));

        // 상태 이름(paid -> PAID)만 맞추고, 범위 필터(ready/failed/PENDING 제외)는 거치지 않고 대조
        List<Row> a = new ArrayList<>();
        List<Row> b = new ArrayList<>();
        try (Reader r = Files.newBufferedReader(dir.resolve(SampleDataGenerator.PAYMENTS_FILE))) {
            PaymentCsvMapper.parse(r).valid()
                    .forEach(p -> a.add(new Row(p.merchantUid(), p.amount(), ScopeRules.normalizeA(p.status()))));
        }
        try (Reader r = Files.newBufferedReader(dir.resolve(SampleDataGenerator.ORDERS_FILE))) {
            OrderCsvMapper.parse(r).valid().forEach(o -> b.add(new Row(o.orderNo(), o.totalAmount(), o.status())));
        }

        ReconciliationResult raw = new ReconciliationEngine().reconcile(a, b);

        assertThat(raw.items().size()).isEqualTo(expected.totalMismatches() + expected.excludedA() + expected.excludedB());
    }
}
