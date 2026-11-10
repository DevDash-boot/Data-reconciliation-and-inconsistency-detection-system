package com.example.recon.mismatch;

/** 모든 조건은 선택. null 이면 조건 없음. keyword 는 매칭 키의 "앞부분 일치". */
public record MismatchSearchCriteria(Long jobId, MismatchType type, MismatchStatus status, String keyword) {
}
