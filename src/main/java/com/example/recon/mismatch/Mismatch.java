package com.example.recon.mismatch;

import com.example.recon.job.ReconciliationJob;
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

/**
 * 대조 결과로 발견된 불일치 1건.
 * 도메인 규칙: 한 Job 안에서 키 하나당 불일치는 1건만 기록한다. (UNIQUE job_id + match_key)
 */
@Entity
@Table(name = "mismatch",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_mismatch_job_key", columnNames = {"job_id", "match_key"})
        },
        indexes = {
                @Index(name = "idx_mismatch_job_type", columnList = "job_id, type"),
                @Index(name = "idx_mismatch_job_status", columnList = "job_id, status")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Mismatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private ReconciliationJob job;

    @Column(name = "match_key", nullable = false, length = 100)
    private String matchKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MismatchType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MismatchStatus status;

    /** 상세 내용(JSON 문자열). 예: {"amount":{"a":50000,"b":45000}} */
    @Column(name = "diff_detail", length = 2000)
    private String diffDetail;

    // ---- 처리 정보 (#19 에서 사용) ----
    @Column(name = "resolved_by", length = 50)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(length = 500)
    private String memo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Mismatch(ReconciliationJob job, String matchKey, MismatchType type, String diffDetail) {
        this.job = job;
        this.matchKey = matchKey;
        this.type = type;
        this.diffDetail = diffDetail;
        this.status = MismatchStatus.OPEN;
    }

    /**
     * 상태를 바꾼다. 전이 규칙 검증은 호출하는 서비스가 한다. (MismatchStatus.canTransitionTo)
     * OPEN 으로 되돌리면 처리자/처리 시각을 비운다.
     */
    public void changeStatus(MismatchStatus to, String by, String memo) {
        this.status = to;
        if (to == MismatchStatus.OPEN) {
            this.resolvedBy = null;
            this.resolvedAt = null;
        } else {
            this.resolvedBy = by;
            this.resolvedAt = LocalDateTime.now();
        }
        if (memo != null) {
            this.memo = memo;
        }
    }
}
