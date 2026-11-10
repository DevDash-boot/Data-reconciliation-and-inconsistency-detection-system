package com.example.recon.dashboard;

import com.example.recon.job.JobResponse;
import com.example.recon.job.ReconciliationJobService;
import com.example.recon.mismatch.MismatchStatus;
import com.example.recon.mismatch.MismatchType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 대시보드 통계. 집계는 JPA 로 전부 읽지 않고 DB 에서 GROUP BY 로 계산한다.
 * 인덱스 사용 여부는 #15 의 EXPLAIN 으로 확인한다. (Q5, Q6, Q7)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final int DAYS = 14;
    private static final int RECENT_JOBS = 5;

    private final JdbcTemplate jdbc;
    private final ReconciliationJobService jobService;

    public DashboardSummary summary(Long jobId) {
        String where = (jobId == null) ? "" : " WHERE job_id = ?";
        Object[] args = (jobId == null) ? new Object[0] : new Object[]{jobId};

        Map<String, Long> byType = zeroed(MismatchType.values());
        jdbc.query("SELECT type, COUNT(*) FROM mismatch" + where + " GROUP BY type",
                (RowCallbackHandler) rs -> byType.put(rs.getString(1), rs.getLong(2)), args);

        Map<String, Long> byStatus = zeroed(MismatchStatus.values());
        jdbc.query("SELECT status, COUNT(*) FROM mismatch" + where + " GROUP BY status",
                (RowCallbackHandler) rs -> byStatus.put(rs.getString(1), rs.getLong(2)), args);

        List<DailyCount> daily = new ArrayList<>();
        jdbc.query("SELECT CAST(created_at AS DATE) AS d, COUNT(*) FROM mismatch" + where
                        + " GROUP BY CAST(created_at AS DATE) ORDER BY d DESC LIMIT " + DAYS,
                (RowCallbackHandler) rs -> daily.add(new DailyCount(rs.getDate(1).toLocalDate().toString(), rs.getLong(2))),
                args);
        Collections.reverse(daily);   // 차트는 오래된 날짜부터

        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        List<JobResponse> recent = jobService.list().stream().limit(RECENT_JOBS).toList();
        return new DashboardSummary(jobId, total, byType, byStatus, daily, recent);
    }

    private static Map<String, Long> zeroed(Enum<?>[] values) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Enum<?> v : values) {
            map.put(v.name(), 0L);
        }
        return map;
    }
}
