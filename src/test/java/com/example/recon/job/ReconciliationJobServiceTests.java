package com.example.recon.job;

import com.example.recon.common.exception.BadRequestException;
import com.example.recon.common.exception.NotFoundException;
import com.example.recon.mismatch.MismatchRepository;
import com.example.recon.support.TestCsv;
import com.example.recon.support.TestDb;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ReconciliationJobServiceTests {

    @Autowired UploadService uploadService;
    @Autowired ReconciliationJobService jobService;
    @Autowired MismatchRepository mismatchRepository;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        TestDb.clean(jdbc);
    }

    private UploadResult[] uploadSample() {
        UploadResult a = uploadService.upload(UploadType.PAYMENT, "a.csv", TestCsv.stream(TestCsv.payments(
                "imp_1,O-1,100,paid,2026-11-01T10:00:00",     // 정상
                "imp_2,O-2,100,paid,2026-11-01T10:00:00",     // MISSING_IN_B
                "imp_3,O-3,100,paid,2026-11-01T10:00:00",     // VALUE_DIFF
                "imp_4,O-4,100,ready,2026-11-01T10:00:00")));  // 범위 밖 (제외)
        UploadResult b = uploadService.upload(UploadType.ORDER, "b.csv", TestCsv.stream(TestCsv.orders(
                "O-1,100,PAID,2026-11-01T10:00:00",
                "O-3,90,PAID,2026-11-01T10:00:00",
                "O-5,100,PAID,2026-11-01T10:00:00",           // MISSING_IN_A
                "O-6,100,PENDING,2026-11-01T10:00:00")));      // 범위 밖 (제외)
        return new UploadResult[]{a, b};
    }

    @Test
    @DisplayName("대조를 실행하면 유형별 결과가 저장되고 Job 요약이 채워진다")
    void runStoresResults() {
        UploadResult[] up = uploadSample();

        JobResponse r = jobService.run(JobRequest.of(up[0].batchId(), up[1].batchId(), "EXACT"));

        assertThat(r.status()).isEqualTo("SUCCESS");
        assertThat(r.totalA()).isEqualTo(3);
        assertThat(r.totalB()).isEqualTo(3);
        assertThat(r.excludedA()).isEqualTo(1);
        assertThat(r.excludedB()).isEqualTo(1);
        assertThat(r.missingInA()).isEqualTo(1);
        assertThat(r.missingInB()).isEqualTo(1);
        assertThat(r.valueDiff()).isEqualTo(1);
        assertThat(r.duplicate()).isZero();
        assertThat(mismatchRepository.findByJobId(r.id())).hasSize(3);
    }

    @Test
    @DisplayName("저장된 Job 을 조회할 수 있다")
    void getJob() {
        UploadResult[] up = uploadSample();
        JobResponse r = jobService.run(JobRequest.of(up[0].batchId(), up[1].batchId(), null));

        JobResponse found = jobService.get(r.id());

        assertThat(found.strategy()).isEqualTo("EXACT");
        assertThat(found.status()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("존재하지 않는 배치면 404")
    void unknownBatch() {
        assertThatThrownBy(() -> jobService.run(JobRequest.of(9999L, 9998L, "EXACT")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("결제 자리에 주문 배치를 넣으면 400")
    void wrongBatchType() {
        UploadResult[] up = uploadSample();

        assertThatThrownBy(() -> jobService.run(JobRequest.of(up[1].batchId(), up[0].batchId(), "EXACT")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("모르는 전략이면 400 이고 Job 이 만들어지지 않는다")
    void unknownStrategy() {
        UploadResult[] up = uploadSample();

        assertThatThrownBy(() -> jobService.run(JobRequest.of(up[0].batchId(), up[1].batchId(), "FUZZY")))
                .isInstanceOf(BadRequestException.class);
        assertThat(jobService.list()).isEmpty();
    }
}
