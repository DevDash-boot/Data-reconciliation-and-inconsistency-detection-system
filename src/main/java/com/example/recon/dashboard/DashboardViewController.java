package com.example.recon.dashboard;

import com.example.recon.job.JobResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 대시보드 화면. 차트 데이터는 JSON 문자열로 만들어 템플릿에 그대로 넣는다.
 * (라벨은 enum 이름과 날짜뿐이라 따옴표 이스케이프가 필요 없다)
 * 주의: JMustache 는 null/없는 값에서 예외가 날 수 있으므로 모든 값을 채워서 넘긴다.
 */
@Controller
@RequiredArgsConstructor
public class DashboardViewController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false) Long jobId, Model model) {
        DashboardSummary s = dashboardService.summary(jobId);

        model.addAttribute("jobId", jobId == null ? "" : jobId);
        model.addAttribute("total", s.total());
        model.addAttribute("scopeLabel", jobId == null ? "전체 Job" : "Job #" + jobId);
        model.addAttribute("typeLabelsJson", strings(s.byType().keySet()));
        model.addAttribute("typeValuesJson", numbers(s.byType().values()));
        model.addAttribute("statusLabelsJson", strings(s.byStatus().keySet()));
        model.addAttribute("statusValuesJson", numbers(s.byStatus().values()));
        model.addAttribute("dailyLabelsJson", strings(s.daily().stream().map(DailyCount::date).toList()));
        model.addAttribute("dailyValuesJson", numbers(s.daily().stream().map(DailyCount::count).toList()));

        List<Map<String, Object>> jobs = new ArrayList<>();
        for (JobResponse j : s.recentJobs()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", j.id());
            row.put("status", j.status());
            row.put("strategy", j.strategy());
            row.put("totalA", j.totalA());
            row.put("totalB", j.totalB());
            row.put("mismatches", j.missingInA() + j.missingInB() + j.duplicate() + j.valueDiff());
            jobs.add(row);
        }
        model.addAttribute("jobs", jobs);
        model.addAttribute("hasJobs", !jobs.isEmpty());
        return "dashboard";
    }

    private static String strings(java.util.Collection<String> values) {
        return values.stream().map(v -> "\"" + v + "\"").collect(Collectors.joining(",", "[", "]"));
    }

    private static String numbers(java.util.Collection<Long> values) {
        return values.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }
}
