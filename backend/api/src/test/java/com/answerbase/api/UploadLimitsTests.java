package com.answerbase.api;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UploadLimitsTests {
    @Test
    void quotasFailClosedAndHandleCapacityBoundariesWithoutOverflow() {
        assertThatThrownBy(() -> new UploadLimits(0, 100, 1000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UploadLimits(200, -1, 1000))
                .isInstanceOf(IllegalArgumentException.class);
        var limits = new UploadLimits(2000, 100, 1000);
        assertThat(limits.exceedsTotalStorage(999, 2)).isTrue();
        assertThat(limits.exceedsTotalStorage(990, 10)).isFalse();
        assertThat(limits.exceedsStorage(90, 10)).isFalse();
        assertThat(limits.exceedsStorage(90, 11)).isTrue();
        assertThat(limits.exceedsStorage(101, 0)).isTrue();
        assertThat(
                        new UploadLimits(2000, Long.MAX_VALUE, Long.MAX_VALUE)
                                .exceedsStorage(Long.MAX_VALUE - 1, 10))
                .isTrue();
    }
}
