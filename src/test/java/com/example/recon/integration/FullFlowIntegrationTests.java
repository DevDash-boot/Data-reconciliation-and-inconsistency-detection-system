package com.example.recon.integration;

import com.example.recon.generator.SampleDataGenerator;
import com.example.recon.support.JsonLite;
import com.example.recon.support.TestDb;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 업로드 -> 대조 -> 조회 -> 상태 변경 -> 대시보드 -> 권한까지 HTTP 요청으로 전체 흐름을 검증한다.
 * 데이터는 샘플 생성기(#4)로 만들고, 결과를 생성기의 expected 값과 비교한다.
 * 테스트 DB 는 H2 이므로 MySQL 전용 기능(CHUNKED)은 여기서 다루지 않는다. (PerfRunner 로 MySQL 에서 확인)
 */
@SpringBootTest
@ActiveProfiles("test")
class FullFlowIntegrationTests {

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;

    private MockMvc mvc;
    private final RequestPostProcessor admin = user("admin").roles("ADMIN");
    private final RequestPostProcessor reader = user("reader").roles("USER");

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        TestDb.clean(jdbc);
    }

    @AfterEach
    void cleanUp() {
        TestDb.clean(jdbc);
    }

    private Path generate(int size) throws IOException {
        Path dir = Files.createTempDirectory("recon-it-");
        SampleDataGenerator.generate(new SampleDataGenerator.Config(size, 0.02, 0.02, 0.01, 0.03, 0.02, 7L, dir));
        return dir;
    }

    private String uploadJson(String type, Path file) throws Exception {
        return mvc.perform(multipart("/api/uploads")
                        .file(new MockMultipartFile("file", file.getFileName().toString(), "text/csv",
                                Files.readAllBytes(file)))
                        .param("type", type).with(admin))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String runJob(long paymentBatch, long orderBatch, String strategy, int expectedStatus) throws Exception {
        return mvc.perform(post("/api/jobs").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentBatchId\":" + paymentBatch + ",\"orderBatchId\":" + orderBatch
                                + ",\"strategy\":\"" + strategy + "\"}"))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("전체 흐름: 업로드 -> 대조 -> 목록 -> 상태 변경 -> 대시보드, 결과는 생성기의 expected 와 일치")
    void fullFlow() throws Exception {
        Path dir = generate(300);
        SampleDataGenerator.Expected expected = SampleDataGenerator.readExpected(dir);

        // 1) 업로드
        String payUpload = uploadJson("PAYMENT", dir.resolve(SampleDataGenerator.PAYMENTS_FILE));
        String ordUpload = uploadJson("ORDER", dir.resolve(SampleDataGenerator.ORDERS_FILE));
        assertThat(JsonLite.getLong(payUpload, "validRows")).isEqualTo(expected.totalA());
        assertThat(JsonLite.getLong(payUpload, "errorRows")).isEqualTo(0L);
        assertThat(JsonLite.getLong(ordUpload, "validRows")).isEqualTo(expected.totalB());
        long pb = JsonLite.getLong(payUpload, "batchId");
        long ob = JsonLite.getLong(ordUpload, "batchId");

        // 2) 대조 -> 유형별 건수가 expected 와 같다
        String job = runJob(pb, ob, "EXACT", 201);
        assertThat(JsonLite.getLong(job, "missingInA")).isEqualTo(expected.missingInA());
        assertThat(JsonLite.getLong(job, "missingInB")).isEqualTo(expected.missingInB());
        assertThat(JsonLite.getLong(job, "duplicate")).isEqualTo(expected.duplicate());
        assertThat(JsonLite.getLong(job, "valueDiff")).isEqualTo(expected.valueDiff());
        assertThat(JsonLite.getLong(job, "excludedA")).isEqualTo(expected.excludedA());
        assertThat(job).contains("\"status\":\"SUCCESS\"");
        long jobId = JsonLite.getLong(job, "id");

        // 3) 같은 요청을 다시 보내면 200 + 기존 결과 재사용 (멱등성)
        String again = runJob(pb, ob, "EXACT", 200);
        assertThat(again).contains("\"reused\":true");
        assertThat(JsonLite.getLong(again, "id")).isEqualTo(jobId);

        // 4) 목록 조회: 유형 필터 건수가 expected 와 같다
        String diffs = mvc.perform(get("/api/mismatches").with(reader)
                        .param("jobId", String.valueOf(jobId)).param("type", "VALUE_DIFF").param("size", "200"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(JsonLite.getLong(diffs, "totalElements")).isEqualTo(expected.valueDiff());
        long mismatchId = JsonLite.getLong(diffs, "id");

        // 5) 상태 변경 (ADMIN) -> 같은 변경을 또 하면 400 (이미 RESOLVED)
        mvc.perform(patch("/api/mismatches/" + mismatchId + "/status").with(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"memo\":\"확인 완료\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/mismatches/" + mismatchId + "/status").with(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isBadRequest());
        String history = mvc.perform(get("/api/mismatches/" + mismatchId + "/history").with(reader))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(history).contains("\"toStatus\":\"RESOLVED\"");
        assertThat(history).contains("admin");

        // 6) 대시보드: 합계와 상태별 건수
        String dash = mvc.perform(get("/api/dashboard/summary").with(reader).param("jobId", String.valueOf(jobId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(JsonLite.getLong(dash, "total")).isEqualTo(expected.totalMismatches());
        assertThat(JsonLite.getLong(dash, "RESOLVED")).isEqualTo(1L);
        assertThat(JsonLite.getLong(dash, "OPEN")).isEqualTo(expected.totalMismatches() - 1L);

        // 7) 화면도 렌더링된다
        mvc.perform(get("/mismatches").with(reader).param("jobId", String.valueOf(jobId))).andExpect(status().isOk());
        mvc.perform(get("/dashboard").with(reader)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("헤더가 틀린 파일은 400 이고 아무것도 저장되지 않는다")
    void wrongHeaderIsRejected() throws Exception {
        mvc.perform(multipart("/api/uploads")
                        .file(new MockMultipartFile("file", "bad.csv", "text/csv", "a,b,c\n1,2,3\n".getBytes()))
                        .param("type", "PAYMENT").with(admin))
                .andExpect(status().isBadRequest());

        Long batches = jdbc.queryForObject("SELECT COUNT(*) FROM upload_batch", Long.class);
        assertThat(batches).isEqualTo(0L);
    }

    @Test
    @DisplayName("type 이 잘못되면 400")
    void invalidTypeIsRejected() throws Exception {
        mvc.perform(multipart("/api/uploads")
                        .file(new MockMultipartFile("file", "a.csv", "text/csv", "x".getBytes()))
                        .param("type", "REFUND").with(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("AMOUNT_ONLY 는 상태 차이를 무시하므로 EXACT 보다 VALUE_DIFF 가 같거나 적다")
    void strategiesDiffer() throws Exception {
        Path dir = generate(400);
        long pb = JsonLite.getLong(uploadJson("PAYMENT", dir.resolve(SampleDataGenerator.PAYMENTS_FILE)), "batchId");
        long ob = JsonLite.getLong(uploadJson("ORDER", dir.resolve(SampleDataGenerator.ORDERS_FILE)), "batchId");

        long exact = JsonLite.getLong(runJob(pb, ob, "EXACT", 201), "valueDiff");
        long amountOnly = JsonLite.getLong(runJob(pb, ob, "AMOUNT_ONLY", 201), "valueDiff");

        assertThat(amountOnly).isLessThanOrEqualTo(exact);
    }

    @Test
    @DisplayName("권한: 비로그인 401, USER 는 조회만, 존재하지 않는 Job 은 404")
    void permissionsAndNotFound() throws Exception {
        mvc.perform(get("/api/jobs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/jobs").with(reader)).andExpect(status().isOk());
        mvc.perform(post("/api/jobs").with(reader).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/jobs/999999").with(reader)).andExpect(status().isNotFound());
    }
}
