package com.example.recon.perf;

import com.example.recon.generator.SampleDataGenerator;
import com.example.recon.job.JobRequest;
import com.example.recon.job.JobResponse;
import com.example.recon.job.ReconciliationJobService;
import com.example.recon.upload.UploadResult;
import com.example.recon.upload.UploadService;
import com.example.recon.upload.UploadType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 성능 측정 실행기. perf 프로파일에서만 동작한다.
 *
 *  ./gradlew bootRun -Pxmx=1g --args="--spring.profiles.active=perf --perf.dataset=data/100k --perf.label=baseline"
 *
 * 옵션
 *  --perf.dataset=data/100k   (필수) generateSample 로 만든 폴더
 *  --perf.label=baseline      표에 찍을 방식 이름
 *  --perf.repeat=3            측정 횟수 (평균 계산)
 *  --perf.warmup=1            버리는 워밍업 횟수
 *  --perf.strategy=EXACT
 *  --perf.mode=IN_MEMORY      (#14 이후) IN_MEMORY | CHUNKED
 *
 * 매 실행마다 테이블을 비우고 업로드 -> 대조까지 단계별 시간을 재고, 결과가 expected 와 같은지 검증한다.
 */
@Component
@Profile("perf")
@RequiredArgsConstructor
@Slf4j
public class PerfRunner implements ApplicationRunner {

    private final UploadService uploadService;
    private final ReconciliationJobService jobService;
    private final JdbcTemplate jdbc;
    private final ApplicationContext context;

    private record Run(long uploadA, long uploadB, long load, long compare, long save, long total, boolean ok) {
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String dataset = option(args, "perf.dataset", null);
        if (dataset == null) {
            throw new IllegalArgumentException("--perf.dataset=data/100k 를 지정하세요");
        }
        String label = option(args, "perf.label", "baseline");
        String strategy = option(args, "perf.strategy", "EXACT");
        int repeat = Integer.parseInt(option(args, "perf.repeat", "3"));
        int warmup = Integer.parseInt(option(args, "perf.warmup", "1"));

        Path dir = Path.of(dataset);
        SampleDataGenerator.Expected expected = SampleDataGenerator.readExpected(dir);

        printEnvironment();
        System.out.println();
        System.out.println("데이터셋: " + dir + " (A " + expected.totalA() + "행, B " + expected.totalB() + "행)");

        List<Run> measured = new ArrayList<>();
        boolean allOk = true;
        for (int i = 1; i <= warmup + repeat; i++) {
            Run r = runOnce(dir, strategy, expected);
            boolean isWarmup = i <= warmup;
            System.out.println((isWarmup ? "[워밍업] " : "[측정 " + (i - warmup) + "] ")
                    + "업로드A " + sec(r.uploadA) + " / 업로드B " + sec(r.uploadB)
                    + " / 로드 " + sec(r.load) + " / 대조 " + sec(r.compare) + " / 저장 " + sec(r.save)
                    + " / 합계 " + sec(r.total) + " / 정확성 " + (r.ok ? "OK" : "불일치!"));
            allOk &= r.ok;
            if (!isWarmup) {
                measured.add(r);
            }
        }

        System.out.println();
        System.out.println("Wiki 7번 페이지에 붙여넣을 행 (평균 " + measured.size() + "회):");
        System.out.println("| 날짜 | 이슈 | 규모 | 방식 | 업로드 적재 | 대조(로드+비교) | 결과 저장 | 합계 | 메모 |");
        System.out.println("|---|---|---|---|---|---|---|---|---|");
        long up = avg(measured, r -> r.uploadA + r.uploadB);
        long cmp = avg(measured, r -> r.load + r.compare);
        long sv = avg(measured, r -> r.save);
        long tot = avg(measured, r -> r.total);
        System.out.println("| " + java.time.LocalDate.now() + " | | " + expected.totalA() + " / " + expected.totalB()
                + " | " + label + " | " + sec(up) + " | " + sec(cmp) + " | " + sec(sv) + " | " + sec(tot)
                + " | 정확성 " + (allOk ? "OK" : "불일치") + " |");

        int exit = SpringApplication.exit(context, () -> 0);
        System.exit(allOk ? exit : 1);
    }

    private Run runOnce(Path dir, String strategy, SampleDataGenerator.Expected expected) throws Exception {
        clean();
        long t0 = System.nanoTime();
        UploadResult a = uploadService.upload(UploadType.PAYMENT, "payments.csv",
                Files.newInputStream(dir.resolve(SampleDataGenerator.PAYMENTS_FILE)));
        long t1 = System.nanoTime();
        UploadResult b = uploadService.upload(UploadType.ORDER, "orders.csv",
                Files.newInputStream(dir.resolve(SampleDataGenerator.ORDERS_FILE)));
        long t2 = System.nanoTime();
        JobResponse job = jobService.run(JobRequest.of(a.batchId(), b.batchId(), strategy));
        long t3 = System.nanoTime();

        boolean ok = job.missingInA() == expected.missingInA()
                && job.missingInB() == expected.missingInB()
                && job.duplicate() == expected.duplicate()
                && job.valueDiff() == expected.valueDiff()
                && job.totalA() == expected.inScopeA()
                && job.totalB() == expected.inScopeB()
                && job.excludedA() == expected.excludedA()
                && job.excludedB() == expected.excludedB();

        return new Run(ms(t1 - t0), ms(t2 - t1),
                job.timingsMs().getOrDefault("loadMs", 0L),
                job.timingsMs().getOrDefault("compareMs", 0L),
                job.timingsMs().getOrDefault("saveMs", 0L),
                ms(t3 - t0), ok);
    }

    private void clean() {
        for (String table : List.of("mismatch_status_history", "mismatch", "reconciliation_job",
                "source_payment", "source_order", "upload_batch")) {
            try {
                jdbc.execute("DELETE FROM " + table);
            } catch (Exception ignored) {
                // 아직 없는 테이블
            }
        }
    }

    private void printEnvironment() {
        String dbVersion = "?";
        try {
            dbVersion = jdbc.queryForObject("SELECT VERSION()", String.class);
        } catch (Exception ignored) {
        }
        System.out.println("측정 환경 (Wiki 7번 '측정 환경' 표에 기록)");
        System.out.println("  OS      : " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        System.out.println("  CPU 코어: " + Runtime.getRuntime().availableProcessors());
        System.out.println("  최대 힙 : " + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MB");
        System.out.println("  JDK     : " + System.getProperty("java.version"));
        System.out.println("  DB      : " + dbVersion);
    }

    private static String option(ApplicationArguments args, String name, String def) {
        List<String> v = args.getOptionValues(name);
        return (v == null || v.isEmpty()) ? def : v.get(0);
    }

    private static long ms(long nanos) {
        return nanos / 1_000_000;
    }

    private static String sec(long ms) {
        return String.format("%.1f초", ms / 1000.0);
    }

    private static long avg(List<Run> runs, java.util.function.ToLongFunction<Run> f) {
        return runs.isEmpty() ? 0 : (long) runs.stream().mapToLong(f).average().orElse(0);
    }
}
