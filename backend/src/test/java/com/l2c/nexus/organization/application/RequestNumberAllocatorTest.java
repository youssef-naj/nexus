package com.l2c.nexus.organization.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestNumberAllocatorTest {

    @Test
    void padsToSixDigits() {
        assertThat(RequestNumberAllocator.format(1)).isEqualTo("REQ-000001");
        assertThat(RequestNumberAllocator.format(42)).isEqualTo("REQ-000042");
    }

    @Test
    void doesNotTruncateLargeNumbers() {
        assertThat(RequestNumberAllocator.format(1_234_567)).isEqualTo("REQ-1234567");
    }

    @Test
    void theLargestReferenceThatFitsTheColumnHasSixteenDigits() {
        // varchar(20) holds "REQ-" plus 16 digits. A counter that high (about ten quadrillion
        // requests in one organization) is unreachable in practice, so the limit is documented
        // rather than guarded.
        assertThat(RequestNumberAllocator.format(9_999_999_999_999_999L)).hasSize(20);
    }
}
