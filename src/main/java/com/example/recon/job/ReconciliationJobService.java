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
import com.example.recon.source.SourceOrderRepository;
import com.example.recon.source.SourcePaymentRepository;
import com.example.recon.upload.UploadBatch;
import com.example.recon.upload.UploadBatchRepository;
import com.example.recon.upload.UploadType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 트랜잭션을 세 단계로 나눈 이유
 *  1) Job 생성(RUNNING)은 먼저 커밋한다  -> 실행 중 실패해도 Job 기록이 남는다
 *  2) 대조 + 결과 저장은 한 트랜잭션     -> 실패하면 불일치가 부분 저장되지 않는다 (롤백)
 *  3) 실패하면 별도 트랜잭션에서 FAILED  -> 롤백과 무관하게 실패 상태가 남는다
 *
 * v13: 불일치 저장을 JPA saveAll 대신 JDBC batch(MismatchBatchWriter)로 바꿨다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationJobService {

    private final UploadBatchRepository batchRepository;
    private final ReconciliationJobRepository jobRepository;
    private final SourcePaymentRepository paymentRepository;
    private final SourceOrderRepository orderRepository;
    private final MismatchBatchWriter mismatchWriter;
    private final PlatformTransactionManager transactionManager;

    public JobResponse run(JobRequest request) {
        if (request == null || request.paymentBatchId() == null || request.orderBatchId() == null) {
            throw new BadRequestException("paymentBatchId, orderBatchId 가 필요합니다");
        }
        String strategyName = (request.strategy() == null || request.strategy().isBlank())
                ? ExactMatchStrategy.NAME : request.strategy().trim().toUpperCase();
        MatchingStrategy strategy;
        try {
            strategy = MatchingStrategies.of(strategyName);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
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
            return tx.execute(status -> execute(jobId, strategy));
        } catch (RuntimeException e) {
            log.error("Job #{} 실행 실패", jobId, e);
            tx.executeWithoutResult(status ->
                    jobRepository.findById(jobId).ifPresent(j -> j.markFailed(e.getMessage())));
            throw new JobFailedException(jobId, e.getMessage(), e);
        }
    }

    private JobResponse execute(Long jobId, MatchingStrategy strategy) {
        ReconciliationJob job = jobRepository.findById(jobId).orElseThrow();
        Map<String, Long> timings = new LinkedHashMap<>();

        long t0 = System.nanoTime();
        List<Row> rawA = paymentRepository.findByBatchId(job.getPaymentBatch().getId()).stream()
                .map(p -> new Row(p.getMerchantUid(), p.getAmount(), p.getStatus())).toList();
        List<Row> rawB = orderRepository.findByBatchId(job.getOrderBatch().getId()).stream()
                .map(o -> new Row(o.getOrderNo(), o.getTotalAmount(), o.getStatus())).toList();
        ScopeRules.Scoped a = ScopeRules.scopeA(rawA);
        ScopeRules.Scoped b = ScopeRules.scopeB(rawB);
        long t1 = System.nanoTime();

        ReconciliationResult result = new ReconciliationEngine(strategy).reconcile(a.rows(), b.rows());
        long t2 = System.nanoTime();

        mismatchWriter.insertAll(jobId, result.items());
        long t3 = System.nanoTime();

        job.markSuccess(new JobSummary(a.rows().size(), b.rows().size(), a.excluded(), b.excluded(),
                result.count(MismatchType.MISSING_IN_A), result.count(MismatchType.MISSING_IN_B),
                result.count(MismatchType.DUPLICATE), result.count(MismatchType.VALUE_DIFF)));

        timings.put("loadMs", (t1 - t0) / 1_000_000);
        timings.put("compareMs", (t2 - t1) / 1_000_000);
        timings.put("saveMs", (t3 - t2) / 1_000_000);
        log.info("Job #{} 완료: A={}, B={}, 불일치={}, 시간={}", jobId,
                a.rows().size(), b.rows().size(), result.items().size(), timings);
        return JobResponse.from(job, timings, false);
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

    private UploadBatch requireBatch(Long id, UploadType expected) {
        UploadBatch batch = batchRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("업로드 배치를 찾을 수 없습니다: " + id));
        if (batch.getType() != expected) {
            throw new BadRequestException("배치 #" + id + " 는 " + batch.getType() + " 입니다. "
                    + expected + " 배치가 필요합니다");
        }
        return batch;
    }
}
