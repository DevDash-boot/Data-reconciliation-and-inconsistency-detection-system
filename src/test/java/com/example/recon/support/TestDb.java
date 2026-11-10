package com.example.recon.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/** 커밋되는 테스트 뒤에 데이터를 비운다. (아직 없는 테이블은 무시) */
public final class TestDb {

    private static final List<String> TABLES = List.of(
            "mismatch_status_history", "mismatch", "reconciliation_job",
            "source_payment", "source_order", "upload_batch");

    private TestDb() {
    }

    public static void clean(JdbcTemplate jdbc) {
        for (String table : TABLES) {
            try {
                jdbc.execute("DELETE FROM " + table);
            } catch (Exception ignored) {
                // 해당 이슈에서 만들어지는 테이블이면 아직 없을 수 있다
            }
        }
    }
}
