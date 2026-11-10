package com.example.recon.job;

public enum ExecutionMode {
    /** 전체를 메모리에 올려 한 번에 대조 */
    IN_MEMORY,
    /** 키 해시로 N 개 청크로 나눠 청크별로 대조 (메모리 사용량이 1/N) */
    CHUNKED
}
