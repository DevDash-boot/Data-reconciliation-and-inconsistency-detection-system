package com.example.recon.source;

import com.example.recon.upload.UploadBatch;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** B: 주문 원천 데이터 (내부 주문 DB). order_no 는 DUPLICATE 탐지를 위해 UNIQUE 로 두지 않는다. */
@Entity
@Table(name = "source_order", indexes = {
        @Index(name = "idx_order_batch_key", columnList = "batch_id, order_no")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SourceOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private UploadBatch batch;

    /** 매칭 키 (= 결제의 merchant_uid) */
    @Column(name = "order_no", nullable = false, length = 100)
    private String orderNo;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    /** 파일의 원본 값 그대로 저장 (PAID / CANCELED / PENDING ...) */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;

    @Column(name = "row_no", nullable = false)
    private int rowNo;

    @Builder
    public SourceOrder(UploadBatch batch, String orderNo, long totalAmount,
                       String status, LocalDateTime orderedAt, int rowNo) {
        this.batch = batch;
        this.orderNo = orderNo;
        this.totalAmount = totalAmount;
        this.status = status;
        this.orderedAt = orderedAt;
        this.rowNo = rowNo;
    }
}
