package com.example.recon.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 서버가 Job 실행 도중 종료되면 RUNNING 으로 남아 같은 요청이 영원히 409 가 된다.
 * 시작할 때 PENDING/RUNNING 으로 남은 Job 을 FAILED 로 정리해 다시 실행할 수 있게 한다.
 * (서버를 여러 대 띄우는 환경에서는 다른 서버의 실행 중 Job 까지 정리하므로 별도 방식이 필요하다)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobRecovery implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        int n = jdbc.update(
                "UPDATE reconciliation_job SET status = 'FAILED', active_key = NULL, finished_at = ?, "
                        + "error_message = ? WHERE status IN ('PENDING', 'RUNNING')",
                Timestamp.valueOf(LocalDateTime.now()), "서버가 재시작되어 중단된 Job");
        if (n > 0) {
            log.warn("중단된 Job {}건을 FAILED 로 정리했습니다", n);
        }
    }
}
