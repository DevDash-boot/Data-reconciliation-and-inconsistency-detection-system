package com.example.recon.job;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Job 과 그 결과(불일치, 상태 변경 이력)를 한꺼번에 지운다. (force 재실행용) */
@Component
@RequiredArgsConstructor
public class JobCleanup {

    private final JdbcTemplate jdbc;

    @Transactional
    public void deleteJob(long jobId) {
        jdbc.update("DELETE FROM mismatch_status_history WHERE mismatch_id IN "
                + "(SELECT id FROM mismatch WHERE job_id = ?)", jobId);
        jdbc.update("DELETE FROM mismatch WHERE job_id = ?", jobId);
        jdbc.update("DELETE FROM reconciliation_job WHERE id = ?", jobId);
    }
}
