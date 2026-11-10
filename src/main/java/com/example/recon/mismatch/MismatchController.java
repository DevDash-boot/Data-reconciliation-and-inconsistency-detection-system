package com.example.recon.mismatch;

import com.example.recon.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mismatches")
@RequiredArgsConstructor
public class MismatchController {

    private final MismatchQueryService queryService;

    /** GET /api/mismatches?jobId=1&type=VALUE_DIFF&status=OPEN&keyword=O-00&page=0&size=20 */
    @GetMapping
    public PageResult<MismatchResponse> search(@RequestParam(required = false) Long jobId,
                                               @RequestParam(required = false) String type,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        MismatchSearchCriteria criteria = new MismatchSearchCriteria(jobId,
                MismatchQueryService.parseEnum(MismatchType.class, type),
                MismatchQueryService.parseEnum(MismatchStatus.class, status),
                keyword);
        return queryService.search(criteria, page, size);
    }

    @GetMapping("/{id}")
    public MismatchResponse get(@PathVariable Long id) {
        return queryService.get(id);
    }
}
