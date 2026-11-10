package com.example.recon.mismatch;

import com.example.recon.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/mismatches")
@RequiredArgsConstructor
public class MismatchController {

    private final MismatchQueryService queryService;
    private final MismatchStatusService statusService;

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

    /** 처리자(actor)는 로그인한 사용자. 로그인이 없는 동안(#21 이전)은 "anonymous". */
    @PatchMapping("/{id}/status")
    public MismatchResponse changeStatus(@PathVariable Long id, @RequestBody StatusChangeRequest request,
                                         Principal principal) {
        return statusService.change(id, request.status(), request.memo(), actor(principal));
    }

    @PatchMapping("/status")
    public List<MismatchResponse> changeStatusBulk(@RequestBody BulkStatusChangeRequest request,
                                                   Principal principal) {
        return statusService.changeBulk(request.ids(), request.status(), request.memo(), actor(principal));
    }

    @GetMapping("/{id}/history")
    public List<StatusHistoryResponse> history(@PathVariable Long id) {
        return statusService.history(id);
    }

    private static String actor(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
