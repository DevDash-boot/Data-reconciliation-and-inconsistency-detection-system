package com.example.recon.mismatch;

import com.example.recon.common.PageResult;
import com.example.recon.common.exception.BadRequestException;
import com.example.recon.common.exception.NotFoundException;
import com.example.recon.job.JobRequest;
import com.example.recon.job.JobResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class MismatchQueryServiceTests {

    @Autowired UploadService uploadService;
    @Autowired ReconciliationJobService jobService;
    @Autowired MismatchQueryService queryService;
    @Autowired JdbcTemplate jdbc;

    private Long jobId;

    @BeforeEach
    void setUp() {
        TestDb.clean(jdbc);
        SampleUploads.Batches b = SampleUploads.upload(uploadService);
        JobResponse job = jobService.run(JobRequest.of(b.paymentBatchId(), b.orderBatchId(), "EXACT"));
        jobId = job.id();
    }

    @AfterEach
    void cleanUp() {
        TestDb.clean(jdbc);
    }

    @Test
    @DisplayName("조건 없이 조회하면 전부 나온다")
    void noFilter() {
        PageResult<MismatchResponse> r = queryService.search(new MismatchSearchCriteria(null, null, null, null), 0, 20);

        assertThat(r.totalElements()).isEqualTo(SampleUploads.TOTAL_MISMATCHES);
        assertThat(r.content()).hasSize(3);
    }

    @Test
    @DisplayName("유형으로 필터링한다")
    void filterByType() {
        PageResult<MismatchResponse> r = queryService.search(
                new MismatchSearchCriteria(jobId, MismatchType.VALUE_DIFF, null, null), 0, 20);

        assertThat(r.totalElements()).isEqualTo(1);
        assertThat(r.content().get(0).matchKey()).isEqualTo("O-3");
    }

    @Test
    @DisplayName("상태로 필터링한다 (모두 OPEN)")
    void filterByStatus() {
        assertThat(queryService.search(new MismatchSearchCriteria(jobId, null, MismatchStatus.OPEN, null), 0, 20)
                .totalElements()).isEqualTo(3);
        assertThat(queryService.search(new MismatchSearchCriteria(jobId, null, MismatchStatus.RESOLVED, null), 0, 20)
                .totalElements()).isZero();
    }

    @Test
    @DisplayName("키 앞부분으로 검색한다")
    void filterByKeywordPrefix() {
        PageResult<MismatchResponse> r = queryService.search(
                new MismatchSearchCriteria(null, null, null, "O-5"), 0, 20);

        assertThat(r.totalElements()).isEqualTo(1);
        assertThat(r.content().get(0).type()).isEqualTo(MismatchType.MISSING_IN_A);
    }

    @Test
    @DisplayName("검색어의 % 와 _ 는 와일드카드가 아니라 글자로 취급한다")
    void likeWildcardsAreEscaped() {
        assertThat(queryService.search(new MismatchSearchCriteria(null, null, null, "%"), 0, 20)
                .totalElements()).isZero();
        assertThat(queryService.search(new MismatchSearchCriteria(null, null, null, "O_"), 0, 20)
                .totalElements()).isZero();
    }

    @Test
    @DisplayName("페이징: size=2 면 첫 페이지 2건, 둘째 페이지 1건, 최신(id 큰 것)부터")
    void paging() {
        PageResult<MismatchResponse> p0 = queryService.search(new MismatchSearchCriteria(null, null, null, null), 0, 2);
        PageResult<MismatchResponse> p1 = queryService.search(new MismatchSearchCriteria(null, null, null, null), 1, 2);

        assertThat(p0.content()).hasSize(2);
        assertThat(p1.content()).hasSize(1);
        assertThat(p0.totalPages()).isEqualTo(2);
        assertThat(p0.content().get(0).id() > p0.content().get(1).id()).isTrue();
    }

    @Test
    @DisplayName("잘못된 page / size / enum 값은 400, 없는 id 는 404")
    void validation() {
        MismatchSearchCriteria any = new MismatchSearchCriteria(null, null, null, null);
        assertThatThrownBy(() -> queryService.search(any, -1, 20)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> queryService.search(any, 0, 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> queryService.search(any, 0, 201)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> MismatchQueryService.parseEnum(MismatchType.class, "NOPE"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> queryService.get(999999L)).isInstanceOf(NotFoundException.class);
        assertThat(MismatchQueryService.parseEnum(MismatchType.class, "")).isNull();
        assertThat(MismatchQueryService.parseEnum(MismatchType.class, "duplicate")).isEqualTo(MismatchType.DUPLICATE);
    }
}
