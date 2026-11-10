package com.example.recon.mismatch;

import com.example.recon.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 불일치 목록/상세 화면. 템플릿에서 다루기 쉽도록 Map 으로 값을 넘긴다.
 * 주의: JMustache 는 값이 null 이거나 없으면 예외가 날 수 있으므로 null 대신 빈 문자열을 넘긴다.
 */
@Controller
@RequiredArgsConstructor
public class MismatchViewController {

    private final MismatchQueryService queryService;

    @GetMapping("/mismatches")
    public String list(@RequestParam(required = false) Long jobId,
                       @RequestParam(required = false) String type,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       Model model) {
        MismatchType typeFilter = MismatchQueryService.parseEnum(MismatchType.class, type);
        MismatchStatus statusFilter = MismatchQueryService.parseEnum(MismatchStatus.class, status);
        PageResult<MismatchResponse> result = queryService.search(
                new MismatchSearchCriteria(jobId, typeFilter, statusFilter, keyword), page, size);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (MismatchResponse m : result.content()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.id());
            row.put("jobId", m.jobId());
            row.put("matchKey", m.matchKey());
            row.put("type", m.type().name());
            row.put("status", m.status().name());
            row.put("createdAt", String.valueOf(m.createdAt()).replace('T', ' '));
            rows.add(row);
        }

        String base = "/mismatches?size=" + size
                + (jobId != null ? "&jobId=" + jobId : "")
                + (typeFilter != null ? "&type=" + typeFilter.name() : "")
                + (statusFilter != null ? "&status=" + statusFilter.name() : "")
                + (keyword != null && !keyword.isBlank() ? "&keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8) : "");

        model.addAttribute("rows", rows);
        model.addAttribute("hasRows", !rows.isEmpty());
        model.addAttribute("jobId", jobId == null ? "" : jobId);   // Mustache 는 null 값을 싫어하므로 빈 문자열
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("typeOptions", options(MismatchType.values(), typeFilter));
        model.addAttribute("statusOptions", options(MismatchStatus.values(), statusFilter));
        model.addAttribute("totalElements", result.totalElements());
        model.addAttribute("pageDisplay", result.page() + 1);
        model.addAttribute("totalPages", Math.max(result.totalPages(), 1));
        model.addAttribute("hasPrev", result.page() > 0);
        model.addAttribute("hasNext", result.page() + 1 < result.totalPages());
        model.addAttribute("prevUrl", base + "&page=" + (result.page() - 1));
        model.addAttribute("nextUrl", base + "&page=" + (result.page() + 1));
        return "mismatches";
    }

    @GetMapping("/mismatches/{id}")
    public String detail(@PathVariable Long id, Model model) {
        MismatchResponse m = queryService.get(id);
        model.addAttribute("id", m.id());
        model.addAttribute("jobId", m.jobId());
        model.addAttribute("matchKey", m.matchKey());
        model.addAttribute("type", m.type().name());
        model.addAttribute("status", m.status().name());
        model.addAttribute("diffDetail", m.diffDetail() == null ? "" : m.diffDetail());
        model.addAttribute("resolvedBy", m.resolvedBy() == null ? "" : m.resolvedBy());
        model.addAttribute("memo", m.memo() == null ? "" : m.memo());
        model.addAttribute("createdAt", String.valueOf(m.createdAt()).replace('T', ' '));
        return "mismatch-detail";
    }

    private static List<Map<String, Object>> options(Enum<?>[] values, Enum<?> selected) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Enum<?> v : values) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("value", v.name());
            o.put("selected", v == selected);
            list.add(o);
        }
        return list;
    }
}
