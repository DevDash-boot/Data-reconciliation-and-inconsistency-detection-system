package com.example.recon.mismatch;

import com.example.recon.common.exception.BadRequestException;
import com.example.recon.common.exception.NotFoundException;
import com.example.recon.job.JobRequest;
import com.example.recon.job.ReconciliationJobService;
import com.example.recon.support.SampleUploads;
import com.example.recon.support.TestDb;
import com.example.recon.upload.UploadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class MismatchStatusServiceTests {

    @Autowired UploadService uploadService;
    @Autowired ReconciliationJobService jobService;
    @Autowired MismatchStatusService statusService;
    @Autowired MismatchRepository mismatchRepository;
    @Autowired JdbcTemplate jdbc;

    private List<Long> ids;

    @BeforeEach
    void setUp() {
        TestDb.clean(jdbc);
        SampleUploads.Batches b = SampleUploads.upload(uploadService);
        Long jobId = jobService.run(JobRequest.of(b.paymentBatchId(), b.orderBatchId(), "EXACT")).id();
        ids = mismatchRepository.findByJobId(jobId).stream().map(Mismatch::getId).toList();
    }

    @AfterEach
    void cleanUp() {
        TestDb.clean(jdbc);
    }

    @Test
    @DisplayName("OPEN -> RESOLVED: 처리자/시각/메모가 기록되고 이력이 남는다")
    void resolve() {
        MismatchResponse r = statusService.change(ids.get(0), "RESOLVED", "원인 확인", "admin");

        assertThat(r.status()).isEqualTo(MismatchStatus.RESOLVED);
        assertThat(r.resolvedBy()).isEqualTo("admin");
        assertThat(r.resolvedAt()).isNotNull();
        assertThat(r.memo()).isEqualTo("원인 확인");
        List<StatusHistoryResponse> history = statusService.history(ids.get(0));
        assertThat(history).hasSize(1);
        assertThat(history.get(0).fromStatus()).isEqualTo(MismatchStatus.OPEN);
        assertThat(history.get(0).toStatus()).isEqualTo(MismatchStatus.RESOLVED);
    }

    @Test
    @DisplayName("재오픈하면 처리자/시각이 비워지고 이력이 2건이 된다")
    void reopen() {
        statusService.change(ids.get(0), "IGNORED", "테스트 데이터", "admin");

        MismatchResponse r = statusService.change(ids.get(0), "OPEN", null, "admin");

        assertThat(r.status()).isEqualTo(MismatchStatus.OPEN);
        assertThat(r.resolvedBy()).isNull();
        assertThat(r.resolvedAt()).isNull();
        assertThat(statusService.history(ids.get(0))).hasSize(2);
    }

    @Test
    @DisplayName("허용되지 않는 전이는 400 이고 이력이 남지 않는다")
    void invalidTransition() {
        statusService.change(ids.get(0), "RESOLVED", null, "admin");

        assertThatThrownBy(() -> statusService.change(ids.get(0), "IGNORED", null, "admin"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> statusService.change(ids.get(1), "OPEN", null, "admin"))
                .isInstanceOf(BadRequestException.class);
        assertThat(statusService.history(ids.get(0))).hasSize(1);
    }

    @Test
    @DisplayName("없는 id 는 404, 잘못된 status 는 400")
    void notFoundAndInvalidValue() {
        assertThatThrownBy(() -> statusService.change(999999L, "RESOLVED", null, "admin"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> statusService.change(ids.get(0), "DONE", null, "admin"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> statusService.change(ids.get(0), null, null, "admin"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("일괄 변경: 전부 바뀌고 이력이 건마다 남는다")
    void bulkSuccess() {
        List<MismatchResponse> r = statusService.changeBulk(ids, "IGNORED", "일괄 무시", "admin");

        assertThat(r).hasSize(3);
        for (Long id : ids) {
            assertThat(mismatchRepository.findById(id).orElseThrow().getStatus()).isEqualTo(MismatchStatus.IGNORED);
            assertThat(statusService.history(id)).hasSize(1);
        }
    }

    @Test
    @DisplayName("일괄 변경: 하나라도 규칙에 어긋나면 아무것도 바뀌지 않는다 (전부 또는 전무)")
    void bulkIsAllOrNothing() {
        statusService.change(ids.get(0), "RESOLVED", null, "admin");   // 하나는 이미 RESOLVED

        assertThatThrownBy(() -> statusService.changeBulk(ids, "RESOLVED", null, "admin"))
                .isInstanceOf(BadRequestException.class);

        assertThat(mismatchRepository.findById(ids.get(1)).orElseThrow().getStatus()).isEqualTo(MismatchStatus.OPEN);
        assertThat(mismatchRepository.findById(ids.get(2)).orElseThrow().getStatus()).isEqualTo(MismatchStatus.OPEN);
    }

    @Test
    @DisplayName("일괄 변경: 빈 목록은 400, 없는 id 가 섞이면 404")
    void bulkValidation() {
        assertThatThrownBy(() -> statusService.changeBulk(List.of(), "RESOLVED", null, "admin"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> statusService.changeBulk(List.of(ids.get(0), 999999L), "RESOLVED", null, "admin"))
                .isInstanceOf(NotFoundException.class);
    }
}
