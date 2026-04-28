package kr.co.mz.agenticai.core.common.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmptyToolProviderTest {

    @Test
    void returnsEmptyToolList() {
        assertThat(new EmptyToolProvider().tools()).isEmpty();
    }
}
