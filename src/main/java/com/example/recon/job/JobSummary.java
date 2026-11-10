package com.example.recon.job;

/** Job 결과 요약. total 은 대조 범위 안의 행 수, excluded 는 범위 규칙으로 제외된 행 수. */
public record JobSummary(int totalA, int totalB, int excludedA, int excludedB,
                         int missingInA, int missingInB, int duplicate, int valueDiff) {
}
