package com.example.recon.generator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SampleDataGeneratorTest {

    private Path tempDir() throws IOException {
        return Files.createTempDirectory("recon-gen-");
    }

    private SampleDataGenerator.Config config(int size, Path dir) {
        return new SampleDataGenerator.Config(size, 0.02, 0.03, 0.01, 0.04, 0.02, 7L, dir);
    }

    @Test
    @DisplayName("설정한 비율대로 기대 건수가 정확히 계산된다")
    void expectedCountsMatchRates() throws IOException {
        SampleDataGenerator.Expected e = SampleDataGenerator.generate(config(1000, tempDir()));

        assertThat(e.missingInA()).isEqualTo(20);
        assertThat(e.missingInB()).isEqualTo(30);
        assertThat(e.duplicate()).isEqualTo(10);
        assertThat(e.valueDiff()).isEqualTo(40);
        assertThat(e.excludedA()).isEqualTo(20);
        assertThat(e.totalMismatches()).isEqualTo(100);
    }

    @Test
    @DisplayName("expected_mismatches.csv 의 줄 수가 기대 불일치 수와 같다")
    void expectedMismatchFileHasAllEntries() throws IOException {
        Path dir = tempDir();
        SampleDataGenerator.Expected e = SampleDataGenerator.generate(config(1000, dir));

        List<String> lines = Files.readAllLines(dir.resolve(SampleDataGenerator.EXPECTED_MISMATCHES_FILE));

        assertThat(lines.size() - 1).isEqualTo(e.totalMismatches());
    }

    @Test
    @DisplayName("파일의 실제 행 수가 expected 의 totalA/totalB 와 같다")
    void fileRowCountsMatchExpected() throws IOException {
        Path dir = tempDir();
        SampleDataGenerator.Expected e = SampleDataGenerator.generate(config(500, dir));

        long a = Files.readAllLines(dir.resolve(SampleDataGenerator.PAYMENTS_FILE)).size() - 1;
        long b = Files.readAllLines(dir.resolve(SampleDataGenerator.ORDERS_FILE)).size() - 1;

        assertThat(a).isEqualTo(e.totalA());
        assertThat(b).isEqualTo(e.totalB());
    }

    @Test
    @DisplayName("같은 seed 면 파일 내용이 완전히 같다 (재현 가능)")
    void sameSeedProducesSameFiles() throws IOException {
        Path d1 = tempDir();
        Path d2 = tempDir();
        SampleDataGenerator.generate(config(300, d1));
        SampleDataGenerator.generate(config(300, d2));

        for (String f : List.of(SampleDataGenerator.PAYMENTS_FILE, SampleDataGenerator.ORDERS_FILE,
                SampleDataGenerator.EXPECTED_MISMATCHES_FILE, SampleDataGenerator.EXPECTED_FILE)) {
            assertThat(Files.readString(d1.resolve(f))).isEqualTo(Files.readString(d2.resolve(f)));
        }
    }

    @Test
    @DisplayName("expected.properties 를 다시 읽으면 생성 결과와 같다")
    void readExpectedRoundTrip() throws IOException {
        Path dir = tempDir();
        SampleDataGenerator.Expected written = SampleDataGenerator.generate(config(400, dir));

        assertThat(SampleDataGenerator.readExpected(dir)).isEqualTo(written);
    }

    @Test
    @DisplayName("불일치 비율의 합이 1 을 넘으면 거부한다")
    void rejectsRatesOverOne() {
        assertThatThrownBy(() -> new SampleDataGenerator.Config(100, 0.5, 0.5, 0.1, 0.0, 0.0, 1L, Path.of("x")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
