package com.example.recon.mismatch;

import java.time.LocalDate;

/**
 * 모든 조건은 선택. null 이면 조건 없음.
 * keyword 는 매칭 키의 "앞부분 일치".
 * from / to 는 발견 시각(created_at)의 날짜 범위 (둘 다 그날을 포함한다).
 */
public record MismatchSearchCriteria(Long jobId, MismatchType type, MismatchStatus status, String keyword,
                                     LocalDate from, LocalDate to) {

    /** 기간 조건 없이 쓰는 생성자 */
    public MismatchSearchCriteria(Long jobId, MismatchType type, MismatchStatus status, String keyword) {
        this(jobId, type, status, keyword, null, null);
    }
}
