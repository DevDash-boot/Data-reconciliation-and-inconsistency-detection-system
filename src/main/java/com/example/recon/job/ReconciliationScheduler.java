package com.example.recon.job;

import com.example.recon.common.exception.ConflictException;
import com.example.recon.upload.UploadBatch;
import com.example.recon.upload.UploadBatchRepository;
import com.example.recon.upload.UploadType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 주기 대조.
 *
 * 이 프로젝트의 입력은 "업로드한 CSV 배치" 이므로, 대상 기간을 계산하는 대신
 * 가장 최근에 올라온 결제 배치와 주문 배치를 짝지어 대조한다.
 * 이미 같은 조합으로 대조한 적이 있으면 #16 의 멱등성 덕분에 기존 결과를 재사용하므로
 * 몇 번을 돌려도 안전하다. (서버가 여러 대여도 active_key UNIQUE 가 중복 실행을 막는다)
 *
 * 설정 (application.yml)
 *   recon.schedule.enabled : true 일 때만 동작 (기본 false)
 *   recon.schedule.cron    : 스프링 cron (초 분 시 일 월 요일), 기본 매일 02:00
 *   recon.schedule.strategy: 사용할 전략, 기본 EXACT
 */
@Component
@ConditionalOnProperty(name = "recon.schedule.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ReconciliationScheduler {

    private final UploadBatchRepository batchRepository;
    private final ReconciliationJobService jobService;

    @Value("${recon.schedule.strategy:EXACT}")
    private String strategy;

    @Scheduled(cron = "${recon.schedule.cron:0 0 2 * * *}")
    public void scheduledRun() {
        runOnce();
    }

    /** 한 번 실행한다. 실패해도 예외를 밖으로 던지지 않는다. (스케줄러 스레드가 죽지 않도록) */
    public Optional<JobResponse> runOnce() {
        Optional<UploadBatch> payment = batchRepository.findFirstByTypeOrderByIdDesc(UploadType.PAYMENT);
        Optional<UploadBatch> order = batchRepository.findFirstByTypeOrderByIdDesc(UploadType.ORDER);
        if (payment.isEmpty() || order.isEmpty()) {
            log.info("[SCHEDULE] 결제 또는 주문 배치가 없어 건너뜁니다");
            return Optional.empty();
        }

        JobRequest request = new JobRequest(payment.get().getId(), order.get().getId(), strategy, null, false);
        try {
            JobResponse response = jobService.run(request);
            log.info("[SCHEDULE] Job #{} {} ({})", response.id(), response.status(),
                    response.reused() ? "기존 결과 재사용" : "새로 실행");
            return Optional.of(response);
        } catch (ConflictException e) {
            log.warn("[SCHEDULE] 건너뜀: {}", e.getMessage());
            return Optional.empty();
        } catch (RuntimeException e) {
            log.error("[SCHEDULE] 실행 실패: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
}
