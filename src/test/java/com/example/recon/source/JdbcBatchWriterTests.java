package com.example.recon.source;

import com.example.recon.job.ReconciliationJob;
import com.example.recon.job.ReconciliationJobRepository;
import com.example.recon.matching.MismatchItem;
import com.example.recon.mismatch.MismatchBatchWriter;
import com.example.recon.mismatch.MismatchType;
import com.example.recon.support.TestCsv;
import com.example.recon.support.TestDb;
import com.example.recon.upload.UploadBatchRepository;
import com.example.recon.upload.UploadResult;
import com.example.recon.upload.UploadService;
import com.example.recon.upload.UploadType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class JdbcBatchWriterTests {

    @Autowired UploadService uploadService;
    @Autowired UploadBatchRepository batchRepository;
    @Autowired ReconciliationJobRepository jobRepository;
    @Autowired MismatchBatchWriter mismatchWriter;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        TestDb.clean(jdbc);
    }

    @Test
    @DisplayName("batch size(1000) 보다 많은 2,500 행도 빠짐없이 저장된다")
    void uploadsMoreRowsThanBatchSize() {
        String[] rows = new String[2500];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = "imp_" + i + ",O-" + i + ",100,paid,2026-11-01T10:00:00";
        }

        UploadResult r = uploadService.upload(UploadType.PAYMENT, "big.csv", TestCsv.stream(TestCsv.payments(rows)));

        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM source_payment WHERE batch_id = ?", Long.class, r.batchId());
        assertThat(count).isEqualTo(2500L);
    }

    @Test
    @DisplayName("저장된 결제 행의 값이 정확하다")
    void storedValuesAreCorrect() {
        UploadResult r = uploadService.upload(UploadType.PAYMENT, "p.csv",
                TestCsv.stream(TestCsv.payments("imp_1,O-1,30000,paid,2026-11-01T10:05:09")));

        String key = jdbc.queryForObject(
                "SELECT merchant_uid FROM source_payment WHERE batch_id = ?", String.class, r.batchId());
        Long amount = jdbc.queryForObject(
                "SELECT amount FROM source_payment WHERE batch_id = ?", Long.class, r.batchId());
        Integer rowNo = jdbc.queryForObject(
                "SELECT row_no FROM source_payment WHERE batch_id = ?", Integer.class, r.batchId());

        assertThat(key).isEqualTo("O-1");
        assertThat(amount).isEqualTo(30000L);
        assertThat(rowNo).isEqualTo(2);   // 헤더가 1행, 첫 데이터가 2행
    }

    @Test
    @DisplayName("불일치를 batch 로 저장하면 상태는 OPEN 이고 created_at 이 채워진다")
    void mismatchBatchInsert() {
        UploadResult a = uploadService.upload(UploadType.PAYMENT, "a.csv",
                TestCsv.stream(TestCsv.payments("imp_1,O-1,1,paid,2026-11-01T10:00:00")));
        UploadResult b = uploadService.upload(UploadType.ORDER, "b.csv",
                TestCsv.stream(TestCsv.orders("O-1,1,PAID,2026-11-01T10:00:00")));
        ReconciliationJob job = jobRepository.save(ReconciliationJob.builder()
                .paymentBatch(batchRepository.getReferenceById(a.batchId()))
                .orderBatch(batchRepository.getReferenceById(b.batchId()))
                .strategy("EXACT").jobKey("test").build());

        mismatchWriter.insertAll(job.getId(), List.of(
                new MismatchItem("O-8", MismatchType.MISSING_IN_B, "{\"presentIn\":\"A\"}"),
                new MismatchItem("O-9", MismatchType.VALUE_DIFF, "{}")));

        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM mismatch WHERE job_id = ?", Long.class, job.getId());
        String status = jdbc.queryForObject(
                "SELECT status FROM mismatch WHERE match_key = 'O-8'", String.class);
        Long withTime = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mismatch WHERE job_id = ? AND created_at IS NOT NULL", Long.class, job.getId());

        assertThat(count).isEqualTo(2L);
        assertThat(status).isEqualTo("OPEN");
        assertThat(withTime).isEqualTo(2L);
    }
}
