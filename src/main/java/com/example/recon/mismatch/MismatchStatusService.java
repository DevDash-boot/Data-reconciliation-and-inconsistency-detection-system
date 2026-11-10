package com.example.recon.mismatch;

import com.example.recon.common.exception.BadRequestException;
import com.example.recon.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 불일치 처리 상태 변경. 모든 변경은 이력(mismatch_status_history)에 남는다. */
@Service
@RequiredArgsConstructor
public class MismatchStatusService {

    public static final int MAX_BULK = 1000;
    private static final int MAX_MEMO = 500;

    private final MismatchRepository mismatchRepository;
    private final MismatchStatusHistoryRepository historyRepository;

    @Transactional
    public MismatchResponse change(Long id, String status, String memo, String actor) {
        MismatchStatus to = parseTarget(status);
        validateMemo(memo);
        Mismatch m = mismatchRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("불일치를 찾을 수 없습니다: " + id));
        apply(m, to, memo, actorOrDefault(actor));
        return MismatchResponse.from(m);
    }

    /** 일괄 변경. 전부 성공하거나 전부 실패한다 (하나라도 규칙에 어긋나면 아무것도 바꾸지 않는다). */
    @Transactional
    public List<MismatchResponse> changeBulk(List<Long> ids, String status, String memo, String actor) {
        if (ids == null || ids.isEmpty()) {
            throw new BadRequestException("ids 가 비어 있습니다");
        }
        Set<Long> distinct = new LinkedHashSet<>(ids);
        if (distinct.size() > MAX_BULK) {
            throw new BadRequestException("한 번에 최대 " + MAX_BULK + "건까지 변경할 수 있습니다");
        }
        MismatchStatus to = parseTarget(status);
        validateMemo(memo);

        List<Mismatch> found = mismatchRepository.findAllById(distinct);
        if (found.size() != distinct.size()) {
            Set<Long> missing = new LinkedHashSet<>(distinct);
            found.forEach(m -> missing.remove(m.getId()));
            throw new NotFoundException("불일치를 찾을 수 없습니다: " + missing);
        }
        for (Mismatch m : found) {   // 먼저 전부 검증
            if (!m.getStatus().canTransitionTo(to)) {
                throw new BadRequestException("#" + m.getId() + " 는 " + m.getStatus() + " -> " + to
                        + " 로 변경할 수 없습니다. 일괄 변경이 취소되었습니다");
            }
        }
        String by = actorOrDefault(actor);
        List<MismatchResponse> result = new ArrayList<>();
        for (Mismatch m : found) {
            apply(m, to, memo, by);
            result.add(MismatchResponse.from(m));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> history(Long mismatchId) {
        if (!mismatchRepository.existsById(mismatchId)) {
            throw new NotFoundException("불일치를 찾을 수 없습니다: " + mismatchId);
        }
        return historyRepository.findByMismatchIdOrderByIdAsc(mismatchId)
                .stream().map(StatusHistoryResponse::from).toList();
    }

    private void apply(Mismatch m, MismatchStatus to, String memo, String actor) {
        MismatchStatus from = m.getStatus();
        if (!from.canTransitionTo(to)) {
            throw new BadRequestException("#" + m.getId() + " 는 " + from + " -> " + to + " 로 변경할 수 없습니다");
        }
        m.changeStatus(to, actor, memo);
        historyRepository.save(MismatchStatusHistory.builder()
                .mismatchId(m.getId()).fromStatus(from).toStatus(to).changedBy(actor).memo(memo).build());
    }

    private MismatchStatus parseTarget(String status) {
        MismatchStatus to = MismatchQueryService.parseEnum(MismatchStatus.class, status);
        if (to == null) {
            throw new BadRequestException("status 가 필요합니다 (OPEN / RESOLVED / IGNORED)");
        }
        return to;
    }

    private void validateMemo(String memo) {
        if (memo != null && memo.length() > MAX_MEMO) {
            throw new BadRequestException("memo 는 " + MAX_MEMO + "자 이하여야 합니다");
        }
    }

    private String actorOrDefault(String actor) {
        return (actor == null || actor.isBlank()) ? "anonymous" : actor;
    }
}
