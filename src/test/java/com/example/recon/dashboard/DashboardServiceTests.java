package com.example.recon.dashboard;

import com.example.recon.job.JobRequest;
import com.example.recon.job.ReconciliationJobService;
import com.example.recon.mismatch.MismatchStatusService;
import com.example.recon.mismatch.MismatchRepository;
import com.example.recon.mismatch.Mismatch;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DashboardServiceTests {

    @Autowired UploadService uploadService;
    @Autowired ReconciliationJobService jobService;
    @Autowired MismatchStatusService statusService;
    @Autowired MismatchRepository mismatchRepository;
    @Autowired DashboardService dashboardService;
    @Autowired JdbcTemplate jdbc;

    private Long jobId;

    @BeforeEach
    void setUp() {
        TestDb.clean(jdbc);
        SampleUploads.Batches b = SampleUploads.upload(uploadService);
        jobId = jobService.run(JobRequest.of(b.paymentBatchId(), b.orderBatchId(), "EXACT")).id();
    }

    @AfterEach
    void cleanUp() {
        TestDb.clean(jdbc);
    }

    @Test
    @DisplayName("유형별 집계가 DB 의 실제 건수와 같고, 0 건인 유형도 포함된다")
    void byTypeMatchesDatabase() {
        DashboardSummary s = dashboardService.summary(null);

        assertThat(s.total()).isEqualTo(SampleUploads.TOTAL_MISMATCHES);
        assertThat(s.byType().get("MISSING_IN_A")).isEqualTo(1L);
        assertThat(s.byType().get("MISSING_IN_B")).isEqualTo(1L);
        assertThat(s.byType().get("VALUE_DIFF")).isEqualTo(1L);
        assertThat(s.byType().get("DUPLICATE")).isEqualTo(0L);
        assertThat(s.byType()).hasSize(4);
    }

    @Test
    @DisplayName("상태를 바꾸면 상태별 집계에 반영된다")
    void byStatusReflectsChanges() {
        Mismatch first = mismatchRepository.findByJobId(jobId).get(0);
        statusService.change(first.getId(), "RESOLVED", null, "admin");

        DashboardSummary s = dashboardService.summary(jobId);

        assertThat(s.byStatus().get("OPEN")).isEqualTo(2L);
        assertThat(s.byStatus().get("RESOLVED")).isEqualTo(1L);
        assertThat(s.byStatus().get("IGNORED")).isEqualTo(0L);
        assertThat(s.total()).isEqualTo(3L);
    }

    @Test
    @DisplayName("일별 추이는 오늘 하루에 3건으로 나온다")
    void dailyTrend() {
        DashboardSummary s = dashboardService.summary(null);

        assertThat(s.daily()).hasSize(1);
        assertThat(s.daily().get(0).count()).isEqualTo(3L);
    }

    @Test
    @DisplayName("없는 Job 을 지정하면 전부 0 이다")
    void unknownJob() {
        DashboardSummary s = dashboardService.summary(999999L);

        assertThat(s.total()).isEqualTo(0L);
        assertThat(s.daily()).isEmpty();
    }

    @Test
    @DisplayName("최근 Job 목록에 방금 실행한 Job 이 들어 있다")
    void recentJobs() {
        DashboardSummary s = dashboardService.summary(null);

        assertThat(s.recentJobs()).hasSize(1);
        assertThat(s.recentJobs().get(0).id()).isEqualTo(jobId);
    }
}
