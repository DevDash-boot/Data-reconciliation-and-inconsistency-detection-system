package com.example.recon.job;

import com.example.recon.support.SampleUploads;
import com.example.recon.support.TestDb;
import com.example.recon.upload.UploadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// cron 은 일부러 먼 미래(1월 1일 0시)로 두고, runOnce() 를 직접 호출해 검증한다
@SpringBootTest(properties = {"recon.schedule.enabled=true", "recon.schedule.cron=0 0 0 1 1 *"})
@ActiveProfiles("test")
class ReconciliationSchedulerTests {

    @Autowired ReconciliationScheduler scheduler;
    @Autowired UploadService uploadService;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        TestDb.clean(jdbc);
    }

    @Test
    @DisplayName("업로드된 배치가 없으면 아무것도 하지 않는다")
    void noBatches() {
        assertThat(scheduler.runOnce().isPresent()).isFalse();
    }

    @Test
    @DisplayName("가장 최근 결제/주문 배치로 대조하고, 같은 조합이면 기존 결과를 재사용한다")
    void runsLatestBatchesAndReuses() {
        SampleUploads.upload(uploadService);

        Optional<JobResponse> first = scheduler.runOnce();
        Optional<JobResponse> second = scheduler.runOnce();

        assertThat(first.isPresent()).isTrue();
        assertThat(first.get().status()).isEqualTo("SUCCESS");
        assertThat(first.get().reused()).isFalse();
        assertThat(second.get().reused()).isTrue();
        assertThat(second.get().id()).isEqualTo(first.get().id());
    }

    @Test
    @DisplayName("새 배치가 올라오면 새 Job 이 만들어진다")
    void newBatchesCreateNewJob() {
        SampleUploads.upload(uploadService);
        JobResponse first = scheduler.runOnce().orElseThrow();

        SampleUploads.upload(uploadService);
        JobResponse second = scheduler.runOnce().orElseThrow();

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.reused()).isFalse();
    }
}
