package com.li.lipicturecloud.application.airuntime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectivityResultTest {

    @Test
    void factoriesBuildValidResults() {
        assertThat(ConnectivityResult.success().reachable()).isTrue();
        assertThat(ConnectivityResult.success().safeErrorCode()).isNull();

        ConnectivityResult failed = ConnectivityResult.failed(ConnectivityResult.UPSTREAM_TIMEOUT);
        assertThat(failed.reachable()).isFalse();
        assertThat(failed.safeErrorCode()).isEqualTo("UPSTREAM_TIMEOUT");
    }

    @Test
    void rejectsInconsistentFields() {
        assertThatThrownBy(() -> new ConnectivityResult(true, "EXTRA"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConnectivityResult(false, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConnectivityResult(false, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
