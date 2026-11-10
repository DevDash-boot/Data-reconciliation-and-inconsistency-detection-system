package com.example.recon.matching;

import com.example.recon.generator.SampleDataGenerator;
import com.example.recon.upload.OrderCsvMapper;
import com.example.recon.upload.PaymentCsvMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 키 해시로 나눠서 따로 대조한 결과가, 한 번에 대조한 결과와 정확히 같음을 검증한다. */
class ChunkEquivalenceTest {

    @Test
    @DisplayName("CRC32 파티션이 MySQL 문서의 CRC32 값과 맞다 (CRC32('MySQL') = 3259397556)")
    void crcMatchesMySqlDocumentation() {
        // 3259397556 % 10 = 6, 3259397556 % 7 = 0 ... 값 자체를 파티션 수로 나눈 나머지로 확인
        assertThat(HashPartitioner.partition("MySQL", 10)).isEqualTo((int) (3259397556L % 10));
        assertThat(HashPartitioner.partition("123456789", 10)).isEqualTo((int) (3421780262L % 10));
    }

    @Test
    @DisplayName("같은 키는 항상 같은 파티션이고, 파티션 번호는 범위 안이다")
    void deterministicAndInRange() {
        for (int i = 0; i < 1000; i++) {
            int p = HashPartitioner.partition("O-" + i, 16);
            assertThat(p).isEqualTo(HashPartitioner.partition("O-" + i, 16));
            assertThat(p).isGreaterThan(-1);
            assertThat(p).isLessThanOrEqualTo(15);
        }
    }

    @Test
    @DisplayName("2만 건: 16개 청크로 나눠 대조한 결과 == 한 번에 대조한 결과")
    void chunkedEqualsInMemory() throws IOException {
        Path dir = Files.createTempDirectory("recon-chunk-");
        SampleDataGenerator.generate(new SampleDataGenerator.Config(20_000, 0.02, 0.02, 0.01, 0.03, 0.02, 11L, dir));

        List<Row> rawA = new ArrayList<>();
        List<Row> rawB = new ArrayList<>();
        try (Reader r = Files.newBufferedReader(dir.resolve(SampleDataGenerator.PAYMENTS_FILE))) {
            PaymentCsvMapper.parse(r).valid().forEach(p -> rawA.add(new Row(p.merchantUid(), p.amount(), p.status())));
        }
        try (Reader r = Files.newBufferedReader(dir.resolve(SampleDataGenerator.ORDERS_FILE))) {
            OrderCsvMapper.parse(r).valid().forEach(o -> rawB.add(new Row(o.orderNo(), o.totalAmount(), o.status())));
        }

        ReconciliationEngine engine = new ReconciliationEngine();
        ScopeRules.Scoped a = ScopeRules.scopeA(rawA);
        ScopeRules.Scoped b = ScopeRules.scopeB(rawB);
        List<MismatchItem> whole = engine.reconcile(a.rows(), b.rows()).items();

        int partitions = 16;
        List<List<Row>> chunksA = emptyChunks(partitions);
        List<List<Row>> chunksB = emptyChunks(partitions);
        for (Row r : rawA) {
            chunksA.get(HashPartitioner.partition(r.key(), partitions)).add(r);
        }
        for (Row r : rawB) {
            chunksB.get(HashPartitioner.partition(r.key(), partitions)).add(r);
        }
        List<MismatchItem> merged = new ArrayList<>();
        int excludedA = 0;
        int excludedB = 0;
        for (int p = 0; p < partitions; p++) {
            ScopeRules.Scoped ca = ScopeRules.scopeA(chunksA.get(p));
            ScopeRules.Scoped cb = ScopeRules.scopeB(chunksB.get(p));
            merged.addAll(engine.reconcile(ca.rows(), cb.rows()).items());
            excludedA += ca.excluded();
            excludedB += cb.excluded();
        }
        merged.sort(Comparator.comparing(MismatchItem::key));

        assertThat(merged).isEqualTo(whole);
        assertThat(excludedA).isEqualTo(a.excluded());
        assertThat(excludedB).isEqualTo(b.excluded());
    }

    @Test
    @DisplayName("청크 크기가 한쪽으로 쏠리지 않는다 (가장 큰 청크 <= 평균의 1.5배)")
    void chunksAreBalanced() {
        int partitions = 16;
        int n = 50_000;
        int[] counts = new int[partitions];
        for (int i = 1; i <= n; i++) {
            counts[HashPartitioner.partition("O-" + String.format("%07d", i), partitions)]++;
        }
        int max = 0;
        for (int c : counts) {
            max = Math.max(max, c);
        }
        assertThat(max).isLessThanOrEqualTo((int) (n / partitions * 1.5));
    }

    @Test
    @DisplayName("파티션 수가 0 이하면 거부한다")
    void rejectsInvalidPartitions() {
        assertThatThrownBy(() -> HashPartitioner.partition("x", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static List<List<Row>> emptyChunks(int n) {
        List<List<Row>> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new ArrayList<>());
        }
        return list;
    }
}
