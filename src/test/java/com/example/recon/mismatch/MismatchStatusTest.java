package com.example.recon.mismatch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MismatchStatusTest {

    @Test
    @DisplayName("OPEN 에서는 RESOLVED, IGNORED 로 바꿀 수 있다")
    void fromOpen() {
        assertThat(MismatchStatus.OPEN.canTransitionTo(MismatchStatus.RESOLVED)).isTrue();
        assertThat(MismatchStatus.OPEN.canTransitionTo(MismatchStatus.IGNORED)).isTrue();
    }

    @Test
    @DisplayName("RESOLVED, IGNORED 에서는 OPEN(재오픈)으로만 돌아갈 수 있다")
    void reopen() {
        assertThat(MismatchStatus.RESOLVED.canTransitionTo(MismatchStatus.OPEN)).isTrue();
        assertThat(MismatchStatus.IGNORED.canTransitionTo(MismatchStatus.OPEN)).isTrue();
    }

    @Test
    @DisplayName("RESOLVED <-> IGNORED 로 바로 바꿀 수 없다")
    void noDirectSwitch() {
        assertThat(MismatchStatus.RESOLVED.canTransitionTo(MismatchStatus.IGNORED)).isFalse();
        assertThat(MismatchStatus.IGNORED.canTransitionTo(MismatchStatus.RESOLVED)).isFalse();
    }

    @Test
    @DisplayName("같은 상태로의 변경은 허용하지 않는다")
    void noSameState() {
        for (MismatchStatus s : MismatchStatus.values()) {
            assertThat(s.canTransitionTo(s)).isFalse();
        }
    }
}
