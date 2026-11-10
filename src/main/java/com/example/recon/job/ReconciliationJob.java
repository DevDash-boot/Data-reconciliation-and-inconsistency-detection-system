package com.example.recon.job;

import com.example.recon.upload.UploadBatch;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** 대조 1회 실행 단위. 입력(두 배치), 전략, 결과 요약을 이력으로 남긴다. */
@Entity
@Table(name = "reconciliation_job",
        indexes = {
                @Index(name = "idx_job_key", columnList = "job_key")
        },
        uniqueConstraints = {
                // 같은 active_key 를 가진 Job 은 하나만 존재할 수 있다 (NULL 은 여러 개 가능)
                @UniqueConstraint(name = "uq_job_active_key", columnNames = {"active_key"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReconciliationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_batch_id", nullable = false)
    private UploadBatch paymentBatch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_batch_id", nullable = false)
    private UploadBatch orderBatch;

    /** 사용한 MatchingStrategy 이름 (EXACT, AMOUNT_ONLY) */
    @Column(nullable = false, length = 50)
    private String strategy;

    /** "동일 Job" 판별용 키: paymentBatchId:orderBatchId:strategy */
    @Column(name = "job_key", length = 100)
    private String jobKey;

    /**
     * PENDING / RUNNING / SUCCESS 인 동안만 job_key 와 같은 값을 가진다. FAILED 가 되면 NULL.
     * UNIQUE 라서 "같은 Job 의 동시 실행" 과 "중복 성공 결과" 를 DB 가 막아 준다. (#16)
     */
    @Column(name = "active_key", length = 100)
    private String activeKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "total_a", nullable = false)
    private int totalA;

    @Column(name = "total_b", nullable = false)
    private int totalB;

    @Column(name = "excluded_a", nullable = false)
    private int excludedA;

    @Column(name = "excluded_b", nullable = false)
    private int excludedB;

    @Column(name = "missing_in_a_count", nullable = false)
    private int missingInACount;

    @Column(name = "missing_in_b_count", nullable = false)
    private int missingInBCount;

    @Column(name = "duplicate_count", nullable = false)
    private int duplicateCount;

    @Column(name = "value_diff_count", nullable = false)
    private int valueDiffCount;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ReconciliationJob(UploadBatch paymentBatch, UploadBatch orderBatch,
                             String strategy, String jobKey) {
        this.paymentBatch = paymentBatch;
        this.orderBatch = orderBatch;
        this.strategy = strategy;
        this.jobKey = jobKey;
        this.activeKey = jobKey;
        this.status = JobStatus.PENDING;
    }

    public void markRunning() {
        this.status = JobStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    public void markSuccess(JobSummary s) {
        this.totalA = s.totalA();
        this.totalB = s.totalB();
        this.excludedA = s.excludedA();
        this.excludedB = s.excludedB();
        this.missingInACount = s.missingInA();
        this.missingInBCount = s.missingInB();
        this.duplicateCount = s.duplicate();
        this.valueDiffCount = s.valueDiff();
        this.status = JobStatus.SUCCESS;
        this.finishedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    public void markFailed(String message) {
        this.status = JobStatus.FAILED;
        this.activeKey = null;   // 실패한 Job 은 같은 요청으로 다시 실행할 수 있다
        this.finishedAt = LocalDateTime.now();
        this.errorMessage = (message == null) ? "알 수 없는 오류"
                : (message.length() > 1000 ? message.substring(0, 1000) : message);
    }
}
