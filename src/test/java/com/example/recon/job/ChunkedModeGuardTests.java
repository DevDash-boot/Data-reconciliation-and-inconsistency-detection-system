package com.example.recon.job;

import com.example.recon.common.exception.BadRequestException;
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
class ChunkedModeGuardTests {

    @Autowired UploadService uploadService;
    @Autowired ReconciliationJobService jobService;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        TestDb.clean(jdbc);
    }

    @Test
    @DisplayName("테스트 DB(H2)에서는 CHUNKED 를 거부하고 Job 을 만들지 않는다")
    void chunkedRejectedOnH2() {
        UploadResult a = uploadService.upload(UploadType.PAYMENT, "a.csv",
                TestCsv.stream(TestCsv.payments("imp_1,O-1,1,paid,2026-11-01T10:00:00")));
        UploadResult b = uploadService.upload(UploadType.ORDER, "b.csv",
                TestCsv.stream(TestCsv.orders("O-1,1,PAID,2026-11-01T10:00:00")));

        assertThatThrownBy(() -> jobService.run(new JobRequest(a.batchId(), b.batchId(), "EXACT", "CHUNKED", null)))
                .isInstanceOf(BadRequestException.class);
        assertThat(jobService.list()).isEmpty();
    }

    @Test
    @DisplayName("IN_MEMORY 모드는 H2 에서도 정상 동작한다")
    void inMemoryWorks() {
        UploadResult a = uploadService.upload(UploadType.PAYMENT, "a.csv",
                TestCsv.stream(TestCsv.payments("imp_1,O-1,1,paid,2026-11-01T10:00:00")));
        UploadResult b = uploadService.upload(UploadType.ORDER, "b.csv",
                TestCsv.stream(TestCsv.orders("O-1,1,PAID,2026-11-01T10:00:00")));

        JobResponse r = jobService.run(new JobRequest(a.batchId(), b.batchId(), "EXACT", "IN_MEMORY", null));

        assertThat(r.status()).isEqualTo("SUCCESS");
    }
}
