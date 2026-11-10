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

/**
 * A: 결제 내역 원천 데이터 (PortOne).
 * merchant_uid 는 DUPLICATE 탐지를 위해 UNIQUE 로 두지 않는다.
 */
@Entity
@Table(name = "source_payment", indexes = {
        @Index(name = "idx_payment_batch_key", columnList = "batch_id, merchant_uid")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SourcePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private UploadBatch batch;

    @Column(name = "payment_id", nullable = false, length = 100)
    private String paymentId;

    /** 매칭 키 (= 주문의 order_no) */
    @Column(name = "merchant_uid", nullable = false, length = 100)
    private String merchantUid;

    @Column(nullable = false)
    private long amount;

    /** 파일의 원본 값 그대로 저장 (paid / cancelled / ready / failed) */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    /** 원본 파일에서의 행 번호 (오류 추적용) */
    @Column(name = "row_no", nullable = false)
    private int rowNo;

    @Builder
    public SourcePayment(UploadBatch batch, String paymentId, String merchantUid,
                         long amount, String status, LocalDateTime paidAt, int rowNo) {
        this.batch = batch;
        this.paymentId = paymentId;
        this.merchantUid = merchantUid;
        this.amount = amount;
        this.status = status;
        this.paidAt = paidAt;
        this.rowNo = rowNo;
    }
}
