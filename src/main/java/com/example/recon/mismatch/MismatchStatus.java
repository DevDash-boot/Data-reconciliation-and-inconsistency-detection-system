package com.example.recon.mismatch;

/**
 * 불일치 처리 상태.
 *
 * 허용되는 전이
 *   OPEN     -> RESOLVED, IGNORED
 *   RESOLVED -> OPEN (재오픈)
 *   IGNORED  -> OPEN (재오픈)
 * RESOLVED <-> IGNORED 로 바로 바꿀 수는 없다. (반드시 OPEN 으로 되돌린 뒤 다시 판단)
 * 같은 상태로의 변경도 허용하지 않는다.
 */
public enum MismatchStatus {
    OPEN,
    RESOLVED,
    IGNORED;

    public boolean canTransitionTo(MismatchStatus to) {
        return switch (this) {
            case OPEN -> to == RESOLVED || to == IGNORED;
            case RESOLVED, IGNORED -> to == OPEN;
        };
    }
}
