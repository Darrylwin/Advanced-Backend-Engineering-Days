package com.darcode.day02;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("atomic")
class AtomicWithdrawalTest extends AbstractWithdrawalApiTest {

    @Test
    void concurrentWithdrawals_areAllApplied_withoutAnyLock() throws Exception {
        Outcome outcome = withdrawConcurrently();

        assertThat(outcome.accepted()).isEqualTo(CONCURRENT_REQUESTS);
        assertThat(lostDebits(outcome)).isZero();
        assertThat(balance()).isEqualTo(INITIAL_BALANCE_CENTS - CONCURRENT_REQUESTS * AMOUNT_CENTS);
    }
}