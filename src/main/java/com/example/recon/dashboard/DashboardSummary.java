package com.example.recon.dashboard;

import com.example.recon.job.JobResponse;

import java.util.List;
import java.util.Map;

/**
 * 대시보드 통계. jobId 가 null 이면 전체 Job 기준.
 * byType, byStatus 는 값이 0 이어도 모든 항목을 포함한다. (차트가 비어 보이지 않게)
 */
public record DashboardSummary(Long jobId, long total, Map<String, Long> byType, Map<String, Long> byStatus,
                               List<DailyCount> daily, List<JobResponse> recentJobs) {
}
