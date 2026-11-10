package com.example.recon;

import com.example.recon.job.JobStatus;
import com.example.recon.job.ReconciliationJob;
import com.example.recon.job.ReconciliationJobRepository;
import com.example.recon.mismatch.Mismatch;
import com.example.recon.mismatch.MismatchRepository;
import com.example.recon.mismatch.MismatchStatus;
import com.example.recon.mismatch.MismatchType;
import com.example.recon.source.SourceOrder;
import com.example.recon.source.SourceOrderRepository;
import com.example.recon.source.SourcePayment;
import com.example.recon.source.SourcePaymentRepository;
import com.example.recon.upload.UploadBatch;
import com.example.recon.upload.UploadBatchRepository;
import com.example.recon.upload.UploadType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EntityMappingTests {

    @Autowired UploadBatchRepository batchRepository;
    @Autowired SourcePaymentRepository paymentRepository;
    @Autowired SourceOrderRepository orderRepository;
    @Autowired ReconciliationJobRepository jobRepository;
    @Autowired MismatchRepository mismatchRepository;

    private UploadBatch newBatch(UploadType type) {
        return batchRepository.save(UploadBatch.builder()
                .type(type).fileName(type + ".csv")
                .totalRows(2).validRows(2).errorRows(0)
                .build());
    }

    private ReconciliationJob newJob() {
        UploadBatch a = newBatch(UploadType.PAYMENT);
        UploadBatch b = newBatch(UploadType.ORDER);
        return jobRepository.save(ReconciliationJob.builder()
                .paymentBatch(a).orderBatch(b)
                .strategy("EXACT").jobKey(a.getId() + ":" + b.getId() + ":EXACT")
                .build());
    }

    @Test
    @DisplayName("결제 행과 주문 행을 배치 기준으로 저장하고 조회한다")
    void saveAndFindSourceRows() {
        UploadBatch paymentBatch = newBatch(UploadType.PAYMENT);
        UploadBatch orderBatch = newBatch(UploadType.ORDER);
        LocalDateTime now = LocalDateTime.of(2026, 11, 1, 10, 0);

        paymentRepository.save(SourcePayment.builder()
                .batch(paymentBatch).paymentId("imp_1").merchantUid("O-0001")
                .amount(30000).status("paid").paidAt(now).rowNo(1).build());
        orderRepository.save(SourceOrder.builder()
                .batch(orderBatch).orderNo("O-0001")
                .totalAmount(30000).status("PAID").orderedAt(now).rowNo(1).build());

        List<SourcePayment> payments = paymentRepository.findByBatchId(paymentBatch.getId());

        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getMerchantUid()).isEqualTo("O-0001");
        assertThat(orderRepository.countByBatchId(orderBatch.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 키가 한 배치에 2건 있어도 저장된다 (DUPLICATE 탐지를 위해 UNIQUE 아님)")
    void duplicateKeysAreAllowedInSource() {
        UploadBatch batch = newBatch(UploadType.PAYMENT);
        LocalDateTime now = LocalDateTime.of(2026, 11, 1, 10, 0);

        for (int i = 1; i <= 2; i++) {
            paymentRepository.save(SourcePayment.builder()
                    .batch(batch).paymentId("imp_" + i).merchantUid("O-3001")
                    .amount(20000).status("paid").paidAt(now).rowNo(i).build());
        }

        assertThat(paymentRepository.countByBatchId(batch.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("Job 은 PENDING 으로 시작하고 요약 값은 0 으로 초기화된다")
    void jobStartsAsPending() {
        ReconciliationJob job = newJob();

        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getMissingInACount()).isZero();
        assertThat(job.getValueDiffCount()).isZero();
    }

    @Test
    @DisplayName("불일치는 OPEN 상태로 저장되고 유형별로 집계된다")
    void mismatchIsSavedAsOpen() {
        ReconciliationJob job = newJob();

        Mismatch saved = mismatchRepository.save(Mismatch.builder()
                .job(job).matchKey("O-4001").type(MismatchType.VALUE_DIFF)
                .diffDetail("{\"amount\":{\"a\":50000,\"b\":45000}}").build());

        assertThat(saved.getStatus()).isEqualTo(MismatchStatus.OPEN);
        assertThat(mismatchRepository.countByJobIdAndType(job.getId(), MismatchType.VALUE_DIFF)).isEqualTo(1);
        assertThat(mismatchRepository.countByJobIdAndType(job.getId(), MismatchType.DUPLICATE)).isZero();
    }

    @Test
    @DisplayName("같은 Job 에서 같은 키의 불일치를 2건 저장하면 UNIQUE 위반이다")
    void sameKeyInSameJobIsRejected() {
        ReconciliationJob job = newJob();
        mismatchRepository.saveAndFlush(Mismatch.builder()
                .job(job).matchKey("O-5001").type(MismatchType.MISSING_IN_B).build());

        Mismatch second = Mismatch.builder()
                .job(job).matchKey("O-5001").type(MismatchType.VALUE_DIFF).build();

        assertThatThrownBy(() -> mismatchRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
