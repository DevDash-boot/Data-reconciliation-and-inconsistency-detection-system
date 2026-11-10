package com.example.recon.job;

import com.example.recon.common.exception.ConflictException;
import com.example.recon.support.SampleUploads;
import com.example.recon.support.TestDb;
import com.example.recon.upload.UploadBatchRepository;
import com.example.recon.upload.UploadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class JobIdempotencyTests {

    @Autowired UploadService uploadService;
    @Autowired ReconciliationJobService jobService;
    @Autowired ReconciliationJobRepository jobRepository;
    @Autowired UploadBatchRepository batchRepository;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        TestDb.clean(jdbc);
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("같은 요청을 3번 보내도 Job 은 1개, 불일치 건수는 그대로다")
    void sameRequestThreeTimes() {
        SampleUploads.Batches b = SampleUploads.upload(uploadService);
        JobRequest req = JobRequest.of(b.paymentBatchId(), b.orderBatchId(), "EXACT");

        JobResponse first = jobService.run(req);
        JobResponse second = jobService.run(req);
        JobResponse third = jobService.run(req);

        assertThat(first.reused()).isFalse();
        assertThat(second.reused()).isTrue();
        assertThat(third.id()).isEqualTo(first.id());
        assertThat(count("SELECT COUNT(*) FROM reconciliation_job")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM mismatch")).isEqualTo(SampleUploads.TOTAL_MISMATCHES);
    }

    @Test
    @DisplayName("force=true 면 기존 결과를 지우고 다시 계산하되 건수는 늘지 않는다")
    void forceReplacesPreviousResult() {
        SampleUploads.Batches b = SampleUploads.upload(uploadService);
        JobResponse first = jobService.run(JobRequest.of(b.paymentBatchId(), b.orderBatchId(), "EXACT"));

        JobResponse forced = jobService.run(
                new JobRequest(b.paymentBatchId(), b.orderBatchId(), "EXACT", null, true));

        assertThat(forced.reused()).isFalse();
        assertThat(forced.id()).isNotEqualTo(first.id());
        assertThat(count("SELECT COUNT(*) FROM reconciliation_job")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM mismatch")).isEqualTo(SampleUploads.TOTAL_MISMATCHES);
    }

    @Test
    @DisplayName("전략이 다르면 다른 Job 이다")
    void differentStrategyIsDifferentJob() {
        SampleUploads.Batches b = SampleUploads.upload(uploadService);

        JobResponse exact = jobService.run(JobRequest.of(b.paymentBatchId(), b.orderBatchId(), "EXACT"));
        JobResponse amountOnly = jobService.run(JobRequest.of(b.paymentBatchId(), b.orderBatchId(), "AMOUNT_ONLY"));

        assertThat(amountOnly.id()).isNotEqualTo(exact.id());
        assertThat(count("SELECT COUNT(*) FROM reconciliation_job")).isEqualTo(2);
    }

    @Test
    @DisplayName("실패한 Job 이 있어도 같은 요청으로 다시 실행할 수 있다")
    void failedJobCanBeRerun() {
        SampleUploads.Batches b = SampleUploads.upload(uploadService);
        ReconciliationJob failed = jobRepository.save(ReconciliationJob.builder()
                .paymentBatch(batchRepository.getReferenceById(b.paymentBatchId()))
                .orderBatch(batchRepository.getReferenceById(b.orderBatchId()))
                .strategy("EXACT").jobKey(b.paymentBatchId() + ":" + b.orderBatchId() + ":EXACT").build());
        failed.markFailed("boom");
        jobRepository.save(failed);

        JobResponse rerun = jobService.run(JobRequest.of(b.paymentBatchId(), b.orderBatchId(), "EXACT"));

        assertThat(rerun.status()).isEqualTo("SUCCESS");
        assertThat(count("SELECT COUNT(*) FROM reconciliation_job")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM reconciliation_job WHERE active_key IS NOT NULL")).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 RUNNING 인 동일 Job 이 있으면 409 (ConflictException)")
    void runningJobBlocksSameRequest() {
        SampleUploads.Batches b = SampleUploads.upload(uploadService);
        ReconciliationJob running = jobRepository.save(ReconciliationJob.builder()
                .paymentBatch(batchRepository.getReferenceById(b.paymentBatchId()))
                .orderBatch(batchRepository.getReferenceById(b.orderBatchId()))
                .strategy("EXACT").jobKey(b.paymentBatchId() + ":" + b.orderBatchId() + ":EXACT").build());
        running.markRunning();
        jobRepository.save(running);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        jobService.run(JobRequest.of(b.paymentBatchId(), b.orderBatchId(), "EXACT")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("동시에 같은 요청 2개가 와도 성공 Job 은 하나, 불일치는 중복되지 않는다")
    void concurrentSameRequests() throws Exception {
        SampleUploads.Batches b = SampleUploads.upload(uploadService);
        JobRequest req = JobRequest.of(b.paymentBatchId(), b.orderBatchId(), "EXACT");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Object> task = () -> {
            ready.countDown();
            start.await();
            try {
                return jobService.run(req);
            } catch (ConflictException e) {
                return e;   // 동시 실행 방지로 거절된 쪽
            }
        };
        List<Future<Object>> futures = new ArrayList<>();
        futures.add(pool.submit(task));
        futures.add(pool.submit(task));
        ready.await();
        start.countDown();

        int succeeded = 0;
        for (Future<Object> f : futures) {
            Object r = f.get(60, TimeUnit.SECONDS);
            if (r instanceof JobResponse) {
                succeeded++;
            }
        }
        pool.shutdown();

        assertThat(succeeded).isGreaterThan(0);
        assertThat(count("SELECT COUNT(*) FROM reconciliation_job WHERE active_key IS NOT NULL")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM mismatch")).isEqualTo(SampleUploads.TOTAL_MISMATCHES);
    }

    @Test
    @DisplayName("서버 재시작 시 RUNNING 으로 남은 Job 은 FAILED 로 정리된다")
    void recoveryCleansStaleRunningJobs() {
        SampleUploads.Batches b = SampleUploads.upload(uploadService);
        ReconciliationJob stale = jobRepository.save(ReconciliationJob.builder()
                .paymentBatch(batchRepository.getReferenceById(b.paymentBatchId()))
                .orderBatch(batchRepository.getReferenceById(b.orderBatchId()))
                .strategy("EXACT").jobKey("x:y:EXACT").build());
        stale.markRunning();
        jobRepository.save(stale);

        new JobRecovery(jdbc).run((ApplicationArguments) null);

        ReconciliationJob after = jobRepository.findById(stale.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(after.getActiveKey()).isNull();
    }
}
