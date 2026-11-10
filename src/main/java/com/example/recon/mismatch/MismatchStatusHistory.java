package com.example.recon.mismatch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 상태 변경 이력. mismatch_id 는 FK 로 걸지 않고 값만 가진다.
 * (Job 을 다시 계산하며 불일치를 지울 때 이력을 함께 지우기 쉽도록. JobCleanup 참고)
 */
@Entity
@Table(name = "mismatch_status_history", indexes = {
        @Index(name = "idx_history_mismatch", columnList = "mismatch_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MismatchStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mismatch_id", nullable = false)
    private Long mismatchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 20)
    private MismatchStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private MismatchStatus toStatus;

    @Column(name = "changed_by", nullable = false, length = 50)
    private String changedBy;

    @Column(length = 500)
    private String memo;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @Builder
    public MismatchStatusHistory(Long mismatchId, MismatchStatus fromStatus, MismatchStatus toStatus,
                                 String changedBy, String memo) {
        this.mismatchId = mismatchId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.memo = memo;
    }
}
