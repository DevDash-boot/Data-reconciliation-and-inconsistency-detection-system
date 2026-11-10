package com.example.recon.job;

import com.example.recon.common.exception.BadRequestException;
import com.example.recon.common.exception.JobFailedException;
import com.example.recon.common.exception.NotFoundException;
import com.example.recon.matching.ExactMatchStrategy;
import com.example.recon.matching.MatchingStrategies;
import com.example.recon.matching.MatchingStrategy;
import com.example.recon.matching.ReconciliationEngine;
import com.example.recon.matching.ReconciliationResult;
import com.example.recon.matching.Row;
import com.example.recon.matching.ScopeRules;
import com.example.recon.mismatch.MismatchBatchWriter;
import com.example.recon.mismatch.MismatchType;
import com.example.recon.upload.UploadBatch;
import com.example.recon.upload.UploadBatchRepository;
import com.example.recon.upload.UploadType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 대조 Job 실행 (동기).
 *
 * v14: 실행 방식 두 가지
 *  - IN_MEMORY : 전체를 읽어 한 번에 대조
 *  - CHUNKED   : 키 해시로 N 개 청크로 나눠, 청크마다 읽기 -> 대조 -> 저장 후 메모리를 비운다
 *
 * 두 방식은 같은 process() 를 쓰므로 결과가 같아야 한다. (같은 키는 항상 같은 청크로 가므로 경계 문제가 없다)
 * 트랜잭션 구조는 v9 와 같다: Job 생성 커밋 -> 대조+저장 한 트랜잭션 -> 실패 시 별도 트랜잭션에서 FAILED.
 */
@Service
@Slf4j
public class ReconciliationJobService {

    private final UploadBatchRepository batchRepository;
    private final ReconciliationJobRepository jobRepository;
    private final RowLoader rowLoader;
    private final MismatchBatchWriter mismatchWriter;
    private final PlatformTransactionManager transactionManager;
    private final int partitions;
    private final String defaultMode;

    public ReconciliationJobService(UploadBatchRepository batchRepository,
                                    ReconciliationJobRepository jobRepository,
                                    RowLoader rowLoader,
                                    MismatchBatchWriter mismatchWriter,
                                    PlatformTransactionManager transactionManager,
                                    @Value("${recon.chunk.partitions:16}") int partitions,
                                    @Value("${recon.engine.default-mode:IN_MEMORY}") String defaultMode) {
        this.batchRepository = batchRepository;
        this.jobRepository = jobRepository;
        this.rowLoader = rowLoader;
        this.mismatchWriter = mismatchWriter;
        this.transactionManager = transactionManager;
        this.partitions = partitions;
        this.defaultMode = defaultMode;
    }

    public JobResponse run(JobRequest request) {
        if (request == null || request.paymentBatchId() == null || request.orderBatchId() == null) {
            throw new BadRequestException("paymentBatchId, orderBatchId 가 필요합니다");
        }
        MatchingStrategy strategy = resolveStrategy(request.strategy());
        ExecutionMode mode = resolveMode(request.mode());
        requireBatch(request.paymentBatchId(), UploadType.PAYMENT);
        requireBatch(request.orderBatchId(), UploadType.ORDER);

        String jobKey = request.paymentBatchId() + ":" + request.orderBatchId() + ":" + strategy.name();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Long jobId = tx.execute(status -> {
            ReconciliationJob job = jobRepository.save(ReconciliationJob.builder()
                    .paymentBatch(batchRepository.getReferenceById(request.paymentBatchId()))
                    .orderBatch(batchRepository.getReferenceById(request.orderBatchId()))
                    .strategy(strategy.name())
                    .jobKey(jobKey)
                    .build());
            job.markRunning();
            return job.getId();
        });

        try {
            return tx.execute(status -> execute(jobId, strategy, mode));
        } catch (RuntimeException e) {
            log.error("Job #{} 실행 실패", jobId, e);
            tx.executeWithoutResult(status ->
                    jobRepository.findById(jobId).ifPresent(j -> j.markFailed(e.getMessage())));
            throw new JobFailedException(jobId, e.getMessage(), e);
        }
    }

    private JobResponse execute(Long jobId, MatchingStrategy strategy, ExecutionMode mode) {
        ReconciliationJob job = jobRepository.findById(jobId).orElseThrow();
        long paymentBatchId = job.getPaymentBatch().getId();
        long orderBatchId = job.getOrderBatch().getId();
        ReconciliationEngine engine = new ReconciliationEngine(strategy);
        Totals totals = new Totals();

        if (mode == ExecutionMode.IN_MEMORY) {
            long t0 = System.nanoTime();
            List<Row> rawA = rowLoader.loadPayments(paymentBatchId);
            List<Row> rawB = rowLoader.loadOrders(orderBatchId);
            totals.loadNs += System.nanoTime() - t0;
            process(jobId, rawA, rawB, engine, totals);
        } else {
            for (int p = 0; p < partitions; p++) {
                long t0 = System.nanoTime();
                List<Row> rawA = rowLoader.loadPayments(paymentBatchId, partitions, p);
                List<Row> rawB = rowLoader.loadOrders(orderBatchId, partitions, p);
                totals.loadNs += System.nanoTime() - t0;
                process(jobId, rawA, rawB, engine, totals);
                log.debug("Job #{} 청크 {}/{} 완료 (A {}행, B {}행)", jobId, p + 1, partitions, rawA.size(), rawB.size());
            }
        }

        job.markSuccess(totals.toSummary());
        Map<String, Long> timings = new LinkedHashMap<>();
        timings.put("loadMs", totals.loadNs / 1_000_000);
        timings.put("compareMs", totals.compareNs / 1_000_000);
        timings.put("saveMs", totals.saveNs / 1_000_000);
        log.info("Job #{} 완료 ({}): A={}, B={}, 불일치={}, 시간={}", jobId, mode,
                totals.totalA, totals.totalB, totals.missingInA + totals.missingInB + totals.duplicate + totals.valueDiff,
                timings);
        return JobResponse.from(job, timings, false);
    }

    /** 범위 규칙 적용 -> 대조 -> 불일치 저장. 두 방식이 공통으로 쓴다. */
    private void process(long jobId, List<Row> rawA, List<Row> rawB, ReconciliationEngine engine, Totals t) {
        long c0 = System.nanoTime();
        ScopeRules.Scoped a = ScopeRules.scopeA(rawA);
        ScopeRules.Scoped b = ScopeRules.scopeB(rawB);
        ReconciliationResult result = engine.reconcile(a.rows(), b.rows());
        long c1 = System.nanoTime();
        mismatchWriter.insertAll(jobId, result.items());
        long c2 = System.nanoTime();

        t.compareNs += c1 - c0;
        t.saveNs += c2 - c1;
        t.totalA += a.rows().size();
        t.totalB += b.rows().size();
        t.excludedA += a.excluded();
        t.excludedB += b.excluded();
        t.missingInA += result.count(MismatchType.MISSING_IN_A);
        t.missingInB += result.count(MismatchType.MISSING_IN_B);
        t.duplicate += result.count(MismatchType.DUPLICATE);
        t.valueDiff += result.count(MismatchType.VALUE_DIFF);
    }

    @Transactional(readOnly = true)
    public JobResponse get(Long id) {
        ReconciliationJob job = jobRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Job 을 찾을 수 없습니다: " + id));
        return JobResponse.from(job, null, false);
    }

    @Transactional(readOnly = true)
    public List<JobResponse> list() {
        return jobRepository.findTop50ByOrderByIdDesc().stream()
                .map(j -> JobResponse.from(j, null, false)).toList();
    }

    private MatchingStrategy resolveStrategy(String requested) {
        String name = (requested == null || requested.isBlank()) ? ExactMatchStrategy.NAME : requested.trim();
        try {
            return MatchingStrategies.of(name);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private ExecutionMode resolveMode(String requested) {
        String name = (requested == null || requested.isBlank()) ? defaultMode : requested;
        ExecutionMode mode;
        try {
            mode = ExecutionMode.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("mode 는 IN_MEMORY 또는 CHUNKED 여야 합니다: " + name);
        }
        if (mode == ExecutionMode.CHUNKED && !rowLoader.supportsHashPartition()) {
            throw new BadRequestException("CHUNKED 모드는 CRC32() 를 지원하는 MySQL/MariaDB 에서만 사용할 수 있습니다");
        }
        return mode;
    }

    private UploadBatch requireBatch(Long id, UploadType expected) {
        UploadBatch batch = batchRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("업로드 배치를 찾을 수 없습니다: " + id));
        if (batch.getType() != expected) {
            throw new BadRequestException("배치 #" + id + " 는 " + batch.getType() + " 입니다. "
                    + expected + " 배치가 필요합니다");
        }
        return batch;
    }

    /** 청크를 돌며 누적하는 합계 */
    private static final class Totals {
        long loadNs, compareNs, saveNs;
        int totalA, totalB, excludedA, excludedB, missingInA, missingInB, duplicate, valueDiff;

        JobSummary toSummary() {
            return new JobSummary(totalA, totalB, excludedA, excludedB, missingInA, missingInB, duplicate, valueDiff);
        }
    }
}
