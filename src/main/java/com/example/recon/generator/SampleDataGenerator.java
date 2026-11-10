package com.example.recon.generator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.Random;

/**
 * 정답(expected)을 알고 있는 샘플 데이터를 생성한다.
 *
 * 출력 파일 (outDir)
 *  - payments.csv            : A (결제 내역)
 *  - orders.csv              : B (주문)
 *  - expected.properties     : 유형별 기대 건수
 *  - expected_mismatches.csv : 기대 불일치 목록 (match_key,type)
 *
 * 같은 seed + 같은 설정이면 항상 같은 파일이 만들어진다.
 * 키 형식은 O-0000001 (7자리) 이므로 size 는 9,999,999 이하만 지원한다.
 */
public final class SampleDataGenerator {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 11, 1, 0, 0, 0);

    public static final String PAYMENTS_FILE = "payments.csv";
    public static final String ORDERS_FILE = "orders.csv";
    public static final String EXPECTED_FILE = "expected.properties";
    public static final String EXPECTED_MISMATCHES_FILE = "expected_mismatches.csv";

    public record Config(int size, double missingInA, double missingInB, double duplicate,
                         double valueDiff, double excluded, long seed, Path outDir) {
        public Config {
            if (size <= 0 || size > 9_999_999) {
                throw new IllegalArgumentException("size 는 1 ~ 9,999,999 여야 합니다: " + size);
            }
            for (double r : new double[]{missingInA, missingInB, duplicate, valueDiff, excluded}) {
                if (r < 0 || r > 1) {
                    throw new IllegalArgumentException("비율은 0 ~ 1 사이여야 합니다: " + r);
                }
            }
            if (missingInA + missingInB + duplicate + valueDiff > 1.0) {
                throw new IllegalArgumentException("불일치 비율의 합이 1 을 넘을 수 없습니다");
            }
            if (outDir == null) {
                throw new IllegalArgumentException("outDir 가 필요합니다");
            }
        }

        public static Config defaults(Path outDir) {
            return new Config(10_000, 0.02, 0.02, 0.01, 0.03, 0.02, 42L, outDir);
        }
    }

    /** 생성 결과의 기대값 */
    public record Expected(int totalA, int totalB, int excludedA, int excludedB,
                           int missingInA, int missingInB, int duplicate, int valueDiff) {
        public int inScopeA() { return totalA - excludedA; }
        public int inScopeB() { return totalB - excludedB; }
        public int totalMismatches() { return missingInA + missingInB + duplicate + valueDiff; }
    }

    private enum Scenario { NORMAL, MISSING_IN_A, MISSING_IN_B, DUPLICATE, VALUE_DIFF }

    private SampleDataGenerator() {
    }

    public static Expected generate(Config cfg) throws IOException {
        Files.createDirectories(cfg.outDir());

        int nMissA = (int) Math.round(cfg.size() * cfg.missingInA());
        int nMissB = (int) Math.round(cfg.size() * cfg.missingInB());
        int nDup = (int) Math.round(cfg.size() * cfg.duplicate());
        int nDiff = (int) Math.round(cfg.size() * cfg.valueDiff());
        if ((long) nMissA + nMissB + nDup + nDiff > cfg.size()) {
            throw new IllegalArgumentException("불일치 건수의 합이 size 보다 큽니다");
        }
        int nExcluded = (int) Math.round(cfg.size() * cfg.excluded());

        // 시나리오 배정 후 섞기 (건수가 정확히 맞도록 확률이 아니라 개수로 배정)
        Scenario[] scenarios = new Scenario[cfg.size()];
        java.util.Arrays.fill(scenarios, Scenario.NORMAL);
        int idx = 0;
        idx = assign(scenarios, idx, nMissA, Scenario.MISSING_IN_A);
        idx = assign(scenarios, idx, nMissB, Scenario.MISSING_IN_B);
        idx = assign(scenarios, idx, nDup, Scenario.DUPLICATE);
        assign(scenarios, idx, nDiff, Scenario.VALUE_DIFF);

        Random rnd = new Random(cfg.seed());
        for (int i = scenarios.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            Scenario tmp = scenarios[i];
            scenarios[i] = scenarios[j];
            scenarios[j] = tmp;
        }

        int[] counters = new int[3]; // 0: totalA, 1: totalB, 2: paymentSeq

        try (BufferedWriter wa = Files.newBufferedWriter(cfg.outDir().resolve(PAYMENTS_FILE), StandardCharsets.UTF_8);
             BufferedWriter wb = Files.newBufferedWriter(cfg.outDir().resolve(ORDERS_FILE), StandardCharsets.UTF_8);
             BufferedWriter we = Files.newBufferedWriter(cfg.outDir().resolve(EXPECTED_MISMATCHES_FILE), StandardCharsets.UTF_8)) {

            wa.write("payment_id,merchant_uid,amount,status,paid_at\n");
            wb.write("order_no,total_amount,status,ordered_at\n");
            we.write("match_key,type\n");

            for (int i = 0; i < cfg.size(); i++) {
                String key = "O-" + pad(i + 1, 7);
                long amount = 1000L + rnd.nextInt(200) * 500L;
                boolean cancelled = rnd.nextInt(10) == 0;
                String ts = BASE_TIME.plusSeconds((i * 7L) % (30L * 86400)).format(TS);
                String aStatus = cancelled ? "cancelled" : "paid";
                String bStatus = cancelled ? "CANCELED" : "PAID";

                switch (scenarios[i]) {
                    case NORMAL -> {
                        writeA(wa, counters, key, amount, aStatus, ts);
                        writeB(wb, counters, key, amount, bStatus, ts);
                    }
                    case MISSING_IN_A -> {
                        writeB(wb, counters, key, amount, bStatus, ts);
                        we.write(key + ",MISSING_IN_A\n");
                    }
                    case MISSING_IN_B -> {
                        writeA(wa, counters, key, amount, aStatus, ts);
                        we.write(key + ",MISSING_IN_B\n");
                    }
                    case DUPLICATE -> {
                        if (rnd.nextBoolean()) {   // A 에 2건
                            writeA(wa, counters, key, amount, aStatus, ts);
                            writeA(wa, counters, key, amount, aStatus, ts);
                            writeB(wb, counters, key, amount, bStatus, ts);
                        } else {                   // B 에 2건
                            writeA(wa, counters, key, amount, aStatus, ts);
                            writeB(wb, counters, key, amount, bStatus, ts);
                            writeB(wb, counters, key, amount, bStatus, ts);
                        }
                        we.write(key + ",DUPLICATE\n");
                    }
                    case VALUE_DIFF -> {
                        if (rnd.nextBoolean()) {   // 금액 불일치
                            writeA(wa, counters, key, amount, aStatus, ts);
                            writeB(wb, counters, key, amount - 500, bStatus, ts);
                        } else {                   // 상태 불일치
                            writeA(wa, counters, key, amount, "paid", ts);
                            writeB(wb, counters, key, amount, "CANCELED", ts);
                        }
                        we.write(key + ",VALUE_DIFF\n");
                    }
                }
            }

            // 대조 범위 밖 행 (결제 ready/failed, 주문 PENDING) : 불일치로 잡히면 안 된다
            for (int j = 1; j <= nExcluded; j++) {
                String ts = BASE_TIME.plusSeconds(j).format(TS);
                writeA(wa, counters, "X-A-" + pad(j, 7), 1000, (j % 2 == 0) ? "ready" : "failed", ts);
                writeB(wb, counters, "X-B-" + pad(j, 7), 1000, "PENDING", ts);
            }
        }

        Expected expected = new Expected(counters[0], counters[1], nExcluded, nExcluded,
                nMissA, nMissB, nDup, nDiff);
        writeExpected(cfg, expected);
        return expected;
    }

    /** 이전에 생성된 expected.properties 를 읽는다. */
    public static Expected readExpected(Path dir) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(dir.resolve(EXPECTED_FILE), StandardCharsets.UTF_8)) {
            p.load(r);
        }
        return new Expected(
                Integer.parseInt(p.getProperty("total_a")),
                Integer.parseInt(p.getProperty("total_b")),
                Integer.parseInt(p.getProperty("excluded_a")),
                Integer.parseInt(p.getProperty("excluded_b")),
                Integer.parseInt(p.getProperty("missing_in_a")),
                Integer.parseInt(p.getProperty("missing_in_b")),
                Integer.parseInt(p.getProperty("duplicate")),
                Integer.parseInt(p.getProperty("value_diff")));
    }

    private static void writeExpected(Config cfg, Expected e) throws IOException {
        String body = "size=" + cfg.size() + "\n"
                + "seed=" + cfg.seed() + "\n"
                + "total_a=" + e.totalA() + "\n"
                + "total_b=" + e.totalB() + "\n"
                + "excluded_a=" + e.excludedA() + "\n"
                + "excluded_b=" + e.excludedB() + "\n"
                + "in_scope_a=" + e.inScopeA() + "\n"
                + "in_scope_b=" + e.inScopeB() + "\n"
                + "missing_in_a=" + e.missingInA() + "\n"
                + "missing_in_b=" + e.missingInB() + "\n"
                + "duplicate=" + e.duplicate() + "\n"
                + "value_diff=" + e.valueDiff() + "\n";
        Files.writeString(cfg.outDir().resolve(EXPECTED_FILE), body, StandardCharsets.UTF_8);
    }

    private static int assign(Scenario[] arr, int from, int count, Scenario s) {
        for (int i = 0; i < count; i++) {
            arr[from + i] = s;
        }
        return from + count;
    }

    private static void writeA(BufferedWriter w, int[] c, String key, long amount, String status, String ts)
            throws IOException {
        c[2]++;
        w.write("imp_" + pad(c[2], 8) + "," + key + "," + amount + "," + status + "," + ts + "\n");
        c[0]++;
    }

    private static void writeB(BufferedWriter w, int[] c, String key, long amount, String status, String ts)
            throws IOException {
        w.write(key + "," + amount + "," + status + "," + ts + "\n");
        c[1]++;
    }

    private static String pad(int n, int width) {
        String s = Integer.toString(n);
        return "0".repeat(Math.max(0, width - s.length())) + s;
    }
}
